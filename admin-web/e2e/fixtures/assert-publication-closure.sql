-- Browser postconditions for the synthetic governance-to-task publication Case.
-- This proves only technical workflow behavior; it is not clinical, copyright,
-- authorization, or professional evidence for a real scale.

do $$
declare
    target_scale_id bigint;
    target_task_id bigint;
    target_version_group_id bigint;
    target_content_hash varchar(64);
    actual_count bigint;
begin
    select scale.id, coalesce(scale.version_group_id, scale.id), scale.published_content_hash
      into target_scale_id, target_version_group_id, target_content_hash
    from psy_scale scale
    where scale.scale_name like 'E2E Governance Technical Fixture %'
      and scale.status = 'PUBLISHED';

    if target_scale_id is null or target_content_hash !~ '^[0-9a-f]{64}$' then
        raise exception 'expected one published E2E governance scale with a content hash';
    end if;

    select count(*) into actual_count
    from psy_scale_golden_case golden_case
    where golden_case.scale_id = target_scale_id
      and golden_case.approved_by is not null
      and golden_case.approved_at is not null;
    if actual_count <> 6 then
        raise exception 'expected six approved Golden Case revisions, found %', actual_count;
    end if;

    select count(*) into actual_count
    from psy_scale_golden_case_run run
    join psy_scale_golden_case golden_case on golden_case.id = run.golden_case_id
    where run.scale_id = target_scale_id
      and run.passed
      and run.scale_content_hash = target_content_hash
      and run.case_content_hash = golden_case.case_content_hash
      and run.algorithm_code = 'GENERIC_SCORE_CALCULATOR'
      and run.algorithm_version = '1';
    if actual_count <> 6 then
        raise exception 'expected six passing hash-bound Golden Case runs, found %', actual_count;
    end if;

    select count(*) into actual_count
    from (
        select review_type
        from psy_scale_publication_review
        where scale_id = target_scale_id
          and decision = 'APPROVED'
          and scale_content_hash = target_content_hash
        group by review_type
    ) approved_review_types;
    if actual_count <> 2 then
        raise exception 'expected independent professional and business approvals, found % review types', actual_count;
    end if;

    if exists (
        select 1
        from psy_scale_publication_review professional
        join psy_scale_publication_review business
          on business.scale_id = professional.scale_id
         and business.review_type = 'BUSINESS'
         and business.decision = 'APPROVED'
         and business.release_fingerprint = professional.release_fingerprint
        where professional.scale_id = target_scale_id
          and professional.review_type = 'PROFESSIONAL'
          and professional.decision = 'APPROVED'
          and professional.reviewer_id = business.reviewer_id
    ) then
        raise exception 'professional and business approvals used the same reviewer';
    end if;

    select task.id into target_task_id
    from psy_assessment_task task
    where task.task_name like 'E2E Governance Lock %'
      and task.scale_id = target_scale_id
      and task.scale_version_group_id = target_version_group_id
      and task.scale_content_hash = target_content_hash;
    if target_task_id is null then
        raise exception 'task did not lock the published scale version and content hash';
    end if;

    select count(*) into actual_count
    from psy_scale later
    where coalesce(later.version_group_id, later.id) = target_version_group_id
      and later.id <> target_scale_id
      and later.scale_name like 'E2E Governance Later Draft %'
      and later.status = 'DRAFT';
    if actual_count <> 1 then
        raise exception 'expected one later draft in the same version group, found %', actual_count;
    end if;

    select count(*) into actual_count
    from psy_scale_golden_case golden_case
    join psy_scale later on later.id = golden_case.scale_id
    where coalesce(later.version_group_id, later.id) = target_version_group_id
      and later.scale_name like 'E2E Governance Later Draft %'
      and golden_case.approved_by is not null;
    if actual_count <> 6 then
        raise exception 'expected six retained approved cases on the deliberately changed draft, found %', actual_count;
    end if;

    select count(*) into actual_count
    from psy_scale_publication_review review
    join psy_scale later on later.id = review.scale_id
    where coalesce(later.version_group_id, later.id) = target_version_group_id
      and later.scale_name like 'E2E Governance Later Draft %'
      and review.decision = 'APPROVED';
    if actual_count <> 2 then
        raise exception 'expected two append-only pre-change reviews on the deliberately changed draft, found %', actual_count;
    end if;

    if exists (
        select 1
        from psy_assessment_task task
        join psy_scale locked on locked.id = task.scale_id
        where task.id = target_task_id
          and (
              task.scale_version_no is distinct from locked.version_no
              or task.scale_version_group_id is distinct from coalesce(locked.version_group_id, locked.id)
              or task.scale_content_hash is distinct from locked.published_content_hash
              or task.tenant_id is distinct from locked.tenant_id
          )
    ) then
        raise exception 'task version snapshot or tenant ownership drifted after a later draft was created';
    end if;

    raise notice 'publication closure verified: scale %, task %, content hash %',
        target_scale_id, target_task_id, target_content_hash;
end
$$;
