-- Read-only checks to run before enabling the V13 publication gate.

select 'published_scale_without_current_golden_case_set' as issue, count(*) as affected_rows
from psy_scale s
where s.status = 'PUBLISHED'
  and not exists (
      select 1 from psy_scale_golden_case c
      where c.scale_id = s.id and c.scale_content_hash = s.published_content_hash
  );

select 'golden_case_tenant_mismatch' as issue, count(*) as affected_rows
from psy_scale_golden_case c
join psy_scale s on s.id = c.scale_id
where c.tenant_id is distinct from s.tenant_id;

select 'golden_run_tenant_or_parent_mismatch' as issue, count(*) as affected_rows
from psy_scale_golden_case_run r
join psy_scale_golden_case c on c.id = r.golden_case_id
join psy_scale s on s.id = r.scale_id
where r.scale_id <> c.scale_id
   or r.tenant_id is distinct from s.tenant_id;

select 'publication_review_tenant_mismatch' as issue, count(*) as affected_rows
from psy_scale_publication_review r
join psy_scale s on s.id = r.scale_id
where r.tenant_id is distinct from s.tenant_id;
