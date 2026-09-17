-- A professional review must be an authenticated action by the counselor
-- recorded on the draft before an independent administrator can activate it.

alter table psy_safety_response_policy
    add column if not exists professional_reviewed_at timestamp;

alter table psy_safety_response_policy
    add constraint ck_psy_safety_policy_professional_review
    check (
        (professional_reviewer_id is null and professional_reviewed_at is null)
        or (professional_reviewer_id is not null and professional_reviewed_at is not null)
    ) not valid;

alter table psy_safety_response_policy
    drop constraint if exists ck_psy_safety_policy_activation;

alter table psy_safety_response_policy
    add constraint ck_psy_safety_policy_activation
    check (
        active_flag = false
        or (
            status = 'APPROVED'
            and approved_by is not null
            and professional_reviewer_id is not null
            and professional_reviewed_at is not null
            and approved_at is not null
        )
    ) not valid;
