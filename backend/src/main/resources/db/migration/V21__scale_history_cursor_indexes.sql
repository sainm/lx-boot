-- Keyset pagination indexes for append-only ScalePackage evidence.
-- This migration is deliberately non-transactional so large installations do
-- not hold an ACCESS EXCLUSIVE table lock while the indexes are built.
create index concurrently if not exists idx_psy_scale_golden_case_history_cursor
    on psy_scale_golden_case(scale_id, id desc);

create index concurrently if not exists idx_psy_scale_golden_run_history_cursor
    on psy_scale_golden_case_run(scale_id, id desc);

create index concurrently if not exists idx_psy_scale_publication_review_history_cursor
    on psy_scale_publication_review(scale_id, id desc);
