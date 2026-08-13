-- Declaration-only in-scale skip rules applied by the respondent task UI.
-- A skipped question carries no answer and therefore never enters scoring.

alter table psy_scale
    add column if not exists skip_rules_json jsonb;
