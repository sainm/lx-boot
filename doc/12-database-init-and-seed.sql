-- 心理测评系统初始化与测试数据草案
-- 说明：
-- 1. 依赖 auth-starter 已完成基础表初始化
-- 2. 依赖 11-database-ddl-draft.sql 已执行
-- 3. 本脚本用于本地联调和演示环境，不建议直接用于生产

begin;

-- 字典数据示例
insert into sys_dict_type (id, type_code, type_name)
values
    (1001, 'risk_level', '风险等级'),
    (1002, 'task_status', '任务状态'),
    (1003, 'appointment_status', '预约状态'),
    (1004, 'warning_status', '预警状态')
on conflict do nothing;

insert into sys_dict_item (type_id, item_code, item_name, sort_no)
values
    (1001, 'normal', '正常', 1),
    (1001, 'attention', '关注', 2),
    (1001, 'high', '高风险', 3),
    (1002, 'draft', '草稿', 1),
    (1002, 'running', '进行中', 2),
    (1002, 'finished', '已结束', 3),
    (1003, 'pending', '待确认', 1),
    (1003, 'confirmed', '已预约', 2),
    (1003, 'completed', '已完成', 3),
    (1003, 'missed', '已失约', 4),
    (1004, 'pending', '待接单', 1),
    (1004, 'processing', '处理中', 2),
    (1004, 'closed', '已结案', 3)
on conflict do nothing;

-- 量表示例
insert into psy_scale (
    id, scale_code, scale_name, description, applicable_target, version_no, status, anonymous_supported
) values (
    2001, 'SCL-STRESS-01', '大学生压力测评量表', '用于示例演示的压力测评量表', 'student', 'v1', 'published', false
)
on conflict do nothing;

insert into psy_scale_dimension (
    id, scale_id, dimension_code, dimension_name, description, sort_no
) values
    (2101, 2001, 'emotion', '情绪状态', '评估情绪波动和压抑程度', 1),
    (2102, 2001, 'pressure', '压力水平', '评估当前学习与生活压力', 2),
    (2103, 2001, 'sleep', '睡眠情况', '评估近期睡眠质量', 3)
on conflict do nothing;

insert into psy_scale_question (
    id, scale_id, dimension_id, question_no, question_title, question_type, required_flag, reverse_score_flag, weight_value, sort_no
) values
    (2201, 2001, 2101, 1, '最近两周，你是否经常感到情绪低落？', 'single_choice', true, false, 1.00, 1),
    (2202, 2001, 2102, 2, '最近两周，你是否感到学习或工作压力较大？', 'single_choice', true, false, 1.00, 2),
    (2203, 2001, 2103, 3, '最近两周，你是否存在睡眠困难？', 'single_choice', true, false, 1.00, 3)
on conflict do nothing;

insert into psy_scale_option (
    id, question_id, option_code, option_label, score_value, sort_no
) values
    (2301, 2201, 'A', '从不', 1, 1),
    (2302, 2201, 'B', '偶尔', 2, 2),
    (2303, 2201, 'C', '经常', 3, 3),
    (2304, 2201, 'D', '总是', 4, 4),
    (2305, 2202, 'A', '从不', 1, 1),
    (2306, 2202, 'B', '偶尔', 2, 2),
    (2307, 2202, 'C', '经常', 3, 3),
    (2308, 2202, 'D', '总是', 4, 4),
    (2309, 2203, 'A', '从不', 1, 1),
    (2310, 2203, 'B', '偶尔', 2, 2),
    (2311, 2203, 'C', '经常', 3, 3),
    (2312, 2203, 'D', '总是', 4, 4)
on conflict do nothing;

insert into psy_scale_scoring_rule (
    id, scale_id, rule_type, expression, enabled_flag
) values
    (2401, 2001, 'sum', 'sum(all_question_scores)', true)
on conflict do nothing;

insert into psy_scale_result_rule (
    id, scale_id, risk_level, score_min, score_max, result_title, result_description, suggestion_text
) values
    (2501, 2001, 'normal', 0, 5, '整体状态平稳', '当前情绪与压力状态总体正常。', '建议保持规律作息与适度运动。'),
    (2502, 2001, 'attention', 6, 8, '存在关注信号', '存在一定压力或情绪波动。', '建议适当调整节奏并关注近期状态变化。'),
    (2503, 2001, 'high', 9, 12, '存在高风险信号', '可能存在较明显压力与情绪问题。', '建议尽快联系咨询师进一步评估。')
on conflict do nothing;

commit;
-- Historical draft only. Do not use this file to initialize a new environment.
-- Use doc/templates/init-sys-admin.sql for SYS_ADMIN bootstrap and current schema files for DDL.
