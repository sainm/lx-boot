-- Add aggregation methods required by common symptom and rating scales without
-- weakening explicit method validation.

alter table psy_scale drop constraint if exists ck_psy_scale_score_method;

alter table psy_scale
    add constraint ck_psy_scale_score_method
    check (score_method in (
        'SIMPLE_SUM', 'REVERSE_SUM', 'WEIGHTED_SUM', 'AVERAGE', 'WEIGHTED_AVERAGE'
    )) not valid;
