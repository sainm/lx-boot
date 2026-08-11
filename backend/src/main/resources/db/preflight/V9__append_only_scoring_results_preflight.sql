-- Both checks must return issue_count = 0 before V9 is applied.
select 'duplicate_result_rows_per_answer_sheet' as check_name, count(*) as issue_count
from (
    select answer_sheet_id from psy_assessment_result
    group by answer_sheet_id having count(*) > 1
) duplicate_results;

select 'result_rows_without_answer_sheet' as check_name, count(*) as issue_count
from psy_assessment_result result
left join psy_assessment_answer_sheet sheet on sheet.id = result.answer_sheet_id
where sheet.id is null;

