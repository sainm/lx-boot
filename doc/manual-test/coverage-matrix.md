# 手动测试覆盖矩阵（生成物）

> 由 `python3 scripts/manual_test/coverage_audit.py` 生成；请勿手工编辑。
> 判定口径：方法一致且路径逐段匹配；设计文档中的占位段（`{id}`）视为通配，因此用例实际请求 `/api/v1/tasks/12` 即视为覆盖 `/api/v1/tasks/{id}`；用例侧由变量拼出的 `*` 必须与占位段对齐，未实际请求的固定路径不会被计入。
> 接口总数 168，被 API 用例覆盖 168；其中自动执行 168、仅在手顺中声明 0；前端路由 23，在手顺中被引用 23。

## 1. 接口覆盖

| 方法 | 路径 | 自动用例 | 手顺用例 |
| --- | --- | --- | --- |
| GET | `/api/v1/appointments` | MT-API-002 | MT-API-002  |
| POST | `/api/v1/appointments` | MT-APPT-003, MT-APPT-006, MT-APPT-010 | —  |
| GET | `/api/v1/appointments/my` | MT-APPT-006 | —  |
| POST | `/api/v1/appointments/{id}/cancel` | MT-APPT-012 | —  |
| GET | `/api/v1/counselors` | MT-APPT-012, MT-SEC-018 | —  |
| POST | `/api/v1/counselors/me/schedules` | MT-APPT-003, MT-APPT-006, MT-APPT-007, MT-APPT-010 | —  |
| GET | `/api/v1/counselors/{id}/schedules` | MT-APPT-007 | —  |
| POST | `/api/v1/answer-sheets/save` | MT-NFR-003, MT-OPS-001, MT-OPS-002, MT-OPS-004… | —  |
| POST | `/api/v1/answer-sheets/submit` | MT-ANS-007, MT-ANS-009, MT-ANS-010, MT-ANS-011… | —  |
| GET | `/api/v1/my/tasks/{taskId}/questions` | MT-ANS-007, MT-ANS-009, MT-ANS-010, MT-ANS-011… | —  |
| POST | `/api/v1/results/{resultId}/rescore` | MT-SCORE-015 | MT-SCORE-015  |
| GET | `/api/v1/my/tasks` | MT-AUTH-040, MT-HOME-001, MT-HOME-002, MT-HOME-007 | —  |
| GET | `/api/v1/tasks` | MT-SEC-002, MT-TASK-014, MT-TASK-015 | —  |
| POST | `/api/v1/tasks` | MT-ANS-007, MT-ANS-009, MT-ANS-010, MT-ANS-011… | MT-SEC-003  |
| DELETE | `/api/v1/tasks/{id}` | MT-API-009, MT-TASK-010 | —  |
| GET | `/api/v1/tasks/{id}` | MT-SCALE-025, MT-SEC-005 | —  |
| POST | `/api/v1/tasks/{id}` | MT-API-009 | MT-API-009  |
| POST | `/api/v1/tasks/{id}/assign-groups` | MT-TASK-004 | —  |
| POST | `/api/v1/tasks/{id}/assign-users` | MT-ANS-007, MT-ANS-009, MT-ANS-010, MT-ANS-011… | —  |
| POST | `/api/v1/tasks/{id}/close` | MT-ANS-020, MT-TASK-005 | —  |
| POST | `/api/v1/counseling-records` | MT-APPT-008 | —  |
| GET | `/api/v1/directory/groups` | MT-API-001 | MT-API-001  |
| GET | `/api/v1/directory/scales` | MT-API-001 | MT-API-001  |
| GET | `/api/v1/directory/tasks` | MT-API-001 | MT-API-001  |
| GET | `/api/v1/directory/users` | MT-API-001 | MT-API-001  |
| POST | `/api/v1/exports/reports` | MT-API-005 | MT-API-005  |
| GET | `/api/v1/exports/reports/download` | MT-RPT-007, MT-RPT-010 | MT-EXP-001  |
| GET | `/api/v1/exports/reports/jobs` | MT-EXP-005 | —  |
| POST | `/api/v1/exports/reports/jobs` | MT-EXP-005, MT-EXP-008, MT-EXP-011, MT-EXP-012… | MT-EXP-003  |
| GET | `/api/v1/exports/reports/jobs/{jobId}` | MT-EXP-008, MT-NET-006, MT-SEC-008 | —  |
| GET | `/api/v1/exports/reports/jobs/{jobId}/download` | MT-API-006, MT-EXP-008, MT-NET-006 | MT-API-006  |
| POST | `/api/v1/exports/reports/jobs/{jobId}/retry` | MT-EXP-008, MT-NFR-004 | —  |
| GET | `/api/v1/exports/reports/storage` | MT-EXP-006, MT-EXP-008, MT-NET-006 | —  |
| POST | `/api/v1/interventions` | MT-WARN-007 | —  |
| POST | `/api/v1/interventions/{id}/close` | MT-WARN-007 | —  |
| GET | `/api/v1/my/notifications` | MT-API-007, MT-HOME-001, MT-HOME-008 | —  |
| POST | `/api/v1/my/notifications/deliveries/{deliveryId}/clicked` | MT-NOTI-011 | —  |
| POST | `/api/v1/my/notifications/deliveries/{deliveryId}/received` | MT-NOTI-011 | —  |
| POST | `/api/v1/my/notifications/{id}/read` | MT-API-007 | MT-API-007  |
| POST | `/api/v1/notifications/deliveries/retry-batch` | MT-NOTI-007 | —  |
| GET | `/api/v1/notifications/deliveries/summary` | MT-NOTI-008, MT-NOTI-013 | —  |
| POST | `/api/v1/notifications/deliveries/{deliveryId}/callbacks` | MT-NOTI-012 | MT-NOTI-012  |
| GET | `/api/v1/notifications/ops/feed` | MT-NOTI-014 | —  |
| GET | `/api/v1/notifications/policies` | MT-NOTI-008 | —  |
| POST | `/api/v1/notifications/policies` | MT-NOTI-008 | —  |
| GET | `/api/v1/notifications/{id}/deliveries` | MT-NOTI-005 | —  |
| POST | `/api/v1/notifications/{id}/deliveries/retry` | MT-NOTI-006, MT-NOTI-015 | —  |
| GET | `/api/v1/my/profile` | MT-SEC-020 | —  |
| POST | `/api/v1/my/profile` | MT-HOME-006, MT-SEC-020 | —  |
| GET | `/api/v1/reports` | MT-RPT-005, MT-RPT-014 | —  |
| GET | `/api/v1/reports/by-result/{resultId}` | MT-API-008 | MT-API-008  |
| GET | `/api/v1/reports/my` | MT-AUTH-040, MT-HOME-001, MT-HOME-002, MT-RPT-002… | MT-AUTH-040  |
| GET | `/api/v1/reports/users/{userId}` | MT-RPT-006 | MT-RPT-006  |
| GET | `/api/v1/reports/{id}` | MT-RPT-007, MT-RPT-009, MT-SEC-006, MT-SEC-018 | —  |
| POST | `/api/v1/reports/{id}/regenerate` | MT-RPT-008 | —  |
| GET | `/api/v1/scales` | MT-I18N-007, MT-SCALE-024, MT-SEC-002, MT-SEC-004… | MT-SEC-003  |
| POST | `/api/v1/scales` | MT-SCALE-023, MT-SCALE-024, MT-SCORE-006 | MT-SCALE-024  |
| DELETE | `/api/v1/scales/{id}` | MT-SCALE-023 | —  |
| GET | `/api/v1/scales/{id}` | MT-ANS-009, MT-ANS-010, MT-ANS-011, MT-ANS-012… | —  |
| POST | `/api/v1/scales/{id}/basic` | MT-SCALE-003, MT-SCALE-022 | —  |
| POST | `/api/v1/scales/{id}/dimensions/batch` | MT-API-014 | MT-API-014  |
| POST | `/api/v1/scales/{id}/dimensions/{dimensionId}` | MT-API-014 | MT-API-014  |
| GET | `/api/v1/scales/{id}/norm-coverage` | MT-SCALE-016 | —  |
| POST | `/api/v1/scales/{id}/norms/batch` | MT-SCALE-016 | —  |
| POST | `/api/v1/scales/{id}/options/{optionId}` | MT-SCALE-012 | —  |
| POST | `/api/v1/scales/{id}/publish` | MT-ANS-009, MT-ANS-010, MT-ANS-011, MT-ANS-012… | —  |
| POST | `/api/v1/scales/{id}/questions/batch` | MT-SCALE-006, MT-SCALE-008, MT-SCALE-009, MT-SCALE-010… | —  |
| POST | `/api/v1/scales/{id}/questions/{questionId}` | MT-PUB-007, MT-SCALE-007 | —  |
| POST | `/api/v1/scales/{id}/result-rules/batch` | MT-SCALE-013 | —  |
| GET | `/api/v1/scales/{id}/versions` | MT-SCALE-019 | —  |
| POST | `/api/v1/scales/{id}/versions` | MT-SCALE-018, MT-SCALE-025 | —  |
| GET | `/api/v1/scales/{id}/versions/{targetId}/diff` | MT-SCALE-019 | —  |
| POST | `/api/v1/scales/{id}/visualizations` | MT-PUB-006, MT-SCALE-017 | —  |
| GET | `/api/v1/scales/import-template` | MT-API-010 | MT-API-010  |
| GET | `/api/v1/scales/imports` | MT-IMP-009 | —  |
| POST | `/api/v1/scales/imports/package/preview` | MT-IMP-007, MT-IMP-008, MT-IMP-011, MT-SCORE-016 | —  |
| POST | `/api/v1/scales/imports/package/{id}/confirm` | MT-SCORE-016 | —  |
| POST | `/api/v1/scales/imports/parse` | MT-ANS-009, MT-ANS-010, MT-ANS-011, MT-ANS-012… | —  |
| GET | `/api/v1/scales/imports/{id}` | MT-ANS-009, MT-ANS-010, MT-ANS-011, MT-ANS-012… | —  |
| POST | `/api/v1/scales/imports/{id}/confirm` | MT-ANS-009, MT-ANS-010, MT-ANS-011, MT-ANS-012… | —  |
| GET | `/api/v1/scales/{scaleId}/package` | MT-API-011 | MT-API-011  |
| PUT | `/api/v1/scales/{scaleId}/package` | MT-ANS-009, MT-ANS-010, MT-ANS-011, MT-ANS-012… | MT-PUB-018  |
| GET | `/api/v1/scales/{scaleId}/package/export` | MT-IMP-013, MT-PUB-006 | —  |
| GET | `/api/v1/scales/{scaleId}/publication/golden-cases` | MT-SCORE-008, MT-SCORE-016 | —  |
| POST | `/api/v1/scales/{scaleId}/publication/golden-cases` | MT-ANS-009, MT-ANS-010, MT-ANS-011, MT-ANS-012… | —  |
| POST | `/api/v1/scales/{scaleId}/publication/golden-cases/{caseId}/approve` | MT-ANS-009, MT-ANS-010, MT-ANS-011, MT-ANS-012… | —  |
| POST | `/api/v1/scales/{scaleId}/publication/golden-cases/{caseId}/run` | MT-ANS-009, MT-ANS-010, MT-ANS-011, MT-ANS-012… | —  |
| GET | `/api/v1/scales/{scaleId}/publication/history` | MT-API-012 | MT-API-012  |
| GET | `/api/v1/scales/{scaleId}/publication/history/cases` | MT-PUB-014 | —  |
| GET | `/api/v1/scales/{scaleId}/publication/history/reviews` | MT-API-012 | —  |
| GET | `/api/v1/scales/{scaleId}/publication/history/runs` | MT-API-012 | —  |
| GET | `/api/v1/scales/{scaleId}/publication/readiness` | MT-ANS-009, MT-ANS-010, MT-ANS-011, MT-ANS-012… | —  |
| POST | `/api/v1/scales/{scaleId}/publication/reviews/{reviewType}` | MT-ANS-009, MT-ANS-010, MT-ANS-011, MT-ANS-012… | MT-API-013  |
| GET | `/api/v1/statistics/dashboard` | MT-API-004 | MT-API-004  |
| GET | `/api/v1/statistics/group-reports` | MT-STAT-005 | —  |
| GET | `/api/v1/statistics/group-reports/download` | MT-STAT-007 | —  |
| GET | `/api/v1/admin/external-registrations/pending` | MT-AUTH-023 | —  |
| POST | `/api/v1/admin/external-registrations/{userId}/approve` | MT-AUTH-024, MT-NOTI-016 | —  |
| POST | `/api/v1/admin/external-registrations/{userId}/reject` | MT-AUTH-025 | —  |
| GET | `/api/v1/user-admin/groups` | MT-AUTH-002, MT-AUTH-015, MT-AUTH-016, MT-AUTH-037… | —  |
| GET | `/api/v1/user-admin/roles` | MT-USER-009 | —  |
| GET | `/api/v1/user-admin/tenants` | MT-USER-007 | —  |
| GET | `/api/v1/user-admin/users` | MT-AUTH-009, MT-SEC-002, MT-USER-001, MT-USER-010 | MT-SEC-003  |
| POST | `/api/v1/user-admin/users` | MT-AUTH-002, MT-AUTH-015, MT-AUTH-016, MT-AUTH-037… | —  |
| POST | `/api/v1/user-admin/users/{userId}/password/reset` | MT-AUTH-016, MT-USER-006 | —  |
| POST | `/api/v1/user-admin/users/{userId}/roles` | MT-USER-004 | —  |
| POST | `/api/v1/user-admin/users/{userId}/status` | MT-USER-005 | —  |
| GET | `/api/v1/safety-response-policies` | MT-WARN-009 | —  |
| POST | `/api/v1/safety-response-policies` | MT-WARN-007 | —  |
| POST | `/api/v1/safety-response-policies/{id}/approve` | MT-WARN-007, MT-WARN-009 | —  |
| POST | `/api/v1/safety-response-policies/{id}/professional-review` | MT-WARN-007 | —  |
| GET | `/api/v1/warnings` | MT-API-003, MT-SEC-018, MT-WARN-001, MT-WARN-014… | MT-SEC-003  |
| GET | `/api/v1/warnings/assignee-options` | MT-API-003 | MT-API-003  |
| POST | `/api/v1/warnings/{id}/assign` | MT-API-003 | MT-API-003  |
| POST | `/api/v1/warnings/{id}/claim` | MT-SEC-007, MT-WARN-014, MT-WARN-015 | —  |
| POST | `/api/v1/warnings/{id}/policy-resolution` | MT-WARN-007 | —  |
| POST | `/api/v1/wechat/menu/sync` | MT-API-027 | MT-API-027, MT-NET-004  |
| GET | `/auth/admin/ping` | MT-API-019 | MT-API-019  |
| GET | `/auth/email-verify` | MT-API-022 | MT-API-022, MT-AUTH-022, MT-NET-002  |
| POST | `/auth/external-register` | MT-AUTH-020, MT-NET-002 | —  |
| POST | `/auth/external-register/resend` | MT-AUTH-021 | —  |
| GET | `/auth/groups` | MT-API-015 | MT-API-015, MT-AUTH-039  |
| POST | `/auth/groups` | MT-USER-011, MT-USER-012 | MT-USER-011  |
| POST | `/auth/groups/{groupId}/roles` | MT-USER-012 | MT-USER-012  |
| GET | `/auth/login-logs` | MT-AUTH-036, MT-SEC-003 | —  |
| POST | `/auth/login/password` | MT-AUTH-008, MT-AUTH-009 | —  |
| POST | `/auth/logout` | MT-AUTH-005 | —  |
| GET | `/auth/me` | MT-AUTH-001, MT-AUTH-004, MT-AUTH-005, MT-AUTH-009… | MT-AUTH-040  |
| GET | `/auth/me/devices` | MT-AUTH-031, MT-NOTI-009 | MT-AUTH-031  |
| POST | `/auth/me/devices` | MT-AUTH-032, MT-AUTH-038, MT-NET-005, MT-NOTI-009… | —  |
| POST | `/auth/me/devices/{deviceId}/deactivate` | MT-AUTH-033, MT-NOTI-010 | —  |
| GET | `/auth/me/login-activities` | MT-AUTH-034 | —  |
| GET | `/auth/me/security-events` | MT-AUTH-035 | —  |
| GET | `/auth/me/session-policy` | MT-AUTH-011 | —  |
| POST | `/auth/me/session-policy` | MT-AUTH-011, MT-AUTH-014 | —  |
| GET | `/auth/me/sessions` | MT-AUTH-010, MT-AUTH-012 | —  |
| POST | `/auth/me/sessions/revoke-others` | MT-AUTH-013 | —  |
| POST | `/auth/me/sessions/{sessionId}/revoke` | MT-AUTH-012 | —  |
| POST | `/auth/password/change` | MT-AUTH-015 | MT-AUTH-015  |
| POST | `/auth/password/reset` | MT-API-020 | MT-API-020  |
| GET | `/auth/permissions` | MT-API-015 | MT-API-015, MT-AUTH-039  |
| POST | `/auth/qr/cancel` | MT-API-021 | MT-API-021  |
| POST | `/auth/qr/confirm` | MT-AUTH-030 | —  |
| POST | `/auth/qr/scan` | MT-AUTH-030 | —  |
| POST | `/auth/qr/scene` | MT-AUTH-030 | MT-AUTH-030  |
| GET | `/auth/qr/scene/{sceneCode}` | MT-AUTH-030 | —  |
| POST | `/auth/register` | MT-AUTH-018, MT-AUTH-019 | MT-AUTH-019  |
| GET | `/auth/register/options` | MT-AUTH-017, MT-AUTH-018, MT-AUTH-019 | MT-AUTH-017  |
| GET | `/auth/roles` | MT-API-015 | MT-API-015, MT-AUTH-039  |
| GET | `/auth/security-events` | MT-API-018, MT-SEC-003 | MT-API-018  |
| POST | `/auth/social/google` | MT-AUTH-028 | —  |
| POST | `/auth/social/wechat` | MT-AUTH-028 | —  |
| POST | `/auth/sso/token` | MT-API-022, MT-NET-003, MT-SEC-014 | MT-API-022  |
| GET | `/auth/sso/{provider}/authorize` | MT-API-023, MT-AUTH-026 | MT-API-023, MT-AUTH-026  |
| GET | `/auth/sso/{provider}/callback` | MT-API-023 | MT-API-023  |
| GET | `/auth/tenants` | MT-API-015 | MT-API-015, MT-AUTH-039  |
| POST | `/auth/tenants` | MT-USER-011 | MT-USER-011  |
| POST | `/auth/token/refresh` | MT-AUTH-004 | —  |
| GET | `/auth/users` | MT-API-015 | MT-API-015  |
| GET | `/auth/users/{userId}/devices` | MT-API-017 | MT-API-017  |
| POST | `/auth/users/{userId}/devices/{deviceId}/deactivate` | MT-AUTH-038 | —  |
| POST | `/auth/users/{userId}/roles` | MT-API-024 | MT-API-024  |
| GET | `/auth/users/{userId}/sessions` | MT-API-016 | MT-API-016  |
| POST | `/auth/users/{userId}/sessions/revoke-all` | MT-AUTH-037 | —  |
| POST | `/auth/users/{userId}/sessions/{sessionId}/revoke` | MT-API-016 | MT-API-016  |
| POST | `/wechat/jssdk/config` | MT-API-025 | MT-API-025, MT-NET-004  |
| GET | `/wechat/portal` | MT-API-026 | MT-API-026  |
| POST | `/wechat/portal` | MT-API-026 | MT-API-026, MT-NET-004  |

## 2. 前端路由覆盖

| 路由 | 菜单 | 角色 | 手顺引用 |
| --- | --- | --- | --- |
| `/home` | 是 | USER | 是 |
| `/my/tasks` | 是 | USER | 是 |
| `/my/reports` | 是 | USER | 是 |
| `/my/profile` | 是 | USER | 是 |
| `/dashboard` | 是 | ASSESSMENT_ADMIN, COUNSELOR, ORG_MANAGER, SCHOOL_LEADER, SYS_ADMIN | 是 |
| `/scales` | 是 | ASSESSMENT_ADMIN, SYS_ADMIN | 是 |
| `/scale-publication` | 是 | COUNSELOR, ASSESSMENT_ADMIN, ORG_MANAGER, SYS_ADMIN | 是 |
| `/scale-governance` | 是 | ASSESSMENT_ADMIN, SYS_ADMIN | 是 |
| `/tasks` | 是 | ASSESSMENT_ADMIN, SYS_ADMIN | 是 |
| `/warnings` | 是 | ASSESSMENT_ADMIN, COUNSELOR, SYS_ADMIN | 是 |
| `/safety-response-policies` | 是 | ASSESSMENT_ADMIN, ORG_MANAGER, COUNSELOR, SYS_ADMIN | 是 |
| `/group-reports` | 是 | ASSESSMENT_ADMIN, COUNSELOR, ORG_MANAGER, SCHOOL_LEADER, SYS_ADMIN | 是 |
| `/user-reports` | 是 | ASSESSMENT_ADMIN, COUNSELOR, ORG_MANAGER, SYS_ADMIN | 是 |
| `/appointments` | 是 | USER, COUNSELOR, ASSESSMENT_ADMIN, SYS_ADMIN | 是 |
| `/exports-center` | 是 | ASSESSMENT_ADMIN, ORG_MANAGER, SYS_ADMIN | 是 |
| `/notifications` | 是 | USER, ASSESSMENT_ADMIN, COUNSELOR, ORG_MANAGER, SYS_ADMIN | 是 |
| `/user-admin` | 是 | ORG_MANAGER, SYS_ADMIN | 是 |
| `/pending-registrations` | 是 | ASSESSMENT_ADMIN, ORG_MANAGER, SYS_ADMIN | 是 |
| `/auth-audit` | 是 | ORG_MANAGER, SYS_ADMIN | 是 |
| `/session` | 是 | ASSESSMENT_ADMIN, COUNSELOR, ORG_MANAGER, SYS_ADMIN | 是 |
| `/reports` | 否 | USER, ASSESSMENT_ADMIN, COUNSELOR, ORG_MANAGER, SYS_ADMIN | 是 |
| `/reports/:reportId` | 否 | USER, ASSESSMENT_ADMIN, COUNSELOR, ORG_MANAGER, SYS_ADMIN | **否** |
| `/my/tasks/:taskId` | 否 | USER | **否** |
