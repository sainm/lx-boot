-- Append-only audit evidence for generic scoring. The answer items, task
-- content hash and calculation version remain the authoritative inputs; this
-- JSONB column records the derived calculation path without storing free-text
-- answers or secrets.

alter table psy_assessment_result
    add column if not exists scoring_trace_json jsonb;

alter table psy_assessment_result drop constraint if exists ck_psy_result_scoring_trace_json;
alter table psy_assessment_result
    add constraint ck_psy_result_scoring_trace_json
    check (scoring_trace_json is null or jsonb_typeof(scoring_trace_json) = 'object') not valid;
alter table psy_assessment_result validate constraint ck_psy_result_scoring_trace_json;

