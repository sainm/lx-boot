DROP TABLE IF EXISTS psy_report_item;
DROP TABLE IF EXISTS psy_report_dimension;
DROP TABLE IF EXISTS psy_report;
DROP TABLE IF EXISTS psy_assessment_assignment;
DROP TABLE IF EXISTS psy_assessment_plan;


CREATE TABLE psy_report_dimension
(
    id               BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    report_id        BIGINT NOT NULL COMMENT '对应报告ID（逻辑关联 psy_report.id）',
    dimension_code   VARCHAR(100) NOT NULL COMMENT '维度编码',
    dimension_name   VARCHAR(200) NOT NULL COMMENT '维度名称',
    score            DECIMAL(10, 2) DEFAULT 0.00 COMMENT '维度得分',
    level            VARCHAR(100) NULL COMMENT '等级（如高、中、低）',
    interpretation   TEXT NULL COMMENT '维度解释说明',
    suggestion       TEXT NULL COMMENT '维度建议',
    create_time      DATETIME DEFAULT CURRENT_TIMESTAMP() COMMENT '创建时间',
    update_time      DATETIME DEFAULT CURRENT_TIMESTAMP() ON UPDATE CURRENT_TIMESTAMP() COMMENT '更新时间'
) COMMENT='测评报告维度结果表';
CREATE TABLE psy_report_item
(
    id             BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    report_id      BIGINT NOT NULL COMMENT '报告ID（逻辑关联 psy_report.id）',
    dimension_code VARCHAR(100) NULL COMMENT '所属维度编码',
    question_id    BIGINT NOT NULL COMMENT '题目ID',
    option_id      BIGINT NULL COMMENT '选项ID',
    score          DECIMAL(10, 2) DEFAULT 0.00 COMMENT '题目得分',
    remark         TEXT NULL COMMENT '备注或解释',
    create_time    DATETIME DEFAULT CURRENT_TIMESTAMP() COMMENT '创建时间',
    update_time    DATETIME DEFAULT CURRENT_TIMESTAMP() ON UPDATE CURRENT_TIMESTAMP() COMMENT '更新时间'
) COMMENT='测评报告题目明细表';

CREATE TABLE psy_report
(
    id             BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '报告ID',
    record_id      BIGINT NOT NULL COMMENT '测评记录ID',
    user_id        BIGINT NOT NULL COMMENT '用户ID',
    scale_id       BIGINT NOT NULL COMMENT '量表ID',
    total_score    DECIMAL(10, 2) DEFAULT 0.00 COMMENT '总分',
    result_level   VARCHAR(100) NULL COMMENT '结果等级（如低、中、高风险）',
    suggestion     TEXT NULL COMMENT '系统建议或干预建议',
    create_by      BIGINT NULL COMMENT '创建人',
    update_by      BIGINT NULL COMMENT '最后修改人',
    create_time    DATETIME DEFAULT CURRENT_TIMESTAMP() COMMENT '生成时间',
    update_time    DATETIME DEFAULT CURRENT_TIMESTAMP() ON UPDATE CURRENT_TIMESTAMP() COMMENT '更新时间'
) COMMENT='测评报告表';

-- ================================
-- 心理测评预警类型定义表
-- ================================
DROP TABLE IF EXISTS psy_warning_type;
CREATE TABLE psy_warning_type
(
    id            BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '预警类型ID',
    code          VARCHAR(50)  NOT NULL COMMENT '预警编码',
    name          VARCHAR(100) NOT NULL COMMENT '预警名称',
    description   TEXT          NULL COMMENT '预警说明',
    level         VARCHAR(50)   NULL COMMENT '风险等级（高/中/低）',
    trigger_rule  TEXT          NULL COMMENT '触发规则描述（例如维度得分 > 70）',
    create_time   DATETIME DEFAULT CURRENT_TIMESTAMP() COMMENT '创建时间',
    update_time   DATETIME DEFAULT CURRENT_TIMESTAMP() ON UPDATE CURRENT_TIMESTAMP() COMMENT '更新时间'
) COMMENT='心理测评预警类型表';

-- ================================
-- 初始化预警类型数据
-- ================================
INSERT INTO psy_warning_type (code, name, description, level, trigger_rule)
VALUES
    ('HIGH_RISK', '高危预警', '存在严重心理风险，如自杀念头、极端行为倾向、重度抑郁或焦虑，需要立即干预。', '高', 'T分 ≥ 80 或自杀相关题项阳性'),
    ('MODERATE_RISK', '中度风险预警', '存在明显心理问题，需要心理辅导与持续关注。', '中', 'T分 ≥ 70 且 < 80'),
    ('MILD_RISK', '轻度风险预警', '存在轻微心理不适，建议自我调节或参与辅导活动。', '低', 'T分 ≥ 60 且 < 70'),
    ('STRESS_WARNING', '压力过载预警', '工作或学习压力过大，出现疲惫、焦虑或失眠。', '中', '压力维度得分 ≥ 70'),
    ('EMOTIONAL_INSTABILITY', '情绪失调预警', '情绪波动较大，易怒、焦虑或抑郁交替出现。', '中', '焦虑维度与抑郁维度均 ≥ 70'),
    ('SLEEP_DISORDER', '睡眠障碍预警', '存在入睡困难、早醒或睡眠质量差的问题。', '低', '睡眠维度 ≥ 65'),
    ('SOCIAL_WITHDRAWAL', '社交退缩预警', '社交兴趣下降、孤独感增强、回避人际互动。', '中', '社交维度 ≤ 40'),
    ('AGGRESSION', '攻击/冲动预警', '存在冲动、敌意或攻击行为风险。', '高', '攻击性维度 ≥ 75'),
    ('ADDICTION', '成瘾倾向预警', '存在网络、游戏或物质成瘾行为倾向。', '中', '成瘾维度 ≥ 70'),
    ('COGNITIVE_IMPAIRMENT', '认知功能异常预警', '注意力、记忆力显著下降，可能影响学习或工作。', '中', '认知维度 ≤ 40'),
    ('SOMATIZATION', '身体化症状预警', '心理压力通过身体不适表现出来，如头痛、胸闷、乏力。', '低', '身体化维度 ≥ 65'),
    ('BURNOUT', '职业倦怠预警', '对工作失去兴趣、疲惫感强，绩效明显下降。', '中', '职业倦怠维度 ≥ 70');


-- ================================
-- 心理测评预警规则表
-- ================================
DROP TABLE IF EXISTS psy_warning_rule;
CREATE TABLE psy_warning_rule
(
    id              BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '规则ID',
    warning_type_id BIGINT NOT NULL COMMENT '预警类型ID（逻辑关联 psy_warning_type.id）',
    scale_id        BIGINT NULL COMMENT '适用量表ID（NULL 表示适用于所有量表）',
    dimension_code  VARCHAR(100) NULL COMMENT '适用维度编码（NULL 表示总分规则）',
    threshold_min   DECIMAL(10,2) NULL COMMENT '分数下限（闭区间）',
    threshold_max   DECIMAL(10,2) NULL COMMENT '分数上限（闭区间）',
    operator        VARCHAR(20)  NULL COMMENT '比较符号（>, >=, <, <=, between 等）',
    logic_condition VARCHAR(50)  NULL COMMENT '逻辑条件说明（如 “或”“与”）',
    description     TEXT          NULL COMMENT '规则说明',
    enabled         TINYINT(1) DEFAULT 1 COMMENT '是否启用（1=启用，0=停用）',
    create_time     DATETIME DEFAULT CURRENT_TIMESTAMP() COMMENT '创建时间',
    update_time     DATETIME DEFAULT CURRENT_TIMESTAMP() ON UPDATE CURRENT_TIMESTAMP() COMMENT '更新时间'
) COMMENT='心理测评预警规则表';
-- ================================
-- 预警规则初始化
-- ================================
INSERT INTO psy_warning_rule (warning_type_id, scale_id, dimension_code, threshold_min, threshold_max, operator, description)
VALUES
-- 高危预警
(1, NULL, 'DEPRESSION', 80, 200, '>=', '抑郁维度T分 ≥ 80 触发高危预警'),
(1, NULL, 'SUICIDE', 1, 1, '=', '自杀相关题项阳性触发高危预警'),
-- 中度风险预警
(2, NULL, 'ANXIETY', 70, 79.99, 'between', '焦虑维度T分 70~79.99 触发中度风险预警'),
(2, NULL, 'DEPRESSION', 70, 79.99, 'between', '抑郁维度T分 70~79.99 触发中度风险预警'),
-- 轻度风险预警
(3, NULL, 'ANXIETY', 60, 69.99, 'between', '焦虑维度T分 60~69.99 触发轻度风险预警'),
(3, NULL, 'DEPRESSION', 60, 69.99, 'between', '抑郁维度T分 60~69.99 触发轻度风险预警'),
-- 压力过载预警
(4, NULL, 'STRESS', 70, 200, '>=', '压力维度得分 ≥ 70'),
-- 情绪失调预警
(5, NULL, 'EMOTION_INSTABILITY', 70, 200, '>=', '情绪稳定性维度 ≥ 70'),
-- 睡眠障碍预警
(6, NULL, 'SLEEP', 65, 200, '>=', '睡眠质量维度 ≥ 65'),
-- 社交退缩预警
(7, NULL, 'SOCIAL', 0, 40, '<=', '社交维度 ≤ 40'),
-- 攻击/冲动预警
(8, NULL, 'AGGRESSION', 75, 200, '>=', '攻击性维度 ≥ 75'),
-- 成瘾倾向预警
(9, NULL, 'ADDICTION', 70, 200, '>=', '成瘾维度 ≥ 70'),
-- 认知功能异常预警
(10, NULL, 'COGNITIVE', 0, 40, '<=', '认知维度 ≤ 40'),
-- 身体化症状预警
(11, NULL, 'SOMATIZATION', 65, 200, '>=', '身体化维度 ≥ 65'),
-- 职业倦怠预警
(12, NULL, 'BURNOUT', 70, 200, '>=', '职业倦怠维度 ≥ 70');

create table psy_assessment_assignment
(
    id          bigint auto_increment comment '任务分配ID'
        primary key,
    plan_id     bigint                                    not null comment '测评计划ID',
    user_id     bigint                                    null comment '用户ID（单人分配）',
    group_id    bigint                                    null comment '用户组ID（批量分配）',
    assign_type tinyint       default 0                   null comment '分配类型：0个人 1组',
    progress    decimal(5, 2) default 0.00                null comment '答题进度百分比',
    status      tinyint       default 0                   null comment '状态：0未开始 1进行中 2已完成 3已过期',
    assigned_by bigint                                    null comment '分配人ID',
    create_by   bigint                                    null comment '创建人',
    update_by   bigint                                    null comment '最后修改人',
    create_time datetime      default current_timestamp() null comment '创建时间',
    update_time datetime      default current_timestamp() null on update current_timestamp() comment '更新时间'
)
    comment '测评任务分配表';


create table psy_assessment_plan
(
    id           bigint auto_increment comment '测评计划ID'
        primary key,
    name         varchar(200)                         not null comment '测评计划名称',
    description  varchar(1000)                        null comment '测评计划说明',
    scale_id     bigint                               not null comment '量表ID',
    version_id   bigint                               null comment '量表版本ID',
    target_group varchar(255)                         null comment '目标群体（标签或分组描述）',
    start_time   datetime                             not null comment '开始时间',
    end_time     datetime                             null comment '结束时间',
    creator_id   bigint                               not null comment '创建人ID',
    status       tinyint  default 1                   null comment '状态：1启用 0停用',
    create_by    bigint                               null comment '创建人',
    update_by    bigint                               null comment '最后修改人',
    create_time  datetime default current_timestamp() null comment '创建时间',
    update_time  datetime default current_timestamp() null on update current_timestamp() comment '更新时间'
)
    comment '测评计划表';

