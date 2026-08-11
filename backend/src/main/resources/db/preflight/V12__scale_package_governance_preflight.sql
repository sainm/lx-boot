-- Run before V12. This script is read-only and intentionally reports unknown
-- governance as work requiring human review instead of inventing metadata.

select 'published_scales_requiring_governance_review' as check_name, count(*) as issue_count
from psy_scale
where status = 'PUBLISHED';

select 'scale_versions_requiring_three_language_content_review' as check_name, count(*) as issue_count
from psy_scale;

select 'norm_rows_requiring_source_and_review' as check_name, count(*) as issue_count
from psy_scale_norm;

select 'published_scales_using_non_builtin_score_method' as check_name, count(*) as issue_count
from psy_scale
where status = 'PUBLISHED'
  and score_method not in ('SIMPLE_SUM', 'REVERSE_SUM', 'WEIGHTED_SUM', 'AVERAGE', 'WEIGHTED_AVERAGE');

