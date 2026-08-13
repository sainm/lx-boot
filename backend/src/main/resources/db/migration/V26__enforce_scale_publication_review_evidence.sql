-- V25 intentionally preserved historical reviews without inventing evidence.
-- NOT VALID avoids rejecting those legacy rows while PostgreSQL still enforces
-- this constraint for every new or subsequently updated review.
alter table psy_scale_publication_review
    add constraint ck_psy_scale_publication_approval_evidence
    check (
        decision <> 'APPROVED'
        or (
            nullif(btrim(reviewer_name_snapshot), '') is not null
            and nullif(btrim(evidence_reference), '') is not null
            and nullif(btrim(review_scope), '') is not null
            and (
                review_type <> 'PROFESSIONAL'
                or nullif(btrim(qualification_reference), '') is not null
            )
        )
    ) not valid;
