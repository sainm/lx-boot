alter table psy_scale_publication_review
    add column if not exists reviewer_name_snapshot varchar(128),
    add column if not exists qualification_reference text,
    add column if not exists evidence_reference text,
    add column if not exists review_scope text;

update psy_scale_publication_review review
set reviewer_name_snapshot = coalesce(nullif(btrim(actor.display_name), ''), actor.username)
from sys_user actor
where actor.id = review.reviewer_id
  and review.reviewer_name_snapshot is null;

comment on column psy_scale_publication_review.reviewer_name_snapshot is
    'Immutable display-name snapshot for the person who submitted the review.';
comment on column psy_scale_publication_review.qualification_reference is
    'Professional credential or internal qualification record reference; required for new professional approvals.';
comment on column psy_scale_publication_review.evidence_reference is
    'Document, ticket, controlled file, or other auditable evidence reference used for the decision.';
comment on column psy_scale_publication_review.review_scope is
    'Explicit statement of the content, population, language, scoring, and report scope reviewed.';
