-- Preserve every scoring calculation while keeping one current result per answer sheet.
alter table psy_assessment_result add column if not exists calculation_version int not null default 1;
alter table psy_assessment_result add column if not exists is_current boolean not null default true;
alter table psy_assessment_result add column if not exists supersedes_result_id bigint;
alter table psy_assessment_result add column if not exists rescored_by bigint;

do $$
declare
    constraint_name text;
begin
    for constraint_name in
        select c.conname
        from pg_constraint c
        join pg_class t on t.oid = c.conrelid
        join pg_namespace n on n.oid = t.relnamespace
        where n.nspname = current_schema()
          and t.relname = 'psy_assessment_result'
          and c.contype = 'u'
          and array_length(c.conkey, 1) = 1
          and c.conkey[1] = (
              select a.attnum from pg_attribute a
              where a.attrelid = t.oid and a.attname = 'answer_sheet_id' and not a.attisdropped
          )
    loop
        execute format('alter table psy_assessment_result drop constraint %I', constraint_name);
    end loop;
end $$;

create unique index if not exists uk_psy_result_sheet_calculation_version
    on psy_assessment_result(answer_sheet_id, calculation_version);
create unique index if not exists uk_psy_result_sheet_current
    on psy_assessment_result(answer_sheet_id) where is_current = true;
create index if not exists idx_psy_result_supersedes
    on psy_assessment_result(supersedes_result_id) where supersedes_result_id is not null;

do $$
begin
    if not exists (
        select 1 from pg_constraint
        where conname = 'fk_psy_result_supersedes'
          and conrelid = 'psy_assessment_result'::regclass
    ) then
        alter table psy_assessment_result
            add constraint fk_psy_result_supersedes
            foreign key (supersedes_result_id) references psy_assessment_result(id) not valid;
    end if;
end $$;

alter table psy_assessment_result drop constraint if exists ck_psy_result_calculation_version;
alter table psy_assessment_result add constraint ck_psy_result_calculation_version
    check (calculation_version > 0) not valid;

