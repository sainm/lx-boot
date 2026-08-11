-- Database-level closure assertions for core-business-closure.spec.ts.
-- This runs only in the disposable psy_e2e_* schema after Playwright succeeds.

do $$
declare
    target_task_id bigint;
    target_sheet_id bigint;
    target_result_id bigint;
    target_report_id bigint;
    target_warning_id bigint;
    target_intervention_id bigint;
    actual_count bigint;
begin
    select count(*), min(id)
      into actual_count, target_task_id
    from psy_assessment_task
    where task_name like 'E2E Core Closure %';
    if actual_count <> 1 then
        raise exception 'expected one E2E core task, found %', actual_count;
    end if;

    select count(*), min(id)
      into actual_count, target_sheet_id
    from psy_assessment_answer_sheet
    where task_id = target_task_id
      and answer_status = 'SUBMITTED';
    if actual_count <> 1 then
        raise exception 'expected one submitted answer sheet, found %', actual_count;
    end if;

    select count(*), min(id)
      into actual_count, target_result_id
    from psy_assessment_result
    where answer_sheet_id = target_sheet_id
      and is_current = true;
    if actual_count <> 1 then
        raise exception 'expected one current assessment result, found %', actual_count;
    end if;

    select count(*) into actual_count
    from psy_report
    where result_id = target_result_id;
    if actual_count <> 1 then
        raise exception 'expected one report, found %', actual_count;
    end if;

    select min(id) into target_report_id
    from psy_report
    where result_id = target_result_id;

    select count(*) into actual_count
    from psy_assessment_result
    where answer_sheet_id = target_sheet_id;
    if actual_count <> 1 then
        raise exception 'unauthorized rescore created result history; expected one result, found %', actual_count;
    end if;

    select count(*) into actual_count
    from psy_assessment_result
    where answer_sheet_id = 990002;
    if actual_count <> 2 then
        raise exception 'expected global administrator rescore history for fixture sheet, found %', actual_count;
    end if;

    select count(*) into actual_count
    from psy_assessment_result
    where answer_sheet_id = 990002
      and is_current = true
      and calculation_version = 2
      and supersedes_result_id = 990003
      and rescored_by = (select id from sys_user where username = 'global_admin');
    if actual_count <> 1 then
        raise exception 'expected one current globally rescored fixture result, found %', actual_count;
    end if;

    select count(*) into actual_count
    from sys_security_event
    where event_type = 'PSY_ASSESSMENT_RESULT_RESCORED'
      and detail_json ->> 'previousResultId' = '990003'
      and detail_json ->> 'resultId' = (select id::text from psy_assessment_result where answer_sheet_id = 990002 and is_current = true);
    if actual_count <> 1 then
        raise exception 'expected one global rescore audit event, found %', actual_count;
    end if;

    select count(*), min(id)
      into actual_count, target_warning_id
    from psy_warning_record
    where result_id = target_result_id
      and status = 'CLOSED';
    if actual_count <> 1 then
        raise exception 'expected one closed warning, found %', actual_count;
    end if;

    select count(*), min(id)
      into actual_count, target_intervention_id
    from psy_intervention_record
    where warning_id = target_warning_id
      and current_status = 'CLOSED';
    if actual_count <> 1 then
        raise exception 'expected one closed intervention, found %', actual_count;
    end if;

    select count(*) into actual_count
    from psy_intervention_record
    where warning_id = target_warning_id;
    if actual_count <> 1 then
        raise exception 'unauthorized intervention mutation detected; expected one intervention, found %', actual_count;
    end if;

    select count(*) into actual_count
    from psy_export_job export_job
    join psy_report report on report.id = export_job.report_id
    join psy_assessment_result result on result.id = report.result_id
    join psy_assessment_answer_sheet sheet on sheet.id = result.answer_sheet_id
    where export_job.report_id = target_report_id
      and export_job.tenant_id = sheet.tenant_id;
    if actual_count <> 1 then
        raise exception 'expected one tenant-consistent export job for report %, found %', target_report_id, actual_count;
    end if;

    select count(*) into actual_count
    from psy_export_job
    where id = 'e2e-dead-export-default'
      and status = 'DONE'
      and retry_count = 0
      and file_size > 0
      and tenant_id = (select id from sys_tenant where tenant_code = 'DEFAULT');
    if actual_count <> 1 then
        raise exception 'expected seeded dead-letter export to be replayed and downloaded, found %', actual_count;
    end if;

    select count(*) into actual_count
    from sys_security_event
    where event_type = 'PSY_EXPORT_JOB_SUBMITTED'
      and detail_json ->> 'reportId' = target_report_id::text;
    if actual_count <> 1 then
        raise exception 'expected one audited async export submission for report %, found %', target_report_id, actual_count;
    end if;

    select count(*) into actual_count
    from sys_security_event
    where event_type = 'PSY_EXPORT_JOB_REPLAYED'
      and detail_json ->> 'jobId' = 'e2e-dead-export-default'
      and detail_json ->> 'previousStatus' = 'DEAD_LETTER';
    if actual_count <> 1 then
        raise exception 'expected one audited dead-letter export replay, found %', actual_count;
    end if;

    select count(*) into actual_count
    from sys_security_event
    where event_type = 'PSY_EXPORT_JOB_DOWNLOADED'
      and detail_json ->> 'jobId' in ('e2e-dead-export-default');
    if actual_count <> 1 then
        raise exception 'expected one audited replayed export download, found %', actual_count;
    end if;

    select count(*) into actual_count
    from psy_notification notification
    join psy_notification_delivery delivery on delivery.notification_id = notification.id
    join psy_assessment_answer_sheet sheet on sheet.user_id = delivery.receiver_user_id
    where notification.notification_type = 'REPORT_GENERATED'
      and notification.biz_type = 'REPORT'
      and notification.biz_id = target_report_id
      and sheet.id = target_sheet_id
      and delivery.tenant_id = sheet.tenant_id
      and delivery.delivery_channel = 'IN_APP'
      and delivery.read_flag = true;
    if actual_count <> 1 then
        raise exception 'expected one owner-read tenant-consistent report notification, found %', actual_count;
    end if;

    select count(*) into actual_count
    from psy_warning_response_event
    where warning_id = target_warning_id
      and event_type in ('CONTACT_ATTEMPT', 'SAFETY_ASSESSMENT', 'RESPONSIBLE_HANDOFF');
    if actual_count <> 3 then
        raise exception 'expected three mandatory warning response events, found %', actual_count;
    end if;

    select count(*) into actual_count
    from psy_warning_follow_up
    where warning_id = target_warning_id
      and status = 'PENDING';
    if actual_count <> 1 then
        raise exception 'expected one pending follow-up, found %', actual_count;
    end if;

    select count(*) into actual_count
    from psy_warning_close_checklist
    where warning_id = target_warning_id
      and contact_attempt_recorded
      and safety_assessment_completed
      and responsible_handoff_completed
      and follow_up_arranged;
    if actual_count <> 1 then
        raise exception 'expected one complete warning close checklist, found %', actual_count;
    end if;

    select count(*) into actual_count
    from sys_security_event
    where event_type in ('PSY_INTERVENTION_CREATED', 'PSY_INTERVENTION_CLOSED')
      and (detail_json ->> 'warningId')::bigint = target_warning_id;
    if actual_count <> 2 then
        raise exception 'expected intervention create/close audit events, found %', actual_count;
    end if;

    select count(*) into actual_count
    from sys_security_event event
    join sys_user operator on operator.id = event.user_id
    where event.event_type = 'PSY_NOTIFICATION_DELIVERY_CALLBACK_APPLIED'
      and operator.username = 'e2e_admin'
      and event.detail_json ->> 'deliveryStatus' = 'FAILED';
    if actual_count <> 1 then
        raise exception 'expected one required notification callback audit event, found %', actual_count;
    end if;

    select count(*) into actual_count
    from sys_security_event event
    join sys_user operator on operator.id = event.user_id
    where event.event_type = 'PSY_NOTIFICATION_DELIVERIES_RETRIED'
      and operator.username = 'e2e_admin'
      and (event.detail_json ->> 'retriedCount')::integer = 1;
    if actual_count <> 1 then
        raise exception 'expected one required notification retry audit event, found %', actual_count;
    end if;

    select count(*) into actual_count
    from psy_notification_delivery delivery
    where delivery.notification_id = (
            select id from psy_notification
            where notification_type = 'REPORT_GENERATED' and biz_id = target_report_id
            order by id desc limit 1
          )
      and delivery.delivery_channel = 'PUSH'
      and (
          coalesce(delivery.error_message, '') like '%e2e-secret-token%'
          or coalesce(delivery.callback_payload_json, '') like '%e2e-secret-token%'
      );
    if actual_count <> 0 then
        raise exception 'notification callback persisted an unredacted credential';
    end if;

    select count(*) into actual_count
    from sys_security_event event
    join sys_user operator on operator.id = event.user_id
    where event.event_type = 'PSY_TENANT_SCOPE_OVERRIDE'
      and operator.username = 'global_admin';
    if actual_count < 4 then
        raise exception 'expected at least four global tenant-scope audit events, found %', actual_count;
    end if;

    select count(distinct event.detail_json ->> 'resourceType') into actual_count
    from sys_security_event event
    join sys_user operator on operator.id = event.user_id
    where event.event_type = 'PSY_TENANT_SCOPE_OVERRIDE'
      and operator.username = 'global_admin'
      and event.detail_json ->> 'resourceType' in ('REPORT', 'EXPORT_JOB', 'NOTIFICATION');
    if actual_count <> 3 then
        raise exception 'expected audited global overrides for report, export job, and notification; found % resource types', actual_count;
    end if;

    select count(*) into actual_count
    from psy_assessment_task task
    join psy_scale scale on scale.id = task.scale_id
    join psy_assessment_answer_sheet sheet on sheet.task_id = task.id
    join psy_assessment_result result on result.answer_sheet_id = sheet.id
    join psy_warning_record warning on warning.result_id = result.id
    join psy_intervention_record intervention on intervention.warning_id = warning.id
    where task.id = target_task_id
      and (
          task.tenant_id is distinct from scale.tenant_id
          or sheet.tenant_id is distinct from task.tenant_id
          or warning.tenant_id is distinct from task.tenant_id
          or intervention.tenant_id is distinct from task.tenant_id
      );
    if actual_count <> 0 then
        raise exception 'found % cross-tenant rows in the E2E core chain', actual_count;
    end if;

    raise notice 'core closure verified: task %, sheet %, result %, warning %, intervention %',
        target_task_id, target_sheet_id, target_result_id, target_warning_id, target_intervention_id;
end
$$;

-- The same technical workflow must select only APPROVED ScalePackage content
-- and persist a generated report in the request language for zh-CN, ja-JP,
-- and en-US. These are technical fixtures, not clinical translations.
do $$
declare
    actual_count bigint;
begin
    select count(*) into actual_count
    from psy_assessment_task
    where task_name like 'E2E Locale %';
    if actual_count <> 3 then
        raise exception 'expected three locale tasks, found %', actual_count;
    end if;

    select count(*) into actual_count
    from psy_assessment_task task
    join psy_assessment_answer_sheet sheet on sheet.task_id = task.id
    join psy_assessment_result result on result.answer_sheet_id = sheet.id and result.is_current = true
    join psy_report report on report.result_id = result.id
    where task.task_name like 'E2E Locale %'
      and sheet.answer_status = 'SUBMITTED'
      and sheet.user_id is not null
      and result.risk_level = 'NORMAL';
    if actual_count <> 3 then
        raise exception 'expected three localized submitted report chains, found %', actual_count;
    end if;

    select count(*) into actual_count
    from psy_warning_record warning
    join psy_assessment_result result on result.id = warning.result_id
    join psy_assessment_answer_sheet sheet on sheet.id = result.answer_sheet_id
    join psy_assessment_task task on task.id = sheet.task_id
    where task.task_name like 'E2E Locale %';
    if actual_count <> 0 then
        raise exception 'NORMAL locale workflows unexpectedly created % warnings', actual_count;
    end if;

    select count(*) into actual_count
    from psy_report report
    join psy_assessment_result result on result.id = report.result_id
    join psy_assessment_answer_sheet sheet on sheet.id = result.answer_sheet_id
    join psy_assessment_task task on task.id = sheet.task_id
    where task.task_name like 'E2E Locale zh %'
      and sheet.response_locale_code = 'zh-CN'
      and report.locale_code = 'zh-CN'
      and report.report_content like '%E2E 技术正常结果。%'
      and report.report_content like '%不等同于临床诊断%';
    if actual_count <> 1 then
        raise exception 'Chinese report snapshot was not persisted in zh-CN';
    end if;

    select count(*) into actual_count
    from psy_report report
    join psy_assessment_result result on result.id = report.result_id
    join psy_assessment_answer_sheet sheet on sheet.id = result.answer_sheet_id
    join psy_assessment_task task on task.id = sheet.task_id
    where task.task_name like 'E2E Locale ja %'
      and sheet.response_locale_code = 'ja-JP'
      and report.locale_code = 'ja-JP'
      and report.report_content like '%E2E 技術テストの正常結果です。%'
      and report.report_content like '%臨床診断ではありません%';
    if actual_count <> 1 then
        raise exception 'Japanese report snapshot was not persisted in ja-JP';
    end if;

    select count(*) into actual_count
    from psy_report report
    join psy_assessment_result result on result.id = report.result_id
    join psy_assessment_answer_sheet sheet on sheet.id = result.answer_sheet_id
    join psy_assessment_task task on task.id = sheet.task_id
    where task.task_name like 'E2E Locale en %'
      and sheet.response_locale_code = 'en'
      and report.locale_code = 'en'
      and report.report_content like '%Technical E2E normal result.%'
      and report.report_content like '%not a clinical diagnosis%';
    if actual_count <> 1 then
        raise exception 'English report snapshot was not persisted in en';
    end if;

    raise notice 'trilingual assessment/report runtime verified for zh-CN, ja-JP, and en';
end
$$;

-- A translated high-risk rule must be the content source for the immutable
-- Japanese identified report, not merely a localized low-risk result rule.
do $$
declare
    actual_count bigint;
begin
    select count(*) into actual_count
    from psy_assessment_task task
    join psy_assessment_answer_sheet sheet on sheet.task_id = task.id
    join psy_assessment_result result on result.answer_sheet_id = sheet.id and result.is_current = true
    join psy_report report on report.result_id = result.id
    where task.task_name like 'E2E High Risk Locale ja %'
      and sheet.response_locale_code = 'ja-JP'
      and report.locale_code = 'ja-JP'
      and result.high_risk_flag = true
      and result.high_risk_rule_code = 'E2E_HIGH_OPTION'
      and report.report_content like '%臨床的な意味を持たない技術自動化トリガーです。%'
      and report.report_content like '%管理されたアラートと介入フローを確認してください。%';
    if actual_count <> 1 then
        raise exception 'Japanese high-risk translation was not persisted in one identified report';
    end if;
    raise notice 'Japanese high-risk rule translation runtime verified';
end
$$;

-- First-save races and later writes with the same optimistic version must each
-- have one winner. The losing request must not overwrite any answer item.
do $$
declare
    target_task_id bigint;
    target_sheet_id bigint;
    actual_count bigint;
    winner_option_code varchar(64);
begin
    select count(*), min(id)
      into actual_count, target_task_id
    from psy_assessment_task
    where task_name like 'E2E Concurrent Save %';
    if actual_count <> 1 then
        raise exception 'expected one concurrent-save task, found %', actual_count;
    end if;

    select count(*), min(id)
      into actual_count, target_sheet_id
    from psy_assessment_answer_sheet
    where task_id = target_task_id
      and answer_status = 'DRAFT'
      and version_no = 3;
    if actual_count <> 1 then
        raise exception 'expected one version-3 draft after save races, found %', actual_count;
    end if;

    select count(*) into actual_count
    from psy_assessment_answer_sheet
    where task_id = target_task_id;
    if actual_count <> 1 then
        raise exception 'expected exactly one sheet after save races, found %', actual_count;
    end if;

    select count(*) into actual_count
    from psy_assessment_answer_item
    where answer_sheet_id = target_sheet_id;
    if actual_count <> 3 then
        raise exception 'expected three winner answer items after save races, found %', actual_count;
    end if;

    select min(option_row.option_code), count(distinct option_row.option_code)
      into winner_option_code, actual_count
    from psy_assessment_answer_item item
    join psy_scale_option option_row on option_row.id = item.option_id
    where item.answer_sheet_id = target_sheet_id;
    if actual_count <> 1 or winner_option_code not in ('A', 'D') then
        raise exception 'concurrent save left mixed or unexpected answers: count %, option %', actual_count, winner_option_code;
    end if;

    select count(*) into actual_count
    from psy_assessment_answer_sheet sheet
    join psy_assessment_task task on task.id = sheet.task_id
    join sys_user respondent on respondent.id = sheet.user_id
    where sheet.id = target_sheet_id
      and (
          sheet.tenant_id is distinct from task.tenant_id
          or sheet.tenant_id is distinct from respondent.tenant_id
      );
    if actual_count <> 0 then
        raise exception 'concurrent save produced a cross-tenant draft';
    end if;

    raise notice 'concurrent draft save verified: task %, sheet %, winning option %',
        target_task_id, target_sheet_id, winner_option_code;
end
$$;

-- Different idempotency keys racing on the same saved draft must not create
-- more than one submitted sheet/result/report. The losing request is expected
-- to receive a stable business conflict from the API.
do $$
declare
    target_task_id bigint;
    target_sheet_id bigint;
    actual_count bigint;
begin
    select count(*), min(id)
      into actual_count, target_task_id
    from psy_assessment_task
    where task_name like 'E2E Concurrent Submit %';
    if actual_count <> 1 then
        raise exception 'expected one concurrent-submit task, found %', actual_count;
    end if;

    select count(*), min(id)
      into actual_count, target_sheet_id
    from psy_assessment_answer_sheet
    where task_id = target_task_id
      and answer_status = 'SUBMITTED'
      and submit_token in (
          'concurrent-a-' || target_task_id::text,
          'concurrent-b-' || target_task_id::text
      );
    if actual_count <> 1 then
        raise exception 'expected one submitted sheet after token race, found %', actual_count;
    end if;

    select count(*) into actual_count
    from psy_assessment_answer_sheet
    where task_id = target_task_id;
    if actual_count <> 1 then
        raise exception 'expected exactly one sheet after token race, found %', actual_count;
    end if;

    select count(*) into actual_count
    from psy_assessment_result
    where answer_sheet_id = target_sheet_id
      and is_current = true;
    if actual_count <> 1 then
        raise exception 'expected one current result after token race, found %', actual_count;
    end if;

    select count(*) into actual_count
    from psy_report report
    join psy_assessment_result result on result.id = report.result_id
    where result.answer_sheet_id = target_sheet_id;
    if actual_count <> 1 then
        raise exception 'expected one report after token race, found %', actual_count;
    end if;

    raise notice 'different-token concurrency verified: task %, sheet %', target_task_id, target_sheet_id;
end
$$;

-- Anonymous submission keeps only a pseudonymous sheet token. Even when the
-- technical fixture produces a HIGH result, no identifiable personal report,
-- warning, result notification, or export source may exist.
do $$
declare
    target_task_id bigint;
    target_sheet_id bigint;
    target_result_id bigint;
    anonymous_identity varchar(128);
    actual_count bigint;
begin
    select count(*), min(id)
      into actual_count, target_task_id
    from psy_assessment_task
    where task_name like 'E2E Anonymous Privacy %'
      and anonymous_flag = true;
    if actual_count <> 1 then
        raise exception 'expected one anonymous privacy task, found %', actual_count;
    end if;

    select count(*) into actual_count
    from psy_assessment_task_assignment
    where task_id = target_task_id
      and target_type = 'USER';
    if actual_count <> 2 then
        raise exception 'expected two users in anonymous assignment cohort, found %', actual_count;
    end if;

    select count(*), min(id), min(anonymous_token)
      into actual_count, target_sheet_id, anonymous_identity
    from psy_assessment_answer_sheet
    where task_id = target_task_id
      and answer_status = 'SUBMITTED'
      and user_id is null
      and anonymous_token is not null;
    if actual_count <> 1 then
        raise exception 'expected one pseudonymous submitted sheet, found %', actual_count;
    end if;

    select count(*), min(id)
      into actual_count, target_result_id
    from psy_assessment_result
    where answer_sheet_id = target_sheet_id
      and is_current = true
      and risk_level = 'HIGH';
    if actual_count <> 1 then
        raise exception 'expected one anonymous HIGH aggregate result, found %', actual_count;
    end if;

    select count(*) into actual_count
    from psy_report
    where result_id = target_result_id;
    if actual_count <> 0 then
        raise exception 'anonymous result unexpectedly created % personal reports', actual_count;
    end if;

    select count(*) into actual_count
    from psy_warning_record
    where result_id = target_result_id;
    if actual_count <> 0 then
        raise exception 'anonymous result unexpectedly created % warnings', actual_count;
    end if;

    select count(*) into actual_count
    from psy_notification
    where notification_type in ('REPORT_GENERATED', 'REPORT_AUTO_SUBMITTED')
      and (
          payload_json like '%"resultId":' || target_result_id::text || '%'
          or payload_json like '%"taskId":' || target_task_id::text || '%'
      );
    if actual_count <> 0 then
        raise exception 'anonymous result unexpectedly created % report notifications', actual_count;
    end if;

    select count(*) into actual_count
    from sys_security_event
    where detail_json::text like '%' || anonymous_identity || '%';
    if actual_count <> 0 then
        raise exception 'anonymous identity token leaked into % security events', actual_count;
    end if;

    raise notice 'anonymous privacy verified: task %, pseudonymous sheet %, aggregate result %',
        target_task_id, target_sheet_id, target_result_id;
end
$$;
