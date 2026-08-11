-- Run before V11. Review every non-zero row and plan manual policy/checklist
-- association after migration; this script never guesses clinical governance.
select 'open_warnings_requiring_policy_review' as check_name, count(*) as issue_count
from psy_warning_record
where status <> 'CLOSED';

select 'open_high_warnings_without_deadline' as check_name, count(*) as issue_count
from psy_warning_record
where status <> 'CLOSED'
  and upper(warning_level) in ('CRITICAL', 'P0', 'HIGH', 'P1')
  and deadline_time is null;

select 'legacy_closed_warnings_requiring_checklist_archive_review' as check_name, count(*) as issue_count
from psy_warning_record
where status = 'CLOSED';
