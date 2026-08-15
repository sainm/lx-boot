-- TIME is a first-class controlled self-report question type used by the
-- declaration-only sleep recode rules. Keep the existing whitelist closed;
-- only add the explicitly supported TIME value.
alter table psy_scale_question
    drop constraint if exists ck_psy_scale_question_type;

alter table psy_scale_question
    add constraint ck_psy_scale_question_type
    check (question_type in (
        'SINGLE_CHOICE',
        'MULTI_SELECT',
        'SLIDER',
        'TEXT',
        'TEXT_WITH_OPTION',
        'MATRIX',
        'TIME'
    )) not valid;
