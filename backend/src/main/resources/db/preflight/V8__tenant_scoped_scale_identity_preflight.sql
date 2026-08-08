-- Read-only checks before applying V8. This derives the tenant exactly as V6
-- does, so it can run before the existing database is baselined or migrated.
-- Both counts must be zero before replacing the legacy global unique index.

select 'duplicate_tenant_scale_versions' as check_name, count(*) as issue_count
from (
    select creator.tenant_id, s.scale_code, s.version_no
    from psy_scale s
    join sys_user creator on creator.id = s.created_by
    where creator.tenant_id is not null
    group by creator.tenant_id, s.scale_code, s.version_no
    having count(*) > 1
) conflicts
union all
select 'duplicate_global_scale_versions', count(*)
from (
    select s.scale_code, s.version_no
    from psy_scale s
    left join sys_user creator on creator.id = s.created_by
    where creator.tenant_id is null
    group by s.scale_code, s.version_no
    having count(*) > 1
) conflicts;
