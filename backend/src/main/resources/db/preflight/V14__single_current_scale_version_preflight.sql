-- Must return zero rows before V14 is applied.
select coalesce(version_group_id, id) as version_group_id,
       count(*) as current_published_count,
       array_agg(id order by id) as scale_ids
from psy_scale
where current_version_flag = true
  and status = 'PUBLISHED'
group by coalesce(version_group_id, id)
having count(*) > 1;
