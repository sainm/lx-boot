-- Read-only tenant ownership report for a database already upgraded through V16.
-- Run with search_path set to the target application schema. The first row set
-- inventories every psy_* table; issue_count must be zero before validating
-- tenant foreign keys or adding NOT NULL constraints. GLOBAL rows are deliberate
-- shared configuration and are not candidates for tenant_id hardening.

with ownership_registry(table_name, ownership_mode, ownership_source, direct_tenant, global_allowed) as (
    values
        ('psy_appointment_record', 'DIRECT', 'tenant_id; users, schedule and warning must agree', true, false),
        ('psy_assessment_answer_item', 'INHERITED', 'answer_sheet_id -> psy_assessment_answer_sheet', false, false),
        ('psy_assessment_answer_sheet', 'DIRECT', 'tenant_id; task, scale and identified user must agree', true, false),
        ('psy_assessment_result', 'INHERITED', 'answer_sheet_id -> psy_assessment_answer_sheet', false, false),
        ('psy_assessment_result_dimension', 'INHERITED', 'result_id -> answer sheet; dimension_id -> scale', false, false),
        ('psy_assessment_task', 'DIRECT', 'tenant_id; scale and creator must agree', true, false),
        ('psy_assessment_task_assignment', 'INHERITED', 'task_id -> psy_assessment_task; target belongs to task tenant', false, false),
        ('psy_counseling_record', 'DIRECT', 'tenant_id; appointment and counselor must agree', true, false),
        ('psy_counselor_schedule', 'DIRECT', 'tenant_id; counselor_user_id must agree', true, false),
        ('psy_export_job', 'DIRECT', 'tenant_id; report/result and creator must agree', true, false),
        ('psy_intervention_record', 'DIRECT', 'tenant_id; warning, counselor and retest task must agree', true, false),
        ('psy_intervention_status_log', 'DIRECT', 'tenant_id; intervention and changer must agree', true, false),
        ('psy_notification', 'GLOBAL', 'shared message envelope; tenant isolation is on delivery', false, true),
        ('psy_notification_delivery', 'DIRECT', 'tenant_id; receiver and business object must agree', true, false),
        ('psy_notification_policy', 'GLOBAL', 'shared delivery policy by notification_type', false, true),
        ('psy_report', 'INHERITED', 'result_id -> answer sheet', false, false),
        ('psy_safety_response_policy', 'DIRECT', 'tenant_id; NULL is an intentional global fallback', true, true),
        ('psy_scale', 'DIRECT', 'tenant_id; creator must agree', true, false),
        ('psy_scale_algorithm_binding', 'INHERITED', 'scale_id -> psy_scale', false, false),
        ('psy_scale_dimension', 'INHERITED', 'scale_id -> psy_scale', false, false),
        ('psy_scale_dimension_translation', 'INHERITED', 'dimension_id -> scale dimension -> scale', false, false),
        ('psy_scale_golden_case', 'DIRECT', 'tenant_id; scale, creator and approver must agree', true, false),
        ('psy_scale_golden_case_run', 'DIRECT', 'tenant_id; scale, case and executor must agree', true, false),
        ('psy_scale_governance', 'INHERITED', 'scale_id -> psy_scale', false, false),
        ('psy_scale_high_risk_rule', 'INHERITED', 'scale_id; question and option must belong to the same scale', false, false),
        ('psy_scale_high_risk_rule_translation', 'INHERITED', 'high_risk_rule_id -> high-risk rule -> scale', false, false),
        ('psy_scale_import_issue', 'INHERITED', 'import_job_id -> psy_scale_import_job', false, false),
        ('psy_scale_import_job', 'DIRECT', 'tenant_id; operator and created scale must agree', true, false),
        ('psy_scale_norm', 'INHERITED', 'scale_id; optional dimension must belong to the same scale', false, false),
        ('psy_scale_option', 'INHERITED', 'question_id -> question -> scale', false, false),
        ('psy_scale_option_translation', 'INHERITED', 'option_id -> option -> question -> scale', false, false),
        ('psy_scale_publication_review', 'DIRECT', 'tenant_id; scale and reviewer must agree', true, false),
        ('psy_scale_quality_policy', 'INHERITED', 'scale_id -> psy_scale', false, false),
        ('psy_scale_question', 'INHERITED', 'scale_id; optional dimension must belong to the same scale', false, false),
        ('psy_scale_question_translation', 'INHERITED', 'question_id -> question -> scale', false, false),
        ('psy_scale_result_rule', 'INHERITED', 'scale_id; optional dimension must belong to the same scale', false, false),
        ('psy_scale_result_rule_translation', 'INHERITED', 'result_rule_id -> result rule -> scale', false, false),
        ('psy_scale_translation', 'INHERITED', 'scale_id -> psy_scale', false, false),
        ('psy_scale_validity_rule', 'INHERITED', 'scale_id -> psy_scale', false, false),
        ('psy_scale_visualization_config', 'INHERITED', 'scale_id -> psy_scale', false, false),
        ('psy_user_device', 'INHERITED', 'user_id -> sys_user', false, false),
        ('psy_warning_assignment', 'DIRECT', 'tenant_id; warning, assignee and assigner must agree', true, false),
        ('psy_warning_close_checklist', 'DIRECT', 'tenant_id; warning and completer must agree', true, false),
        ('psy_warning_follow_up', 'DIRECT', 'tenant_id; warning, creator and completer must agree', true, false),
        ('psy_warning_record', 'DIRECT', 'tenant_id; result answer sheet and tenant policy must agree', true, false),
        ('psy_warning_response_event', 'DIRECT', 'tenant_id; warning and performer must agree', true, false)
), table_metrics as (
    select
        registry.table_name,
        registry.ownership_mode,
        registry.ownership_source,
        ((xpath('/row/c/text()', query_to_xml(
            format('select count(*) as c from %I', registry.table_name), false, true, ''
        )))[1]::text)::bigint as total_rows,
        case when registry.direct_tenant and not registry.global_allowed then
            ((xpath('/row/c/text()', query_to_xml(
                format('select count(*) as c from %I where tenant_id is null', registry.table_name), false, true, ''
            )))[1]::text)::bigint
        else 0 end as unmapped_rows,
        case when registry.direct_tenant then
            ((xpath('/row/c/text()', query_to_xml(
                format(
                    'select count(*) as c from %I owned left join sys_tenant tenant on tenant.id = owned.tenant_id where owned.tenant_id is not null and tenant.id is null',
                    registry.table_name
                ), false, true, ''
            )))[1]::text)::bigint
        else 0 end as orphan_tenant_rows
    from ownership_registry registry
)
select
    'TABLE'::text as record_type,
    table_name as check_name,
    ownership_mode,
    ownership_source,
    total_rows,
    unmapped_rows + orphan_tenant_rows as issue_count
from table_metrics

union all

select
    'RELATIONSHIP',
    relationship.check_name,
    'RELATIONSHIP',
    relationship.ownership_source,
    relationship.total_rows,
    relationship.issue_count
from (
    select 'scale_creator_tenant' as check_name, 'psy_scale.created_by -> sys_user' as ownership_source,
           count(*) as total_rows,
           count(*) filter (where creator.id is null or scale.tenant_id is distinct from creator.tenant_id) as issue_count
    from psy_scale scale left join sys_user creator on creator.id = scale.created_by
    union all
    select 'scale_import_job_tenant', 'operator/created scale -> import job', count(*),
           count(*) filter (where operator.id is null or job.tenant_id is distinct from operator.tenant_id
               or (job.created_scale_id is not null and (scale.id is null or job.tenant_id is distinct from scale.tenant_id)))
    from psy_scale_import_job job
    left join sys_user operator on operator.id = job.operator_user_id
    left join psy_scale scale on scale.id = job.created_scale_id
    union all
    select 'task_tenant', 'scale/creator -> task', count(*),
           count(*) filter (where scale.id is null or creator.id is null
               or task.tenant_id is distinct from scale.tenant_id or task.tenant_id is distinct from creator.tenant_id)
    from psy_assessment_task task
    left join psy_scale scale on scale.id = task.scale_id
    left join sys_user creator on creator.id = task.created_by
    union all
    select 'task_assignment_target_tenant', 'task tenant -> GROUP/USER target tenant', count(*),
           count(*) filter (where task.id is null
               or (assignment.target_type = 'GROUP' and (target_group.id is null or task.tenant_id is distinct from target_group.tenant_id))
               or (assignment.target_type = 'USER' and (target_user.id is null or task.tenant_id is distinct from target_user.tenant_id))
               or assignment.target_type not in ('GROUP', 'USER'))
    from psy_assessment_task_assignment assignment
    left join psy_assessment_task task on task.id = assignment.task_id
    left join sys_group target_group on assignment.target_type = 'GROUP' and target_group.id = assignment.target_id
    left join sys_user target_user on assignment.target_type = 'USER' and target_user.id = assignment.target_id
    union all
    select 'answer_sheet_tenant', 'task/scale/identified user -> answer sheet', count(*),
           count(*) filter (where task.id is null or scale.id is null
               or sheet.tenant_id is distinct from task.tenant_id or sheet.tenant_id is distinct from scale.tenant_id
               or (sheet.user_id is not null and (respondent.id is null or sheet.tenant_id is distinct from respondent.tenant_id)))
    from psy_assessment_answer_sheet sheet
    left join psy_assessment_task task on task.id = sheet.task_id
    left join psy_scale scale on scale.id = sheet.scale_id
    left join sys_user respondent on respondent.id = sheet.user_id
    union all
    select 'answer_item_scale', 'answer sheet scale -> question/option scale', count(*),
           count(*) filter (where sheet.id is null or question.id is null or question.scale_id <> sheet.scale_id
               or (item.option_id is not null and (option.id is null or option.question_id <> item.question_id)))
    from psy_assessment_answer_item item
    left join psy_assessment_answer_sheet sheet on sheet.id = item.answer_sheet_id
    left join psy_scale_question question on question.id = item.question_id
    left join psy_scale_option option on option.id = item.option_id
    union all
    select 'result_parent', 'result -> answer sheet', count(*),
           count(*) filter (where sheet.id is null)
    from psy_assessment_result result left join psy_assessment_answer_sheet sheet on sheet.id = result.answer_sheet_id
    union all
    select 'result_dimension_scale', 'result answer scale -> dimension scale', count(*),
           count(*) filter (where result.id is null or sheet.id is null or dimension.id is null or dimension.scale_id <> sheet.scale_id)
    from psy_assessment_result_dimension score
    left join psy_assessment_result result on result.id = score.result_id
    left join psy_assessment_answer_sheet sheet on sheet.id = result.answer_sheet_id
    left join psy_scale_dimension dimension on dimension.id = score.dimension_id
    union all
    select 'report_parent', 'report -> result -> answer sheet', count(*),
           count(*) filter (where result.id is null or sheet.id is null)
    from psy_report report
    left join psy_assessment_result result on result.id = report.result_id
    left join psy_assessment_answer_sheet sheet on sheet.id = result.answer_sheet_id
    union all
    select 'warning_tenant', 'result answer sheet/policy -> warning', count(*),
           count(*) filter (where result.id is null or sheet.id is null or warning.tenant_id is distinct from sheet.tenant_id
               or (warning.safety_policy_id is not null and (policy.id is null
                   or (policy.tenant_id is not null and warning.tenant_id is distinct from policy.tenant_id))))
    from psy_warning_record warning
    left join psy_assessment_result result on result.id = warning.result_id
    left join psy_assessment_answer_sheet sheet on sheet.id = result.answer_sheet_id
    left join psy_safety_response_policy policy on policy.id = warning.safety_policy_id
    union all
    select 'warning_assignment_tenant', 'warning/assignee/assigner -> assignment', count(*),
           count(*) filter (where warning.id is null or assignee.id is null
               or assignment.tenant_id is distinct from warning.tenant_id
               or assignment.tenant_id is distinct from assignee.tenant_id
               or (assignment.assigned_by is not null and (assigner.id is null or assignment.tenant_id is distinct from assigner.tenant_id)))
    from psy_warning_assignment assignment
    left join psy_warning_record warning on warning.id = assignment.warning_id
    left join sys_user assignee on assignee.id = assignment.assignee_user_id
    left join sys_user assigner on assigner.id = assignment.assigned_by
    union all
    select 'intervention_tenant', 'warning/counselor/retest task -> intervention', count(*),
           count(*) filter (where warning.id is null or intervention.tenant_id is distinct from warning.tenant_id
               or (intervention.counselor_user_id is not null and (counselor.id is null or intervention.tenant_id is distinct from counselor.tenant_id))
               or (intervention.retest_task_id is not null and (retest.id is null or intervention.tenant_id is distinct from retest.tenant_id)))
    from psy_intervention_record intervention
    left join psy_warning_record warning on warning.id = intervention.warning_id
    left join sys_user counselor on counselor.id = intervention.counselor_user_id
    left join psy_assessment_task retest on retest.id = intervention.retest_task_id
    union all
    select 'intervention_log_tenant', 'intervention/changer -> status log', count(*),
           count(*) filter (where intervention.id is null or log.tenant_id is distinct from intervention.tenant_id
               or (log.changed_by is not null and (changer.id is null or log.tenant_id is distinct from changer.tenant_id)))
    from psy_intervention_status_log log
    left join psy_intervention_record intervention on intervention.id = log.intervention_id
    left join sys_user changer on changer.id = log.changed_by
    union all
    select 'schedule_tenant', 'counselor -> schedule', count(*),
           count(*) filter (where counselor.id is null or schedule.tenant_id is distinct from counselor.tenant_id)
    from psy_counselor_schedule schedule left join sys_user counselor on counselor.id = schedule.counselor_user_id
    union all
    select 'appointment_tenant', 'patient/counselor/schedule/warning -> appointment', count(*),
           count(*) filter (where patient.id is null or counselor.id is null
               or appointment.tenant_id is distinct from patient.tenant_id or appointment.tenant_id is distinct from counselor.tenant_id
               or (appointment.schedule_id is not null and (schedule.id is null or appointment.tenant_id is distinct from schedule.tenant_id))
               or (appointment.warning_id is not null and (warning.id is null or appointment.tenant_id is distinct from warning.tenant_id)))
    from psy_appointment_record appointment
    left join sys_user patient on patient.id = appointment.user_id
    left join sys_user counselor on counselor.id = appointment.counselor_user_id
    left join psy_counselor_schedule schedule on schedule.id = appointment.schedule_id
    left join psy_warning_record warning on warning.id = appointment.warning_id
    union all
    select 'counseling_tenant', 'appointment/counselor -> counseling', count(*),
           count(*) filter (where appointment.id is null or counselor.id is null
               or counseling.tenant_id is distinct from appointment.tenant_id or counseling.tenant_id is distinct from counselor.tenant_id)
    from psy_counseling_record counseling
    left join psy_appointment_record appointment on appointment.id = counseling.appointment_id
    left join sys_user counselor on counselor.id = counseling.counselor_user_id
    union all
    select 'notification_delivery_tenant', 'receiver -> notification delivery', count(*),
           count(*) filter (where receiver.id is null or delivery.tenant_id is distinct from receiver.tenant_id)
    from psy_notification_delivery delivery left join sys_user receiver on receiver.id = delivery.receiver_user_id
    union all
    select 'export_job_tenant', 'report/result/creator -> export job', count(*),
           count(*) filter (where report.id is null or result.id is null or sheet.id is null
               or export_job.tenant_id is distinct from sheet.tenant_id
               or (export_job.created_by is not null and (creator.id is null or export_job.tenant_id is distinct from creator.tenant_id)))
    from psy_export_job export_job
    left join psy_report report on report.id = export_job.report_id
    left join psy_assessment_result result on result.id = coalesce(export_job.result_id, report.result_id)
    left join psy_assessment_answer_sheet sheet on sheet.id = result.answer_sheet_id
    left join sys_user creator on creator.id = export_job.created_by
    union all
    select 'golden_case_tenant', 'scale/creator/approver -> Golden Case', count(*),
           count(*) filter (where scale.id is null or creator.id is null
               or golden.tenant_id is distinct from scale.tenant_id or golden.tenant_id is distinct from creator.tenant_id
               or (golden.approved_by is not null and (approver.id is null or golden.tenant_id is distinct from approver.tenant_id)))
    from psy_scale_golden_case golden
    left join psy_scale scale on scale.id = golden.scale_id
    left join sys_user creator on creator.id = golden.created_by
    left join sys_user approver on approver.id = golden.approved_by
    union all
    select 'golden_run_tenant', 'scale/Golden Case/executor -> Golden run', count(*),
           count(*) filter (where scale.id is null or golden.id is null or executor.id is null
               or run.tenant_id is distinct from scale.tenant_id or run.tenant_id is distinct from golden.tenant_id
               or run.tenant_id is distinct from executor.tenant_id)
    from psy_scale_golden_case_run run
    left join psy_scale scale on scale.id = run.scale_id
    left join psy_scale_golden_case golden on golden.id = run.golden_case_id
    left join sys_user executor on executor.id = run.executed_by
    union all
    select 'publication_review_tenant', 'scale/reviewer -> publication review', count(*),
           count(*) filter (where scale.id is null or reviewer.id is null
               or review.tenant_id is distinct from scale.tenant_id or review.tenant_id is distinct from reviewer.tenant_id)
    from psy_scale_publication_review review
    left join psy_scale scale on scale.id = review.scale_id
    left join sys_user reviewer on reviewer.id = review.reviewer_id
    union all
    select 'warning_response_event_tenant', 'warning/performer -> response event', count(*),
           count(*) filter (where warning.id is null or performer.id is null
               or event.tenant_id is distinct from warning.tenant_id or event.tenant_id is distinct from performer.tenant_id)
    from psy_warning_response_event event
    left join psy_warning_record warning on warning.id = event.warning_id
    left join sys_user performer on performer.id = event.performed_by
    union all
    select 'warning_follow_up_tenant', 'warning/creator/completer -> follow-up', count(*),
           count(*) filter (where warning.id is null or creator.id is null
               or follow_up.tenant_id is distinct from warning.tenant_id or follow_up.tenant_id is distinct from creator.tenant_id
               or (follow_up.completed_by is not null and (completer.id is null or follow_up.tenant_id is distinct from completer.tenant_id)))
    from psy_warning_follow_up follow_up
    left join psy_warning_record warning on warning.id = follow_up.warning_id
    left join sys_user creator on creator.id = follow_up.created_by
    left join sys_user completer on completer.id = follow_up.completed_by
    union all
    select 'warning_close_checklist_tenant', 'warning/completer -> close checklist', count(*),
           count(*) filter (where warning.id is null or completer.id is null
               or checklist.tenant_id is distinct from warning.tenant_id or checklist.tenant_id is distinct from completer.tenant_id)
    from psy_warning_close_checklist checklist
    left join psy_warning_record warning on warning.id = checklist.warning_id
    left join sys_user completer on completer.id = checklist.completed_by
) relationship
order by record_type, check_name;
