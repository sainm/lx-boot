# 数据库表结构设计（由 V1–V28 实际结构生成）

## 1. 文档说明

本文档由已完成 Flyway 迁移的 PostgreSQL `public` schema 直接生成，描述当前代码对应的真实表结构；历史 DDL 草案不再作为事实来源。

- 生成命令：`python3 scripts/generate_code_docs.py db`
- 生成时间：2026-09-19 13:12:37 CST
- 迁移：V1–V28，共 28 条成功迁移
- 表数量：**61**（`psy_*` 46 张，`sys_*` 15 张），不含 `flyway_schema_history`
- 结构入口：`backend/src/main/resources/db/migration/`；生产默认关闭自动迁移，禁止 clean，失败前滚修复。

## 2. 领域分组

| 领域 | 表数 | 表清单 |
| --- | ---: | --- |
| 报告与导出 | 2 | `psy_export_job`, `psy_report` |
| 测评任务与作答 | 6 | `psy_assessment_answer_item`, `psy_assessment_answer_sheet`, `psy_assessment_result`, `psy_assessment_result_dimension`, `psy_assessment_task`, `psy_assessment_task_assignment` |
| 认证、权限与会话 | 16 | `psy_user_device`, `sys_auth`, `sys_group`, `sys_group_role`, `sys_login_log`, `sys_permission`, `sys_qr_scene`, `sys_role`, `sys_role_permission`, `sys_security_event`, `sys_tenant`, `sys_token_blacklist`, `sys_user`, `sys_user_role`, `sys_user_session`, `sys_user_session_policy` |
| 通知与投递 | 3 | `psy_notification`, `psy_notification_delivery`, `psy_notification_policy` |
| 量表与发布治理 | 23 | `psy_scale`, `psy_scale_algorithm_binding`, `psy_scale_dimension`, `psy_scale_dimension_translation`, `psy_scale_golden_case`, `psy_scale_golden_case_run`, `psy_scale_governance`, `psy_scale_high_risk_rule`, `psy_scale_high_risk_rule_translation`, `psy_scale_import_issue`, `psy_scale_import_job`, `psy_scale_norm`, `psy_scale_option`, `psy_scale_option_translation`, `psy_scale_publication_review`, `psy_scale_quality_policy`, `psy_scale_question`, `psy_scale_question_translation`, `psy_scale_result_rule`, `psy_scale_result_rule_translation`, `psy_scale_translation`, `psy_scale_validity_rule`, `psy_scale_visualization_config` |
| 预约与咨询 | 3 | `psy_appointment_record`, `psy_counseling_record`, `psy_counselor_schedule` |
| 预警、干预与安全响应 | 8 | `psy_intervention_record`, `psy_intervention_status_log`, `psy_safety_response_policy`, `psy_warning_assignment`, `psy_warning_close_checklist`, `psy_warning_follow_up`, `psy_warning_record`, `psy_warning_response_event` |

## 3. 迁移清单

| 版本 | 描述 | 成功 |
| --- | --- | --- |
| V1 | application baseline | 是 |
| V2 | business data guards | 是 |
| V3 | notification delivery retry state | 是 |
| V4 | notification delivery retry indexes | 是 |
| V5 | assessment integrity guards | 是 |
| V6 | tenant ownership columns | 是 |
| V7 | scale average score methods | 是 |
| V8 | tenant scoped scale identity | 是 |
| V9 | append only scoring results | 是 |
| V10 | scale content fingerprint | 是 |
| V11 | safety response policy and warning evidence | 是 |
| V12 | scale package governance and localization | 是 |
| V13 | scale golden cases and publication reviews | 是 |
| V14 | single current scale version | 是 |
| V15 | warning contact outcome narrative | 是 |
| V16 | high risk translation and report locale | 是 |
| V17 | validate tenant ownership constraints | 是 |
| V18 | enforce tenant ownership not null | 是 |
| V19 | export job retry leases | 是 |
| V20 | notification delivery processing lease | 是 |
| V21 | scale history cursor indexes | 是 |
| V22 | assessment quality outcomes | 是 |
| V23 | scoring trace audit | 是 |
| V24 | scale skip rules | 是 |
| V25 | scale publication review evidence | 是 |
| V26 | enforce scale publication review evidence | 是 |
| V27 | scale time question type | 是 |
| V28 | safety response policy professional review | 是 |

## 4. 表结构明细

### 4.1 报告与导出

#### `psy_export_job`

| 列 | 类型 | 可空 | 默认值 |
| --- | --- | --- | --- |
| `id` | `character varying(64)` | NO | - |
| `status` | `character varying(32)` | NO | - |
| `report_id` | `bigint` | YES | - |
| `result_id` | `bigint` | YES | - |
| `export_format` | `character varying(32)` | YES | - |
| `locale_tag` | `character varying(64)` | YES | - |
| `desensitized_flag` | `boolean` | NO | true |
| `file_name` | `character varying(255)` | YES | - |
| `content_type` | `character varying(128)` | YES | - |
| `file_path` | `character varying(1024)` | YES | - |
| `file_size` | `bigint` | YES | - |
| `file_bytes` | `bytea` | YES | - |
| `error_message` | `text` | YES | - |
| `created_at` | `timestamp without time zone` | NO | CURRENT_TIMESTAMP |
| `completed_at` | `timestamp without time zone` | YES | - |
| `updated_at` | `timestamp without time zone` | NO | CURRENT_TIMESTAMP |
| `tenant_id` | `bigint` | NO | - |
| `created_by` | `bigint` | YES | - |
| `retry_count` | `integer` | NO | 0 |
| `next_retry_at` | `timestamp without time zone` | YES | - |
| `processing_started_at` | `timestamp without time zone` | YES | - |
| `processing_token` | `character varying(64)` | YES | - |
| `dead_letter_at` | `timestamp without time zone` | YES | - |

约束：

- `ck_psy_export_job_file_size` (CHECK) `CHECK (((file_size IS NULL) OR (file_size >= 0))) NOT VALID`
- `ck_psy_export_job_processing_lease` (CHECK) `CHECK (((((status)::text = 'PROCESSING'::text) AND (processing_started_at IS NOT NULL) AND (processing_token IS NOT NULL)) OR ((status)::text <> 'PROCESSING'::text)))`
- `ck_psy_export_job_retry_count` (CHECK) `CHECK ((retry_count >= 0))`
- `ck_psy_export_job_status` (CHECK) `CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'PROCESSING'::character varying, 'DONE'::character varying, 'FAILED'::character varying, 'DEAD_LETTER'::character varying])::text[])))`
- `ck_psy_export_tenant_required` (CHECK) `CHECK ((tenant_id IS NOT NULL))`
- `fk_psy_export_job_creator` (FOREIGN KEY) `FOREIGN KEY (created_by) REFERENCES sys_user(id) NOT VALID`
- `fk_psy_export_job_tenant` (FOREIGN KEY) `FOREIGN KEY (tenant_id) REFERENCES sys_tenant(id)`
- `psy_export_job_created_at_not_null` (NOT NULL) `NOT NULL created_at`
- `psy_export_job_desensitized_flag_not_null` (NOT NULL) `NOT NULL desensitized_flag`
- `psy_export_job_id_not_null` (NOT NULL) `NOT NULL id`
- `psy_export_job_retry_count_not_null` (NOT NULL) `NOT NULL retry_count`
- `psy_export_job_status_not_null` (NOT NULL) `NOT NULL status`
- `psy_export_job_tenant_id_not_null` (NOT NULL) `NOT NULL tenant_id`
- `psy_export_job_updated_at_not_null` (NOT NULL) `NOT NULL updated_at`
- `psy_export_job_pkey` (PRIMARY KEY) `PRIMARY KEY (id)`

索引：

- `idx_psy_export_job_created_at`：`CREATE INDEX idx_psy_export_job_created_at ON public.psy_export_job USING btree (created_at)`
- `idx_psy_export_job_pending_retry`：`CREATE INDEX idx_psy_export_job_pending_retry ON public.psy_export_job USING btree (next_retry_at, created_at, id) WHERE ((status)::text = 'PENDING'::text)`
- `idx_psy_export_job_report`：`CREATE INDEX idx_psy_export_job_report ON public.psy_export_job USING btree (report_id)`
- `idx_psy_export_job_result`：`CREATE INDEX idx_psy_export_job_result ON public.psy_export_job USING btree (result_id)`
- `idx_psy_export_job_status`：`CREATE INDEX idx_psy_export_job_status ON public.psy_export_job USING btree (status)`
- `idx_psy_export_job_tenant`：`CREATE INDEX idx_psy_export_job_tenant ON public.psy_export_job USING btree (tenant_id, created_at, id)`
- `psy_export_job_pkey`：`CREATE UNIQUE INDEX psy_export_job_pkey ON public.psy_export_job USING btree (id)`

#### `psy_report`

| 列 | 类型 | 可空 | 默认值 |
| --- | --- | --- | --- |
| `id` | `bigint` | NO | nextval('psy_report_id_seq'::regclass) |
| `result_id` | `bigint` | NO | - |
| `report_type` | `character varying(32)` | NO | - |
| `author_user_id` | `bigint` | YES | - |
| `report_title` | `character varying(255)` | YES | - |
| `report_content` | `text` | NO | - |
| `version_no` | `integer` | NO | 1 |
| `created_at` | `timestamp without time zone` | NO | CURRENT_TIMESTAMP |
| `updated_at` | `timestamp without time zone` | NO | CURRENT_TIMESTAMP |
| `locale_code` | `character varying(16)` | YES | - |

约束：

- `ck_psy_report_locale` (CHECK) `CHECK (((locale_code IS NULL) OR ((locale_code)::text = ANY ((ARRAY['zh-CN'::character varying, 'ja-JP'::character varying, 'en'::character varying])::text[])))) NOT VALID`
- `psy_report_result_id_fkey` (FOREIGN KEY) `FOREIGN KEY (result_id) REFERENCES psy_assessment_result(id)`
- `psy_report_created_at_not_null` (NOT NULL) `NOT NULL created_at`
- `psy_report_id_not_null` (NOT NULL) `NOT NULL id`
- `psy_report_report_content_not_null` (NOT NULL) `NOT NULL report_content`
- `psy_report_report_type_not_null` (NOT NULL) `NOT NULL report_type`
- `psy_report_result_id_not_null` (NOT NULL) `NOT NULL result_id`
- `psy_report_updated_at_not_null` (NOT NULL) `NOT NULL updated_at`
- `psy_report_version_no_not_null` (NOT NULL) `NOT NULL version_no`
- `psy_report_pkey` (PRIMARY KEY) `PRIMARY KEY (id)`

索引：

- `idx_psy_report_result_id`：`CREATE INDEX idx_psy_report_result_id ON public.psy_report USING btree (result_id)`
- `psy_report_pkey`：`CREATE UNIQUE INDEX psy_report_pkey ON public.psy_report USING btree (id)`
- `uk_psy_report_result_version`：`CREATE UNIQUE INDEX uk_psy_report_result_version ON public.psy_report USING btree (result_id, version_no)`

### 4.2 测评任务与作答

#### `psy_assessment_answer_item`

| 列 | 类型 | 可空 | 默认值 |
| --- | --- | --- | --- |
| `id` | `bigint` | NO | nextval('psy_assessment_answer_item_id_seq'::regclass) |
| `answer_sheet_id` | `bigint` | NO | - |
| `question_id` | `bigint` | NO | - |
| `option_id` | `bigint` | YES | - |
| `answer_text` | `text` | YES | - |
| `answer_value` | `numeric(10,2)` | YES | - |
| `score_value` | `numeric(10,2)` | YES | - |
| `created_at` | `timestamp without time zone` | NO | CURRENT_TIMESTAMP |

约束：

- `psy_assessment_answer_item_answer_sheet_id_fkey` (FOREIGN KEY) `FOREIGN KEY (answer_sheet_id) REFERENCES psy_assessment_answer_sheet(id) ON DELETE CASCADE`
- `psy_assessment_answer_item_option_id_fkey` (FOREIGN KEY) `FOREIGN KEY (option_id) REFERENCES psy_scale_option(id)`
- `psy_assessment_answer_item_question_id_fkey` (FOREIGN KEY) `FOREIGN KEY (question_id) REFERENCES psy_scale_question(id)`
- `psy_assessment_answer_item_answer_sheet_id_not_null` (NOT NULL) `NOT NULL answer_sheet_id`
- `psy_assessment_answer_item_created_at_not_null` (NOT NULL) `NOT NULL created_at`
- `psy_assessment_answer_item_id_not_null` (NOT NULL) `NOT NULL id`
- `psy_assessment_answer_item_question_id_not_null` (NOT NULL) `NOT NULL question_id`
- `psy_assessment_answer_item_pkey` (PRIMARY KEY) `PRIMARY KEY (id)`

索引：

- `idx_psy_assessment_answer_item_sheet_id`：`CREATE INDEX idx_psy_assessment_answer_item_sheet_id ON public.psy_assessment_answer_item USING btree (answer_sheet_id)`
- `psy_assessment_answer_item_pkey`：`CREATE UNIQUE INDEX psy_assessment_answer_item_pkey ON public.psy_assessment_answer_item USING btree (id)`

#### `psy_assessment_answer_sheet`

| 列 | 类型 | 可空 | 默认值 |
| --- | --- | --- | --- |
| `id` | `bigint` | NO | nextval('psy_assessment_answer_sheet_id_seq'::regclass) |
| `task_id` | `bigint` | NO | - |
| `scale_id` | `bigint` | NO | - |
| `user_id` | `bigint` | YES | - |
| `answer_status` | `character varying(32)` | NO | - |
| `version_no` | `integer` | NO | 1 |
| `start_time` | `timestamp without time zone` | YES | - |
| `submit_time` | `timestamp without time zone` | YES | - |
| `duration_seconds` | `integer` | YES | - |
| `anonymous_token` | `character varying(128)` | YES | - |
| `submit_token` | `character varying(128)` | YES | - |
| `created_at` | `timestamp without time zone` | NO | CURRENT_TIMESTAMP |
| `updated_at` | `timestamp without time zone` | NO | CURRENT_TIMESTAMP |
| `tenant_id` | `bigint` | NO | - |
| `response_locale_code` | `character varying(16)` | YES | - |
| `quality_status` | `character varying(32)` | NO | 'NOT_EVALUATED'::character varying |
| `quality_issue_codes` | `character varying(1000)` | YES | - |
| `quality_missing_ratio` | `numeric(6,5)` | YES | - |
| `quality_duration_seconds` | `integer` | YES | - |

约束：

- `ck_psy_answer_sheet_duration` (CHECK) `CHECK (((duration_seconds IS NULL) OR (duration_seconds >= 0))) NOT VALID`
- `ck_psy_answer_sheet_identity` (CHECK) `CHECK ((((user_id IS NOT NULL) AND (anonymous_token IS NULL)) OR ((user_id IS NULL) AND (anonymous_token IS NOT NULL)))) NOT VALID`
- `ck_psy_answer_sheet_quality_duration` (CHECK) `CHECK (((quality_duration_seconds IS NULL) OR (quality_duration_seconds >= 0)))`
- `ck_psy_answer_sheet_quality_ratio` (CHECK) `CHECK (((quality_missing_ratio IS NULL) OR ((quality_missing_ratio >= (0)::numeric) AND (quality_missing_ratio <= (1)::numeric))))`
- `ck_psy_answer_sheet_quality_status` (CHECK) `CHECK (((quality_status)::text = ANY ((ARRAY['NOT_EVALUATED'::character varying, 'VALID'::character varying, 'WARNING'::character varying, 'REVIEW_REQUIRED'::character varying, 'INVALID'::character varying])::text[])))`
- `ck_psy_answer_sheet_response_locale` (CHECK) `CHECK (((response_locale_code IS NULL) OR ((response_locale_code)::text = ANY ((ARRAY['zh-CN'::character varying, 'ja-JP'::character varying, 'en'::character varying])::text[])))) NOT VALID`
- `ck_psy_answer_sheet_status` (CHECK) `CHECK (((answer_status)::text = ANY ((ARRAY['DRAFT'::character varying, 'SUBMITTED'::character varying])::text[]))) NOT VALID`
- `ck_psy_answer_tenant_required` (CHECK) `CHECK ((tenant_id IS NOT NULL))`
- `fk_psy_answer_tenant` (FOREIGN KEY) `FOREIGN KEY (tenant_id) REFERENCES sys_tenant(id)`
- `psy_assessment_answer_sheet_scale_id_fkey` (FOREIGN KEY) `FOREIGN KEY (scale_id) REFERENCES psy_scale(id)`
- `psy_assessment_answer_sheet_task_id_fkey` (FOREIGN KEY) `FOREIGN KEY (task_id) REFERENCES psy_assessment_task(id)`
- `psy_assessment_answer_sheet_answer_status_not_null` (NOT NULL) `NOT NULL answer_status`
- `psy_assessment_answer_sheet_created_at_not_null` (NOT NULL) `NOT NULL created_at`
- `psy_assessment_answer_sheet_id_not_null` (NOT NULL) `NOT NULL id`
- `psy_assessment_answer_sheet_quality_status_not_null` (NOT NULL) `NOT NULL quality_status`
- `psy_assessment_answer_sheet_scale_id_not_null` (NOT NULL) `NOT NULL scale_id`
- `psy_assessment_answer_sheet_task_id_not_null` (NOT NULL) `NOT NULL task_id`
- `psy_assessment_answer_sheet_tenant_id_not_null` (NOT NULL) `NOT NULL tenant_id`
- `psy_assessment_answer_sheet_updated_at_not_null` (NOT NULL) `NOT NULL updated_at`
- `psy_assessment_answer_sheet_version_no_not_null` (NOT NULL) `NOT NULL version_no`
- `psy_assessment_answer_sheet_pkey` (PRIMARY KEY) `PRIMARY KEY (id)`

索引：

- `idx_psy_answer_tenant_task`：`CREATE INDEX idx_psy_answer_tenant_task ON public.psy_assessment_answer_sheet USING btree (tenant_id, task_id, id)`
- `idx_psy_assessment_answer_sheet_submit_token`：`CREATE INDEX idx_psy_assessment_answer_sheet_submit_token ON public.psy_assessment_answer_sheet USING btree (submit_token)`
- `idx_psy_assessment_answer_sheet_task_id`：`CREATE INDEX idx_psy_assessment_answer_sheet_task_id ON public.psy_assessment_answer_sheet USING btree (task_id)`
- `idx_psy_assessment_answer_sheet_user_id`：`CREATE INDEX idx_psy_assessment_answer_sheet_user_id ON public.psy_assessment_answer_sheet USING btree (user_id)`
- `psy_assessment_answer_sheet_pkey`：`CREATE UNIQUE INDEX psy_assessment_answer_sheet_pkey ON public.psy_assessment_answer_sheet USING btree (id)`
- `uk_psy_answer_sheet_active_draft_anonymous`：`CREATE UNIQUE INDEX uk_psy_answer_sheet_active_draft_anonymous ON public.psy_assessment_answer_sheet USING btree (task_id, anonymous_token) WHERE (((answer_status)::text = 'DRAFT'::text) AND (user_id IS NULL) AND (anonymous_token IS NOT NULL))`
- `uk_psy_answer_sheet_active_draft_user`：`CREATE UNIQUE INDEX uk_psy_answer_sheet_active_draft_user ON public.psy_assessment_answer_sheet USING btree (task_id, user_id) WHERE (((answer_status)::text = 'DRAFT'::text) AND (user_id IS NOT NULL))`
- `uk_psy_answer_sheet_submit_token_anonymous`：`CREATE UNIQUE INDEX uk_psy_answer_sheet_submit_token_anonymous ON public.psy_assessment_answer_sheet USING btree (task_id, anonymous_token, submit_token) WHERE (((answer_status)::text = 'SUBMITTED'::text) AND (user_id IS NULL) AND (anonymous_token IS NOT NULL) AND (submit_token IS NOT NULL))`
- `uk_psy_answer_sheet_submit_token_user_task`：`CREATE UNIQUE INDEX uk_psy_answer_sheet_submit_token_user_task ON public.psy_assessment_answer_sheet USING btree (task_id, user_id, submit_token) WHERE (((answer_status)::text = 'SUBMITTED'::text) AND (user_id IS NOT NULL) AND (submit_token IS NOT NULL))`

#### `psy_assessment_result`

| 列 | 类型 | 可空 | 默认值 |
| --- | --- | --- | --- |
| `id` | `bigint` | NO | nextval('psy_assessment_result_id_seq'::regclass) |
| `answer_sheet_id` | `bigint` | NO | - |
| `total_score` | `numeric(10,2)` | NO | - |
| `risk_level` | `character varying(32)` | NO | - |
| `warning_flag` | `boolean` | NO | false |
| `result_summary` | `text` | YES | - |
| `score_source` | `character varying(32)` | NO | 'RAW_SCORE'::character varying |
| `standard_score` | `numeric(10,4)` | YES | - |
| `z_score` | `numeric(10,4)` | YES | - |
| `t_score` | `numeric(10,4)` | YES | - |
| `norm_code` | `character varying(64)` | YES | - |
| `high_risk_flag` | `boolean` | NO | false |
| `high_risk_rule_code` | `character varying(64)` | YES | - |
| `scored_at` | `timestamp without time zone` | NO | CURRENT_TIMESTAMP |
| `created_at` | `timestamp without time zone` | NO | CURRENT_TIMESTAMP |
| `calculation_version` | `integer` | NO | 1 |
| `is_current` | `boolean` | NO | true |
| `supersedes_result_id` | `bigint` | YES | - |
| `rescored_by` | `bigint` | YES | - |
| `scale_content_hash` | `character varying(64)` | YES | - |
| `scoring_engine_version` | `character varying(64)` | NO | 'generic-v1'::character varying |
| `quality_status` | `character varying(32)` | NO | 'NOT_EVALUATED'::character varying |
| `quality_issue_codes` | `character varying(1000)` | YES | - |
| `quality_missing_ratio` | `numeric(6,5)` | YES | - |
| `quality_duration_seconds` | `integer` | YES | - |
| `scoring_trace_json` | `jsonb` | YES | - |

约束：

- `ck_psy_result_calculation_version` (CHECK) `CHECK ((calculation_version > 0)) NOT VALID`
- `ck_psy_result_quality_duration` (CHECK) `CHECK (((quality_duration_seconds IS NULL) OR (quality_duration_seconds >= 0)))`
- `ck_psy_result_quality_ratio` (CHECK) `CHECK (((quality_missing_ratio IS NULL) OR ((quality_missing_ratio >= (0)::numeric) AND (quality_missing_ratio <= (1)::numeric))))`
- `ck_psy_result_quality_status` (CHECK) `CHECK (((quality_status)::text = ANY ((ARRAY['NOT_EVALUATED'::character varying, 'VALID'::character varying, 'WARNING'::character varying, 'REVIEW_REQUIRED'::character varying, 'INVALID'::character varying])::text[])))`
- `ck_psy_result_scoring_trace_json` (CHECK) `CHECK (((scoring_trace_json IS NULL) OR (jsonb_typeof(scoring_trace_json) = 'object'::text)))`
- `fk_psy_result_supersedes` (FOREIGN KEY) `FOREIGN KEY (supersedes_result_id) REFERENCES psy_assessment_result(id) NOT VALID`
- `psy_assessment_result_answer_sheet_id_fkey` (FOREIGN KEY) `FOREIGN KEY (answer_sheet_id) REFERENCES psy_assessment_answer_sheet(id)`
- `psy_assessment_result_answer_sheet_id_not_null` (NOT NULL) `NOT NULL answer_sheet_id`
- `psy_assessment_result_calculation_version_not_null` (NOT NULL) `NOT NULL calculation_version`
- `psy_assessment_result_created_at_not_null` (NOT NULL) `NOT NULL created_at`
- `psy_assessment_result_high_risk_flag_not_null` (NOT NULL) `NOT NULL high_risk_flag`
- `psy_assessment_result_id_not_null` (NOT NULL) `NOT NULL id`
- `psy_assessment_result_is_current_not_null` (NOT NULL) `NOT NULL is_current`
- `psy_assessment_result_quality_status_not_null` (NOT NULL) `NOT NULL quality_status`
- `psy_assessment_result_risk_level_not_null` (NOT NULL) `NOT NULL risk_level`
- `psy_assessment_result_score_source_not_null` (NOT NULL) `NOT NULL score_source`
- `psy_assessment_result_scored_at_not_null` (NOT NULL) `NOT NULL scored_at`
- `psy_assessment_result_scoring_engine_version_not_null` (NOT NULL) `NOT NULL scoring_engine_version`
- `psy_assessment_result_total_score_not_null` (NOT NULL) `NOT NULL total_score`
- `psy_assessment_result_warning_flag_not_null` (NOT NULL) `NOT NULL warning_flag`
- `psy_assessment_result_pkey` (PRIMARY KEY) `PRIMARY KEY (id)`

索引：

- `idx_psy_result_supersedes`：`CREATE INDEX idx_psy_result_supersedes ON public.psy_assessment_result USING btree (supersedes_result_id) WHERE (supersedes_result_id IS NOT NULL)`
- `psy_assessment_result_pkey`：`CREATE UNIQUE INDEX psy_assessment_result_pkey ON public.psy_assessment_result USING btree (id)`
- `uk_psy_result_sheet_calculation_version`：`CREATE UNIQUE INDEX uk_psy_result_sheet_calculation_version ON public.psy_assessment_result USING btree (answer_sheet_id, calculation_version)`
- `uk_psy_result_sheet_current`：`CREATE UNIQUE INDEX uk_psy_result_sheet_current ON public.psy_assessment_result USING btree (answer_sheet_id) WHERE (is_current = true)`

#### `psy_assessment_result_dimension`

| 列 | 类型 | 可空 | 默认值 |
| --- | --- | --- | --- |
| `id` | `bigint` | NO | nextval('psy_assessment_result_dimension_id_seq'::regclass) |
| `result_id` | `bigint` | NO | - |
| `dimension_id` | `bigint` | NO | - |
| `dimension_score` | `numeric(10,4)` | NO | - |
| `risk_level` | `character varying(32)` | YES | - |
| `result_title` | `character varying(255)` | YES | - |
| `created_at` | `timestamp without time zone` | NO | CURRENT_TIMESTAMP |

约束：

- `psy_assessment_result_dimension_result_id_fkey` (FOREIGN KEY) `FOREIGN KEY (result_id) REFERENCES psy_assessment_result(id)`
- `psy_assessment_result_dimension_created_at_not_null` (NOT NULL) `NOT NULL created_at`
- `psy_assessment_result_dimension_dimension_id_not_null` (NOT NULL) `NOT NULL dimension_id`
- `psy_assessment_result_dimension_dimension_score_not_null` (NOT NULL) `NOT NULL dimension_score`
- `psy_assessment_result_dimension_id_not_null` (NOT NULL) `NOT NULL id`
- `psy_assessment_result_dimension_result_id_not_null` (NOT NULL) `NOT NULL result_id`
- `psy_assessment_result_dimension_pkey` (PRIMARY KEY) `PRIMARY KEY (id)`

索引：

- `idx_psy_result_dimension_result_id`：`CREATE INDEX idx_psy_result_dimension_result_id ON public.psy_assessment_result_dimension USING btree (result_id)`
- `psy_assessment_result_dimension_pkey`：`CREATE UNIQUE INDEX psy_assessment_result_dimension_pkey ON public.psy_assessment_result_dimension USING btree (id)`

#### `psy_assessment_task`

| 列 | 类型 | 可空 | 默认值 |
| --- | --- | --- | --- |
| `id` | `bigint` | NO | nextval('psy_assessment_task_id_seq'::regclass) |
| `task_name` | `character varying(255)` | NO | - |
| `scale_id` | `bigint` | NO | - |
| `scale_version_no` | `character varying(32)` | YES | - |
| `scale_version_group_id` | `bigint` | YES | - |
| `task_mode` | `character varying(32)` | NO | - |
| `anonymous_flag` | `boolean` | NO | false |
| `allow_save_flag` | `boolean` | NO | true |
| `allow_timeout_submit_flag` | `boolean` | NO | false |
| `allow_retake_flag` | `boolean` | NO | false |
| `start_time` | `timestamp without time zone` | NO | - |
| `end_time` | `timestamp without time zone` | NO | - |
| `status` | `character varying(32)` | NO | 'DRAFT'::character varying |
| `created_by` | `bigint` | YES | - |
| `created_at` | `timestamp without time zone` | NO | CURRENT_TIMESTAMP |
| `updated_at` | `timestamp without time zone` | NO | CURRENT_TIMESTAMP |
| `overdue_notified_at` | `timestamp without time zone` | YES | - |
| `closed_at` | `timestamp without time zone` | YES | - |
| `closed_by` | `bigint` | YES | - |
| `close_reason` | `character varying(500)` | YES | - |
| `tenant_id` | `bigint` | NO | - |
| `scale_content_hash` | `character varying(64)` | YES | - |

约束：

- `ck_psy_assessment_task_status` (CHECK) `CHECK (((status)::text = ANY ((ARRAY['DRAFT'::character varying, 'IN_PROGRESS'::character varying, 'OVERDUE'::character varying, 'CLOSED'::character varying])::text[]))) NOT VALID`
- `ck_psy_assessment_task_time_range` (CHECK) `CHECK ((end_time > start_time)) NOT VALID`
- `ck_psy_task_tenant_required` (CHECK) `CHECK ((tenant_id IS NOT NULL))`
- `fk_psy_task_tenant` (FOREIGN KEY) `FOREIGN KEY (tenant_id) REFERENCES sys_tenant(id)`
- `psy_assessment_task_scale_id_fkey` (FOREIGN KEY) `FOREIGN KEY (scale_id) REFERENCES psy_scale(id)`
- `psy_assessment_task_allow_retake_flag_not_null` (NOT NULL) `NOT NULL allow_retake_flag`
- `psy_assessment_task_allow_save_flag_not_null` (NOT NULL) `NOT NULL allow_save_flag`
- `psy_assessment_task_allow_timeout_submit_flag_not_null` (NOT NULL) `NOT NULL allow_timeout_submit_flag`
- `psy_assessment_task_anonymous_flag_not_null` (NOT NULL) `NOT NULL anonymous_flag`
- `psy_assessment_task_created_at_not_null` (NOT NULL) `NOT NULL created_at`
- `psy_assessment_task_end_time_not_null` (NOT NULL) `NOT NULL end_time`
- `psy_assessment_task_id_not_null` (NOT NULL) `NOT NULL id`
- `psy_assessment_task_scale_id_not_null` (NOT NULL) `NOT NULL scale_id`
- `psy_assessment_task_start_time_not_null` (NOT NULL) `NOT NULL start_time`
- `psy_assessment_task_status_not_null` (NOT NULL) `NOT NULL status`
- `psy_assessment_task_task_mode_not_null` (NOT NULL) `NOT NULL task_mode`
- `psy_assessment_task_task_name_not_null` (NOT NULL) `NOT NULL task_name`
- `psy_assessment_task_tenant_id_not_null` (NOT NULL) `NOT NULL tenant_id`
- `psy_assessment_task_updated_at_not_null` (NOT NULL) `NOT NULL updated_at`
- `psy_assessment_task_pkey` (PRIMARY KEY) `PRIMARY KEY (id)`

索引：

- `idx_psy_assessment_task_scale_id`：`CREATE INDEX idx_psy_assessment_task_scale_id ON public.psy_assessment_task USING btree (scale_id)`
- `idx_psy_assessment_task_scale_version_group`：`CREATE INDEX idx_psy_assessment_task_scale_version_group ON public.psy_assessment_task USING btree (scale_version_group_id)`
- `idx_psy_assessment_task_status`：`CREATE INDEX idx_psy_assessment_task_status ON public.psy_assessment_task USING btree (status)`
- `idx_psy_task_scale_content_hash`：`CREATE INDEX idx_psy_task_scale_content_hash ON public.psy_assessment_task USING btree (scale_content_hash) WHERE (scale_content_hash IS NOT NULL)`
- `idx_psy_task_tenant_status`：`CREATE INDEX idx_psy_task_tenant_status ON public.psy_assessment_task USING btree (tenant_id, status, id)`
- `psy_assessment_task_pkey`：`CREATE UNIQUE INDEX psy_assessment_task_pkey ON public.psy_assessment_task USING btree (id)`

#### `psy_assessment_task_assignment`

| 列 | 类型 | 可空 | 默认值 |
| --- | --- | --- | --- |
| `id` | `bigint` | NO | nextval('psy_assessment_task_assignment_id_seq'::regclass) |
| `task_id` | `bigint` | NO | - |
| `target_type` | `character varying(32)` | NO | - |
| `target_id` | `bigint` | NO | - |
| `assigned_by` | `bigint` | YES | - |
| `assigned_at` | `timestamp without time zone` | NO | CURRENT_TIMESTAMP |

约束：

- `ck_psy_task_assignment_target_type` (CHECK) `CHECK (((target_type)::text = ANY ((ARRAY['USER'::character varying, 'GROUP'::character varying])::text[]))) NOT VALID`
- `psy_assessment_task_assignment_task_id_fkey` (FOREIGN KEY) `FOREIGN KEY (task_id) REFERENCES psy_assessment_task(id)`
- `psy_assessment_task_assignment_assigned_at_not_null` (NOT NULL) `NOT NULL assigned_at`
- `psy_assessment_task_assignment_id_not_null` (NOT NULL) `NOT NULL id`
- `psy_assessment_task_assignment_target_id_not_null` (NOT NULL) `NOT NULL target_id`
- `psy_assessment_task_assignment_target_type_not_null` (NOT NULL) `NOT NULL target_type`
- `psy_assessment_task_assignment_task_id_not_null` (NOT NULL) `NOT NULL task_id`
- `psy_assessment_task_assignment_pkey` (PRIMARY KEY) `PRIMARY KEY (id)`

索引：

- `idx_psy_assessment_task_assignment_target`：`CREATE INDEX idx_psy_assessment_task_assignment_target ON public.psy_assessment_task_assignment USING btree (target_type, target_id)`
- `idx_psy_assessment_task_assignment_task_id`：`CREATE INDEX idx_psy_assessment_task_assignment_task_id ON public.psy_assessment_task_assignment USING btree (task_id)`
- `psy_assessment_task_assignment_pkey`：`CREATE UNIQUE INDEX psy_assessment_task_assignment_pkey ON public.psy_assessment_task_assignment USING btree (id)`
- `uk_psy_task_assignment_target`：`CREATE UNIQUE INDEX uk_psy_task_assignment_target ON public.psy_assessment_task_assignment USING btree (task_id, target_type, target_id)`

### 4.3 认证、权限与会话

#### `psy_user_device`

| 列 | 类型 | 可空 | 默认值 |
| --- | --- | --- | --- |
| `id` | `bigint` | NO | nextval('psy_user_device_id_seq'::regclass) |
| `user_id` | `bigint` | NO | - |
| `device_type` | `character varying(32)` | NO | - |
| `device_id` | `character varying(128)` | NO | - |
| `push_token` | `character varying(512)` | YES | - |
| `app_version` | `character varying(64)` | YES | - |
| `active_flag` | `boolean` | NO | true |
| `last_active_at` | `timestamp without time zone` | YES | - |
| `created_at` | `timestamp without time zone` | NO | CURRENT_TIMESTAMP |
| `updated_at` | `timestamp without time zone` | NO | CURRENT_TIMESTAMP |

约束：

- `psy_user_device_user_id_fkey` (FOREIGN KEY) `FOREIGN KEY (user_id) REFERENCES sys_user(id)`
- `psy_user_device_active_flag_not_null` (NOT NULL) `NOT NULL active_flag`
- `psy_user_device_created_at_not_null` (NOT NULL) `NOT NULL created_at`
- `psy_user_device_device_id_not_null` (NOT NULL) `NOT NULL device_id`
- `psy_user_device_device_type_not_null` (NOT NULL) `NOT NULL device_type`
- `psy_user_device_id_not_null` (NOT NULL) `NOT NULL id`
- `psy_user_device_updated_at_not_null` (NOT NULL) `NOT NULL updated_at`
- `psy_user_device_user_id_not_null` (NOT NULL) `NOT NULL user_id`
- `psy_user_device_pkey` (PRIMARY KEY) `PRIMARY KEY (id)`
- `uk_psy_user_device_user_device` (UNIQUE) `UNIQUE (user_id, device_id)`

索引：

- `idx_psy_user_device_push_token`：`CREATE INDEX idx_psy_user_device_push_token ON public.psy_user_device USING btree (push_token)`
- `idx_psy_user_device_user_active`：`CREATE INDEX idx_psy_user_device_user_active ON public.psy_user_device USING btree (user_id, active_flag)`
- `psy_user_device_pkey`：`CREATE UNIQUE INDEX psy_user_device_pkey ON public.psy_user_device USING btree (id)`
- `uk_psy_user_device_user_device`：`CREATE UNIQUE INDEX uk_psy_user_device_user_device ON public.psy_user_device USING btree (user_id, device_id)`

#### `sys_auth`

| 列 | 类型 | 可空 | 默认值 |
| --- | --- | --- | --- |
| `id` | `bigint` | NO | nextval('sys_auth_id_seq'::regclass) |
| `user_id` | `bigint` | NO | - |
| `identity_type` | `character varying(32)` | NO | - |
| `principal_key` | `character varying(191)` | NO | - |
| `credential_hash` | `character varying(255)` | YES | - |
| `metadata_json` | `jsonb` | YES | - |
| `enabled` | `smallint` | NO | 1 |
| `created_at` | `timestamp without time zone` | NO | CURRENT_TIMESTAMP |
| `updated_at` | `timestamp without time zone` | NO | CURRENT_TIMESTAMP |

约束：

- `fk_sys_auth_user_id` (FOREIGN KEY) `FOREIGN KEY (user_id) REFERENCES sys_user(id)`
- `sys_auth_created_at_not_null` (NOT NULL) `NOT NULL created_at`
- `sys_auth_enabled_not_null` (NOT NULL) `NOT NULL enabled`
- `sys_auth_id_not_null` (NOT NULL) `NOT NULL id`
- `sys_auth_identity_type_not_null` (NOT NULL) `NOT NULL identity_type`
- `sys_auth_principal_key_not_null` (NOT NULL) `NOT NULL principal_key`
- `sys_auth_updated_at_not_null` (NOT NULL) `NOT NULL updated_at`
- `sys_auth_user_id_not_null` (NOT NULL) `NOT NULL user_id`
- `sys_auth_pkey` (PRIMARY KEY) `PRIMARY KEY (id)`

索引：

- `idx_sys_auth_user_id`：`CREATE INDEX idx_sys_auth_user_id ON public.sys_auth USING btree (user_id)`
- `sys_auth_pkey`：`CREATE UNIQUE INDEX sys_auth_pkey ON public.sys_auth USING btree (id)`
- `uk_sys_auth_identity_principal`：`CREATE UNIQUE INDEX uk_sys_auth_identity_principal ON public.sys_auth USING btree (identity_type, principal_key)`

#### `sys_group`

| 列 | 类型 | 可空 | 默认值 |
| --- | --- | --- | --- |
| `id` | `bigint` | NO | nextval('sys_group_id_seq'::regclass) |
| `tenant_id` | `bigint` | YES | - |
| `group_code` | `character varying(64)` | NO | - |
| `group_name` | `character varying(128)` | NO | - |
| `parent_id` | `bigint` | YES | - |
| `ancestors` | `character varying(512)` | YES | - |
| `is_default` | `smallint` | NO | 0 |
| `created_at` | `timestamp without time zone` | NO | CURRENT_TIMESTAMP |
| `updated_at` | `timestamp without time zone` | NO | CURRENT_TIMESTAMP |

约束：

- `sys_group_created_at_not_null` (NOT NULL) `NOT NULL created_at`
- `sys_group_group_code_not_null` (NOT NULL) `NOT NULL group_code`
- `sys_group_group_name_not_null` (NOT NULL) `NOT NULL group_name`
- `sys_group_id_not_null` (NOT NULL) `NOT NULL id`
- `sys_group_is_default_not_null` (NOT NULL) `NOT NULL is_default`
- `sys_group_updated_at_not_null` (NOT NULL) `NOT NULL updated_at`
- `sys_group_pkey` (PRIMARY KEY) `PRIMARY KEY (id)`

索引：

- `idx_sys_group_parent_id`：`CREATE INDEX idx_sys_group_parent_id ON public.sys_group USING btree (parent_id)`
- `sys_group_pkey`：`CREATE UNIQUE INDEX sys_group_pkey ON public.sys_group USING btree (id)`
- `uk_sys_group_code`：`CREATE UNIQUE INDEX uk_sys_group_code ON public.sys_group USING btree (group_code)`

#### `sys_group_role`

| 列 | 类型 | 可空 | 默认值 |
| --- | --- | --- | --- |
| `id` | `bigint` | NO | nextval('sys_group_role_id_seq'::regclass) |
| `group_id` | `bigint` | NO | - |
| `role_id` | `bigint` | NO | - |
| `created_at` | `timestamp without time zone` | NO | CURRENT_TIMESTAMP |

约束：

- `fk_sys_group_role_group_id` (FOREIGN KEY) `FOREIGN KEY (group_id) REFERENCES sys_group(id)`
- `fk_sys_group_role_role_id` (FOREIGN KEY) `FOREIGN KEY (role_id) REFERENCES sys_role(id)`
- `sys_group_role_created_at_not_null` (NOT NULL) `NOT NULL created_at`
- `sys_group_role_group_id_not_null` (NOT NULL) `NOT NULL group_id`
- `sys_group_role_id_not_null` (NOT NULL) `NOT NULL id`
- `sys_group_role_role_id_not_null` (NOT NULL) `NOT NULL role_id`
- `sys_group_role_pkey` (PRIMARY KEY) `PRIMARY KEY (id)`

索引：

- `sys_group_role_pkey`：`CREATE UNIQUE INDEX sys_group_role_pkey ON public.sys_group_role USING btree (id)`
- `uk_sys_group_role`：`CREATE UNIQUE INDEX uk_sys_group_role ON public.sys_group_role USING btree (group_id, role_id)`

#### `sys_login_log`

| 列 | 类型 | 可空 | 默认值 |
| --- | --- | --- | --- |
| `id` | `bigint` | NO | nextval('sys_login_log_id_seq'::regclass) |
| `user_id` | `bigint` | YES | - |
| `principal` | `character varying(191)` | YES | - |
| `login_type` | `character varying(32)` | NO | - |
| `result` | `character varying(32)` | NO | - |
| `ip` | `character varying(64)` | YES | - |
| `user_agent` | `character varying(512)` | YES | - |
| `location` | `character varying(255)` | YES | - |
| `reason` | `character varying(255)` | YES | - |
| `created_at` | `timestamp without time zone` | NO | CURRENT_TIMESTAMP |

约束：

- `sys_login_log_created_at_not_null` (NOT NULL) `NOT NULL created_at`
- `sys_login_log_id_not_null` (NOT NULL) `NOT NULL id`
- `sys_login_log_login_type_not_null` (NOT NULL) `NOT NULL login_type`
- `sys_login_log_result_not_null` (NOT NULL) `NOT NULL result`
- `sys_login_log_pkey` (PRIMARY KEY) `PRIMARY KEY (id)`

索引：

- `idx_sys_login_log_created_at`：`CREATE INDEX idx_sys_login_log_created_at ON public.sys_login_log USING btree (created_at)`
- `idx_sys_login_log_principal_result_id_desc`：`CREATE INDEX idx_sys_login_log_principal_result_id_desc ON public.sys_login_log USING btree (principal, result, id DESC)`
- `idx_sys_login_log_result_id_desc`：`CREATE INDEX idx_sys_login_log_result_id_desc ON public.sys_login_log USING btree (result, id DESC)`
- `idx_sys_login_log_user_id`：`CREATE INDEX idx_sys_login_log_user_id ON public.sys_login_log USING btree (user_id)`
- `sys_login_log_pkey`：`CREATE UNIQUE INDEX sys_login_log_pkey ON public.sys_login_log USING btree (id)`

#### `sys_permission`

| 列 | 类型 | 可空 | 默认值 |
| --- | --- | --- | --- |
| `id` | `bigint` | NO | nextval('sys_permission_id_seq'::regclass) |
| `tenant_id` | `bigint` | YES | - |
| `permission_code` | `character varying(128)` | NO | - |
| `permission_name` | `character varying(128)` | NO | - |
| `permission_type` | `character varying(32)` | NO | - |
| `parent_id` | `bigint` | YES | - |
| `path` | `character varying(255)` | YES | - |
| `http_method` | `character varying(16)` | YES | - |
| `resource_pattern` | `character varying(255)` | YES | - |
| `enabled` | `smallint` | NO | 1 |
| `created_at` | `timestamp without time zone` | NO | CURRENT_TIMESTAMP |
| `updated_at` | `timestamp without time zone` | NO | CURRENT_TIMESTAMP |

约束：

- `sys_permission_created_at_not_null` (NOT NULL) `NOT NULL created_at`
- `sys_permission_enabled_not_null` (NOT NULL) `NOT NULL enabled`
- `sys_permission_id_not_null` (NOT NULL) `NOT NULL id`
- `sys_permission_permission_code_not_null` (NOT NULL) `NOT NULL permission_code`
- `sys_permission_permission_name_not_null` (NOT NULL) `NOT NULL permission_name`
- `sys_permission_permission_type_not_null` (NOT NULL) `NOT NULL permission_type`
- `sys_permission_updated_at_not_null` (NOT NULL) `NOT NULL updated_at`
- `sys_permission_pkey` (PRIMARY KEY) `PRIMARY KEY (id)`

索引：

- `sys_permission_pkey`：`CREATE UNIQUE INDEX sys_permission_pkey ON public.sys_permission USING btree (id)`
- `uk_sys_perm_tenant_code`：`CREATE UNIQUE INDEX uk_sys_perm_tenant_code ON public.sys_permission USING btree (tenant_id, permission_code)`

#### `sys_qr_scene`

| 列 | 类型 | 可空 | 默认值 |
| --- | --- | --- | --- |
| `id` | `bigint` | NO | nextval('sys_qr_scene_id_seq'::regclass) |
| `scene_code` | `character varying(64)` | NO | - |
| `status` | `character varying(32)` | NO | - |
| `scanned_user_id` | `bigint` | YES | - |
| `scanned_at` | `timestamp without time zone` | YES | - |
| `approved_user_id` | `bigint` | YES | - |
| `approved_at` | `timestamp without time zone` | YES | - |
| `consumed_at` | `timestamp without time zone` | YES | - |
| `expire_at` | `timestamp without time zone` | NO | - |
| `created_at` | `timestamp without time zone` | NO | CURRENT_TIMESTAMP |

约束：

- `sys_qr_scene_created_at_not_null` (NOT NULL) `NOT NULL created_at`
- `sys_qr_scene_expire_at_not_null` (NOT NULL) `NOT NULL expire_at`
- `sys_qr_scene_id_not_null` (NOT NULL) `NOT NULL id`
- `sys_qr_scene_scene_code_not_null` (NOT NULL) `NOT NULL scene_code`
- `sys_qr_scene_status_not_null` (NOT NULL) `NOT NULL status`
- `sys_qr_scene_pkey` (PRIMARY KEY) `PRIMARY KEY (id)`

索引：

- `idx_sys_qr_scene_expire_at`：`CREATE INDEX idx_sys_qr_scene_expire_at ON public.sys_qr_scene USING btree (expire_at)`
- `idx_sys_qr_scene_status`：`CREATE INDEX idx_sys_qr_scene_status ON public.sys_qr_scene USING btree (status)`
- `sys_qr_scene_pkey`：`CREATE UNIQUE INDEX sys_qr_scene_pkey ON public.sys_qr_scene USING btree (id)`
- `uk_sys_qr_scene_code`：`CREATE UNIQUE INDEX uk_sys_qr_scene_code ON public.sys_qr_scene USING btree (scene_code)`

#### `sys_role`

| 列 | 类型 | 可空 | 默认值 |
| --- | --- | --- | --- |
| `id` | `bigint` | NO | nextval('sys_role_id_seq'::regclass) |
| `tenant_id` | `bigint` | YES | - |
| `role_code` | `character varying(64)` | NO | - |
| `role_name` | `character varying(128)` | NO | - |
| `data_scope` | `character varying(32)` | NO | 'SELF'::character varying |
| `enabled` | `smallint` | NO | 1 |
| `created_at` | `timestamp without time zone` | NO | CURRENT_TIMESTAMP |
| `updated_at` | `timestamp without time zone` | NO | CURRENT_TIMESTAMP |

约束：

- `sys_role_created_at_not_null` (NOT NULL) `NOT NULL created_at`
- `sys_role_data_scope_not_null` (NOT NULL) `NOT NULL data_scope`
- `sys_role_enabled_not_null` (NOT NULL) `NOT NULL enabled`
- `sys_role_id_not_null` (NOT NULL) `NOT NULL id`
- `sys_role_role_code_not_null` (NOT NULL) `NOT NULL role_code`
- `sys_role_role_name_not_null` (NOT NULL) `NOT NULL role_name`
- `sys_role_updated_at_not_null` (NOT NULL) `NOT NULL updated_at`
- `sys_role_pkey` (PRIMARY KEY) `PRIMARY KEY (id)`

索引：

- `sys_role_pkey`：`CREATE UNIQUE INDEX sys_role_pkey ON public.sys_role USING btree (id)`
- `uk_sys_role_tenant_code`：`CREATE UNIQUE INDEX uk_sys_role_tenant_code ON public.sys_role USING btree (tenant_id, role_code)`

#### `sys_role_permission`

| 列 | 类型 | 可空 | 默认值 |
| --- | --- | --- | --- |
| `id` | `bigint` | NO | nextval('sys_role_permission_id_seq'::regclass) |
| `role_id` | `bigint` | NO | - |
| `permission_id` | `bigint` | NO | - |
| `created_at` | `timestamp without time zone` | NO | CURRENT_TIMESTAMP |

约束：

- `fk_sys_role_permission_permission_id` (FOREIGN KEY) `FOREIGN KEY (permission_id) REFERENCES sys_permission(id)`
- `fk_sys_role_permission_role_id` (FOREIGN KEY) `FOREIGN KEY (role_id) REFERENCES sys_role(id)`
- `sys_role_permission_created_at_not_null` (NOT NULL) `NOT NULL created_at`
- `sys_role_permission_id_not_null` (NOT NULL) `NOT NULL id`
- `sys_role_permission_permission_id_not_null` (NOT NULL) `NOT NULL permission_id`
- `sys_role_permission_role_id_not_null` (NOT NULL) `NOT NULL role_id`
- `sys_role_permission_pkey` (PRIMARY KEY) `PRIMARY KEY (id)`

索引：

- `sys_role_permission_pkey`：`CREATE UNIQUE INDEX sys_role_permission_pkey ON public.sys_role_permission USING btree (id)`
- `uk_sys_role_permission`：`CREATE UNIQUE INDEX uk_sys_role_permission ON public.sys_role_permission USING btree (role_id, permission_id)`

#### `sys_security_event`

| 列 | 类型 | 可空 | 默认值 |
| --- | --- | --- | --- |
| `id` | `bigint` | NO | nextval('sys_security_event_id_seq'::regclass) |
| `event_type` | `character varying(64)` | NO | - |
| `user_id` | `bigint` | YES | - |
| `tenant_id` | `bigint` | YES | - |
| `detail_json` | `jsonb` | YES | - |
| `ip` | `character varying(64)` | YES | - |
| `created_at` | `timestamp without time zone` | NO | CURRENT_TIMESTAMP |

约束：

- `sys_security_event_created_at_not_null` (NOT NULL) `NOT NULL created_at`
- `sys_security_event_event_type_not_null` (NOT NULL) `NOT NULL event_type`
- `sys_security_event_id_not_null` (NOT NULL) `NOT NULL id`
- `sys_security_event_pkey` (PRIMARY KEY) `PRIMARY KEY (id)`

索引：

- `idx_sys_security_event_created_at`：`CREATE INDEX idx_sys_security_event_created_at ON public.sys_security_event USING btree (created_at)`
- `idx_sys_security_event_type`：`CREATE INDEX idx_sys_security_event_type ON public.sys_security_event USING btree (event_type)`
- `idx_sys_security_event_type_id_desc`：`CREATE INDEX idx_sys_security_event_type_id_desc ON public.sys_security_event USING btree (event_type, id DESC)`
- `sys_security_event_pkey`：`CREATE UNIQUE INDEX sys_security_event_pkey ON public.sys_security_event USING btree (id)`

#### `sys_tenant`

| 列 | 类型 | 可空 | 默认值 |
| --- | --- | --- | --- |
| `id` | `bigint` | NO | nextval('sys_tenant_id_seq'::regclass) |
| `tenant_code` | `character varying(64)` | NO | - |
| `tenant_name` | `character varying(128)` | NO | - |
| `is_default` | `smallint` | NO | 0 |
| `created_at` | `timestamp without time zone` | NO | CURRENT_TIMESTAMP |
| `updated_at` | `timestamp without time zone` | NO | CURRENT_TIMESTAMP |

约束：

- `sys_tenant_created_at_not_null` (NOT NULL) `NOT NULL created_at`
- `sys_tenant_id_not_null` (NOT NULL) `NOT NULL id`
- `sys_tenant_is_default_not_null` (NOT NULL) `NOT NULL is_default`
- `sys_tenant_tenant_code_not_null` (NOT NULL) `NOT NULL tenant_code`
- `sys_tenant_tenant_name_not_null` (NOT NULL) `NOT NULL tenant_name`
- `sys_tenant_updated_at_not_null` (NOT NULL) `NOT NULL updated_at`
- `sys_tenant_pkey` (PRIMARY KEY) `PRIMARY KEY (id)`

索引：

- `sys_tenant_pkey`：`CREATE UNIQUE INDEX sys_tenant_pkey ON public.sys_tenant USING btree (id)`
- `uk_sys_tenant_code`：`CREATE UNIQUE INDEX uk_sys_tenant_code ON public.sys_tenant USING btree (tenant_code)`

#### `sys_token_blacklist`

| 列 | 类型 | 可空 | 默认值 |
| --- | --- | --- | --- |
| `id` | `bigint` | NO | nextval('sys_token_blacklist_id_seq'::regclass) |
| `jti` | `character varying(128)` | NO | - |
| `user_id` | `bigint` | NO | - |
| `expire_at` | `timestamp without time zone` | NO | - |
| `created_at` | `timestamp without time zone` | NO | CURRENT_TIMESTAMP |

约束：

- `sys_token_blacklist_created_at_not_null` (NOT NULL) `NOT NULL created_at`
- `sys_token_blacklist_expire_at_not_null` (NOT NULL) `NOT NULL expire_at`
- `sys_token_blacklist_id_not_null` (NOT NULL) `NOT NULL id`
- `sys_token_blacklist_jti_not_null` (NOT NULL) `NOT NULL jti`
- `sys_token_blacklist_user_id_not_null` (NOT NULL) `NOT NULL user_id`
- `sys_token_blacklist_pkey` (PRIMARY KEY) `PRIMARY KEY (id)`

索引：

- `idx_sys_token_blacklist_expire_at`：`CREATE INDEX idx_sys_token_blacklist_expire_at ON public.sys_token_blacklist USING btree (expire_at)`
- `sys_token_blacklist_pkey`：`CREATE UNIQUE INDEX sys_token_blacklist_pkey ON public.sys_token_blacklist USING btree (id)`
- `uk_sys_token_blacklist_jti`：`CREATE UNIQUE INDEX uk_sys_token_blacklist_jti ON public.sys_token_blacklist USING btree (jti)`

#### `sys_user`

| 列 | 类型 | 可空 | 默认值 |
| --- | --- | --- | --- |
| `id` | `bigint` | NO | nextval('sys_user_id_seq'::regclass) |
| `username` | `character varying(64)` | NO | - |
| `nickname` | `character varying(128)` | YES | - |
| `display_name` | `character varying(128)` | YES | - |
| `email` | `character varying(128)` | YES | - |
| `mobile` | `character varying(32)` | YES | - |
| `avatar_url` | `character varying(512)` | YES | - |
| `status` | `smallint` | NO | 1 |
| `group_id` | `bigint` | YES | - |
| `tenant_id` | `bigint` | YES | - |
| `register_source` | `character varying(32)` | YES | - |
| `password_version` | `integer` | NO | 1 |
| `failed_login_attempts` | `integer` | NO | 0 |
| `last_login_at` | `timestamp without time zone` | YES | - |
| `locked_until` | `timestamp without time zone` | YES | - |
| `deleted` | `smallint` | NO | 0 |
| `created_at` | `timestamp without time zone` | NO | CURRENT_TIMESTAMP |
| `updated_at` | `timestamp without time zone` | NO | CURRENT_TIMESTAMP |

约束：

- `sys_user_created_at_not_null` (NOT NULL) `NOT NULL created_at`
- `sys_user_deleted_not_null` (NOT NULL) `NOT NULL deleted`
- `sys_user_failed_login_attempts_not_null` (NOT NULL) `NOT NULL failed_login_attempts`
- `sys_user_id_not_null` (NOT NULL) `NOT NULL id`
- `sys_user_password_version_not_null` (NOT NULL) `NOT NULL password_version`
- `sys_user_status_not_null` (NOT NULL) `NOT NULL status`
- `sys_user_updated_at_not_null` (NOT NULL) `NOT NULL updated_at`
- `sys_user_username_not_null` (NOT NULL) `NOT NULL username`
- `sys_user_pkey` (PRIMARY KEY) `PRIMARY KEY (id)`

索引：

- `sys_user_pkey`：`CREATE UNIQUE INDEX sys_user_pkey ON public.sys_user USING btree (id)`
- `uk_sys_user_email`：`CREATE UNIQUE INDEX uk_sys_user_email ON public.sys_user USING btree (email)`
- `uk_sys_user_mobile`：`CREATE UNIQUE INDEX uk_sys_user_mobile ON public.sys_user USING btree (mobile)`
- `uk_sys_user_username`：`CREATE UNIQUE INDEX uk_sys_user_username ON public.sys_user USING btree (username)`

#### `sys_user_role`

| 列 | 类型 | 可空 | 默认值 |
| --- | --- | --- | --- |
| `id` | `bigint` | NO | nextval('sys_user_role_id_seq'::regclass) |
| `user_id` | `bigint` | NO | - |
| `role_id` | `bigint` | NO | - |
| `created_at` | `timestamp without time zone` | NO | CURRENT_TIMESTAMP |

约束：

- `fk_sys_user_role_role_id` (FOREIGN KEY) `FOREIGN KEY (role_id) REFERENCES sys_role(id)`
- `fk_sys_user_role_user_id` (FOREIGN KEY) `FOREIGN KEY (user_id) REFERENCES sys_user(id)`
- `sys_user_role_created_at_not_null` (NOT NULL) `NOT NULL created_at`
- `sys_user_role_id_not_null` (NOT NULL) `NOT NULL id`
- `sys_user_role_role_id_not_null` (NOT NULL) `NOT NULL role_id`
- `sys_user_role_user_id_not_null` (NOT NULL) `NOT NULL user_id`
- `sys_user_role_pkey` (PRIMARY KEY) `PRIMARY KEY (id)`

索引：

- `sys_user_role_pkey`：`CREATE UNIQUE INDEX sys_user_role_pkey ON public.sys_user_role USING btree (id)`
- `uk_sys_user_role`：`CREATE UNIQUE INDEX uk_sys_user_role ON public.sys_user_role USING btree (user_id, role_id)`

#### `sys_user_session`

| 列 | 类型 | 可空 | 默认值 |
| --- | --- | --- | --- |
| `id` | `bigint` | NO | nextval('sys_user_session_id_seq'::regclass) |
| `session_id` | `character varying(64)` | NO | - |
| `user_id` | `bigint` | NO | - |
| `username` | `character varying(64)` | NO | - |
| `tenant_id` | `bigint` | YES | - |
| `client_id` | `character varying(128)` | YES | - |
| `device_id` | `character varying(128)` | YES | - |
| `device_type` | `character varying(32)` | YES | - |
| `device_name` | `character varying(128)` | YES | - |
| `user_agent` | `character varying(512)` | YES | - |
| `ip` | `character varying(64)` | YES | - |
| `status` | `character varying(32)` | NO | 'ACTIVE'::character varying |
| `last_seen_at` | `timestamp without time zone` | YES | - |
| `access_expire_at` | `timestamp without time zone` | YES | - |
| `refresh_expire_at` | `timestamp without time zone` | YES | - |
| `revoked_at` | `timestamp without time zone` | YES | - |
| `revoke_reason` | `character varying(64)` | YES | - |
| `created_at` | `timestamp without time zone` | NO | CURRENT_TIMESTAMP |
| `updated_at` | `timestamp without time zone` | NO | CURRENT_TIMESTAMP |

约束：

- `fk_sys_user_session_user_id` (FOREIGN KEY) `FOREIGN KEY (user_id) REFERENCES sys_user(id)`
- `sys_user_session_created_at_not_null` (NOT NULL) `NOT NULL created_at`
- `sys_user_session_id_not_null` (NOT NULL) `NOT NULL id`
- `sys_user_session_session_id_not_null` (NOT NULL) `NOT NULL session_id`
- `sys_user_session_status_not_null` (NOT NULL) `NOT NULL status`
- `sys_user_session_updated_at_not_null` (NOT NULL) `NOT NULL updated_at`
- `sys_user_session_user_id_not_null` (NOT NULL) `NOT NULL user_id`
- `sys_user_session_username_not_null` (NOT NULL) `NOT NULL username`
- `sys_user_session_pkey` (PRIMARY KEY) `PRIMARY KEY (id)`
- `uk_sys_user_session_sid` (UNIQUE) `UNIQUE (session_id)`

索引：

- `idx_sys_user_session_refresh_expire_at`：`CREATE INDEX idx_sys_user_session_refresh_expire_at ON public.sys_user_session USING btree (refresh_expire_at)`
- `idx_sys_user_session_updated_at`：`CREATE INDEX idx_sys_user_session_updated_at ON public.sys_user_session USING btree (updated_at DESC)`
- `idx_sys_user_session_user_status`：`CREATE INDEX idx_sys_user_session_user_status ON public.sys_user_session USING btree (user_id, status)`
- `sys_user_session_pkey`：`CREATE UNIQUE INDEX sys_user_session_pkey ON public.sys_user_session USING btree (id)`
- `uk_sys_user_session_sid`：`CREATE UNIQUE INDEX uk_sys_user_session_sid ON public.sys_user_session USING btree (session_id)`

#### `sys_user_session_policy`

| 列 | 类型 | 可空 | 默认值 |
| --- | --- | --- | --- |
| `id` | `bigint` | NO | nextval('sys_user_session_policy_id_seq'::regclass) |
| `user_id` | `bigint` | NO | - |
| `policy_code` | `character varying(32)` | NO | - |
| `created_at` | `timestamp without time zone` | NO | CURRENT_TIMESTAMP |
| `updated_at` | `timestamp without time zone` | NO | CURRENT_TIMESTAMP |

约束：

- `fk_sys_user_session_policy_user_id` (FOREIGN KEY) `FOREIGN KEY (user_id) REFERENCES sys_user(id)`
- `sys_user_session_policy_created_at_not_null` (NOT NULL) `NOT NULL created_at`
- `sys_user_session_policy_id_not_null` (NOT NULL) `NOT NULL id`
- `sys_user_session_policy_policy_code_not_null` (NOT NULL) `NOT NULL policy_code`
- `sys_user_session_policy_updated_at_not_null` (NOT NULL) `NOT NULL updated_at`
- `sys_user_session_policy_user_id_not_null` (NOT NULL) `NOT NULL user_id`
- `sys_user_session_policy_pkey` (PRIMARY KEY) `PRIMARY KEY (id)`
- `uk_sys_user_session_policy_user` (UNIQUE) `UNIQUE (user_id)`

索引：

- `sys_user_session_policy_pkey`：`CREATE UNIQUE INDEX sys_user_session_policy_pkey ON public.sys_user_session_policy USING btree (id)`
- `uk_sys_user_session_policy_user`：`CREATE UNIQUE INDEX uk_sys_user_session_policy_user ON public.sys_user_session_policy USING btree (user_id)`

### 4.4 通知与投递

#### `psy_notification`

| 列 | 类型 | 可空 | 默认值 |
| --- | --- | --- | --- |
| `id` | `bigint` | NO | nextval('psy_notification_id_seq'::regclass) |
| `notification_type` | `character varying(32)` | NO | - |
| `title` | `character varying(255)` | NO | - |
| `content` | `text` | NO | - |
| `biz_type` | `character varying(32)` | YES | - |
| `biz_id` | `bigint` | YES | - |
| `created_at` | `timestamp without time zone` | NO | CURRENT_TIMESTAMP |
| `target_path` | `character varying(512)` | YES | - |
| `target_type` | `character varying(64)` | YES | - |
| `target_id` | `bigint` | YES | - |
| `deep_link` | `character varying(512)` | YES | - |
| `payload_json` | `text` | YES | - |

约束：

- `psy_notification_content_not_null` (NOT NULL) `NOT NULL content`
- `psy_notification_created_at_not_null` (NOT NULL) `NOT NULL created_at`
- `psy_notification_id_not_null` (NOT NULL) `NOT NULL id`
- `psy_notification_notification_type_not_null` (NOT NULL) `NOT NULL notification_type`
- `psy_notification_title_not_null` (NOT NULL) `NOT NULL title`
- `psy_notification_pkey` (PRIMARY KEY) `PRIMARY KEY (id)`

索引：

- `idx_psy_notification_biz`：`CREATE INDEX idx_psy_notification_biz ON public.psy_notification USING btree (biz_type, biz_id)`
- `idx_psy_notification_type`：`CREATE INDEX idx_psy_notification_type ON public.psy_notification USING btree (notification_type)`
- `psy_notification_pkey`：`CREATE UNIQUE INDEX psy_notification_pkey ON public.psy_notification USING btree (id)`

#### `psy_notification_delivery`

| 列 | 类型 | 可空 | 默认值 |
| --- | --- | --- | --- |
| `id` | `bigint` | NO | nextval('psy_notification_delivery_id_seq'::regclass) |
| `notification_id` | `bigint` | NO | - |
| `receiver_user_id` | `bigint` | NO | - |
| `read_flag` | `boolean` | NO | false |
| `read_time` | `timestamp without time zone` | YES | - |
| `delivery_channel` | `character varying(32)` | NO | 'IN_APP'::character varying |
| `created_at` | `timestamp without time zone` | NO | CURRENT_TIMESTAMP |
| `device_id` | `bigint` | YES | - |
| `push_token_snapshot` | `character varying(512)` | YES | - |
| `delivery_status` | `character varying(32)` | NO | 'PENDING'::character varying |
| `provider_name` | `character varying(64)` | YES | - |
| `provider_message_id` | `character varying(255)` | YES | - |
| `delivered_time` | `timestamp without time zone` | YES | - |
| `clicked_time` | `timestamp without time zone` | YES | - |
| `error_message` | `text` | YES | - |
| `callback_payload_json` | `text` | YES | - |
| `updated_at` | `timestamp without time zone` | NO | CURRENT_TIMESTAMP |
| `retry_count` | `integer` | NO | 0 |
| `next_retry_at` | `timestamp without time zone` | YES | - |
| `processing_started_at` | `timestamp without time zone` | YES | - |
| `dead_letter_at` | `timestamp without time zone` | YES | - |
| `tenant_id` | `bigint` | NO | - |
| `processing_token` | `character varying(64)` | YES | - |

约束：

- `ck_psy_notification_delivery_processing_lease` (CHECK) `CHECK (((((delivery_channel)::text = 'PUSH'::text) AND ((delivery_status)::text = 'PROCESSING'::text) AND (processing_started_at IS NOT NULL) AND (processing_token IS NOT NULL)) OR ((NOT (((delivery_channel)::text = 'PUSH'::text) AND ((delivery_status)::text = 'PROCESSING'::text))) AND (processing_token IS NULL))))`
- `ck_psy_notification_delivery_retry_count` (CHECK) `CHECK ((retry_count >= 0)) NOT VALID`
- `ck_psy_notification_delivery_status` (CHECK) `CHECK (((delivery_status)::text = ANY ((ARRAY['PENDING'::character varying, 'PROCESSING'::character varying, 'SENT'::character varying, 'DELIVERED'::character varying, 'FAILED'::character varying, 'CLICKED'::character varying, 'DEAD_LETTER'::character varying])::text[]))) NOT VALID`
- `ck_psy_notification_delivery_tenant_required` (CHECK) `CHECK ((tenant_id IS NOT NULL))`
- `fk_psy_notification_delivery_tenant` (FOREIGN KEY) `FOREIGN KEY (tenant_id) REFERENCES sys_tenant(id)`
- `psy_notification_delivery_notification_id_fkey` (FOREIGN KEY) `FOREIGN KEY (notification_id) REFERENCES psy_notification(id)`
- `psy_notification_delivery_receiver_user_id_fkey` (FOREIGN KEY) `FOREIGN KEY (receiver_user_id) REFERENCES sys_user(id)`
- `psy_notification_delivery_created_at_not_null` (NOT NULL) `NOT NULL created_at`
- `psy_notification_delivery_delivery_channel_not_null` (NOT NULL) `NOT NULL delivery_channel`
- `psy_notification_delivery_delivery_status_not_null` (NOT NULL) `NOT NULL delivery_status`
- `psy_notification_delivery_id_not_null` (NOT NULL) `NOT NULL id`
- `psy_notification_delivery_notification_id_not_null` (NOT NULL) `NOT NULL notification_id`
- `psy_notification_delivery_read_flag_not_null` (NOT NULL) `NOT NULL read_flag`
- `psy_notification_delivery_receiver_user_id_not_null` (NOT NULL) `NOT NULL receiver_user_id`
- `psy_notification_delivery_retry_count_not_null` (NOT NULL) `NOT NULL retry_count`
- `psy_notification_delivery_tenant_id_not_null` (NOT NULL) `NOT NULL tenant_id`
- `psy_notification_delivery_updated_at_not_null` (NOT NULL) `NOT NULL updated_at`
- `psy_notification_delivery_pkey` (PRIMARY KEY) `PRIMARY KEY (id)`

索引：

- `idx_psy_notification_delivery_dead_letter`：`CREATE INDEX idx_psy_notification_delivery_dead_letter ON public.psy_notification_delivery USING btree (dead_letter_at DESC, id DESC) WHERE ((delivery_status)::text = 'DEAD_LETTER'::text)`
- `idx_psy_notification_delivery_pending_retry`：`CREATE INDEX idx_psy_notification_delivery_pending_retry ON public.psy_notification_delivery USING btree (next_retry_at, created_at, id) WHERE (((delivery_channel)::text = 'PUSH'::text) AND ((delivery_status)::text = 'PENDING'::text))`
- `idx_psy_notification_delivery_processing_started`：`CREATE INDEX idx_psy_notification_delivery_processing_started ON public.psy_notification_delivery USING btree (processing_started_at, id) WHERE (((delivery_channel)::text = 'PUSH'::text) AND ((delivery_status)::text = 'PROCESSING'::text))`
- `idx_psy_notification_delivery_provider_message`：`CREATE INDEX idx_psy_notification_delivery_provider_message ON public.psy_notification_delivery USING btree (provider_name, provider_message_id)`
- `idx_psy_notification_delivery_read_flag`：`CREATE INDEX idx_psy_notification_delivery_read_flag ON public.psy_notification_delivery USING btree (receiver_user_id, read_flag)`
- `idx_psy_notification_delivery_receiver`：`CREATE INDEX idx_psy_notification_delivery_receiver ON public.psy_notification_delivery USING btree (receiver_user_id)`
- `idx_psy_notification_delivery_status`：`CREATE INDEX idx_psy_notification_delivery_status ON public.psy_notification_delivery USING btree (delivery_channel, delivery_status)`
- `idx_psy_notification_delivery_tenant`：`CREATE INDEX idx_psy_notification_delivery_tenant ON public.psy_notification_delivery USING btree (tenant_id, receiver_user_id, id)`
- `psy_notification_delivery_pkey`：`CREATE UNIQUE INDEX psy_notification_delivery_pkey ON public.psy_notification_delivery USING btree (id)`

#### `psy_notification_policy`

| 列 | 类型 | 可空 | 默认值 |
| --- | --- | --- | --- |
| `id` | `bigint` | NO | nextval('psy_notification_policy_id_seq'::regclass) |
| `notification_type` | `character varying(64)` | NO | - |
| `in_app_enabled` | `boolean` | NO | true |
| `push_enabled` | `boolean` | NO | true |
| `cooldown_minutes` | `integer` | NO | 0 |
| `created_at` | `timestamp without time zone` | NO | CURRENT_TIMESTAMP |
| `updated_at` | `timestamp without time zone` | NO | CURRENT_TIMESTAMP |

约束：

- `ck_psy_notification_policy_cooldown` (CHECK) `CHECK ((cooldown_minutes >= 0)) NOT VALID`
- `psy_notification_policy_cooldown_minutes_not_null` (NOT NULL) `NOT NULL cooldown_minutes`
- `psy_notification_policy_created_at_not_null` (NOT NULL) `NOT NULL created_at`
- `psy_notification_policy_id_not_null` (NOT NULL) `NOT NULL id`
- `psy_notification_policy_in_app_enabled_not_null` (NOT NULL) `NOT NULL in_app_enabled`
- `psy_notification_policy_notification_type_not_null` (NOT NULL) `NOT NULL notification_type`
- `psy_notification_policy_push_enabled_not_null` (NOT NULL) `NOT NULL push_enabled`
- `psy_notification_policy_updated_at_not_null` (NOT NULL) `NOT NULL updated_at`
- `psy_notification_policy_pkey` (PRIMARY KEY) `PRIMARY KEY (id)`

索引：

- `psy_notification_policy_pkey`：`CREATE UNIQUE INDEX psy_notification_policy_pkey ON public.psy_notification_policy USING btree (id)`
- `uk_psy_notification_policy_type`：`CREATE UNIQUE INDEX uk_psy_notification_policy_type ON public.psy_notification_policy USING btree (notification_type)`

### 4.5 量表与发布治理

#### `psy_scale`

| 列 | 类型 | 可空 | 默认值 |
| --- | --- | --- | --- |
| `id` | `bigint` | NO | nextval('psy_scale_id_seq'::regclass) |
| `scale_code` | `character varying(64)` | NO | - |
| `scale_name` | `character varying(255)` | NO | - |
| `description` | `text` | YES | - |
| `applicable_target` | `character varying(128)` | YES | - |
| `version_no` | `character varying(32)` | YES | - |
| `version_group_id` | `bigint` | YES | - |
| `current_version_flag` | `boolean` | NO | true |
| `status` | `character varying(32)` | NO | 'DRAFT'::character varying |
| `score_method` | `character varying(32)` | NO | 'SIMPLE_SUM'::character varying |
| `score_coefficient` | `numeric(6,4)` | NO | 1.0 |
| `anonymous_supported` | `boolean` | NO | false |
| `report_template` | `text` | YES | - |
| `created_by` | `bigint` | YES | - |
| `created_at` | `timestamp without time zone` | NO | CURRENT_TIMESTAMP |
| `updated_by` | `bigint` | YES | - |
| `updated_at` | `timestamp without time zone` | NO | CURRENT_TIMESTAMP |
| `norm_strategy` | `character varying(32)` | NO | 'RAW_SCORE'::character varying |
| `norm_default_group` | `character varying(64)` | YES | - |
| `high_risk_warning_enabled` | `boolean` | NO | false |
| `tenant_id` | `bigint` | NO | - |
| `published_content_hash` | `character varying(64)` | YES | - |
| `published_at` | `timestamp without time zone` | YES | - |
| `skip_rules_json` | `jsonb` | YES | - |

约束：

- `ck_psy_scale_score_method` (CHECK) `CHECK (((score_method)::text = ANY ((ARRAY['SIMPLE_SUM'::character varying, 'REVERSE_SUM'::character varying, 'WEIGHTED_SUM'::character varying, 'AVERAGE'::character varying, 'WEIGHTED_AVERAGE'::character varying])::text[]))) NOT VALID`
- `ck_psy_scale_status` (CHECK) `CHECK (((status)::text = ANY ((ARRAY['DRAFT'::character varying, 'PUBLISHED'::character varying, 'ARCHIVED'::character varying])::text[]))) NOT VALID`
- `ck_psy_scale_tenant_required` (CHECK) `CHECK ((tenant_id IS NOT NULL))`
- `fk_psy_scale_tenant` (FOREIGN KEY) `FOREIGN KEY (tenant_id) REFERENCES sys_tenant(id)`
- `psy_scale_anonymous_supported_not_null` (NOT NULL) `NOT NULL anonymous_supported`
- `psy_scale_created_at_not_null` (NOT NULL) `NOT NULL created_at`
- `psy_scale_current_version_flag_not_null` (NOT NULL) `NOT NULL current_version_flag`
- `psy_scale_high_risk_warning_enabled_not_null` (NOT NULL) `NOT NULL high_risk_warning_enabled`
- `psy_scale_id_not_null` (NOT NULL) `NOT NULL id`
- `psy_scale_norm_strategy_not_null` (NOT NULL) `NOT NULL norm_strategy`
- `psy_scale_scale_code_not_null` (NOT NULL) `NOT NULL scale_code`
- `psy_scale_scale_name_not_null` (NOT NULL) `NOT NULL scale_name`
- `psy_scale_score_coefficient_not_null` (NOT NULL) `NOT NULL score_coefficient`
- `psy_scale_score_method_not_null` (NOT NULL) `NOT NULL score_method`
- `psy_scale_status_not_null` (NOT NULL) `NOT NULL status`
- `psy_scale_tenant_id_not_null` (NOT NULL) `NOT NULL tenant_id`
- `psy_scale_updated_at_not_null` (NOT NULL) `NOT NULL updated_at`
- `psy_scale_pkey` (PRIMARY KEY) `PRIMARY KEY (id)`

索引：

- `idx_psy_scale_published_content_hash`：`CREATE INDEX idx_psy_scale_published_content_hash ON public.psy_scale USING btree (published_content_hash) WHERE (published_content_hash IS NOT NULL)`
- `idx_psy_scale_status`：`CREATE INDEX idx_psy_scale_status ON public.psy_scale USING btree (status)`
- `idx_psy_scale_tenant`：`CREATE INDEX idx_psy_scale_tenant ON public.psy_scale USING btree (tenant_id, id)`
- `idx_psy_scale_version_group`：`CREATE INDEX idx_psy_scale_version_group ON public.psy_scale USING btree (version_group_id)`
- `psy_scale_pkey`：`CREATE UNIQUE INDEX psy_scale_pkey ON public.psy_scale USING btree (id)`
- `uk_psy_scale_global_code_version`：`CREATE UNIQUE INDEX uk_psy_scale_global_code_version ON public.psy_scale USING btree (scale_code, version_no) WHERE (tenant_id IS NULL)`
- `uk_psy_scale_tenant_code_version`：`CREATE UNIQUE INDEX uk_psy_scale_tenant_code_version ON public.psy_scale USING btree (tenant_id, scale_code, version_no) WHERE (tenant_id IS NOT NULL)`
- `uk_psy_scale_version_group_current`：`CREATE UNIQUE INDEX uk_psy_scale_version_group_current ON public.psy_scale USING btree (COALESCE(version_group_id, id)) WHERE ((current_version_flag = true) AND ((status)::text = 'PUBLISHED'::text))`

#### `psy_scale_algorithm_binding`

| 列 | 类型 | 可空 | 默认值 |
| --- | --- | --- | --- |
| `id` | `bigint` | NO | nextval('psy_scale_algorithm_binding_id_seq'::regclass) |
| `scale_id` | `bigint` | NO | - |
| `algorithm_code` | `character varying(64)` | NO | - |
| `algorithm_version` | `character varying(32)` | NO | - |
| `implementation_type` | `character varying(32)` | NO | - |
| `input_schema_json` | `jsonb` | NO | '{}'::jsonb |
| `output_schema_json` | `jsonb` | NO | '{}'::jsonb |
| `implementation_checksum` | `character varying(64)` | YES | - |
| `review_status` | `character varying(32)` | NO | 'DRAFT'::character varying |
| `created_at` | `timestamp without time zone` | NO | CURRENT_TIMESTAMP |
| `updated_at` | `timestamp without time zone` | NO | CURRENT_TIMESTAMP |

约束：

- `ck_psy_scale_algorithm_implementation` (CHECK) `CHECK (((implementation_type)::text = ANY ((ARRAY['BUILTIN'::character varying, 'RESTRICTED_EXTENSION'::character varying])::text[]))) NOT VALID`
- `ck_psy_scale_algorithm_review` (CHECK) `CHECK (((review_status)::text = ANY ((ARRAY['DRAFT'::character varying, 'PENDING_REVIEW'::character varying, 'APPROVED'::character varying, 'REJECTED'::character varying])::text[]))) NOT VALID`
- `psy_scale_algorithm_binding_scale_id_fkey` (FOREIGN KEY) `FOREIGN KEY (scale_id) REFERENCES psy_scale(id) ON DELETE CASCADE`
- `psy_scale_algorithm_binding_algorithm_code_not_null` (NOT NULL) `NOT NULL algorithm_code`
- `psy_scale_algorithm_binding_algorithm_version_not_null` (NOT NULL) `NOT NULL algorithm_version`
- `psy_scale_algorithm_binding_created_at_not_null` (NOT NULL) `NOT NULL created_at`
- `psy_scale_algorithm_binding_id_not_null` (NOT NULL) `NOT NULL id`
- `psy_scale_algorithm_binding_implementation_type_not_null` (NOT NULL) `NOT NULL implementation_type`
- `psy_scale_algorithm_binding_input_schema_json_not_null` (NOT NULL) `NOT NULL input_schema_json`
- `psy_scale_algorithm_binding_output_schema_json_not_null` (NOT NULL) `NOT NULL output_schema_json`
- `psy_scale_algorithm_binding_review_status_not_null` (NOT NULL) `NOT NULL review_status`
- `psy_scale_algorithm_binding_scale_id_not_null` (NOT NULL) `NOT NULL scale_id`
- `psy_scale_algorithm_binding_updated_at_not_null` (NOT NULL) `NOT NULL updated_at`
- `psy_scale_algorithm_binding_pkey` (PRIMARY KEY) `PRIMARY KEY (id)`
- `psy_scale_algorithm_binding_scale_id_key` (UNIQUE) `UNIQUE (scale_id)`

索引：

- `psy_scale_algorithm_binding_pkey`：`CREATE UNIQUE INDEX psy_scale_algorithm_binding_pkey ON public.psy_scale_algorithm_binding USING btree (id)`
- `psy_scale_algorithm_binding_scale_id_key`：`CREATE UNIQUE INDEX psy_scale_algorithm_binding_scale_id_key ON public.psy_scale_algorithm_binding USING btree (scale_id)`

#### `psy_scale_dimension`

| 列 | 类型 | 可空 | 默认值 |
| --- | --- | --- | --- |
| `id` | `bigint` | NO | nextval('psy_scale_dimension_id_seq'::regclass) |
| `scale_id` | `bigint` | NO | - |
| `dimension_code` | `character varying(64)` | NO | - |
| `dimension_name` | `character varying(255)` | NO | - |
| `description` | `text` | YES | - |
| `sort_no` | `integer` | NO | 0 |
| `created_at` | `timestamp without time zone` | NO | CURRENT_TIMESTAMP |
| `updated_at` | `timestamp without time zone` | NO | CURRENT_TIMESTAMP |

约束：

- `psy_scale_dimension_scale_id_fkey` (FOREIGN KEY) `FOREIGN KEY (scale_id) REFERENCES psy_scale(id)`
- `psy_scale_dimension_created_at_not_null` (NOT NULL) `NOT NULL created_at`
- `psy_scale_dimension_dimension_code_not_null` (NOT NULL) `NOT NULL dimension_code`
- `psy_scale_dimension_dimension_name_not_null` (NOT NULL) `NOT NULL dimension_name`
- `psy_scale_dimension_id_not_null` (NOT NULL) `NOT NULL id`
- `psy_scale_dimension_scale_id_not_null` (NOT NULL) `NOT NULL scale_id`
- `psy_scale_dimension_sort_no_not_null` (NOT NULL) `NOT NULL sort_no`
- `psy_scale_dimension_updated_at_not_null` (NOT NULL) `NOT NULL updated_at`
- `psy_scale_dimension_pkey` (PRIMARY KEY) `PRIMARY KEY (id)`

索引：

- `idx_psy_scale_dimension_scale_id`：`CREATE INDEX idx_psy_scale_dimension_scale_id ON public.psy_scale_dimension USING btree (scale_id)`
- `psy_scale_dimension_pkey`：`CREATE UNIQUE INDEX psy_scale_dimension_pkey ON public.psy_scale_dimension USING btree (id)`
- `uk_psy_scale_dimension_code`：`CREATE UNIQUE INDEX uk_psy_scale_dimension_code ON public.psy_scale_dimension USING btree (scale_id, dimension_code)`

#### `psy_scale_dimension_translation`

| 列 | 类型 | 可空 | 默认值 |
| --- | --- | --- | --- |
| `id` | `bigint` | NO | nextval('psy_scale_dimension_translation_id_seq'::regclass) |
| `dimension_id` | `bigint` | NO | - |
| `locale_code` | `character varying(16)` | NO | - |
| `dimension_name` | `character varying(255)` | NO | - |
| `description` | `text` | YES | - |
| `review_status` | `character varying(32)` | NO | 'DRAFT'::character varying |
| `created_at` | `timestamp without time zone` | NO | CURRENT_TIMESTAMP |
| `updated_at` | `timestamp without time zone` | NO | CURRENT_TIMESTAMP |

约束：

- `ck_psy_scale_dimension_translation_locale` (CHECK) `CHECK (((locale_code)::text = ANY ((ARRAY['zh-CN'::character varying, 'ja-JP'::character varying, 'en'::character varying])::text[]))) NOT VALID`
- `ck_psy_scale_dimension_translation_review` (CHECK) `CHECK (((review_status)::text = ANY ((ARRAY['DRAFT'::character varying, 'PENDING_REVIEW'::character varying, 'APPROVED'::character varying, 'REJECTED'::character varying])::text[]))) NOT VALID`
- `psy_scale_dimension_translation_dimension_id_fkey` (FOREIGN KEY) `FOREIGN KEY (dimension_id) REFERENCES psy_scale_dimension(id) ON DELETE CASCADE`
- `psy_scale_dimension_translation_created_at_not_null` (NOT NULL) `NOT NULL created_at`
- `psy_scale_dimension_translation_dimension_id_not_null` (NOT NULL) `NOT NULL dimension_id`
- `psy_scale_dimension_translation_dimension_name_not_null` (NOT NULL) `NOT NULL dimension_name`
- `psy_scale_dimension_translation_id_not_null` (NOT NULL) `NOT NULL id`
- `psy_scale_dimension_translation_locale_code_not_null` (NOT NULL) `NOT NULL locale_code`
- `psy_scale_dimension_translation_review_status_not_null` (NOT NULL) `NOT NULL review_status`
- `psy_scale_dimension_translation_updated_at_not_null` (NOT NULL) `NOT NULL updated_at`
- `psy_scale_dimension_translation_pkey` (PRIMARY KEY) `PRIMARY KEY (id)`
- `psy_scale_dimension_translation_dimension_id_locale_code_key` (UNIQUE) `UNIQUE (dimension_id, locale_code)`

索引：

- `idx_psy_scale_dimension_translation_locale`：`CREATE INDEX idx_psy_scale_dimension_translation_locale ON public.psy_scale_dimension_translation USING btree (locale_code, review_status)`
- `psy_scale_dimension_translation_dimension_id_locale_code_key`：`CREATE UNIQUE INDEX psy_scale_dimension_translation_dimension_id_locale_code_key ON public.psy_scale_dimension_translation USING btree (dimension_id, locale_code)`
- `psy_scale_dimension_translation_pkey`：`CREATE UNIQUE INDEX psy_scale_dimension_translation_pkey ON public.psy_scale_dimension_translation USING btree (id)`

#### `psy_scale_golden_case`

| 列 | 类型 | 可空 | 默认值 |
| --- | --- | --- | --- |
| `id` | `bigint` | NO | nextval('psy_scale_golden_case_id_seq'::regclass) |
| `tenant_id` | `bigint` | NO | - |
| `scale_id` | `bigint` | NO | - |
| `case_code` | `character varying(64)` | NO | - |
| `revision_no` | `integer` | NO | - |
| `case_type` | `character varying(32)` | NO | - |
| `source_reference` | `text` | NO | - |
| `scale_content_hash` | `character varying(64)` | NO | - |
| `case_content_hash` | `character varying(64)` | NO | - |
| `input_json` | `jsonb` | NO | - |
| `expected_json` | `jsonb` | NO | - |
| `created_by` | `bigint` | NO | - |
| `created_at` | `timestamp without time zone` | NO | CURRENT_TIMESTAMP |
| `approved_by` | `bigint` | YES | - |
| `approved_at` | `timestamp without time zone` | YES | - |

约束：

- `ck_psy_golden_case_tenant_required` (CHECK) `CHECK ((tenant_id IS NOT NULL))`
- `ck_psy_scale_golden_case_approval` (CHECK) `CHECK ((((approved_by IS NULL) AND (approved_at IS NULL)) OR ((approved_by IS NOT NULL) AND (approved_at IS NOT NULL)))) NOT VALID`
- `ck_psy_scale_golden_case_hashes` (CHECK) `CHECK ((((scale_content_hash)::text ~ '^[0-9a-f]{64}$'::text) AND ((case_content_hash)::text ~ '^[0-9a-f]{64}$'::text))) NOT VALID`
- `ck_psy_scale_golden_case_json` (CHECK) `CHECK (((jsonb_typeof(input_json) = 'object'::text) AND (jsonb_typeof(expected_json) = 'object'::text))) NOT VALID`
- `ck_psy_scale_golden_case_revision` (CHECK) `CHECK ((revision_no > 0)) NOT VALID`
- `ck_psy_scale_golden_case_type` (CHECK) `CHECK (((case_type)::text = ANY ((ARRAY['NORMAL'::character varying, 'BOUNDARY'::character varying, 'REVERSE'::character varying, 'MISSING'::character varying, 'INVALID'::character varying, 'HIGH_RISK'::character varying])::text[]))) NOT VALID`
- `psy_scale_golden_case_approved_by_fkey` (FOREIGN KEY) `FOREIGN KEY (approved_by) REFERENCES sys_user(id)`
- `psy_scale_golden_case_created_by_fkey` (FOREIGN KEY) `FOREIGN KEY (created_by) REFERENCES sys_user(id)`
- `psy_scale_golden_case_scale_id_fkey` (FOREIGN KEY) `FOREIGN KEY (scale_id) REFERENCES psy_scale(id) ON DELETE CASCADE`
- `psy_scale_golden_case_tenant_id_fkey` (FOREIGN KEY) `FOREIGN KEY (tenant_id) REFERENCES sys_tenant(id)`
- `psy_scale_golden_case_case_code_not_null` (NOT NULL) `NOT NULL case_code`
- `psy_scale_golden_case_case_content_hash_not_null` (NOT NULL) `NOT NULL case_content_hash`
- `psy_scale_golden_case_case_type_not_null` (NOT NULL) `NOT NULL case_type`
- `psy_scale_golden_case_created_at_not_null` (NOT NULL) `NOT NULL created_at`
- `psy_scale_golden_case_created_by_not_null` (NOT NULL) `NOT NULL created_by`
- `psy_scale_golden_case_expected_json_not_null` (NOT NULL) `NOT NULL expected_json`
- `psy_scale_golden_case_id_not_null` (NOT NULL) `NOT NULL id`
- `psy_scale_golden_case_input_json_not_null` (NOT NULL) `NOT NULL input_json`
- `psy_scale_golden_case_revision_no_not_null` (NOT NULL) `NOT NULL revision_no`
- `psy_scale_golden_case_scale_content_hash_not_null` (NOT NULL) `NOT NULL scale_content_hash`
- `psy_scale_golden_case_scale_id_not_null` (NOT NULL) `NOT NULL scale_id`
- `psy_scale_golden_case_source_reference_not_null` (NOT NULL) `NOT NULL source_reference`
- `psy_scale_golden_case_tenant_id_not_null` (NOT NULL) `NOT NULL tenant_id`
- `psy_scale_golden_case_pkey` (PRIMARY KEY) `PRIMARY KEY (id)`
- `psy_scale_golden_case_scale_id_case_code_revision_no_key` (UNIQUE) `UNIQUE (scale_id, case_code, revision_no)`

索引：

- `idx_psy_scale_golden_case_history_cursor`：`CREATE INDEX idx_psy_scale_golden_case_history_cursor ON public.psy_scale_golden_case USING btree (scale_id, id DESC)`
- `idx_psy_scale_golden_case_latest`：`CREATE INDEX idx_psy_scale_golden_case_latest ON public.psy_scale_golden_case USING btree (scale_id, case_code, revision_no DESC)`
- `idx_psy_scale_golden_case_tenant`：`CREATE INDEX idx_psy_scale_golden_case_tenant ON public.psy_scale_golden_case USING btree (tenant_id, scale_id)`
- `psy_scale_golden_case_pkey`：`CREATE UNIQUE INDEX psy_scale_golden_case_pkey ON public.psy_scale_golden_case USING btree (id)`
- `psy_scale_golden_case_scale_id_case_code_revision_no_key`：`CREATE UNIQUE INDEX psy_scale_golden_case_scale_id_case_code_revision_no_key ON public.psy_scale_golden_case USING btree (scale_id, case_code, revision_no)`

#### `psy_scale_golden_case_run`

| 列 | 类型 | 可空 | 默认值 |
| --- | --- | --- | --- |
| `id` | `bigint` | NO | nextval('psy_scale_golden_case_run_id_seq'::regclass) |
| `tenant_id` | `bigint` | NO | - |
| `scale_id` | `bigint` | NO | - |
| `golden_case_id` | `bigint` | NO | - |
| `scale_content_hash` | `character varying(64)` | NO | - |
| `case_content_hash` | `character varying(64)` | NO | - |
| `algorithm_code` | `character varying(64)` | YES | - |
| `algorithm_version` | `character varying(32)` | YES | - |
| `passed` | `boolean` | NO | - |
| `actual_json` | `jsonb` | NO | - |
| `differences_json` | `jsonb` | NO | '[]'::jsonb |
| `executed_by` | `bigint` | NO | - |
| `executed_at` | `timestamp without time zone` | NO | CURRENT_TIMESTAMP |

约束：

- `ck_psy_golden_run_tenant_required` (CHECK) `CHECK ((tenant_id IS NOT NULL))`
- `ck_psy_scale_golden_run_hashes` (CHECK) `CHECK ((((scale_content_hash)::text ~ '^[0-9a-f]{64}$'::text) AND ((case_content_hash)::text ~ '^[0-9a-f]{64}$'::text))) NOT VALID`
- `ck_psy_scale_golden_run_json` (CHECK) `CHECK (((jsonb_typeof(actual_json) = 'object'::text) AND (jsonb_typeof(differences_json) = 'array'::text))) NOT VALID`
- `psy_scale_golden_case_run_executed_by_fkey` (FOREIGN KEY) `FOREIGN KEY (executed_by) REFERENCES sys_user(id)`
- `psy_scale_golden_case_run_golden_case_id_fkey` (FOREIGN KEY) `FOREIGN KEY (golden_case_id) REFERENCES psy_scale_golden_case(id) ON DELETE CASCADE`
- `psy_scale_golden_case_run_scale_id_fkey` (FOREIGN KEY) `FOREIGN KEY (scale_id) REFERENCES psy_scale(id) ON DELETE CASCADE`
- `psy_scale_golden_case_run_tenant_id_fkey` (FOREIGN KEY) `FOREIGN KEY (tenant_id) REFERENCES sys_tenant(id)`
- `psy_scale_golden_case_run_actual_json_not_null` (NOT NULL) `NOT NULL actual_json`
- `psy_scale_golden_case_run_case_content_hash_not_null` (NOT NULL) `NOT NULL case_content_hash`
- `psy_scale_golden_case_run_differences_json_not_null` (NOT NULL) `NOT NULL differences_json`
- `psy_scale_golden_case_run_executed_at_not_null` (NOT NULL) `NOT NULL executed_at`
- `psy_scale_golden_case_run_executed_by_not_null` (NOT NULL) `NOT NULL executed_by`
- `psy_scale_golden_case_run_golden_case_id_not_null` (NOT NULL) `NOT NULL golden_case_id`
- `psy_scale_golden_case_run_id_not_null` (NOT NULL) `NOT NULL id`
- `psy_scale_golden_case_run_passed_not_null` (NOT NULL) `NOT NULL passed`
- `psy_scale_golden_case_run_scale_content_hash_not_null` (NOT NULL) `NOT NULL scale_content_hash`
- `psy_scale_golden_case_run_scale_id_not_null` (NOT NULL) `NOT NULL scale_id`
- `psy_scale_golden_case_run_tenant_id_not_null` (NOT NULL) `NOT NULL tenant_id`
- `psy_scale_golden_case_run_pkey` (PRIMARY KEY) `PRIMARY KEY (id)`

索引：

- `idx_psy_scale_golden_run_history_cursor`：`CREATE INDEX idx_psy_scale_golden_run_history_cursor ON public.psy_scale_golden_case_run USING btree (scale_id, id DESC)`
- `idx_psy_scale_golden_run_latest`：`CREATE INDEX idx_psy_scale_golden_run_latest ON public.psy_scale_golden_case_run USING btree (golden_case_id, executed_at DESC, id DESC)`
- `idx_psy_scale_golden_run_tenant`：`CREATE INDEX idx_psy_scale_golden_run_tenant ON public.psy_scale_golden_case_run USING btree (tenant_id, scale_id, executed_at DESC)`
- `psy_scale_golden_case_run_pkey`：`CREATE UNIQUE INDEX psy_scale_golden_case_run_pkey ON public.psy_scale_golden_case_run USING btree (id)`

#### `psy_scale_governance`

| 列 | 类型 | 可空 | 默认值 |
| --- | --- | --- | --- |
| `id` | `bigint` | NO | nextval('psy_scale_governance_id_seq'::regclass) |
| `scale_id` | `bigint` | NO | - |
| `source_title` | `character varying(255)` | YES | - |
| `publisher_name` | `character varying(255)` | YES | - |
| `manual_version` | `character varying(64)` | YES | - |
| `citation_text` | `text` | YES | - |
| `source_url` | `text` | YES | - |
| `copyright_status` | `character varying(32)` | NO | 'PENDING_REVIEW'::character varying |
| `rights_holder` | `character varying(255)` | YES | - |
| `authorization_status` | `character varying(32)` | NO | 'PENDING_REVIEW'::character varying |
| `authorization_type` | `character varying(64)` | YES | - |
| `authorization_scope` | `text` | YES | - |
| `authorized_territories` | `text` | YES | - |
| `authorized_languages` | `character varying(255)` | YES | - |
| `authorization_valid_from` | `date` | YES | - |
| `authorization_valid_to` | `date` | YES | - |
| `target_population` | `text` | YES | - |
| `exclusion_criteria` | `text` | YES | - |
| `estimated_minutes` | `integer` | YES | - |
| `result_visibility` | `text` | YES | - |
| `data_usage_statement` | `text` | YES | - |
| `non_diagnostic_statement` | `text` | YES | - |
| `help_resource_text` | `text` | YES | - |
| `governance_status` | `character varying(32)` | NO | 'DRAFT'::character varying |
| `created_by` | `bigint` | YES | - |
| `created_at` | `timestamp without time zone` | NO | CURRENT_TIMESTAMP |
| `updated_by` | `bigint` | YES | - |
| `updated_at` | `timestamp without time zone` | NO | CURRENT_TIMESTAMP |

约束：

- `ck_psy_scale_governance_authorization` (CHECK) `CHECK (((authorization_status)::text = ANY ((ARRAY['PENDING_REVIEW'::character varying, 'AUTHORIZED'::character varying, 'NOT_REQUIRED'::character varying, 'RESTRICTED'::character varying, 'EXPIRED'::character varying, 'REJECTED'::character varying])::text[]))) NOT VALID`
- `ck_psy_scale_governance_copyright` (CHECK) `CHECK (((copyright_status)::text = ANY ((ARRAY['PENDING_REVIEW'::character varying, 'AUTHORIZED'::character varying, 'PUBLIC_DOMAIN'::character varying, 'RESTRICTED'::character varying, 'EXPIRED'::character varying, 'REJECTED'::character varying])::text[]))) NOT VALID`
- `ck_psy_scale_governance_dates` (CHECK) `CHECK (((authorization_valid_to IS NULL) OR (authorization_valid_from IS NULL) OR (authorization_valid_to >= authorization_valid_from))) NOT VALID`
- `ck_psy_scale_governance_duration` (CHECK) `CHECK (((estimated_minutes IS NULL) OR (estimated_minutes > 0))) NOT VALID`
- `ck_psy_scale_governance_status` (CHECK) `CHECK (((governance_status)::text = ANY ((ARRAY['DRAFT'::character varying, 'PENDING_REVIEW'::character varying, 'APPROVED'::character varying, 'REJECTED'::character varying])::text[]))) NOT VALID`
- `psy_scale_governance_created_by_fkey` (FOREIGN KEY) `FOREIGN KEY (created_by) REFERENCES sys_user(id)`
- `psy_scale_governance_scale_id_fkey` (FOREIGN KEY) `FOREIGN KEY (scale_id) REFERENCES psy_scale(id) ON DELETE CASCADE`
- `psy_scale_governance_updated_by_fkey` (FOREIGN KEY) `FOREIGN KEY (updated_by) REFERENCES sys_user(id)`
- `psy_scale_governance_authorization_status_not_null` (NOT NULL) `NOT NULL authorization_status`
- `psy_scale_governance_copyright_status_not_null` (NOT NULL) `NOT NULL copyright_status`
- `psy_scale_governance_created_at_not_null` (NOT NULL) `NOT NULL created_at`
- `psy_scale_governance_governance_status_not_null` (NOT NULL) `NOT NULL governance_status`
- `psy_scale_governance_id_not_null` (NOT NULL) `NOT NULL id`
- `psy_scale_governance_scale_id_not_null` (NOT NULL) `NOT NULL scale_id`
- `psy_scale_governance_updated_at_not_null` (NOT NULL) `NOT NULL updated_at`
- `psy_scale_governance_pkey` (PRIMARY KEY) `PRIMARY KEY (id)`
- `psy_scale_governance_scale_id_key` (UNIQUE) `UNIQUE (scale_id)`

索引：

- `psy_scale_governance_pkey`：`CREATE UNIQUE INDEX psy_scale_governance_pkey ON public.psy_scale_governance USING btree (id)`
- `psy_scale_governance_scale_id_key`：`CREATE UNIQUE INDEX psy_scale_governance_scale_id_key ON public.psy_scale_governance USING btree (scale_id)`

#### `psy_scale_high_risk_rule`

| 列 | 类型 | 可空 | 默认值 |
| --- | --- | --- | --- |
| `id` | `bigint` | NO | nextval('psy_scale_high_risk_rule_id_seq'::regclass) |
| `scale_id` | `bigint` | NO | - |
| `rule_code` | `character varying(64)` | NO | - |
| `question_id` | `bigint` | NO | - |
| `option_id` | `bigint` | YES | - |
| `score_threshold` | `numeric(10,2)` | YES | - |
| `warning_level` | `character varying(32)` | NO | - |
| `result_title` | `character varying(255)` | YES | - |
| `result_description` | `text` | YES | - |
| `suggestion_text` | `text` | YES | - |
| `sort_no` | `integer` | NO | 0 |
| `created_at` | `timestamp without time zone` | NO | CURRENT_TIMESTAMP |
| `updated_at` | `timestamp without time zone` | NO | CURRENT_TIMESTAMP |

约束：

- `psy_scale_high_risk_rule_option_id_fkey` (FOREIGN KEY) `FOREIGN KEY (option_id) REFERENCES psy_scale_option(id)`
- `psy_scale_high_risk_rule_question_id_fkey` (FOREIGN KEY) `FOREIGN KEY (question_id) REFERENCES psy_scale_question(id)`
- `psy_scale_high_risk_rule_scale_id_fkey` (FOREIGN KEY) `FOREIGN KEY (scale_id) REFERENCES psy_scale(id)`
- `psy_scale_high_risk_rule_created_at_not_null` (NOT NULL) `NOT NULL created_at`
- `psy_scale_high_risk_rule_id_not_null` (NOT NULL) `NOT NULL id`
- `psy_scale_high_risk_rule_question_id_not_null` (NOT NULL) `NOT NULL question_id`
- `psy_scale_high_risk_rule_rule_code_not_null` (NOT NULL) `NOT NULL rule_code`
- `psy_scale_high_risk_rule_scale_id_not_null` (NOT NULL) `NOT NULL scale_id`
- `psy_scale_high_risk_rule_sort_no_not_null` (NOT NULL) `NOT NULL sort_no`
- `psy_scale_high_risk_rule_updated_at_not_null` (NOT NULL) `NOT NULL updated_at`
- `psy_scale_high_risk_rule_warning_level_not_null` (NOT NULL) `NOT NULL warning_level`
- `psy_scale_high_risk_rule_pkey` (PRIMARY KEY) `PRIMARY KEY (id)`

索引：

- `idx_psy_scale_high_risk_rule_scale_id`：`CREATE INDEX idx_psy_scale_high_risk_rule_scale_id ON public.psy_scale_high_risk_rule USING btree (scale_id)`
- `psy_scale_high_risk_rule_pkey`：`CREATE UNIQUE INDEX psy_scale_high_risk_rule_pkey ON public.psy_scale_high_risk_rule USING btree (id)`
- `uk_psy_scale_high_risk_rule_code`：`CREATE UNIQUE INDEX uk_psy_scale_high_risk_rule_code ON public.psy_scale_high_risk_rule USING btree (scale_id, rule_code)`

#### `psy_scale_high_risk_rule_translation`

| 列 | 类型 | 可空 | 默认值 |
| --- | --- | --- | --- |
| `id` | `bigint` | NO | nextval('psy_scale_high_risk_rule_translation_id_seq'::regclass) |
| `high_risk_rule_id` | `bigint` | NO | - |
| `locale_code` | `character varying(16)` | NO | - |
| `result_title` | `character varying(255)` | NO | - |
| `result_description` | `text` | YES | - |
| `suggestion_text` | `text` | YES | - |
| `review_status` | `character varying(32)` | NO | 'DRAFT'::character varying |
| `created_at` | `timestamp without time zone` | NO | CURRENT_TIMESTAMP |
| `updated_at` | `timestamp without time zone` | NO | CURRENT_TIMESTAMP |

约束：

- `ck_psy_scale_high_risk_translation_locale` (CHECK) `CHECK (((locale_code)::text = ANY ((ARRAY['zh-CN'::character varying, 'ja-JP'::character varying, 'en'::character varying])::text[]))) NOT VALID`
- `ck_psy_scale_high_risk_translation_review` (CHECK) `CHECK (((review_status)::text = ANY ((ARRAY['DRAFT'::character varying, 'PENDING_REVIEW'::character varying, 'APPROVED'::character varying, 'REJECTED'::character varying])::text[]))) NOT VALID`
- `psy_scale_high_risk_rule_translation_high_risk_rule_id_fkey` (FOREIGN KEY) `FOREIGN KEY (high_risk_rule_id) REFERENCES psy_scale_high_risk_rule(id) ON DELETE CASCADE`
- `psy_scale_high_risk_rule_translation_created_at_not_null` (NOT NULL) `NOT NULL created_at`
- `psy_scale_high_risk_rule_translation_high_risk_rule_id_not_null` (NOT NULL) `NOT NULL high_risk_rule_id`
- `psy_scale_high_risk_rule_translation_id_not_null` (NOT NULL) `NOT NULL id`
- `psy_scale_high_risk_rule_translation_locale_code_not_null` (NOT NULL) `NOT NULL locale_code`
- `psy_scale_high_risk_rule_translation_result_title_not_null` (NOT NULL) `NOT NULL result_title`
- `psy_scale_high_risk_rule_translation_review_status_not_null` (NOT NULL) `NOT NULL review_status`
- `psy_scale_high_risk_rule_translation_updated_at_not_null` (NOT NULL) `NOT NULL updated_at`
- `psy_scale_high_risk_rule_translation_pkey` (PRIMARY KEY) `PRIMARY KEY (id)`
- `psy_scale_high_risk_rule_tran_high_risk_rule_id_locale_code_key` (UNIQUE) `UNIQUE (high_risk_rule_id, locale_code)`

索引：

- `idx_psy_scale_high_risk_translation_locale`：`CREATE INDEX idx_psy_scale_high_risk_translation_locale ON public.psy_scale_high_risk_rule_translation USING btree (locale_code, review_status)`
- `psy_scale_high_risk_rule_tran_high_risk_rule_id_locale_code_key`：`CREATE UNIQUE INDEX psy_scale_high_risk_rule_tran_high_risk_rule_id_locale_code_key ON public.psy_scale_high_risk_rule_translation USING btree (high_risk_rule_id, locale_code)`
- `psy_scale_high_risk_rule_translation_pkey`：`CREATE UNIQUE INDEX psy_scale_high_risk_rule_translation_pkey ON public.psy_scale_high_risk_rule_translation USING btree (id)`

#### `psy_scale_import_issue`

| 列 | 类型 | 可空 | 默认值 |
| --- | --- | --- | --- |
| `id` | `bigint` | NO | nextval('psy_scale_import_issue_id_seq'::regclass) |
| `import_job_id` | `bigint` | NO | - |
| `severity` | `character varying(16)` | NO | - |
| `sheet_name` | `character varying(64)` | NO | - |
| `row_no` | `integer` | YES | - |
| `column_name` | `character varying(64)` | YES | - |
| `error_code` | `character varying(64)` | NO | - |
| `message` | `character varying(500)` | NO | - |
| `created_at` | `timestamp without time zone` | NO | CURRENT_TIMESTAMP |

约束：

- `psy_scale_import_issue_import_job_id_fkey` (FOREIGN KEY) `FOREIGN KEY (import_job_id) REFERENCES psy_scale_import_job(id) ON DELETE CASCADE`
- `psy_scale_import_issue_created_at_not_null` (NOT NULL) `NOT NULL created_at`
- `psy_scale_import_issue_error_code_not_null` (NOT NULL) `NOT NULL error_code`
- `psy_scale_import_issue_id_not_null` (NOT NULL) `NOT NULL id`
- `psy_scale_import_issue_import_job_id_not_null` (NOT NULL) `NOT NULL import_job_id`
- `psy_scale_import_issue_message_not_null` (NOT NULL) `NOT NULL message`
- `psy_scale_import_issue_severity_not_null` (NOT NULL) `NOT NULL severity`
- `psy_scale_import_issue_sheet_name_not_null` (NOT NULL) `NOT NULL sheet_name`
- `psy_scale_import_issue_pkey` (PRIMARY KEY) `PRIMARY KEY (id)`

索引：

- `idx_psy_scale_import_issue_job_id`：`CREATE INDEX idx_psy_scale_import_issue_job_id ON public.psy_scale_import_issue USING btree (import_job_id)`
- `psy_scale_import_issue_pkey`：`CREATE UNIQUE INDEX psy_scale_import_issue_pkey ON public.psy_scale_import_issue USING btree (id)`

#### `psy_scale_import_job`

| 列 | 类型 | 可空 | 默认值 |
| --- | --- | --- | --- |
| `id` | `bigint` | NO | nextval('psy_scale_import_job_id_seq'::regclass) |
| `file_name` | `character varying(255)` | NO | - |
| `file_hash` | `character varying(128)` | YES | - |
| `import_mode` | `character varying(32)` | NO | 'CREATE_ONLY'::character varying |
| `draft_flag` | `boolean` | NO | true |
| `status` | `character varying(32)` | NO | - |
| `summary_json` | `text` | YES | - |
| `preview_json` | `text` | YES | - |
| `error_count` | `integer` | NO | 0 |
| `warning_count` | `integer` | NO | 0 |
| `created_scale_id` | `bigint` | YES | - |
| `operator_user_id` | `bigint` | NO | - |
| `parsed_at` | `timestamp without time zone` | YES | - |
| `confirmed_at` | `timestamp without time zone` | YES | - |
| `finished_at` | `timestamp without time zone` | YES | - |
| `created_at` | `timestamp without time zone` | NO | CURRENT_TIMESTAMP |
| `updated_at` | `timestamp without time zone` | NO | CURRENT_TIMESTAMP |
| `tenant_id` | `bigint` | NO | - |

约束：

- `ck_psy_scale_import_tenant_required` (CHECK) `CHECK ((tenant_id IS NOT NULL))`
- `fk_psy_scale_import_job_tenant` (FOREIGN KEY) `FOREIGN KEY (tenant_id) REFERENCES sys_tenant(id)`
- `psy_scale_import_job_created_scale_id_fkey` (FOREIGN KEY) `FOREIGN KEY (created_scale_id) REFERENCES psy_scale(id)`
- `psy_scale_import_job_operator_user_id_fkey` (FOREIGN KEY) `FOREIGN KEY (operator_user_id) REFERENCES sys_user(id)`
- `psy_scale_import_job_created_at_not_null` (NOT NULL) `NOT NULL created_at`
- `psy_scale_import_job_draft_flag_not_null` (NOT NULL) `NOT NULL draft_flag`
- `psy_scale_import_job_error_count_not_null` (NOT NULL) `NOT NULL error_count`
- `psy_scale_import_job_file_name_not_null` (NOT NULL) `NOT NULL file_name`
- `psy_scale_import_job_id_not_null` (NOT NULL) `NOT NULL id`
- `psy_scale_import_job_import_mode_not_null` (NOT NULL) `NOT NULL import_mode`
- `psy_scale_import_job_operator_user_id_not_null` (NOT NULL) `NOT NULL operator_user_id`
- `psy_scale_import_job_status_not_null` (NOT NULL) `NOT NULL status`
- `psy_scale_import_job_tenant_id_not_null` (NOT NULL) `NOT NULL tenant_id`
- `psy_scale_import_job_updated_at_not_null` (NOT NULL) `NOT NULL updated_at`
- `psy_scale_import_job_warning_count_not_null` (NOT NULL) `NOT NULL warning_count`
- `psy_scale_import_job_pkey` (PRIMARY KEY) `PRIMARY KEY (id)`

索引：

- `idx_psy_scale_import_job_operator`：`CREATE INDEX idx_psy_scale_import_job_operator ON public.psy_scale_import_job USING btree (operator_user_id)`
- `idx_psy_scale_import_job_status`：`CREATE INDEX idx_psy_scale_import_job_status ON public.psy_scale_import_job USING btree (status)`
- `idx_psy_scale_import_job_tenant_created`：`CREATE INDEX idx_psy_scale_import_job_tenant_created ON public.psy_scale_import_job USING btree (tenant_id, created_at DESC, id DESC)`
- `psy_scale_import_job_pkey`：`CREATE UNIQUE INDEX psy_scale_import_job_pkey ON public.psy_scale_import_job USING btree (id)`

#### `psy_scale_norm`

| 列 | 类型 | 可空 | 默认值 |
| --- | --- | --- | --- |
| `id` | `bigint` | NO | nextval('psy_scale_norm_id_seq'::regclass) |
| `scale_id` | `bigint` | NO | - |
| `norm_code` | `character varying(64)` | NO | - |
| `norm_name` | `character varying(255)` | YES | - |
| `dimension_id` | `bigint` | YES | - |
| `applicable_target` | `character varying(128)` | YES | - |
| `age_min` | `integer` | YES | - |
| `age_max` | `integer` | YES | - |
| `gender` | `character varying(32)` | YES | - |
| `org_type` | `character varying(64)` | YES | - |
| `mean_score` | `numeric(10,4)` | YES | - |
| `std_deviation` | `numeric(10,4)` | YES | - |
| `t_score_mean` | `numeric(10,4)` | YES | - |
| `t_score_std_deviation` | `numeric(10,4)` | YES | - |
| `sort_no` | `integer` | NO | 0 |
| `created_at` | `timestamp without time zone` | NO | CURRENT_TIMESTAMP |
| `updated_at` | `timestamp without time zone` | NO | CURRENT_TIMESTAMP |
| `source_reference` | `text` | YES | - |
| `norm_version` | `character varying(64)` | YES | - |
| `sample_size` | `integer` | YES | - |
| `region_code` | `character varying(64)` | YES | - |
| `language_code` | `character varying(16)` | YES | - |
| `valid_from` | `date` | YES | - |
| `valid_to` | `date` | YES | - |
| `review_status` | `character varying(32)` | NO | 'PENDING_REVIEW'::character varying |

约束：

- `ck_psy_scale_norm_review_status` (CHECK) `CHECK (((review_status)::text = ANY ((ARRAY['PENDING_REVIEW'::character varying, 'APPROVED'::character varying, 'REJECTED'::character varying, 'EXPIRED'::character varying])::text[]))) NOT VALID`
- `ck_psy_scale_norm_sample_size` (CHECK) `CHECK (((sample_size IS NULL) OR (sample_size > 0))) NOT VALID`
- `ck_psy_scale_norm_valid_dates` (CHECK) `CHECK (((valid_to IS NULL) OR (valid_from IS NULL) OR (valid_to >= valid_from))) NOT VALID`
- `psy_scale_norm_dimension_id_fkey` (FOREIGN KEY) `FOREIGN KEY (dimension_id) REFERENCES psy_scale_dimension(id)`
- `psy_scale_norm_scale_id_fkey` (FOREIGN KEY) `FOREIGN KEY (scale_id) REFERENCES psy_scale(id)`
- `psy_scale_norm_created_at_not_null` (NOT NULL) `NOT NULL created_at`
- `psy_scale_norm_id_not_null` (NOT NULL) `NOT NULL id`
- `psy_scale_norm_norm_code_not_null` (NOT NULL) `NOT NULL norm_code`
- `psy_scale_norm_review_status_not_null` (NOT NULL) `NOT NULL review_status`
- `psy_scale_norm_scale_id_not_null` (NOT NULL) `NOT NULL scale_id`
- `psy_scale_norm_sort_no_not_null` (NOT NULL) `NOT NULL sort_no`
- `psy_scale_norm_updated_at_not_null` (NOT NULL) `NOT NULL updated_at`
- `psy_scale_norm_pkey` (PRIMARY KEY) `PRIMARY KEY (id)`

索引：

- `idx_psy_scale_norm_scale_id`：`CREATE INDEX idx_psy_scale_norm_scale_id ON public.psy_scale_norm USING btree (scale_id)`
- `psy_scale_norm_pkey`：`CREATE UNIQUE INDEX psy_scale_norm_pkey ON public.psy_scale_norm USING btree (id)`
- `uk_psy_scale_norm_code`：`CREATE UNIQUE INDEX uk_psy_scale_norm_code ON public.psy_scale_norm USING btree (scale_id, norm_code, COALESCE(dimension_id, (0)::bigint))`

#### `psy_scale_option`

| 列 | 类型 | 可空 | 默认值 |
| --- | --- | --- | --- |
| `id` | `bigint` | NO | nextval('psy_scale_option_id_seq'::regclass) |
| `question_id` | `bigint` | NO | - |
| `option_code` | `character varying(64)` | NO | - |
| `option_label` | `character varying(255)` | NO | - |
| `score_value` | `numeric(10,2)` | NO | - |
| `exclusive_flag` | `boolean` | NO | false |
| `option_group_code` | `character varying(64)` | YES | - |
| `sort_no` | `integer` | NO | 0 |
| `created_at` | `timestamp without time zone` | NO | CURRENT_TIMESTAMP |
| `updated_at` | `timestamp without time zone` | NO | CURRENT_TIMESTAMP |

约束：

- `psy_scale_option_question_id_fkey` (FOREIGN KEY) `FOREIGN KEY (question_id) REFERENCES psy_scale_question(id)`
- `psy_scale_option_created_at_not_null` (NOT NULL) `NOT NULL created_at`
- `psy_scale_option_exclusive_flag_not_null` (NOT NULL) `NOT NULL exclusive_flag`
- `psy_scale_option_id_not_null` (NOT NULL) `NOT NULL id`
- `psy_scale_option_option_code_not_null` (NOT NULL) `NOT NULL option_code`
- `psy_scale_option_option_label_not_null` (NOT NULL) `NOT NULL option_label`
- `psy_scale_option_question_id_not_null` (NOT NULL) `NOT NULL question_id`
- `psy_scale_option_score_value_not_null` (NOT NULL) `NOT NULL score_value`
- `psy_scale_option_sort_no_not_null` (NOT NULL) `NOT NULL sort_no`
- `psy_scale_option_updated_at_not_null` (NOT NULL) `NOT NULL updated_at`
- `psy_scale_option_pkey` (PRIMARY KEY) `PRIMARY KEY (id)`

索引：

- `idx_psy_scale_option_question_id`：`CREATE INDEX idx_psy_scale_option_question_id ON public.psy_scale_option USING btree (question_id)`
- `psy_scale_option_pkey`：`CREATE UNIQUE INDEX psy_scale_option_pkey ON public.psy_scale_option USING btree (id)`
- `uk_psy_scale_option_code`：`CREATE UNIQUE INDEX uk_psy_scale_option_code ON public.psy_scale_option USING btree (question_id, option_code)`

#### `psy_scale_option_translation`

| 列 | 类型 | 可空 | 默认值 |
| --- | --- | --- | --- |
| `id` | `bigint` | NO | nextval('psy_scale_option_translation_id_seq'::regclass) |
| `option_id` | `bigint` | NO | - |
| `locale_code` | `character varying(16)` | NO | - |
| `option_label` | `character varying(255)` | NO | - |
| `review_status` | `character varying(32)` | NO | 'DRAFT'::character varying |
| `created_at` | `timestamp without time zone` | NO | CURRENT_TIMESTAMP |
| `updated_at` | `timestamp without time zone` | NO | CURRENT_TIMESTAMP |

约束：

- `ck_psy_scale_option_translation_locale` (CHECK) `CHECK (((locale_code)::text = ANY ((ARRAY['zh-CN'::character varying, 'ja-JP'::character varying, 'en'::character varying])::text[]))) NOT VALID`
- `ck_psy_scale_option_translation_review` (CHECK) `CHECK (((review_status)::text = ANY ((ARRAY['DRAFT'::character varying, 'PENDING_REVIEW'::character varying, 'APPROVED'::character varying, 'REJECTED'::character varying])::text[]))) NOT VALID`
- `psy_scale_option_translation_option_id_fkey` (FOREIGN KEY) `FOREIGN KEY (option_id) REFERENCES psy_scale_option(id) ON DELETE CASCADE`
- `psy_scale_option_translation_created_at_not_null` (NOT NULL) `NOT NULL created_at`
- `psy_scale_option_translation_id_not_null` (NOT NULL) `NOT NULL id`
- `psy_scale_option_translation_locale_code_not_null` (NOT NULL) `NOT NULL locale_code`
- `psy_scale_option_translation_option_id_not_null` (NOT NULL) `NOT NULL option_id`
- `psy_scale_option_translation_option_label_not_null` (NOT NULL) `NOT NULL option_label`
- `psy_scale_option_translation_review_status_not_null` (NOT NULL) `NOT NULL review_status`
- `psy_scale_option_translation_updated_at_not_null` (NOT NULL) `NOT NULL updated_at`
- `psy_scale_option_translation_pkey` (PRIMARY KEY) `PRIMARY KEY (id)`
- `psy_scale_option_translation_option_id_locale_code_key` (UNIQUE) `UNIQUE (option_id, locale_code)`

索引：

- `idx_psy_scale_option_translation_locale`：`CREATE INDEX idx_psy_scale_option_translation_locale ON public.psy_scale_option_translation USING btree (locale_code, review_status)`
- `psy_scale_option_translation_option_id_locale_code_key`：`CREATE UNIQUE INDEX psy_scale_option_translation_option_id_locale_code_key ON public.psy_scale_option_translation USING btree (option_id, locale_code)`
- `psy_scale_option_translation_pkey`：`CREATE UNIQUE INDEX psy_scale_option_translation_pkey ON public.psy_scale_option_translation USING btree (id)`

#### `psy_scale_publication_review`

| 列 | 类型 | 可空 | 默认值 |
| --- | --- | --- | --- |
| `id` | `bigint` | NO | nextval('psy_scale_publication_review_id_seq'::regclass) |
| `tenant_id` | `bigint` | NO | - |
| `scale_id` | `bigint` | NO | - |
| `review_type` | `character varying(32)` | NO | - |
| `decision` | `character varying(32)` | NO | - |
| `reviewer_id` | `bigint` | NO | - |
| `reviewer_role_snapshot` | `character varying(64)` | NO | - |
| `scale_content_hash` | `character varying(64)` | NO | - |
| `release_fingerprint` | `character varying(64)` | NO | - |
| `review_token` | `character varying(128)` | NO | - |
| `comment_text` | `text` | YES | - |
| `created_at` | `timestamp without time zone` | NO | CURRENT_TIMESTAMP |
| `reviewer_name_snapshot` | `character varying(128)` | YES | - |
| `qualification_reference` | `text` | YES | - |
| `evidence_reference` | `text` | YES | - |
| `review_scope` | `text` | YES | - |

约束：

- `ck_psy_publication_review_tenant_required` (CHECK) `CHECK ((tenant_id IS NOT NULL))`
- `ck_psy_scale_publication_approval_evidence` (CHECK) `CHECK ((((decision)::text <> 'APPROVED'::text) OR ((NULLIF(btrim((reviewer_name_snapshot)::text), ''::text) IS NOT NULL) AND (NULLIF(btrim(evidence_reference), ''::text) IS NOT NULL) AND (NULLIF(btrim(review_scope), ''::text) IS NOT NULL) AND (((review_type)::text <> 'PROFESSIONAL'::text) OR (NULLIF(btrim(qualification_reference), ''::text) IS NOT NULL))))) NOT VALID`
- `ck_psy_scale_publication_decision` (CHECK) `CHECK (((decision)::text = ANY ((ARRAY['APPROVED'::character varying, 'REJECTED'::character varying])::text[]))) NOT VALID`
- `ck_psy_scale_publication_hashes` (CHECK) `CHECK ((((scale_content_hash)::text ~ '^[0-9a-f]{64}$'::text) AND ((release_fingerprint)::text ~ '^[0-9a-f]{64}$'::text))) NOT VALID`
- `ck_psy_scale_publication_review_type` (CHECK) `CHECK (((review_type)::text = ANY ((ARRAY['PROFESSIONAL'::character varying, 'BUSINESS'::character varying])::text[]))) NOT VALID`
- `psy_scale_publication_review_reviewer_id_fkey` (FOREIGN KEY) `FOREIGN KEY (reviewer_id) REFERENCES sys_user(id)`
- `psy_scale_publication_review_scale_id_fkey` (FOREIGN KEY) `FOREIGN KEY (scale_id) REFERENCES psy_scale(id) ON DELETE CASCADE`
- `psy_scale_publication_review_tenant_id_fkey` (FOREIGN KEY) `FOREIGN KEY (tenant_id) REFERENCES sys_tenant(id)`
- `psy_scale_publication_review_created_at_not_null` (NOT NULL) `NOT NULL created_at`
- `psy_scale_publication_review_decision_not_null` (NOT NULL) `NOT NULL decision`
- `psy_scale_publication_review_id_not_null` (NOT NULL) `NOT NULL id`
- `psy_scale_publication_review_release_fingerprint_not_null` (NOT NULL) `NOT NULL release_fingerprint`
- `psy_scale_publication_review_review_token_not_null` (NOT NULL) `NOT NULL review_token`
- `psy_scale_publication_review_review_type_not_null` (NOT NULL) `NOT NULL review_type`
- `psy_scale_publication_review_reviewer_id_not_null` (NOT NULL) `NOT NULL reviewer_id`
- `psy_scale_publication_review_reviewer_role_snapshot_not_null` (NOT NULL) `NOT NULL reviewer_role_snapshot`
- `psy_scale_publication_review_scale_content_hash_not_null` (NOT NULL) `NOT NULL scale_content_hash`
- `psy_scale_publication_review_scale_id_not_null` (NOT NULL) `NOT NULL scale_id`
- `psy_scale_publication_review_tenant_id_not_null` (NOT NULL) `NOT NULL tenant_id`
- `psy_scale_publication_review_pkey` (PRIMARY KEY) `PRIMARY KEY (id)`
- `psy_scale_publication_review_scale_id_review_type_review_to_key` (UNIQUE) `UNIQUE (scale_id, review_type, review_token)`

索引：

- `idx_psy_scale_publication_review_history_cursor`：`CREATE INDEX idx_psy_scale_publication_review_history_cursor ON public.psy_scale_publication_review USING btree (scale_id, id DESC)`
- `idx_psy_scale_publication_review_latest`：`CREATE INDEX idx_psy_scale_publication_review_latest ON public.psy_scale_publication_review USING btree (scale_id, release_fingerprint, review_type, created_at DESC, id DESC)`
- `idx_psy_scale_publication_review_tenant`：`CREATE INDEX idx_psy_scale_publication_review_tenant ON public.psy_scale_publication_review USING btree (tenant_id, scale_id, created_at DESC)`
- `psy_scale_publication_review_pkey`：`CREATE UNIQUE INDEX psy_scale_publication_review_pkey ON public.psy_scale_publication_review USING btree (id)`
- `psy_scale_publication_review_scale_id_review_type_review_to_key`：`CREATE UNIQUE INDEX psy_scale_publication_review_scale_id_review_type_review_to_key ON public.psy_scale_publication_review USING btree (scale_id, review_type, review_token)`

#### `psy_scale_quality_policy`

| 列 | 类型 | 可空 | 默认值 |
| --- | --- | --- | --- |
| `id` | `bigint` | NO | nextval('psy_scale_quality_policy_id_seq'::regclass) |
| `scale_id` | `bigint` | NO | - |
| `missing_answer_policy` | `character varying(32)` | NO | 'REJECT'::character varying |
| `max_missing_ratio` | `numeric(6,5)` | NO | 0 |
| `minimum_duration_seconds` | `integer` | YES | - |
| `maximum_duration_seconds` | `integer` | YES | - |
| `invalid_result_action` | `character varying(32)` | NO | 'INVALIDATE'::character varying |
| `require_all_required_answers` | `boolean` | NO | true |
| `created_at` | `timestamp without time zone` | NO | CURRENT_TIMESTAMP |
| `updated_at` | `timestamp without time zone` | NO | CURRENT_TIMESTAMP |

约束：

- `ck_psy_scale_quality_duration` (CHECK) `CHECK ((((minimum_duration_seconds IS NULL) OR (minimum_duration_seconds > 0)) AND ((maximum_duration_seconds IS NULL) OR (maximum_duration_seconds > 0)) AND ((minimum_duration_seconds IS NULL) OR (maximum_duration_seconds IS NULL) OR (maximum_duration_seconds >= minimum_duration_seconds)))) NOT VALID`
- `ck_psy_scale_quality_invalid_action` (CHECK) `CHECK (((invalid_result_action)::text = ANY ((ARRAY['INVALIDATE'::character varying, 'REQUIRE_REVIEW'::character varying, 'ALLOW_WITH_WARNING'::character varying])::text[]))) NOT VALID`
- `ck_psy_scale_quality_missing_policy` (CHECK) `CHECK (((missing_answer_policy)::text = ANY ((ARRAY['REJECT'::character varying, 'ALLOW'::character varying, 'PRORATE'::character varying, 'PENDING_PROFESSIONAL_REVIEW'::character varying])::text[]))) NOT VALID`
- `ck_psy_scale_quality_missing_ratio` (CHECK) `CHECK (((max_missing_ratio >= (0)::numeric) AND (max_missing_ratio <= (1)::numeric))) NOT VALID`
- `psy_scale_quality_policy_scale_id_fkey` (FOREIGN KEY) `FOREIGN KEY (scale_id) REFERENCES psy_scale(id) ON DELETE CASCADE`
- `psy_scale_quality_policy_created_at_not_null` (NOT NULL) `NOT NULL created_at`
- `psy_scale_quality_policy_id_not_null` (NOT NULL) `NOT NULL id`
- `psy_scale_quality_policy_invalid_result_action_not_null` (NOT NULL) `NOT NULL invalid_result_action`
- `psy_scale_quality_policy_max_missing_ratio_not_null` (NOT NULL) `NOT NULL max_missing_ratio`
- `psy_scale_quality_policy_missing_answer_policy_not_null` (NOT NULL) `NOT NULL missing_answer_policy`
- `psy_scale_quality_policy_require_all_required_answers_not_null` (NOT NULL) `NOT NULL require_all_required_answers`
- `psy_scale_quality_policy_scale_id_not_null` (NOT NULL) `NOT NULL scale_id`
- `psy_scale_quality_policy_updated_at_not_null` (NOT NULL) `NOT NULL updated_at`
- `psy_scale_quality_policy_pkey` (PRIMARY KEY) `PRIMARY KEY (id)`
- `psy_scale_quality_policy_scale_id_key` (UNIQUE) `UNIQUE (scale_id)`

索引：

- `psy_scale_quality_policy_pkey`：`CREATE UNIQUE INDEX psy_scale_quality_policy_pkey ON public.psy_scale_quality_policy USING btree (id)`
- `psy_scale_quality_policy_scale_id_key`：`CREATE UNIQUE INDEX psy_scale_quality_policy_scale_id_key ON public.psy_scale_quality_policy USING btree (scale_id)`

#### `psy_scale_question`

| 列 | 类型 | 可空 | 默认值 |
| --- | --- | --- | --- |
| `id` | `bigint` | NO | nextval('psy_scale_question_id_seq'::regclass) |
| `scale_id` | `bigint` | NO | - |
| `dimension_id` | `bigint` | YES | - |
| `question_no` | `integer` | NO | - |
| `question_title` | `text` | NO | - |
| `question_type` | `character varying(32)` | NO | - |
| `required_flag` | `boolean` | NO | true |
| `reverse_score_flag` | `boolean` | NO | false |
| `weight_value` | `numeric(10,2)` | NO | 1.00 |
| `option_selection_limit` | `integer` | YES | - |
| `slider_min` | `numeric(10,2)` | YES | - |
| `slider_max` | `numeric(10,2)` | YES | - |
| `slider_step` | `numeric(10,2)` | YES | - |
| `text_input_enabled` | `boolean` | NO | false |
| `text_input_placeholder` | `character varying(255)` | YES | - |
| `matrix_group_code` | `character varying(64)` | YES | - |
| `row_code` | `character varying(64)` | YES | - |
| `column_code` | `character varying(64)` | YES | - |
| `sort_no` | `integer` | NO | 0 |
| `created_at` | `timestamp without time zone` | NO | CURRENT_TIMESTAMP |
| `updated_at` | `timestamp without time zone` | NO | CURRENT_TIMESTAMP |

约束：

- `ck_psy_scale_question_type` (CHECK) `CHECK (((question_type)::text = ANY ((ARRAY['SINGLE_CHOICE'::character varying, 'MULTI_SELECT'::character varying, 'SLIDER'::character varying, 'TEXT'::character varying, 'TEXT_WITH_OPTION'::character varying, 'MATRIX'::character varying, 'TIME'::character varying])::text[]))) NOT VALID`
- `psy_scale_question_dimension_id_fkey` (FOREIGN KEY) `FOREIGN KEY (dimension_id) REFERENCES psy_scale_dimension(id)`
- `psy_scale_question_scale_id_fkey` (FOREIGN KEY) `FOREIGN KEY (scale_id) REFERENCES psy_scale(id)`
- `psy_scale_question_created_at_not_null` (NOT NULL) `NOT NULL created_at`
- `psy_scale_question_id_not_null` (NOT NULL) `NOT NULL id`
- `psy_scale_question_question_no_not_null` (NOT NULL) `NOT NULL question_no`
- `psy_scale_question_question_title_not_null` (NOT NULL) `NOT NULL question_title`
- `psy_scale_question_question_type_not_null` (NOT NULL) `NOT NULL question_type`
- `psy_scale_question_required_flag_not_null` (NOT NULL) `NOT NULL required_flag`
- `psy_scale_question_reverse_score_flag_not_null` (NOT NULL) `NOT NULL reverse_score_flag`
- `psy_scale_question_scale_id_not_null` (NOT NULL) `NOT NULL scale_id`
- `psy_scale_question_sort_no_not_null` (NOT NULL) `NOT NULL sort_no`
- `psy_scale_question_text_input_enabled_not_null` (NOT NULL) `NOT NULL text_input_enabled`
- `psy_scale_question_updated_at_not_null` (NOT NULL) `NOT NULL updated_at`
- `psy_scale_question_weight_value_not_null` (NOT NULL) `NOT NULL weight_value`
- `psy_scale_question_pkey` (PRIMARY KEY) `PRIMARY KEY (id)`

索引：

- `idx_psy_scale_question_scale_id`：`CREATE INDEX idx_psy_scale_question_scale_id ON public.psy_scale_question USING btree (scale_id)`
- `psy_scale_question_pkey`：`CREATE UNIQUE INDEX psy_scale_question_pkey ON public.psy_scale_question USING btree (id)`
- `uk_psy_scale_question_no`：`CREATE UNIQUE INDEX uk_psy_scale_question_no ON public.psy_scale_question USING btree (scale_id, question_no)`

#### `psy_scale_question_translation`

| 列 | 类型 | 可空 | 默认值 |
| --- | --- | --- | --- |
| `id` | `bigint` | NO | nextval('psy_scale_question_translation_id_seq'::regclass) |
| `question_id` | `bigint` | NO | - |
| `locale_code` | `character varying(16)` | NO | - |
| `question_title` | `text` | NO | - |
| `text_input_placeholder` | `character varying(255)` | YES | - |
| `review_status` | `character varying(32)` | NO | 'DRAFT'::character varying |
| `created_at` | `timestamp without time zone` | NO | CURRENT_TIMESTAMP |
| `updated_at` | `timestamp without time zone` | NO | CURRENT_TIMESTAMP |

约束：

- `ck_psy_scale_question_translation_locale` (CHECK) `CHECK (((locale_code)::text = ANY ((ARRAY['zh-CN'::character varying, 'ja-JP'::character varying, 'en'::character varying])::text[]))) NOT VALID`
- `ck_psy_scale_question_translation_review` (CHECK) `CHECK (((review_status)::text = ANY ((ARRAY['DRAFT'::character varying, 'PENDING_REVIEW'::character varying, 'APPROVED'::character varying, 'REJECTED'::character varying])::text[]))) NOT VALID`
- `psy_scale_question_translation_question_id_fkey` (FOREIGN KEY) `FOREIGN KEY (question_id) REFERENCES psy_scale_question(id) ON DELETE CASCADE`
- `psy_scale_question_translation_created_at_not_null` (NOT NULL) `NOT NULL created_at`
- `psy_scale_question_translation_id_not_null` (NOT NULL) `NOT NULL id`
- `psy_scale_question_translation_locale_code_not_null` (NOT NULL) `NOT NULL locale_code`
- `psy_scale_question_translation_question_id_not_null` (NOT NULL) `NOT NULL question_id`
- `psy_scale_question_translation_question_title_not_null` (NOT NULL) `NOT NULL question_title`
- `psy_scale_question_translation_review_status_not_null` (NOT NULL) `NOT NULL review_status`
- `psy_scale_question_translation_updated_at_not_null` (NOT NULL) `NOT NULL updated_at`
- `psy_scale_question_translation_pkey` (PRIMARY KEY) `PRIMARY KEY (id)`
- `psy_scale_question_translation_question_id_locale_code_key` (UNIQUE) `UNIQUE (question_id, locale_code)`

索引：

- `idx_psy_scale_question_translation_locale`：`CREATE INDEX idx_psy_scale_question_translation_locale ON public.psy_scale_question_translation USING btree (locale_code, review_status)`
- `psy_scale_question_translation_pkey`：`CREATE UNIQUE INDEX psy_scale_question_translation_pkey ON public.psy_scale_question_translation USING btree (id)`
- `psy_scale_question_translation_question_id_locale_code_key`：`CREATE UNIQUE INDEX psy_scale_question_translation_question_id_locale_code_key ON public.psy_scale_question_translation USING btree (question_id, locale_code)`

#### `psy_scale_result_rule`

| 列 | 类型 | 可空 | 默认值 |
| --- | --- | --- | --- |
| `id` | `bigint` | NO | nextval('psy_scale_result_rule_id_seq'::regclass) |
| `scale_id` | `bigint` | NO | - |
| `dimension_id` | `bigint` | YES | - |
| `risk_level` | `character varying(32)` | NO | - |
| `score_min` | `numeric(10,2)` | NO | - |
| `score_max` | `numeric(10,2)` | NO | - |
| `score_source` | `character varying(32)` | NO | 'RAW_SCORE'::character varying |
| `norm_code` | `character varying(64)` | YES | - |
| `result_title` | `character varying(255)` | YES | - |
| `result_description` | `text` | YES | - |
| `suggestion_text` | `text` | YES | - |
| `created_at` | `timestamp without time zone` | NO | CURRENT_TIMESTAMP |
| `updated_at` | `timestamp without time zone` | NO | CURRENT_TIMESTAMP |

约束：

- `ck_psy_scale_result_score_source` (CHECK) `CHECK (((score_source)::text = ANY ((ARRAY['RAW_SCORE'::character varying, 'Z_SCORE'::character varying, 'T_SCORE'::character varying])::text[]))) NOT VALID`
- `psy_scale_result_rule_dimension_id_fkey` (FOREIGN KEY) `FOREIGN KEY (dimension_id) REFERENCES psy_scale_dimension(id)`
- `psy_scale_result_rule_scale_id_fkey` (FOREIGN KEY) `FOREIGN KEY (scale_id) REFERENCES psy_scale(id)`
- `psy_scale_result_rule_created_at_not_null` (NOT NULL) `NOT NULL created_at`
- `psy_scale_result_rule_id_not_null` (NOT NULL) `NOT NULL id`
- `psy_scale_result_rule_risk_level_not_null` (NOT NULL) `NOT NULL risk_level`
- `psy_scale_result_rule_scale_id_not_null` (NOT NULL) `NOT NULL scale_id`
- `psy_scale_result_rule_score_max_not_null` (NOT NULL) `NOT NULL score_max`
- `psy_scale_result_rule_score_min_not_null` (NOT NULL) `NOT NULL score_min`
- `psy_scale_result_rule_score_source_not_null` (NOT NULL) `NOT NULL score_source`
- `psy_scale_result_rule_updated_at_not_null` (NOT NULL) `NOT NULL updated_at`
- `psy_scale_result_rule_pkey` (PRIMARY KEY) `PRIMARY KEY (id)`

索引：

- `idx_psy_scale_result_rule_scale_id`：`CREATE INDEX idx_psy_scale_result_rule_scale_id ON public.psy_scale_result_rule USING btree (scale_id)`
- `psy_scale_result_rule_pkey`：`CREATE UNIQUE INDEX psy_scale_result_rule_pkey ON public.psy_scale_result_rule USING btree (id)`

#### `psy_scale_result_rule_translation`

| 列 | 类型 | 可空 | 默认值 |
| --- | --- | --- | --- |
| `id` | `bigint` | NO | nextval('psy_scale_result_rule_translation_id_seq'::regclass) |
| `result_rule_id` | `bigint` | NO | - |
| `locale_code` | `character varying(16)` | NO | - |
| `result_title` | `character varying(255)` | NO | - |
| `result_description` | `text` | YES | - |
| `suggestion_text` | `text` | YES | - |
| `review_status` | `character varying(32)` | NO | 'DRAFT'::character varying |
| `created_at` | `timestamp without time zone` | NO | CURRENT_TIMESTAMP |
| `updated_at` | `timestamp without time zone` | NO | CURRENT_TIMESTAMP |

约束：

- `ck_psy_scale_result_translation_locale` (CHECK) `CHECK (((locale_code)::text = ANY ((ARRAY['zh-CN'::character varying, 'ja-JP'::character varying, 'en'::character varying])::text[]))) NOT VALID`
- `ck_psy_scale_result_translation_review` (CHECK) `CHECK (((review_status)::text = ANY ((ARRAY['DRAFT'::character varying, 'PENDING_REVIEW'::character varying, 'APPROVED'::character varying, 'REJECTED'::character varying])::text[]))) NOT VALID`
- `psy_scale_result_rule_translation_result_rule_id_fkey` (FOREIGN KEY) `FOREIGN KEY (result_rule_id) REFERENCES psy_scale_result_rule(id) ON DELETE CASCADE`
- `psy_scale_result_rule_translation_created_at_not_null` (NOT NULL) `NOT NULL created_at`
- `psy_scale_result_rule_translation_id_not_null` (NOT NULL) `NOT NULL id`
- `psy_scale_result_rule_translation_locale_code_not_null` (NOT NULL) `NOT NULL locale_code`
- `psy_scale_result_rule_translation_result_rule_id_not_null` (NOT NULL) `NOT NULL result_rule_id`
- `psy_scale_result_rule_translation_result_title_not_null` (NOT NULL) `NOT NULL result_title`
- `psy_scale_result_rule_translation_review_status_not_null` (NOT NULL) `NOT NULL review_status`
- `psy_scale_result_rule_translation_updated_at_not_null` (NOT NULL) `NOT NULL updated_at`
- `psy_scale_result_rule_translation_pkey` (PRIMARY KEY) `PRIMARY KEY (id)`
- `psy_scale_result_rule_translatio_result_rule_id_locale_code_key` (UNIQUE) `UNIQUE (result_rule_id, locale_code)`

索引：

- `idx_psy_scale_result_translation_locale`：`CREATE INDEX idx_psy_scale_result_translation_locale ON public.psy_scale_result_rule_translation USING btree (locale_code, review_status)`
- `psy_scale_result_rule_translatio_result_rule_id_locale_code_key`：`CREATE UNIQUE INDEX psy_scale_result_rule_translatio_result_rule_id_locale_code_key ON public.psy_scale_result_rule_translation USING btree (result_rule_id, locale_code)`
- `psy_scale_result_rule_translation_pkey`：`CREATE UNIQUE INDEX psy_scale_result_rule_translation_pkey ON public.psy_scale_result_rule_translation USING btree (id)`

#### `psy_scale_translation`

| 列 | 类型 | 可空 | 默认值 |
| --- | --- | --- | --- |
| `id` | `bigint` | NO | nextval('psy_scale_translation_id_seq'::regclass) |
| `scale_id` | `bigint` | NO | - |
| `locale_code` | `character varying(16)` | NO | - |
| `scale_name` | `character varying(255)` | NO | - |
| `description` | `text` | YES | - |
| `instruction_text` | `text` | YES | - |
| `purpose_text` | `text` | YES | - |
| `data_usage_text` | `text` | YES | - |
| `result_visibility_text` | `text` | YES | - |
| `non_diagnostic_text` | `text` | YES | - |
| `high_risk_action_text` | `text` | YES | - |
| `help_resource_text` | `text` | YES | - |
| `review_status` | `character varying(32)` | NO | 'DRAFT'::character varying |
| `created_at` | `timestamp without time zone` | NO | CURRENT_TIMESTAMP |
| `updated_at` | `timestamp without time zone` | NO | CURRENT_TIMESTAMP |

约束：

- `ck_psy_scale_translation_locale` (CHECK) `CHECK (((locale_code)::text = ANY ((ARRAY['zh-CN'::character varying, 'ja-JP'::character varying, 'en'::character varying])::text[]))) NOT VALID`
- `ck_psy_scale_translation_review` (CHECK) `CHECK (((review_status)::text = ANY ((ARRAY['DRAFT'::character varying, 'PENDING_REVIEW'::character varying, 'APPROVED'::character varying, 'REJECTED'::character varying])::text[]))) NOT VALID`
- `psy_scale_translation_scale_id_fkey` (FOREIGN KEY) `FOREIGN KEY (scale_id) REFERENCES psy_scale(id) ON DELETE CASCADE`
- `psy_scale_translation_created_at_not_null` (NOT NULL) `NOT NULL created_at`
- `psy_scale_translation_id_not_null` (NOT NULL) `NOT NULL id`
- `psy_scale_translation_locale_code_not_null` (NOT NULL) `NOT NULL locale_code`
- `psy_scale_translation_review_status_not_null` (NOT NULL) `NOT NULL review_status`
- `psy_scale_translation_scale_id_not_null` (NOT NULL) `NOT NULL scale_id`
- `psy_scale_translation_scale_name_not_null` (NOT NULL) `NOT NULL scale_name`
- `psy_scale_translation_updated_at_not_null` (NOT NULL) `NOT NULL updated_at`
- `psy_scale_translation_pkey` (PRIMARY KEY) `PRIMARY KEY (id)`
- `psy_scale_translation_scale_id_locale_code_key` (UNIQUE) `UNIQUE (scale_id, locale_code)`

索引：

- `idx_psy_scale_translation_locale`：`CREATE INDEX idx_psy_scale_translation_locale ON public.psy_scale_translation USING btree (locale_code, review_status)`
- `psy_scale_translation_pkey`：`CREATE UNIQUE INDEX psy_scale_translation_pkey ON public.psy_scale_translation USING btree (id)`
- `psy_scale_translation_scale_id_locale_code_key`：`CREATE UNIQUE INDEX psy_scale_translation_scale_id_locale_code_key ON public.psy_scale_translation USING btree (scale_id, locale_code)`

#### `psy_scale_validity_rule`

| 列 | 类型 | 可空 | 默认值 |
| --- | --- | --- | --- |
| `id` | `bigint` | NO | nextval('psy_scale_validity_rule_id_seq'::regclass) |
| `scale_id` | `bigint` | NO | - |
| `rule_code` | `character varying(64)` | NO | - |
| `rule_type` | `character varying(32)` | NO | - |
| `rule_version` | `character varying(32)` | NO | - |
| `config_json` | `jsonb` | NO | '{}'::jsonb |
| `review_status` | `character varying(32)` | NO | 'DRAFT'::character varying |
| `enabled` | `boolean` | NO | true |
| `sort_no` | `integer` | NO | 0 |
| `created_at` | `timestamp without time zone` | NO | CURRENT_TIMESTAMP |
| `updated_at` | `timestamp without time zone` | NO | CURRENT_TIMESTAMP |

约束：

- `ck_psy_scale_validity_rule_review` (CHECK) `CHECK (((review_status)::text = ANY ((ARRAY['DRAFT'::character varying, 'PENDING_REVIEW'::character varying, 'APPROVED'::character varying, 'REJECTED'::character varying])::text[]))) NOT VALID`
- `ck_psy_scale_validity_rule_type` (CHECK) `CHECK (((rule_type)::text = ANY ((ARRAY['CONSISTENCY'::character varying, 'CONTRADICTION'::character varying, 'DURATION'::character varying, 'RESPONSE_PATTERN'::character varying, 'CUSTOM_EXTENSION'::character varying])::text[]))) NOT VALID`
- `psy_scale_validity_rule_scale_id_fkey` (FOREIGN KEY) `FOREIGN KEY (scale_id) REFERENCES psy_scale(id) ON DELETE CASCADE`
- `psy_scale_validity_rule_config_json_not_null` (NOT NULL) `NOT NULL config_json`
- `psy_scale_validity_rule_created_at_not_null` (NOT NULL) `NOT NULL created_at`
- `psy_scale_validity_rule_enabled_not_null` (NOT NULL) `NOT NULL enabled`
- `psy_scale_validity_rule_id_not_null` (NOT NULL) `NOT NULL id`
- `psy_scale_validity_rule_review_status_not_null` (NOT NULL) `NOT NULL review_status`
- `psy_scale_validity_rule_rule_code_not_null` (NOT NULL) `NOT NULL rule_code`
- `psy_scale_validity_rule_rule_type_not_null` (NOT NULL) `NOT NULL rule_type`
- `psy_scale_validity_rule_rule_version_not_null` (NOT NULL) `NOT NULL rule_version`
- `psy_scale_validity_rule_scale_id_not_null` (NOT NULL) `NOT NULL scale_id`
- `psy_scale_validity_rule_sort_no_not_null` (NOT NULL) `NOT NULL sort_no`
- `psy_scale_validity_rule_updated_at_not_null` (NOT NULL) `NOT NULL updated_at`
- `psy_scale_validity_rule_pkey` (PRIMARY KEY) `PRIMARY KEY (id)`
- `psy_scale_validity_rule_scale_id_rule_code_rule_version_key` (UNIQUE) `UNIQUE (scale_id, rule_code, rule_version)`

索引：

- `idx_psy_scale_validity_rule_scale`：`CREATE INDEX idx_psy_scale_validity_rule_scale ON public.psy_scale_validity_rule USING btree (scale_id, enabled, sort_no)`
- `psy_scale_validity_rule_pkey`：`CREATE UNIQUE INDEX psy_scale_validity_rule_pkey ON public.psy_scale_validity_rule USING btree (id)`
- `psy_scale_validity_rule_scale_id_rule_code_rule_version_key`：`CREATE UNIQUE INDEX psy_scale_validity_rule_scale_id_rule_code_rule_version_key ON public.psy_scale_validity_rule USING btree (scale_id, rule_code, rule_version)`

#### `psy_scale_visualization_config`

| 列 | 类型 | 可空 | 默认值 |
| --- | --- | --- | --- |
| `id` | `bigint` | NO | nextval('psy_scale_visualization_config_id_seq'::regclass) |
| `scale_id` | `bigint` | NO | - |
| `chart_type` | `character varying(64)` | NO | - |
| `chart_title` | `character varying(128)` | NO | - |
| `view_scope` | `character varying(64)` | NO | - |
| `data_source` | `character varying(64)` | NO | - |
| `config_json` | `jsonb` | NO | '{}'::jsonb |
| `enabled` | `boolean` | NO | true |
| `sort_no` | `integer` | NO | 0 |
| `created_at` | `timestamp without time zone` | NO | CURRENT_TIMESTAMP |
| `updated_at` | `timestamp without time zone` | NO | CURRENT_TIMESTAMP |

约束：

- `psy_scale_visualization_config_scale_id_fkey` (FOREIGN KEY) `FOREIGN KEY (scale_id) REFERENCES psy_scale(id)`
- `psy_scale_visualization_config_chart_title_not_null` (NOT NULL) `NOT NULL chart_title`
- `psy_scale_visualization_config_chart_type_not_null` (NOT NULL) `NOT NULL chart_type`
- `psy_scale_visualization_config_config_json_not_null` (NOT NULL) `NOT NULL config_json`
- `psy_scale_visualization_config_created_at_not_null` (NOT NULL) `NOT NULL created_at`
- `psy_scale_visualization_config_data_source_not_null` (NOT NULL) `NOT NULL data_source`
- `psy_scale_visualization_config_enabled_not_null` (NOT NULL) `NOT NULL enabled`
- `psy_scale_visualization_config_id_not_null` (NOT NULL) `NOT NULL id`
- `psy_scale_visualization_config_scale_id_not_null` (NOT NULL) `NOT NULL scale_id`
- `psy_scale_visualization_config_sort_no_not_null` (NOT NULL) `NOT NULL sort_no`
- `psy_scale_visualization_config_updated_at_not_null` (NOT NULL) `NOT NULL updated_at`
- `psy_scale_visualization_config_view_scope_not_null` (NOT NULL) `NOT NULL view_scope`
- `psy_scale_visualization_config_pkey` (PRIMARY KEY) `PRIMARY KEY (id)`

索引：

- `idx_psy_scale_viz_scale_scope`：`CREATE INDEX idx_psy_scale_viz_scale_scope ON public.psy_scale_visualization_config USING btree (scale_id, view_scope, enabled)`
- `psy_scale_visualization_config_pkey`：`CREATE UNIQUE INDEX psy_scale_visualization_config_pkey ON public.psy_scale_visualization_config USING btree (id)`

### 4.6 预约与咨询

#### `psy_appointment_record`

| 列 | 类型 | 可空 | 默认值 |
| --- | --- | --- | --- |
| `id` | `bigint` | NO | nextval('psy_appointment_record_id_seq'::regclass) |
| `user_id` | `bigint` | NO | - |
| `counselor_user_id` | `bigint` | NO | - |
| `warning_id` | `bigint` | YES | - |
| `schedule_id` | `bigint` | YES | - |
| `appointment_status` | `character varying(32)` | NO | 'CREATED'::character varying |
| `source_type` | `character varying(32)` | NO | - |
| `remark` | `text` | YES | - |
| `created_at` | `timestamp without time zone` | NO | CURRENT_TIMESTAMP |
| `updated_at` | `timestamp without time zone` | NO | CURRENT_TIMESTAMP |
| `tenant_id` | `bigint` | NO | - |

约束：

- `ck_psy_appointment_source` (CHECK) `CHECK (((source_type)::text = ANY ((ARRAY['USER'::character varying, 'ADMIN'::character varying])::text[]))) NOT VALID`
- `ck_psy_appointment_status` (CHECK) `CHECK (((appointment_status)::text = ANY ((ARRAY['CREATED'::character varying, 'CONFIRMED'::character varying, 'CANCELLED'::character varying, 'COMPLETED'::character varying, 'NO_SHOW'::character varying])::text[]))) NOT VALID`
- `ck_psy_appointment_tenant_required` (CHECK) `CHECK ((tenant_id IS NOT NULL))`
- `fk_psy_appointment_tenant` (FOREIGN KEY) `FOREIGN KEY (tenant_id) REFERENCES sys_tenant(id)`
- `psy_appointment_record_counselor_user_id_fkey` (FOREIGN KEY) `FOREIGN KEY (counselor_user_id) REFERENCES sys_user(id)`
- `psy_appointment_record_schedule_id_fkey` (FOREIGN KEY) `FOREIGN KEY (schedule_id) REFERENCES psy_counselor_schedule(id)`
- `psy_appointment_record_user_id_fkey` (FOREIGN KEY) `FOREIGN KEY (user_id) REFERENCES sys_user(id)`
- `psy_appointment_record_warning_id_fkey` (FOREIGN KEY) `FOREIGN KEY (warning_id) REFERENCES psy_warning_record(id)`
- `psy_appointment_record_appointment_status_not_null` (NOT NULL) `NOT NULL appointment_status`
- `psy_appointment_record_counselor_user_id_not_null` (NOT NULL) `NOT NULL counselor_user_id`
- `psy_appointment_record_created_at_not_null` (NOT NULL) `NOT NULL created_at`
- `psy_appointment_record_id_not_null` (NOT NULL) `NOT NULL id`
- `psy_appointment_record_source_type_not_null` (NOT NULL) `NOT NULL source_type`
- `psy_appointment_record_tenant_id_not_null` (NOT NULL) `NOT NULL tenant_id`
- `psy_appointment_record_updated_at_not_null` (NOT NULL) `NOT NULL updated_at`
- `psy_appointment_record_user_id_not_null` (NOT NULL) `NOT NULL user_id`
- `psy_appointment_record_pkey` (PRIMARY KEY) `PRIMARY KEY (id)`

索引：

- `idx_psy_appointment_record_counselor_id`：`CREATE INDEX idx_psy_appointment_record_counselor_id ON public.psy_appointment_record USING btree (counselor_user_id)`
- `idx_psy_appointment_record_schedule_id`：`CREATE INDEX idx_psy_appointment_record_schedule_id ON public.psy_appointment_record USING btree (schedule_id)`
- `idx_psy_appointment_record_user_id`：`CREATE INDEX idx_psy_appointment_record_user_id ON public.psy_appointment_record USING btree (user_id)`
- `idx_psy_appointment_tenant_status`：`CREATE INDEX idx_psy_appointment_tenant_status ON public.psy_appointment_record USING btree (tenant_id, appointment_status, id)`
- `psy_appointment_record_pkey`：`CREATE UNIQUE INDEX psy_appointment_record_pkey ON public.psy_appointment_record USING btree (id)`

#### `psy_counseling_record`

| 列 | 类型 | 可空 | 默认值 |
| --- | --- | --- | --- |
| `id` | `bigint` | NO | nextval('psy_counseling_record_id_seq'::regclass) |
| `appointment_id` | `bigint` | NO | - |
| `counselor_user_id` | `bigint` | NO | - |
| `summary_text` | `text` | YES | - |
| `suggestion_text` | `text` | YES | - |
| `need_retest_flag` | `boolean` | NO | false |
| `need_transfer_flag` | `boolean` | NO | false |
| `created_at` | `timestamp without time zone` | NO | CURRENT_TIMESTAMP |
| `updated_at` | `timestamp without time zone` | NO | CURRENT_TIMESTAMP |
| `tenant_id` | `bigint` | NO | - |

约束：

- `ck_psy_counseling_tenant_required` (CHECK) `CHECK ((tenant_id IS NOT NULL))`
- `fk_psy_counseling_tenant` (FOREIGN KEY) `FOREIGN KEY (tenant_id) REFERENCES sys_tenant(id)`
- `psy_counseling_record_appointment_id_fkey` (FOREIGN KEY) `FOREIGN KEY (appointment_id) REFERENCES psy_appointment_record(id)`
- `psy_counseling_record_counselor_user_id_fkey` (FOREIGN KEY) `FOREIGN KEY (counselor_user_id) REFERENCES sys_user(id)`
- `psy_counseling_record_appointment_id_not_null` (NOT NULL) `NOT NULL appointment_id`
- `psy_counseling_record_counselor_user_id_not_null` (NOT NULL) `NOT NULL counselor_user_id`
- `psy_counseling_record_created_at_not_null` (NOT NULL) `NOT NULL created_at`
- `psy_counseling_record_id_not_null` (NOT NULL) `NOT NULL id`
- `psy_counseling_record_need_retest_flag_not_null` (NOT NULL) `NOT NULL need_retest_flag`
- `psy_counseling_record_need_transfer_flag_not_null` (NOT NULL) `NOT NULL need_transfer_flag`
- `psy_counseling_record_tenant_id_not_null` (NOT NULL) `NOT NULL tenant_id`
- `psy_counseling_record_updated_at_not_null` (NOT NULL) `NOT NULL updated_at`
- `psy_counseling_record_pkey` (PRIMARY KEY) `PRIMARY KEY (id)`

索引：

- `idx_psy_counseling_record_appointment_id`：`CREATE INDEX idx_psy_counseling_record_appointment_id ON public.psy_counseling_record USING btree (appointment_id)`
- `idx_psy_counseling_record_counselor_id`：`CREATE INDEX idx_psy_counseling_record_counselor_id ON public.psy_counseling_record USING btree (counselor_user_id)`
- `psy_counseling_record_pkey`：`CREATE UNIQUE INDEX psy_counseling_record_pkey ON public.psy_counseling_record USING btree (id)`

#### `psy_counselor_schedule`

| 列 | 类型 | 可空 | 默认值 |
| --- | --- | --- | --- |
| `id` | `bigint` | NO | nextval('psy_counselor_schedule_id_seq'::regclass) |
| `counselor_user_id` | `bigint` | NO | - |
| `schedule_date` | `date` | NO | - |
| `start_time` | `timestamp without time zone` | NO | - |
| `end_time` | `timestamp without time zone` | NO | - |
| `quota_count` | `integer` | NO | 1 |
| `status` | `character varying(32)` | NO | - |
| `created_at` | `timestamp without time zone` | NO | CURRENT_TIMESTAMP |
| `tenant_id` | `bigint` | NO | - |

约束：

- `ck_psy_counselor_schedule_quota` (CHECK) `CHECK ((quota_count > 0)) NOT VALID`
- `ck_psy_counselor_schedule_status` (CHECK) `CHECK (((status)::text = ANY ((ARRAY['AVAILABLE'::character varying, 'CLOSED'::character varying])::text[]))) NOT VALID`
- `ck_psy_counselor_schedule_time_range` (CHECK) `CHECK ((end_time > start_time)) NOT VALID`
- `ck_psy_schedule_tenant_required` (CHECK) `CHECK ((tenant_id IS NOT NULL))`
- `fk_psy_schedule_tenant` (FOREIGN KEY) `FOREIGN KEY (tenant_id) REFERENCES sys_tenant(id)`
- `psy_counselor_schedule_counselor_user_id_fkey` (FOREIGN KEY) `FOREIGN KEY (counselor_user_id) REFERENCES sys_user(id)`
- `psy_counselor_schedule_counselor_user_id_not_null` (NOT NULL) `NOT NULL counselor_user_id`
- `psy_counselor_schedule_created_at_not_null` (NOT NULL) `NOT NULL created_at`
- `psy_counselor_schedule_end_time_not_null` (NOT NULL) `NOT NULL end_time`
- `psy_counselor_schedule_id_not_null` (NOT NULL) `NOT NULL id`
- `psy_counselor_schedule_quota_count_not_null` (NOT NULL) `NOT NULL quota_count`
- `psy_counselor_schedule_schedule_date_not_null` (NOT NULL) `NOT NULL schedule_date`
- `psy_counselor_schedule_start_time_not_null` (NOT NULL) `NOT NULL start_time`
- `psy_counselor_schedule_status_not_null` (NOT NULL) `NOT NULL status`
- `psy_counselor_schedule_tenant_id_not_null` (NOT NULL) `NOT NULL tenant_id`
- `psy_counselor_schedule_pkey` (PRIMARY KEY) `PRIMARY KEY (id)`

索引：

- `idx_psy_counselor_schedule_counselor_id`：`CREATE INDEX idx_psy_counselor_schedule_counselor_id ON public.psy_counselor_schedule USING btree (counselor_user_id)`
- `idx_psy_counselor_schedule_status`：`CREATE INDEX idx_psy_counselor_schedule_status ON public.psy_counselor_schedule USING btree (status)`
- `idx_psy_schedule_tenant_date`：`CREATE INDEX idx_psy_schedule_tenant_date ON public.psy_counselor_schedule USING btree (tenant_id, schedule_date, id)`
- `psy_counselor_schedule_pkey`：`CREATE UNIQUE INDEX psy_counselor_schedule_pkey ON public.psy_counselor_schedule USING btree (id)`

### 4.7 预警、干预与安全响应

#### `psy_intervention_record`

| 列 | 类型 | 可空 | 默认值 |
| --- | --- | --- | --- |
| `id` | `bigint` | NO | nextval('psy_intervention_record_id_seq'::regclass) |
| `warning_id` | `bigint` | NO | - |
| `counselor_user_id` | `bigint` | YES | - |
| `current_status` | `character varying(32)` | NO | - |
| `plan_text` | `text` | YES | - |
| `close_summary` | `text` | YES | - |
| `need_retest_flag` | `boolean` | NO | false |
| `retest_task_id` | `bigint` | YES | - |
| `created_at` | `timestamp without time zone` | NO | CURRENT_TIMESTAMP |
| `updated_at` | `timestamp without time zone` | NO | CURRENT_TIMESTAMP |
| `tenant_id` | `bigint` | NO | - |

约束：

- `ck_psy_intervention_status` (CHECK) `CHECK (((current_status)::text = ANY ((ARRAY['PROCESSING'::character varying, 'CLOSED'::character varying])::text[]))) NOT VALID`
- `ck_psy_intervention_tenant_required` (CHECK) `CHECK ((tenant_id IS NOT NULL))`
- `fk_psy_intervention_tenant` (FOREIGN KEY) `FOREIGN KEY (tenant_id) REFERENCES sys_tenant(id)`
- `psy_intervention_record_counselor_user_id_fkey` (FOREIGN KEY) `FOREIGN KEY (counselor_user_id) REFERENCES sys_user(id)`
- `psy_intervention_record_retest_task_id_fkey` (FOREIGN KEY) `FOREIGN KEY (retest_task_id) REFERENCES psy_assessment_task(id)`
- `psy_intervention_record_warning_id_fkey` (FOREIGN KEY) `FOREIGN KEY (warning_id) REFERENCES psy_warning_record(id)`
- `psy_intervention_record_created_at_not_null` (NOT NULL) `NOT NULL created_at`
- `psy_intervention_record_current_status_not_null` (NOT NULL) `NOT NULL current_status`
- `psy_intervention_record_id_not_null` (NOT NULL) `NOT NULL id`
- `psy_intervention_record_need_retest_flag_not_null` (NOT NULL) `NOT NULL need_retest_flag`
- `psy_intervention_record_tenant_id_not_null` (NOT NULL) `NOT NULL tenant_id`
- `psy_intervention_record_updated_at_not_null` (NOT NULL) `NOT NULL updated_at`
- `psy_intervention_record_warning_id_not_null` (NOT NULL) `NOT NULL warning_id`
- `psy_intervention_record_pkey` (PRIMARY KEY) `PRIMARY KEY (id)`

索引：

- `idx_psy_intervention_record_counselor_id`：`CREATE INDEX idx_psy_intervention_record_counselor_id ON public.psy_intervention_record USING btree (counselor_user_id)`
- `idx_psy_intervention_record_status`：`CREATE INDEX idx_psy_intervention_record_status ON public.psy_intervention_record USING btree (current_status)`
- `idx_psy_intervention_record_warning_id`：`CREATE INDEX idx_psy_intervention_record_warning_id ON public.psy_intervention_record USING btree (warning_id)`
- `idx_psy_intervention_tenant_status`：`CREATE INDEX idx_psy_intervention_tenant_status ON public.psy_intervention_record USING btree (tenant_id, current_status, id)`
- `psy_intervention_record_pkey`：`CREATE UNIQUE INDEX psy_intervention_record_pkey ON public.psy_intervention_record USING btree (id)`

#### `psy_intervention_status_log`

| 列 | 类型 | 可空 | 默认值 |
| --- | --- | --- | --- |
| `id` | `bigint` | NO | nextval('psy_intervention_status_log_id_seq'::regclass) |
| `intervention_id` | `bigint` | NO | - |
| `from_status` | `character varying(32)` | YES | - |
| `to_status` | `character varying(32)` | NO | - |
| `remark` | `text` | YES | - |
| `changed_by` | `bigint` | YES | - |
| `changed_at` | `timestamp without time zone` | NO | CURRENT_TIMESTAMP |
| `tenant_id` | `bigint` | NO | - |

约束：

- `ck_psy_intervention_log_tenant_required` (CHECK) `CHECK ((tenant_id IS NOT NULL))`
- `fk_psy_intervention_log_tenant` (FOREIGN KEY) `FOREIGN KEY (tenant_id) REFERENCES sys_tenant(id)`
- `psy_intervention_status_log_changed_by_fkey` (FOREIGN KEY) `FOREIGN KEY (changed_by) REFERENCES sys_user(id)`
- `psy_intervention_status_log_intervention_id_fkey` (FOREIGN KEY) `FOREIGN KEY (intervention_id) REFERENCES psy_intervention_record(id)`
- `psy_intervention_status_log_changed_at_not_null` (NOT NULL) `NOT NULL changed_at`
- `psy_intervention_status_log_id_not_null` (NOT NULL) `NOT NULL id`
- `psy_intervention_status_log_intervention_id_not_null` (NOT NULL) `NOT NULL intervention_id`
- `psy_intervention_status_log_tenant_id_not_null` (NOT NULL) `NOT NULL tenant_id`
- `psy_intervention_status_log_to_status_not_null` (NOT NULL) `NOT NULL to_status`
- `psy_intervention_status_log_pkey` (PRIMARY KEY) `PRIMARY KEY (id)`

索引：

- `idx_psy_intervention_status_log_intervention_id`：`CREATE INDEX idx_psy_intervention_status_log_intervention_id ON public.psy_intervention_status_log USING btree (intervention_id)`
- `psy_intervention_status_log_pkey`：`CREATE UNIQUE INDEX psy_intervention_status_log_pkey ON public.psy_intervention_status_log USING btree (id)`

#### `psy_safety_response_policy`

| 列 | 类型 | 可空 | 默认值 |
| --- | --- | --- | --- |
| `id` | `bigint` | NO | nextval('psy_safety_response_policy_id_seq'::regclass) |
| `tenant_id` | `bigint` | YES | - |
| `policy_code` | `character varying(64)` | NO | - |
| `version_no` | `integer` | NO | - |
| `risk_category` | `character varying(16)` | NO | - |
| `first_response_minutes` | `integer` | NO | - |
| `escalation_minutes` | `integer` | NO | - |
| `follow_up_minutes` | `integer` | YES | - |
| `responsible_role` | `character varying(64)` | NO | - |
| `backup_role` | `character varying(64)` | NO | - |
| `emergency_contact_text` | `text` | NO | - |
| `status` | `character varying(16)` | NO | 'DRAFT'::character varying |
| `active_flag` | `boolean` | NO | false |
| `approved_by` | `bigint` | YES | - |
| `professional_reviewer_id` | `bigint` | YES | - |
| `approved_at` | `timestamp without time zone` | YES | - |
| `created_by` | `bigint` | YES | - |
| `created_at` | `timestamp without time zone` | NO | CURRENT_TIMESTAMP |
| `updated_at` | `timestamp without time zone` | NO | CURRENT_TIMESTAMP |
| `professional_reviewed_at` | `timestamp without time zone` | YES | - |

约束：

- `ck_psy_safety_policy_activation` (CHECK) `CHECK (((active_flag = false) OR (((status)::text = 'APPROVED'::text) AND (approved_by IS NOT NULL) AND (professional_reviewer_id IS NOT NULL) AND (professional_reviewed_at IS NOT NULL) AND (approved_at IS NOT NULL)))) NOT VALID`
- `ck_psy_safety_policy_minutes` (CHECK) `CHECK (((first_response_minutes > 0) AND (escalation_minutes >= first_response_minutes) AND ((follow_up_minutes IS NULL) OR (follow_up_minutes > 0)))) NOT VALID`
- `ck_psy_safety_policy_professional_review` (CHECK) `CHECK ((((professional_reviewer_id IS NULL) AND (professional_reviewed_at IS NULL)) OR ((professional_reviewer_id IS NOT NULL) AND (professional_reviewed_at IS NOT NULL)))) NOT VALID`
- `ck_psy_safety_policy_risk` (CHECK) `CHECK (((risk_category)::text = ANY ((ARRAY['P0'::character varying, 'P1'::character varying, 'P2'::character varying, 'P3'::character varying])::text[]))) NOT VALID`
- `ck_psy_safety_policy_status` (CHECK) `CHECK (((status)::text = ANY ((ARRAY['DRAFT'::character varying, 'APPROVED'::character varying, 'RETIRED'::character varying])::text[]))) NOT VALID`
- `psy_safety_response_policy_approved_by_fkey` (FOREIGN KEY) `FOREIGN KEY (approved_by) REFERENCES sys_user(id)`
- `psy_safety_response_policy_created_by_fkey` (FOREIGN KEY) `FOREIGN KEY (created_by) REFERENCES sys_user(id)`
- `psy_safety_response_policy_professional_reviewer_id_fkey` (FOREIGN KEY) `FOREIGN KEY (professional_reviewer_id) REFERENCES sys_user(id)`
- `psy_safety_response_policy_tenant_id_fkey` (FOREIGN KEY) `FOREIGN KEY (tenant_id) REFERENCES sys_tenant(id)`
- `psy_safety_response_policy_active_flag_not_null` (NOT NULL) `NOT NULL active_flag`
- `psy_safety_response_policy_backup_role_not_null` (NOT NULL) `NOT NULL backup_role`
- `psy_safety_response_policy_created_at_not_null` (NOT NULL) `NOT NULL created_at`
- `psy_safety_response_policy_emergency_contact_text_not_null` (NOT NULL) `NOT NULL emergency_contact_text`
- `psy_safety_response_policy_escalation_minutes_not_null` (NOT NULL) `NOT NULL escalation_minutes`
- `psy_safety_response_policy_first_response_minutes_not_null` (NOT NULL) `NOT NULL first_response_minutes`
- `psy_safety_response_policy_id_not_null` (NOT NULL) `NOT NULL id`
- `psy_safety_response_policy_policy_code_not_null` (NOT NULL) `NOT NULL policy_code`
- `psy_safety_response_policy_responsible_role_not_null` (NOT NULL) `NOT NULL responsible_role`
- `psy_safety_response_policy_risk_category_not_null` (NOT NULL) `NOT NULL risk_category`
- `psy_safety_response_policy_status_not_null` (NOT NULL) `NOT NULL status`
- `psy_safety_response_policy_updated_at_not_null` (NOT NULL) `NOT NULL updated_at`
- `psy_safety_response_policy_version_no_not_null` (NOT NULL) `NOT NULL version_no`
- `psy_safety_response_policy_pkey` (PRIMARY KEY) `PRIMARY KEY (id)`
- `psy_safety_response_policy_tenant_id_policy_code_version_no_key` (UNIQUE) `UNIQUE (tenant_id, policy_code, version_no)`

索引：

- `psy_safety_response_policy_pkey`：`CREATE UNIQUE INDEX psy_safety_response_policy_pkey ON public.psy_safety_response_policy USING btree (id)`
- `psy_safety_response_policy_tenant_id_policy_code_version_no_key`：`CREATE UNIQUE INDEX psy_safety_response_policy_tenant_id_policy_code_version_no_key ON public.psy_safety_response_policy USING btree (tenant_id, policy_code, version_no)`
- `uk_psy_safety_policy_active_global_risk`：`CREATE UNIQUE INDEX uk_psy_safety_policy_active_global_risk ON public.psy_safety_response_policy USING btree (risk_category) WHERE ((active_flag = true) AND (tenant_id IS NULL))`
- `uk_psy_safety_policy_active_tenant_risk`：`CREATE UNIQUE INDEX uk_psy_safety_policy_active_tenant_risk ON public.psy_safety_response_policy USING btree (tenant_id, risk_category) WHERE ((active_flag = true) AND (tenant_id IS NOT NULL))`
- `uk_psy_safety_policy_global_version`：`CREATE UNIQUE INDEX uk_psy_safety_policy_global_version ON public.psy_safety_response_policy USING btree (policy_code, version_no) WHERE (tenant_id IS NULL)`

#### `psy_warning_assignment`

| 列 | 类型 | 可空 | 默认值 |
| --- | --- | --- | --- |
| `id` | `bigint` | NO | nextval('psy_warning_assignment_id_seq'::regclass) |
| `warning_id` | `bigint` | NO | - |
| `assignee_user_id` | `bigint` | NO | - |
| `assigned_by` | `bigint` | YES | - |
| `assigned_at` | `timestamp without time zone` | NO | CURRENT_TIMESTAMP |
| `claim_time` | `timestamp without time zone` | YES | - |
| `tenant_id` | `bigint` | NO | - |

约束：

- `ck_psy_warning_assignment_tenant_required` (CHECK) `CHECK ((tenant_id IS NOT NULL))`
- `fk_psy_warning_assignment_tenant` (FOREIGN KEY) `FOREIGN KEY (tenant_id) REFERENCES sys_tenant(id)`
- `psy_warning_assignment_assignee_user_id_fkey` (FOREIGN KEY) `FOREIGN KEY (assignee_user_id) REFERENCES sys_user(id)`
- `psy_warning_assignment_warning_id_fkey` (FOREIGN KEY) `FOREIGN KEY (warning_id) REFERENCES psy_warning_record(id)`
- `psy_warning_assignment_assigned_at_not_null` (NOT NULL) `NOT NULL assigned_at`
- `psy_warning_assignment_assignee_user_id_not_null` (NOT NULL) `NOT NULL assignee_user_id`
- `psy_warning_assignment_id_not_null` (NOT NULL) `NOT NULL id`
- `psy_warning_assignment_tenant_id_not_null` (NOT NULL) `NOT NULL tenant_id`
- `psy_warning_assignment_warning_id_not_null` (NOT NULL) `NOT NULL warning_id`
- `psy_warning_assignment_pkey` (PRIMARY KEY) `PRIMARY KEY (id)`

索引：

- `idx_psy_warning_assignment_assignee_user_id`：`CREATE INDEX idx_psy_warning_assignment_assignee_user_id ON public.psy_warning_assignment USING btree (assignee_user_id)`
- `idx_psy_warning_assignment_warning_id`：`CREATE INDEX idx_psy_warning_assignment_warning_id ON public.psy_warning_assignment USING btree (warning_id)`
- `psy_warning_assignment_pkey`：`CREATE UNIQUE INDEX psy_warning_assignment_pkey ON public.psy_warning_assignment USING btree (id)`

#### `psy_warning_close_checklist`

| 列 | 类型 | 可空 | 默认值 |
| --- | --- | --- | --- |
| `id` | `bigint` | NO | nextval('psy_warning_close_checklist_id_seq'::regclass) |
| `tenant_id` | `bigint` | NO | - |
| `warning_id` | `bigint` | NO | - |
| `contact_attempt_recorded` | `boolean` | NO | - |
| `safety_assessment_completed` | `boolean` | NO | - |
| `responsible_handoff_completed` | `boolean` | NO | - |
| `follow_up_arranged` | `boolean` | NO | - |
| `closure_reason` | `text` | NO | - |
| `completed_by` | `bigint` | NO | - |
| `completed_at` | `timestamp without time zone` | NO | CURRENT_TIMESTAMP |

约束：

- `ck_psy_warning_close_checklist_complete` (CHECK) `CHECK ((contact_attempt_recorded AND safety_assessment_completed AND responsible_handoff_completed AND follow_up_arranged AND (length(TRIM(BOTH FROM closure_reason)) > 0))) NOT VALID`
- `psy_warning_close_checklist_completed_by_fkey` (FOREIGN KEY) `FOREIGN KEY (completed_by) REFERENCES sys_user(id)`
- `psy_warning_close_checklist_tenant_id_fkey` (FOREIGN KEY) `FOREIGN KEY (tenant_id) REFERENCES sys_tenant(id)`
- `psy_warning_close_checklist_warning_id_fkey` (FOREIGN KEY) `FOREIGN KEY (warning_id) REFERENCES psy_warning_record(id)`
- `psy_warning_close_checklist_closure_reason_not_null` (NOT NULL) `NOT NULL closure_reason`
- `psy_warning_close_checklist_completed_at_not_null` (NOT NULL) `NOT NULL completed_at`
- `psy_warning_close_checklist_completed_by_not_null` (NOT NULL) `NOT NULL completed_by`
- `psy_warning_close_checklist_contact_attempt_recorded_not_null` (NOT NULL) `NOT NULL contact_attempt_recorded`
- `psy_warning_close_checklist_follow_up_arranged_not_null` (NOT NULL) `NOT NULL follow_up_arranged`
- `psy_warning_close_checklist_id_not_null` (NOT NULL) `NOT NULL id`
- `psy_warning_close_checklist_responsible_handoff_comple_not_null` (NOT NULL) `NOT NULL responsible_handoff_completed`
- `psy_warning_close_checklist_safety_assessment_complete_not_null` (NOT NULL) `NOT NULL safety_assessment_completed`
- `psy_warning_close_checklist_tenant_id_not_null` (NOT NULL) `NOT NULL tenant_id`
- `psy_warning_close_checklist_warning_id_not_null` (NOT NULL) `NOT NULL warning_id`
- `psy_warning_close_checklist_pkey` (PRIMARY KEY) `PRIMARY KEY (id)`
- `psy_warning_close_checklist_warning_id_key` (UNIQUE) `UNIQUE (warning_id)`

索引：

- `psy_warning_close_checklist_pkey`：`CREATE UNIQUE INDEX psy_warning_close_checklist_pkey ON public.psy_warning_close_checklist USING btree (id)`
- `psy_warning_close_checklist_warning_id_key`：`CREATE UNIQUE INDEX psy_warning_close_checklist_warning_id_key ON public.psy_warning_close_checklist USING btree (warning_id)`

#### `psy_warning_follow_up`

| 列 | 类型 | 可空 | 默认值 |
| --- | --- | --- | --- |
| `id` | `bigint` | NO | nextval('psy_warning_follow_up_id_seq'::regclass) |
| `tenant_id` | `bigint` | NO | - |
| `warning_id` | `bigint` | NO | - |
| `due_time` | `timestamp without time zone` | NO | - |
| `status` | `character varying(16)` | NO | 'PENDING'::character varying |
| `follow_up_summary` | `text` | YES | - |
| `completed_by` | `bigint` | YES | - |
| `completed_at` | `timestamp without time zone` | YES | - |
| `created_by` | `bigint` | NO | - |
| `created_at` | `timestamp without time zone` | NO | CURRENT_TIMESTAMP |
| `updated_at` | `timestamp without time zone` | NO | CURRENT_TIMESTAMP |

约束：

- `ck_psy_warning_follow_up_completion` (CHECK) `CHECK ((((status)::text <> 'COMPLETED'::text) OR ((completed_by IS NOT NULL) AND (completed_at IS NOT NULL) AND (follow_up_summary IS NOT NULL)))) NOT VALID`
- `ck_psy_warning_follow_up_status` (CHECK) `CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'COMPLETED'::character varying, 'CANCELLED'::character varying])::text[]))) NOT VALID`
- `psy_warning_follow_up_completed_by_fkey` (FOREIGN KEY) `FOREIGN KEY (completed_by) REFERENCES sys_user(id)`
- `psy_warning_follow_up_created_by_fkey` (FOREIGN KEY) `FOREIGN KEY (created_by) REFERENCES sys_user(id)`
- `psy_warning_follow_up_tenant_id_fkey` (FOREIGN KEY) `FOREIGN KEY (tenant_id) REFERENCES sys_tenant(id)`
- `psy_warning_follow_up_warning_id_fkey` (FOREIGN KEY) `FOREIGN KEY (warning_id) REFERENCES psy_warning_record(id)`
- `psy_warning_follow_up_created_at_not_null` (NOT NULL) `NOT NULL created_at`
- `psy_warning_follow_up_created_by_not_null` (NOT NULL) `NOT NULL created_by`
- `psy_warning_follow_up_due_time_not_null` (NOT NULL) `NOT NULL due_time`
- `psy_warning_follow_up_id_not_null` (NOT NULL) `NOT NULL id`
- `psy_warning_follow_up_status_not_null` (NOT NULL) `NOT NULL status`
- `psy_warning_follow_up_tenant_id_not_null` (NOT NULL) `NOT NULL tenant_id`
- `psy_warning_follow_up_updated_at_not_null` (NOT NULL) `NOT NULL updated_at`
- `psy_warning_follow_up_warning_id_not_null` (NOT NULL) `NOT NULL warning_id`
- `psy_warning_follow_up_pkey` (PRIMARY KEY) `PRIMARY KEY (id)`

索引：

- `idx_psy_warning_follow_up_due`：`CREATE INDEX idx_psy_warning_follow_up_due ON public.psy_warning_follow_up USING btree (status, due_time)`
- `psy_warning_follow_up_pkey`：`CREATE UNIQUE INDEX psy_warning_follow_up_pkey ON public.psy_warning_follow_up USING btree (id)`

#### `psy_warning_record`

| 列 | 类型 | 可空 | 默认值 |
| --- | --- | --- | --- |
| `id` | `bigint` | NO | nextval('psy_warning_record_id_seq'::regclass) |
| `result_id` | `bigint` | NO | - |
| `warning_level` | `character varying(32)` | NO | - |
| `warning_priority` | `character varying(32)` | NO | - |
| `warning_reason` | `text` | YES | - |
| `status` | `character varying(32)` | NO | - |
| `deadline_time` | `timestamp without time zone` | YES | - |
| `first_response_time` | `timestamp without time zone` | YES | - |
| `escalated_at` | `timestamp without time zone` | YES | - |
| `last_reminded_at` | `timestamp without time zone` | YES | - |
| `escalation_count` | `integer` | NO | 0 |
| `closed_time` | `timestamp without time zone` | YES | - |
| `created_at` | `timestamp without time zone` | NO | CURRENT_TIMESTAMP |
| `updated_at` | `timestamp without time zone` | NO | CURRENT_TIMESTAMP |
| `tenant_id` | `bigint` | NO | - |
| `safety_policy_id` | `bigint` | YES | - |
| `safety_policy_version` | `integer` | YES | - |
| `policy_resolution_status` | `character varying(16)` | NO | 'MISSING'::character varying |
| `safety_policy_snapshot` | `jsonb` | YES | - |

约束：

- `ck_psy_warning_escalation_count` (CHECK) `CHECK ((escalation_count >= 0)) NOT VALID`
- `ck_psy_warning_policy_resolution` (CHECK) `CHECK (((policy_resolution_status)::text = ANY ((ARRAY['RESOLVED'::character varying, 'MISSING'::character varying])::text[]))) NOT VALID`
- `ck_psy_warning_status` (CHECK) `CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'ASSIGNED'::character varying, 'PROCESSING'::character varying, 'CLOSED'::character varying])::text[]))) NOT VALID`
- `ck_psy_warning_tenant_required` (CHECK) `CHECK ((tenant_id IS NOT NULL))`
- `fk_psy_warning_safety_policy` (FOREIGN KEY) `FOREIGN KEY (safety_policy_id) REFERENCES psy_safety_response_policy(id) NOT VALID`
- `fk_psy_warning_tenant` (FOREIGN KEY) `FOREIGN KEY (tenant_id) REFERENCES sys_tenant(id)`
- `psy_warning_record_result_id_fkey` (FOREIGN KEY) `FOREIGN KEY (result_id) REFERENCES psy_assessment_result(id)`
- `psy_warning_record_created_at_not_null` (NOT NULL) `NOT NULL created_at`
- `psy_warning_record_escalation_count_not_null` (NOT NULL) `NOT NULL escalation_count`
- `psy_warning_record_id_not_null` (NOT NULL) `NOT NULL id`
- `psy_warning_record_policy_resolution_status_not_null` (NOT NULL) `NOT NULL policy_resolution_status`
- `psy_warning_record_result_id_not_null` (NOT NULL) `NOT NULL result_id`
- `psy_warning_record_status_not_null` (NOT NULL) `NOT NULL status`
- `psy_warning_record_tenant_id_not_null` (NOT NULL) `NOT NULL tenant_id`
- `psy_warning_record_updated_at_not_null` (NOT NULL) `NOT NULL updated_at`
- `psy_warning_record_warning_level_not_null` (NOT NULL) `NOT NULL warning_level`
- `psy_warning_record_warning_priority_not_null` (NOT NULL) `NOT NULL warning_priority`
- `psy_warning_record_pkey` (PRIMARY KEY) `PRIMARY KEY (id)`

索引：

- `idx_psy_warning_deadline_open`：`CREATE INDEX idx_psy_warning_deadline_open ON public.psy_warning_record USING btree (deadline_time, warning_priority) WHERE ((status)::text <> 'CLOSED'::text)`
- `idx_psy_warning_policy_resolution`：`CREATE INDEX idx_psy_warning_policy_resolution ON public.psy_warning_record USING btree (policy_resolution_status, status, created_at) WHERE ((status)::text <> 'CLOSED'::text)`
- `idx_psy_warning_record_escalation`：`CREATE INDEX idx_psy_warning_record_escalation ON public.psy_warning_record USING btree (warning_level, status, escalated_at)`
- `idx_psy_warning_record_result_id`：`CREATE INDEX idx_psy_warning_record_result_id ON public.psy_warning_record USING btree (result_id)`
- `idx_psy_warning_record_status`：`CREATE INDEX idx_psy_warning_record_status ON public.psy_warning_record USING btree (status)`
- `idx_psy_warning_tenant_status`：`CREATE INDEX idx_psy_warning_tenant_status ON public.psy_warning_record USING btree (tenant_id, status, id)`
- `psy_warning_record_pkey`：`CREATE UNIQUE INDEX psy_warning_record_pkey ON public.psy_warning_record USING btree (id)`
- `uk_psy_warning_result`：`CREATE UNIQUE INDEX uk_psy_warning_result ON public.psy_warning_record USING btree (result_id)`

#### `psy_warning_response_event`

| 列 | 类型 | 可空 | 默认值 |
| --- | --- | --- | --- |
| `id` | `bigint` | NO | nextval('psy_warning_response_event_id_seq'::regclass) |
| `tenant_id` | `bigint` | NO | - |
| `warning_id` | `bigint` | NO | - |
| `event_type` | `character varying(32)` | NO | - |
| `contact_channel` | `character varying(32)` | YES | - |
| `contact_outcome` | `text` | YES | - |
| `imminent_danger_flag` | `boolean` | YES | - |
| `summary` | `text` | NO | - |
| `next_action` | `text` | YES | - |
| `performed_by` | `bigint` | NO | - |
| `performed_at` | `timestamp without time zone` | NO | CURRENT_TIMESTAMP |
| `created_at` | `timestamp without time zone` | NO | CURRENT_TIMESTAMP |

约束：

- `ck_psy_warning_response_event_type` (CHECK) `CHECK (((event_type)::text = ANY ((ARRAY['CONTACT_ATTEMPT'::character varying, 'SAFETY_ASSESSMENT'::character varying, 'RESPONSIBLE_HANDOFF'::character varying, 'ESCALATION'::character varying, 'FOLLOW_UP'::character varying, 'CLOSURE_REVIEW'::character varying])::text[]))) NOT VALID`
- `psy_warning_response_event_performed_by_fkey` (FOREIGN KEY) `FOREIGN KEY (performed_by) REFERENCES sys_user(id)`
- `psy_warning_response_event_tenant_id_fkey` (FOREIGN KEY) `FOREIGN KEY (tenant_id) REFERENCES sys_tenant(id)`
- `psy_warning_response_event_warning_id_fkey` (FOREIGN KEY) `FOREIGN KEY (warning_id) REFERENCES psy_warning_record(id)`
- `psy_warning_response_event_created_at_not_null` (NOT NULL) `NOT NULL created_at`
- `psy_warning_response_event_event_type_not_null` (NOT NULL) `NOT NULL event_type`
- `psy_warning_response_event_id_not_null` (NOT NULL) `NOT NULL id`
- `psy_warning_response_event_performed_at_not_null` (NOT NULL) `NOT NULL performed_at`
- `psy_warning_response_event_performed_by_not_null` (NOT NULL) `NOT NULL performed_by`
- `psy_warning_response_event_summary_not_null` (NOT NULL) `NOT NULL summary`
- `psy_warning_response_event_tenant_id_not_null` (NOT NULL) `NOT NULL tenant_id`
- `psy_warning_response_event_warning_id_not_null` (NOT NULL) `NOT NULL warning_id`
- `psy_warning_response_event_pkey` (PRIMARY KEY) `PRIMARY KEY (id)`

索引：

- `idx_psy_warning_response_event_warning`：`CREATE INDEX idx_psy_warning_response_event_warning ON public.psy_warning_response_event USING btree (warning_id, performed_at, id)`
- `psy_warning_response_event_pkey`：`CREATE UNIQUE INDEX psy_warning_response_event_pkey ON public.psy_warning_response_event USING btree (id)`

## 5. 关键不变式（按当前约束与迁移语义）

1. 直接租户表必须显式携带 `tenant_id`；子表通过父表继承归属，跨租户访问必须审计。
2. `psy_assessment_result` 追加写入，不覆盖历史；每份答卷只有一个 `is_current=true` 结果。
3. `psy_safety_response_policy` 生效策略必须满足 `APPROVED` 且审批人与专业复核人不同。
4. ScalePackage 导入时授权、版权、治理状态强制为待审核/草稿，不能由源包声明直接推进。
5. 量表发布必须绑定当前内容指纹、Golden Case 运行证据和发布评审证据。
6. 导出与通知队列使用租约 + fencing；重试、退避、死信和人工重放均有状态约束。
7. 迁移集合只增不改；已执行迁移不得修改，结构变更必须新增版本。
