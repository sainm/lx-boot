-- Database backstop for concurrent publication of versions in the same group.
create unique index concurrently if not exists uk_psy_scale_version_group_current
    on psy_scale ((coalesce(version_group_id, id)))
    where current_version_flag = true and status = 'PUBLISHED';
