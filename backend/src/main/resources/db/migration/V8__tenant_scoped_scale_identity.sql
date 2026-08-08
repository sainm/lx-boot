create unique index concurrently if not exists uk_psy_scale_tenant_code_version
    on psy_scale(tenant_id, scale_code, version_no)
    where tenant_id is not null;

create unique index concurrently if not exists uk_psy_scale_global_code_version
    on psy_scale(scale_code, version_no)
    where tenant_id is null;

drop index concurrently if exists uk_psy_scale_code_version;

create index concurrently if not exists idx_psy_scale_import_job_tenant_created
    on psy_scale_import_job(tenant_id, created_at desc, id desc);

do $$
begin
    if not exists (
        select 1
        from pg_constraint
        where conrelid = 'psy_scale_import_job'::regclass
          and conname = 'fk_psy_scale_import_job_tenant'
    ) then
        alter table psy_scale_import_job
            add constraint fk_psy_scale_import_job_tenant
            foreign key (tenant_id) references sys_tenant(id) not valid;
    end if;
end
$$;
