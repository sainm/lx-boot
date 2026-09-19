# 接口详细设计（由代码生成）

## 1. 文档说明

本文档由 Kotlin 控制器源码直接生成，描述当前仓库实际暴露的 HTTP 契约；不再手工维护接口清单。

- 生成命令：`python3 scripts/generate_code_docs.py api`
- 生成时间：2026-09-19 22:41:10 CST
- 业务端点（本仓库）：**116** 条路径定义，来源 `backend/src/main/kotlin/**/api/*.kt`
- 认证端点（相邻 `auth-starter` 仓库）：**52** 条路径定义
- 权限列来自 `@PreAuthorize`；`未声明` 表示控制器方法依赖全局安全配置或仅需登录，需以安全配置为准。
- 请求参数仅列显式 `@PathVariable` / `@RequestParam` / `@RequestBody` / `@RequestHeader` / `@AuthenticationPrincipal` 绑定。

## 2. 通用约定

- 前缀：本仓库业务端点统一为 `/api/v1`；认证端点为 `/auth/**`。
- 成功响应：`{"code":"0","message":"OK","data":...}`；错误响应沿用同一信封并返回业务码。
- 分页参数：`page` / `size`；时间：ISO-8601（服务端时区 Asia/Shanghai）。

## 3. 业务端点（lx-boot）

| 模块 | 端点数 | 控制器 |
| --- | ---: | --- |
| appointment | 7 | AppointmentController |
| assessment | 13 | AnswerSheetController, AssessmentTaskController |
| counseling | 1 | CounselingRecordController |
| directory | 4 | DirectoryController |
| export | 8 | ExportController |
| intervention | 2 | InterventionController |
| notification | 12 | NotificationController, NotificationOpsController |
| profile | 2 | MyProfileController |
| report | 6 | ReportController |
| scale | 38 | ScaleController, ScaleImportController, ScalePackageController, ScalePublicationGovernanceController |
| statistics | 3 | StatisticsController |
| useradmin | 11 | ExternalRegistrationReviewController, UserAdminController |
| warning | 9 | SafetyResponsePolicyController, WarningController |

### 3.1 appointment

#### AppointmentController

- 基础路径：`/api/v1`
- 源码：`lx-boot/backend/src/main/kotlin/org/sainm/psy/appointment/api/AppointmentController.kt`

| 方法 | 路径 | 权限 | 处理器 | 参数 | 返回 |
| --- | --- | --- | --- | --- | --- |
| GET | `/api/v1/appointments` | COUNSELOR / ASSESSMENT_ADMIN / ORG_MANAGER / ADMIN / SYS_ADMIN / SUPER_ADMIN | `findPage` | `@RequestParam status: String`，否<br>`@RequestParam userId: Long`，否<br>`@RequestParam counselorUserId: Long`，否<br>`@RequestParam dateFrom: LocalDate`，否<br>`@RequestParam dateTo: LocalDate`，否<br>`@RequestParam page: Int`，默认值<br>`@RequestParam size: Int`，默认值 | `ApiResponse<PageResponse<AppointmentSummary>>` |
| POST | `/api/v1/appointments` | USER / COUNSELOR / ASSESSMENT_ADMIN / ORG_MANAGER / ADMIN / SYS_ADMIN / SUPER_ADMIN | `create` | `@RequestBody request: CreateAppointmentRequest`，是 | `ApiResponse<AppointmentCreateResponse>` |
| GET | `/api/v1/appointments/my` | 登录用户 | `findMyAppointments` | - | `ApiResponse<List<AppointmentSummary>>` |
| POST | `/api/v1/appointments/{id}/cancel` | 登录用户 | `cancel` | `@PathVariable id: Long`，是 | `ApiResponse<AppointmentActionResult>` |
| GET | `/api/v1/counselors` | 登录用户 | `findCounselors` | - | `ApiResponse<List<CounselorOptionResponse>>` |
| POST | `/api/v1/counselors/me/schedules` | COUNSELOR / ASSESSMENT_ADMIN / ORG_MANAGER / ADMIN / SYS_ADMIN / SUPER_ADMIN | `createSchedule` | `@RequestBody request: CreateScheduleRequest`，是 | `ApiResponse<CreateScheduleResponse>` |
| GET | `/api/v1/counselors/{id}/schedules` | 登录用户 | `findSchedules` | `@PathVariable id: Long`，是 | `ApiResponse<List<org.sainm.psy.appointment.domain.CounselorScheduleSummary>>` |

### 3.2 assessment

#### AnswerSheetController

- 基础路径：`/api/v1`
- 源码：`lx-boot/backend/src/main/kotlin/org/sainm/psy/assessment/api/AnswerSheetController.kt`

| 方法 | 路径 | 权限 | 处理器 | 参数 | 返回 |
| --- | --- | --- | --- | --- | --- |
| POST | `/api/v1/answer-sheets/save` | USER | `save` | `@RequestBody request: SaveAnswerSheetRequest`，是 | `ApiResponse<AnswerSheetDraftSaveResult>` |
| POST | `/api/v1/answer-sheets/submit` | USER | `submit` | `@RequestBody request: SubmitAnswerSheetRequest`，是<br>`@RequestHeader idempotencyKey: String` | `ApiResponse<AnswerSubmitResult>` |
| GET | `/api/v1/my/tasks/{taskId}/questions` | USER | `getTaskQuestions` | `@PathVariable taskId: Long`，是 | `ApiResponse<TaskQuestionPayload>` |
| POST | `/api/v1/results/{resultId}/rescore` | ASSESSMENT_ADMIN / ORG_MANAGER / ADMIN / SYS_ADMIN / SUPER_ADMIN | `rescoreResult` | `@PathVariable resultId: Long`，是 | `ApiResponse<AnswerSheetRescoreResult>` |

#### AssessmentTaskController

- 基础路径：`/api/v1`
- 源码：`lx-boot/backend/src/main/kotlin/org/sainm/psy/assessment/api/AssessmentTaskController.kt`

| 方法 | 路径 | 权限 | 处理器 | 参数 | 返回 |
| --- | --- | --- | --- | --- | --- |
| GET | `/api/v1/my/tasks` | 登录用户 | `findMyTasks` | - | `ApiResponse<List<MyAssessmentTask>>` |
| GET | `/api/v1/tasks` | ASSESSMENT_ADMIN / ADMIN / SYS_ADMIN / SUPER_ADMIN | `findPage` | `@RequestParam taskName: String`，否<br>`@RequestParam status: String`，否<br>`@RequestParam page: Int`，默认值<br>`@RequestParam size: Int`，默认值 | `ApiResponse<PageResponse<AssessmentTaskSummary>>` |
| POST | `/api/v1/tasks` | ASSESSMENT_ADMIN / ADMIN / SYS_ADMIN / SUPER_ADMIN | `create` | `@RequestBody request: CreateAssessmentTaskRequest`，是 | `ApiResponse<CreateAssessmentTaskResponse>` |
| DELETE | `/api/v1/tasks/{id}` | ASSESSMENT_ADMIN / ADMIN / SYS_ADMIN / SUPER_ADMIN | `delete` | `@PathVariable id: Long`，是 | `ApiResponse<Map<String, Any>> {` |
| GET | `/api/v1/tasks/{id}` | ASSESSMENT_ADMIN / ADMIN / SYS_ADMIN / SUPER_ADMIN | `findDetail` | `@PathVariable id: Long`，是 | `ApiResponse<AssessmentTaskDetail>` |
| POST | `/api/v1/tasks/{id}` | ASSESSMENT_ADMIN / ADMIN / SYS_ADMIN / SUPER_ADMIN | `update` | `@PathVariable id: Long`，是<br>`@RequestBody request: UpdateAssessmentTaskRequest`，是 | `ApiResponse<AssessmentTaskDetail>` |
| POST | `/api/v1/tasks/{id}/assign-groups` | ASSESSMENT_ADMIN / ADMIN / SYS_ADMIN / SUPER_ADMIN | `assignGroups` | `@PathVariable id: Long`，是<br>`@RequestBody request: TaskAssignGroupsRequest`，是 | `ApiResponse<Map<String, Any>> {` |
| POST | `/api/v1/tasks/{id}/assign-users` | ASSESSMENT_ADMIN / ADMIN / SYS_ADMIN / SUPER_ADMIN | `assignUsers` | `@PathVariable id: Long`，是<br>`@RequestBody request: TaskAssignUsersRequest`，是 | `ApiResponse<Map<String, Any>> {` |
| POST | `/api/v1/tasks/{id}/close` | ASSESSMENT_ADMIN / ADMIN / SYS_ADMIN / SUPER_ADMIN | `closeTask` | `@PathVariable id: Long`，是<br>`@RequestBody request: CloseAssessmentTaskRequest`，是 | `ApiResponse<AssessmentTaskDetail>` |

### 3.3 counseling

#### CounselingRecordController

- 基础路径：`/api/v1/counseling-records`
- 源码：`lx-boot/backend/src/main/kotlin/org/sainm/psy/counseling/api/CounselingRecordController.kt`

| 方法 | 路径 | 权限 | 处理器 | 参数 | 返回 |
| --- | --- | --- | --- | --- | --- |
| POST | `/api/v1/counseling-records` | COUNSELOR / ASSESSMENT_ADMIN / ADMIN / SUPER_ADMIN | `create` | `@RequestBody request: CreateCounselingRecordRequest`，是 | `ApiResponse<CounselingRecordActionResult>` |

### 3.4 directory

#### DirectoryController

- 基础路径：`/api/v1/directory`
- 源码：`lx-boot/backend/src/main/kotlin/org/sainm/psy/directory/api/DirectoryController.kt`

| 方法 | 路径 | 权限 | 处理器 | 参数 | 返回 |
| --- | --- | --- | --- | --- | --- |
| GET | `/api/v1/directory/groups` | COUNSELOR / ASSESSMENT_ADMIN / ORG_MANAGER / ADMIN / SYS_ADMIN / SUPER_ADMIN | `groups` | - | `ApiResponse<List<DirectoryGroup>>` |
| GET | `/api/v1/directory/scales` | COUNSELOR / ASSESSMENT_ADMIN / ORG_MANAGER / ADMIN / SYS_ADMIN / SUPER_ADMIN | `scales` | `@RequestParam keyword: String`，否 | `ApiResponse<List<DirectoryScale>>` |
| GET | `/api/v1/directory/tasks` | COUNSELOR / ASSESSMENT_ADMIN / ORG_MANAGER / ADMIN / SYS_ADMIN / SUPER_ADMIN | `tasks` | `@RequestParam keyword: String`，否 | `ApiResponse<List<DirectoryTask>>` |
| GET | `/api/v1/directory/users` | COUNSELOR / ASSESSMENT_ADMIN / ORG_MANAGER / ADMIN / SYS_ADMIN / SUPER_ADMIN | `users` | `@RequestParam keyword: String`，否<br>`@RequestParam staffOnly: Boolean`，默认值<br>`@RequestParam activeOnly: Boolean`，默认值<br>`@RequestParam page: Int`，默认值<br>`@RequestParam size: Int`，默认值 | `ApiResponse<PageResponse<DirectoryUser>>` |

### 3.5 export

#### ExportController

- 基础路径：`/api/v1/exports`
- 源码：`lx-boot/backend/src/main/kotlin/org/sainm/psy/export/api/ExportController.kt`

| 方法 | 路径 | 权限 | 处理器 | 参数 | 返回 |
| --- | --- | --- | --- | --- | --- |
| POST | `/api/v1/exports/reports` | USER / COUNSELOR / ASSESSMENT_ADMIN / ORG_MANAGER / ADMIN / SYS_ADMIN / SUPER_ADMIN | `exportReport` | `@RequestBody request: ExportReportRequest`，是 | `ApiResponse<ExportReportResponse>` |
| GET | `/api/v1/exports/reports/download` | USER / COUNSELOR / ASSESSMENT_ADMIN / ORG_MANAGER / ADMIN / SYS_ADMIN / SUPER_ADMIN | `downloadReport` | `@RequestParam reportId: Long`，否<br>`@RequestParam resultId: Long`，否<br>`@RequestParam exportFormat: String`，默认值<br>`@RequestParam desensitized: Boolean`，默认值 | `ResponseEntity<ByteArrayResource> {` |
| GET | `/api/v1/exports/reports/jobs` | ASSESSMENT_ADMIN / ORG_MANAGER / ADMIN / SYS_ADMIN / SUPER_ADMIN | `listRecentExportJobs` | `@RequestParam limit: Int`，默认值<br>`@RequestParam status: String`，否 | `ApiResponse<List<ExportJobStatusResponse>> {` |
| POST | `/api/v1/exports/reports/jobs` | COUNSELOR / ASSESSMENT_ADMIN / ORG_MANAGER / ADMIN / SYS_ADMIN / SUPER_ADMIN | `submitExportJob` | `@RequestBody request: ExportReportRequest`，是 | `ApiResponse<ExportJobSubmitResponse> {` |
| GET | `/api/v1/exports/reports/jobs/{jobId}` | COUNSELOR / ASSESSMENT_ADMIN / ORG_MANAGER / ADMIN / SYS_ADMIN / SUPER_ADMIN | `getExportJobStatus` | `@PathVariable jobId: String`，是 | `ApiResponse<ExportJobStatusResponse> {` |
| GET | `/api/v1/exports/reports/jobs/{jobId}/download` | COUNSELOR / ASSESSMENT_ADMIN / ORG_MANAGER / ADMIN / SYS_ADMIN / SUPER_ADMIN | `downloadExportJob` | `@PathVariable jobId: String`，是 | `ResponseEntity<ByteArrayResource> {` |
| POST | `/api/v1/exports/reports/jobs/{jobId}/retry` | ASSESSMENT_ADMIN / ORG_MANAGER / ADMIN / SYS_ADMIN / SUPER_ADMIN | `retryExportJob` | `@PathVariable jobId: String`，是 | `ApiResponse<ExportJobSubmitResponse> {` |
| GET | `/api/v1/exports/reports/storage` | ASSESSMENT_ADMIN / ORG_MANAGER / ADMIN / SYS_ADMIN / SUPER_ADMIN | `getExportArtifactStorageInfo` | - | `ApiResponse<ExportArtifactStorageInfoResponse>` |

### 3.6 intervention

#### InterventionController

- 基础路径：`/api/v1/interventions`
- 源码：`lx-boot/backend/src/main/kotlin/org/sainm/psy/intervention/api/InterventionController.kt`

| 方法 | 路径 | 权限 | 处理器 | 参数 | 返回 |
| --- | --- | --- | --- | --- | --- |
| POST | `/api/v1/interventions` | COUNSELOR / ASSESSMENT_ADMIN / ADMIN / SUPER_ADMIN | `create` | `@RequestBody request: CreateInterventionRequest`，是 | `ApiResponse<InterventionActionResult>` |
| POST | `/api/v1/interventions/{id}/close` | COUNSELOR / ASSESSMENT_ADMIN / ADMIN / SUPER_ADMIN | `close` | `@PathVariable id: Long`，是<br>`@RequestBody request: CloseInterventionRequest`，是 | `ApiResponse<InterventionActionResult>` |

### 3.7 notification

#### NotificationController

- 基础路径：`/api/v1/my/notifications`
- 源码：`lx-boot/backend/src/main/kotlin/org/sainm/psy/notification/api/NotificationController.kt`

| 方法 | 路径 | 权限 | 处理器 | 参数 | 返回 |
| --- | --- | --- | --- | --- | --- |
| GET | `/api/v1/my/notifications` | 登录用户 | `findMyNotifications` | - | `ApiResponse<List<MyNotificationSummary>>` |
| POST | `/api/v1/my/notifications/deliveries/{deliveryId}/clicked` | 登录用户 | `reportPushDeliveryClicked` | `@PathVariable deliveryId: Long`，是<br>`@RequestBody request: ReportPushDeliveryReceiptRequest`，否 | `ApiResponse<NotificationDeliveryReceiptResult>` |
| POST | `/api/v1/my/notifications/deliveries/{deliveryId}/received` | 登录用户 | `reportPushDeliveryReceived` | `@PathVariable deliveryId: Long`，是<br>`@RequestBody request: ReportPushDeliveryReceiptRequest`，否 | `ApiResponse<NotificationDeliveryReceiptResult>` |
| POST | `/api/v1/my/notifications/{id}/read` | 登录用户 | `markAsRead` | `@PathVariable id: Long`，是 | `ApiResponse<NotificationActionResult>` |

#### NotificationOpsController

- 基础路径：`/api/v1/notifications`
- 源码：`lx-boot/backend/src/main/kotlin/org/sainm/psy/notification/api/NotificationOpsController.kt`

| 方法 | 路径 | 权限 | 处理器 | 参数 | 返回 |
| --- | --- | --- | --- | --- | --- |
| POST | `/api/v1/notifications/deliveries/retry-batch` | ASSESSMENT_ADMIN / ORG_MANAGER / ADMIN / SYS_ADMIN / SUPER_ADMIN | `retryFailedDeliveriesBatch` | `@RequestBody request: BatchRetryNotificationDeliveriesRequest`，是 | `ApiResponse<NotificationBatchRetryResultResponse>` |
| GET | `/api/v1/notifications/deliveries/summary` | ASSESSMENT_ADMIN / ORG_MANAGER / ADMIN / SYS_ADMIN / SUPER_ADMIN | `findDeliveryOpsSummary` | - | `ApiResponse<NotificationDeliveryOpsSummary>` |
| POST | `/api/v1/notifications/deliveries/{deliveryId}/callbacks` | ASSESSMENT_ADMIN / ORG_MANAGER / ADMIN / SYS_ADMIN / SUPER_ADMIN | `applyPushDeliveryCallback` | `@PathVariable deliveryId: Long`，是<br>`@RequestBody request: ReportPushDeliveryCallbackRequest`，是 | `ApiResponse<NotificationDeliveryReceiptResult>` |
| GET | `/api/v1/notifications/ops/feed` | ASSESSMENT_ADMIN / ORG_MANAGER / ADMIN / SYS_ADMIN / SUPER_ADMIN | `findAdminNotifications` | `query: NotificationOpsListQuery` | `ApiResponse<List<AdminNotificationOpsItemResponse>>` |
| GET | `/api/v1/notifications/policies` | ADMIN / SYS_ADMIN / SUPER_ADMIN | `listPolicies` | - | `ApiResponse<List<NotificationPolicyResponse>>` |
| POST | `/api/v1/notifications/policies` | ADMIN / SYS_ADMIN / SUPER_ADMIN | `upsertPolicy` | `@RequestBody request: UpdateNotificationPolicyRequest`，是 | `ApiResponse<NotificationPolicyResponse>` |
| GET | `/api/v1/notifications/{id}/deliveries` | ASSESSMENT_ADMIN / ORG_MANAGER / ADMIN / SYS_ADMIN / SUPER_ADMIN | `findDeliveries` | `@PathVariable id: Long`，是 | `ApiResponse<List<NotificationDeliverySummary>>` |
| POST | `/api/v1/notifications/{id}/deliveries/retry` | ASSESSMENT_ADMIN / ORG_MANAGER / ADMIN / SYS_ADMIN / SUPER_ADMIN | `retryFailedDeliveries` | `@PathVariable id: Long`，是<br>`@RequestParam deliveryChannel: String`，否 | `ApiResponse<NotificationDeliveryRetryResult>` |

### 3.8 profile

#### MyProfileController

- 基础路径：`/api/v1/my/profile`
- 源码：`lx-boot/backend/src/main/kotlin/org/sainm/psy/profile/api/MyProfileController.kt`

| 方法 | 路径 | 权限 | 处理器 | 参数 | 返回 |
| --- | --- | --- | --- | --- | --- |
| GET | `/api/v1/my/profile` | 登录用户 | `getMyProfile` | - | `ApiResponse<MyProfileResponse>` |
| POST | `/api/v1/my/profile` | 登录用户 | `updateMyProfile` | `@RequestBody request: UpdateMyProfileRequest`，是 | `ApiResponse<MyProfileResponse>` |

### 3.9 report

#### ReportController

- 基础路径：`/api/v1/reports`
- 源码：`lx-boot/backend/src/main/kotlin/org/sainm/psy/report/api/ReportController.kt`

| 方法 | 路径 | 权限 | 处理器 | 参数 | 返回 |
| --- | --- | --- | --- | --- | --- |
| GET | `/api/v1/reports` | COUNSELOR / ASSESSMENT_ADMIN / ORG_MANAGER / ADMIN / SYS_ADMIN / SUPER_ADMIN | `searchReports` | `@RequestParam userId: Long`，否<br>`@RequestParam groupId: Long`，否<br>`@RequestParam scaleId: Long`，否<br>`@RequestParam taskId: Long`，否<br>`@RequestParam page: Int`，默认值<br>`@RequestParam size: Int`，默认值 | `ApiResponse<PageResponse<StaffReportSummary>>` |
| GET | `/api/v1/reports/by-result/{resultId}` | 登录用户 | `findDetailByResultId` | `@PathVariable resultId: Long`，是 | `ApiResponse<ReportDetail>` |
| GET | `/api/v1/reports/my` | 登录用户 | `findMyReports` | - | `ApiResponse<List<MyReportSummary>>` |
| GET | `/api/v1/reports/users/{userId}` | COUNSELOR / ASSESSMENT_ADMIN / ORG_MANAGER / ADMIN / SYS_ADMIN / SUPER_ADMIN | `findUserReports` | `@PathVariable userId: Long`，是 | `ApiResponse<List<MyReportSummary>>` |
| GET | `/api/v1/reports/{id}` | 登录用户 | `findDetail` | `@PathVariable id: Long`，是 | `ApiResponse<ReportDetail>` |
| POST | `/api/v1/reports/{id}/regenerate` | COUNSELOR / ASSESSMENT_ADMIN / ORG_MANAGER / ADMIN / SYS_ADMIN / SUPER_ADMIN | `regenerate` | `@PathVariable id: Long`，是 | `ApiResponse<ReportDetail>` |

### 3.10 scale

#### ScaleController

- 基础路径：`/api/v1/scales`
- 源码：`lx-boot/backend/src/main/kotlin/org/sainm/psy/scale/api/ScaleController.kt`

| 方法 | 路径 | 权限 | 处理器 | 参数 | 返回 |
| --- | --- | --- | --- | --- | --- |
| GET | `/api/v1/scales` | ASSESSMENT_ADMIN / ADMIN / SYS_ADMIN / SUPER_ADMIN | `findPage` | `@RequestParam scaleName: String`，否<br>`@RequestParam status: String`，否<br>`@RequestParam page: Int`，默认值<br>`@RequestParam size: Int`，默认值 | `ApiResponse<PageResponse<ScaleSummary>>` |
| POST | `/api/v1/scales` | ASSESSMENT_ADMIN / ADMIN / SYS_ADMIN / SUPER_ADMIN | `create` | `@RequestBody request: CreateScaleRequest`，是 | `ApiResponse<CreateScaleResponse>` |
| DELETE | `/api/v1/scales/{id}` | ASSESSMENT_ADMIN / ADMIN / SYS_ADMIN / SUPER_ADMIN | `delete` | `@PathVariable id: Long`，是 | `ApiResponse<Map<String, Any>> {` |
| GET | `/api/v1/scales/{id}` | ASSESSMENT_ADMIN / ADMIN / SYS_ADMIN / SUPER_ADMIN | `findDetail` | `@PathVariable id: Long`，是 | `ApiResponse<ScaleDetail>` |
| POST | `/api/v1/scales/{id}/basic` | ASSESSMENT_ADMIN / ADMIN / SYS_ADMIN / SUPER_ADMIN | `updateBasic` | `@PathVariable id: Long`，是<br>`@RequestBody request: UpdateScaleBasicRequest`，是 | `ApiResponse<ScaleDetail>` |
| POST | `/api/v1/scales/{id}/dimensions/batch` | ASSESSMENT_ADMIN / ADMIN / SYS_ADMIN / SUPER_ADMIN | `batchCreateDimensions` | `@PathVariable id: Long`，是<br>`@RequestBody request: BatchCreateScaleDimensionsRequest`，是 | `ApiResponse<BatchCreateResponse>` |
| POST | `/api/v1/scales/{id}/dimensions/{dimensionId}` | ASSESSMENT_ADMIN / ADMIN / SYS_ADMIN / SUPER_ADMIN | `updateDimension` | `@PathVariable id: Long`，是<br>`@PathVariable dimensionId: Long`，是<br>`@RequestBody request: UpdateScaleDimensionRequest`，是 | `ApiResponse<ScaleDetail>` |
| GET | `/api/v1/scales/{id}/norm-coverage` | ASSESSMENT_ADMIN / ADMIN / SYS_ADMIN / SUPER_ADMIN | `getNormCoverage` | `@PathVariable id: Long`，是 | `ApiResponse<ScaleNormCoverage>` |
| POST | `/api/v1/scales/{id}/norms/batch` | ASSESSMENT_ADMIN / ADMIN / SYS_ADMIN / SUPER_ADMIN | `batchCreateNorms` | `@PathVariable id: Long`，是<br>`@RequestBody request: BatchCreateScaleNormsRequest`，是 | `ApiResponse<BatchCreateResponse>` |
| POST | `/api/v1/scales/{id}/options/{optionId}` | ASSESSMENT_ADMIN / ADMIN / SYS_ADMIN / SUPER_ADMIN | `updateOption` | `@PathVariable id: Long`，是<br>`@PathVariable optionId: Long`，是<br>`@RequestBody request: UpdateScaleOptionRequest`，是 | `ApiResponse<ScaleDetail>` |
| POST | `/api/v1/scales/{id}/publish` | ASSESSMENT_ADMIN / ADMIN / SYS_ADMIN / SUPER_ADMIN | `publishVersion` | `@PathVariable id: Long`，是 | `ApiResponse<PublishScaleVersionResponse>` |
| POST | `/api/v1/scales/{id}/questions/batch` | ASSESSMENT_ADMIN / ADMIN / SYS_ADMIN / SUPER_ADMIN | `batchCreateQuestions` | `@PathVariable id: Long`，是<br>`@RequestBody request: BatchCreateScaleQuestionsRequest`，是 | `ApiResponse<BatchCreateResponse>` |
| POST | `/api/v1/scales/{id}/questions/{questionId}` | ASSESSMENT_ADMIN / ADMIN / SYS_ADMIN / SUPER_ADMIN | `updateQuestion` | `@PathVariable id: Long`，是<br>`@PathVariable questionId: Long`，是<br>`@RequestBody request: UpdateScaleQuestionRequest`，是 | `ApiResponse<ScaleDetail>` |
| POST | `/api/v1/scales/{id}/result-rules/batch` | ASSESSMENT_ADMIN / ADMIN / SYS_ADMIN / SUPER_ADMIN | `batchCreateResultRules` | `@PathVariable id: Long`，是<br>`@RequestBody request: BatchCreateScaleResultRulesRequest`，是 | `ApiResponse<BatchCreateResponse>` |
| GET | `/api/v1/scales/{id}/versions` | ASSESSMENT_ADMIN / ADMIN / SYS_ADMIN / SUPER_ADMIN | `listVersions` | `@PathVariable id: Long`，是 | `ApiResponse<List<ScaleSummary>>` |
| POST | `/api/v1/scales/{id}/versions` | ASSESSMENT_ADMIN / ADMIN / SYS_ADMIN / SUPER_ADMIN | `createVersion` | `@PathVariable id: Long`，是<br>`@RequestBody request: CreateScaleVersionRequest`，是 | `ApiResponse<CreateScaleVersionResponse>` |
| GET | `/api/v1/scales/{id}/versions/{targetId}/diff` | ASSESSMENT_ADMIN / ADMIN / SYS_ADMIN / SUPER_ADMIN | `compareVersions` | `@PathVariable id: Long`，是<br>`@PathVariable targetId: Long`，是 | `ApiResponse<ScaleVersionDiff>` |
| POST | `/api/v1/scales/{id}/visualizations` | ASSESSMENT_ADMIN / ADMIN / SYS_ADMIN / SUPER_ADMIN | `updateVisualizations` | `@PathVariable id: Long`，是<br>`@RequestBody request: UpdateScaleVisualizationsRequest`，是 | `ApiResponse<ScaleDetail>` |

#### ScaleImportController

- 基础路径：`/api/v1/scales`
- 源码：`lx-boot/backend/src/main/kotlin/org/sainm/psy/scale/api/ScaleImportController.kt`

| 方法 | 路径 | 权限 | 处理器 | 参数 | 返回 |
| --- | --- | --- | --- | --- | --- |
| GET | `/api/v1/scales/import-template` | ASSESSMENT_ADMIN / ADMIN / SYS_ADMIN / SUPER_ADMIN | `downloadTemplate` | - | `ResponseEntity<ByteArrayResource>` |
| GET | `/api/v1/scales/imports` | ASSESSMENT_ADMIN / ADMIN / SYS_ADMIN / SUPER_ADMIN | `findPage` | `@RequestParam fileName: String`，否<br>`@RequestParam status: String`，否<br>`@RequestParam page: Int`，默认值<br>`@RequestParam size: Int`，默认值 | `ApiResponse<PageResponse<ScaleImportListItemResponse>>` |
| POST | `/api/v1/scales/imports/package/preview` | ASSESSMENT_ADMIN / ADMIN / SYS_ADMIN / SUPER_ADMIN | `previewScalePackage` | `file: MultipartFile` | `ApiResponse<PreviewScalePackageImportResponse>` |
| POST | `/api/v1/scales/imports/package/{id}/confirm` | ASSESSMENT_ADMIN / ADMIN / SYS_ADMIN / SUPER_ADMIN | `confirmScalePackage` | `@PathVariable id: Long`，是 | `ApiResponse<ConfirmScalePackageImportResponse>` |
| POST | `/api/v1/scales/imports/parse` | ASSESSMENT_ADMIN / ADMIN / SYS_ADMIN / SUPER_ADMIN | `parse` | `file: MultipartFile`<br>`@RequestParam importMode: String`，默认值<br>`@RequestParam draftFlag: Boolean`，默认值 | `ApiResponse<ParseScaleImportResponse>` |
| GET | `/api/v1/scales/imports/{id}` | ASSESSMENT_ADMIN / ADMIN / SYS_ADMIN / SUPER_ADMIN | `findDetail` | `@PathVariable id: Long`，是 | `ApiResponse<ScaleImportDetailResponse>` |
| POST | `/api/v1/scales/imports/{id}/confirm` | ASSESSMENT_ADMIN / ADMIN / SYS_ADMIN / SUPER_ADMIN | `confirm` | `@PathVariable id: Long`，是<br>`@RequestBody request: ConfirmScaleImportRequest`，是 | `ApiResponse<ConfirmScaleImportResponse>` |

#### ScalePackageController

- 基础路径：`/api/v1/scales/{scaleId}/package`
- 源码：`lx-boot/backend/src/main/kotlin/org/sainm/psy/scale/api/ScalePackageController.kt`

| 方法 | 路径 | 权限 | 处理器 | 参数 | 返回 |
| --- | --- | --- | --- | --- | --- |
| GET | `/api/v1/scales/{scaleId}/package` | ASSESSMENT_ADMIN / ADMIN / SYS_ADMIN / SUPER_ADMIN | `find` | `@PathVariable scaleId: Long`，是 | `ApiResponse<ScalePackageSnapshot>` |
| PUT | `/api/v1/scales/{scaleId}/package` | ASSESSMENT_ADMIN / ADMIN / SYS_ADMIN / SUPER_ADMIN | `replace` | `@PathVariable scaleId: Long`，是<br>`@RequestBody request: UpdateScalePackageRequest`，是 | `ApiResponse<ScalePackageSnapshot>` |
| GET | `/api/v1/scales/{scaleId}/package/export` | ASSESSMENT_ADMIN / ADMIN / SYS_ADMIN / SUPER_ADMIN | `export` | `@PathVariable scaleId: Long`，是 | `ResponseEntity<ByteArrayResource> {` |

#### ScalePublicationGovernanceController

- 基础路径：`/api/v1/scales/{scaleId}/publication`
- 源码：`lx-boot/backend/src/main/kotlin/org/sainm/psy/scale/api/ScalePublicationGovernanceController.kt`

| 方法 | 路径 | 权限 | 处理器 | 参数 | 返回 |
| --- | --- | --- | --- | --- | --- |
| GET | `/api/v1/scales/{scaleId}/publication/golden-cases` | COUNSELOR / ASSESSMENT_ADMIN / ORG_MANAGER / ADMIN / SYS_ADMIN / SUPER_ADMIN | `listGoldenCases` | `@PathVariable scaleId: Long`，是 | `ApiResponse<List<ScaleGoldenCase>>` |
| POST | `/api/v1/scales/{scaleId}/publication/golden-cases` | ASSESSMENT_ADMIN / ORG_MANAGER / ADMIN / SYS_ADMIN / SUPER_ADMIN | `saveGoldenCase` | `@PathVariable scaleId: Long`，是<br>`@RequestBody request: CreateScaleGoldenCaseRequest`，是 | `ApiResponse<ScaleGoldenCase>` |
| POST | `/api/v1/scales/{scaleId}/publication/golden-cases/{caseId}/approve` | COUNSELOR | `approveGoldenCase` | `@PathVariable scaleId: Long`，是<br>`@PathVariable caseId: Long`，是 | `ApiResponse<ScaleGoldenCase>` |
| POST | `/api/v1/scales/{scaleId}/publication/golden-cases/{caseId}/run` | ASSESSMENT_ADMIN / ORG_MANAGER / ADMIN / SYS_ADMIN / SUPER_ADMIN | `runGoldenCase` | `@PathVariable scaleId: Long`，是<br>`@PathVariable caseId: Long`，是 | `ApiResponse<GoldenCaseRunResponse>` |
| GET | `/api/v1/scales/{scaleId}/publication/history` | COUNSELOR / ASSESSMENT_ADMIN / ORG_MANAGER / ADMIN / SYS_ADMIN / SUPER_ADMIN | `history` | `@PathVariable scaleId: Long`，是 | `ApiResponse<ScalePublicationHistory>` |
| GET | `/api/v1/scales/{scaleId}/publication/history/cases` | COUNSELOR / ASSESSMENT_ADMIN / ORG_MANAGER / ADMIN / SYS_ADMIN / SUPER_ADMIN | `historyCases` | `@PathVariable scaleId: Long`，是<br>`@RequestParam afterId: Long`，否<br>`@RequestParam limit: Int`，默认值 | `ApiResponse<CursorPage<ScaleGoldenCase>>` |
| GET | `/api/v1/scales/{scaleId}/publication/history/reviews` | COUNSELOR / ASSESSMENT_ADMIN / ORG_MANAGER / ADMIN / SYS_ADMIN / SUPER_ADMIN | `historyReviews` | `@PathVariable scaleId: Long`，是<br>`@RequestParam afterId: Long`，否<br>`@RequestParam limit: Int`，默认值 | `ApiResponse<CursorPage<ScalePublicationReview>>` |
| GET | `/api/v1/scales/{scaleId}/publication/history/runs` | COUNSELOR / ASSESSMENT_ADMIN / ORG_MANAGER / ADMIN / SYS_ADMIN / SUPER_ADMIN | `historyRuns` | `@PathVariable scaleId: Long`，是<br>`@RequestParam afterId: Long`，否<br>`@RequestParam limit: Int`，默认值 | `ApiResponse<CursorPage<ScaleGoldenCaseRun>>` |
| GET | `/api/v1/scales/{scaleId}/publication/readiness` | COUNSELOR / ASSESSMENT_ADMIN / ORG_MANAGER / ADMIN / SYS_ADMIN / SUPER_ADMIN | `readiness` | `@PathVariable scaleId: Long`，是 | `ApiResponse<ScalePublicationReadiness>` |
| POST | `/api/v1/scales/{scaleId}/publication/reviews/{reviewType}` | COUNSELOR / ASSESSMENT_ADMIN / ORG_MANAGER / ADMIN / SYS_ADMIN / SUPER_ADMIN | `review` | `@PathVariable scaleId: Long`，是<br>`@PathVariable reviewType: String`，是<br>`@RequestBody request: ScalePublicationReviewRequest`，是 | `ApiResponse<ScalePublicationReview>` |

### 3.11 statistics

#### StatisticsController

- 基础路径：`/api/v1/statistics`
- 源码：`lx-boot/backend/src/main/kotlin/org/sainm/psy/statistics/api/StatisticsController.kt`

| 方法 | 路径 | 权限 | 处理器 | 参数 | 返回 |
| --- | --- | --- | --- | --- | --- |
| GET | `/api/v1/statistics/dashboard` | COUNSELOR / ASSESSMENT_ADMIN / ORG_MANAGER / SCHOOL_LEADER / ADMIN / SYS_ADMIN / SUPER_ADMIN | `dashboard` | - | `ApiResponse<DashboardStatisticsResponse>` |
| GET | `/api/v1/statistics/group-reports` | COUNSELOR / ASSESSMENT_ADMIN / ORG_MANAGER / SCHOOL_LEADER / ADMIN / SYS_ADMIN / SUPER_ADMIN | `groupReports` | `@RequestParam taskId: Long`，否<br>`@RequestParam groupId: Long`，否<br>`@RequestParam scaleId: Long`，否<br>`@RequestParam compareUserId: Long`，否<br>`@RequestParam page: Int`，默认值<br>`@RequestParam size: Int`，默认值 | `ApiResponse<PageResponse<GroupReportSummary>>` |
| GET | `/api/v1/statistics/group-reports/download` | COUNSELOR / ASSESSMENT_ADMIN / ORG_MANAGER / SCHOOL_LEADER / ADMIN / SYS_ADMIN / SUPER_ADMIN | `downloadGroupReports` | `@RequestParam taskId: Long`，否<br>`@RequestParam groupId: Long`，否<br>`@RequestParam scaleId: Long`，否<br>`@RequestParam compareUserId: Long`，否<br>`@RequestParam format: String`，默认值<br>`@RequestParam exportFormat: String`，否<br>`@RequestParam page: Int`，默认值<br>`@RequestParam size: Int`，默认值 | `ResponseEntity<ByteArrayResource> {` |

### 3.12 useradmin

#### ExternalRegistrationReviewController

- 基础路径：`/api/v1/admin`
- 源码：`lx-boot/backend/src/main/kotlin/org/sainm/psy/useradmin/api/ExternalRegistrationReviewController.kt`

| 方法 | 路径 | 权限 | 处理器 | 参数 | 返回 |
| --- | --- | --- | --- | --- | --- |
| GET | `/api/v1/admin/external-registrations/pending` | ASSESSMENT_ADMIN / ORG_MANAGER / ADMIN / SYS_ADMIN / SUPER_ADMIN | `listPending` | - | `ApiResponse<List<Map<String, Any?>>> {` |
| POST | `/api/v1/admin/external-registrations/{userId}/approve` | ASSESSMENT_ADMIN / ORG_MANAGER / ADMIN / SYS_ADMIN / SUPER_ADMIN | `approve` | `@PathVariable userId: Long`，是 | `ApiResponse<Map<String, String>> {` |
| POST | `/api/v1/admin/external-registrations/{userId}/reject` | ASSESSMENT_ADMIN / ORG_MANAGER / ADMIN / SYS_ADMIN / SUPER_ADMIN | `reject` | `@PathVariable userId: Long`，是 | `ApiResponse<Map<String, String>> {` |

#### UserAdminController

- 基础路径：`/api/v1/user-admin`
- 源码：`lx-boot/backend/src/main/kotlin/org/sainm/psy/useradmin/api/UserAdminController.kt`

| 方法 | 路径 | 权限 | 处理器 | 参数 | 返回 |
| --- | --- | --- | --- | --- | --- |
| GET | `/api/v1/user-admin/groups` | ORG_MANAGER / ADMIN / SYS_ADMIN / SUPER_ADMIN | `listGroups` | `@RequestParam tenantId: Long`，否 | `ApiResponse<List<UserAdminGroupResponse>>` |
| GET | `/api/v1/user-admin/roles` | ORG_MANAGER / ADMIN / SYS_ADMIN / SUPER_ADMIN | `listRoles` | `@RequestParam tenantId: Long`，否 | `ApiResponse<List<UserAdminRoleResponse>>` |
| GET | `/api/v1/user-admin/tenants` | ORG_MANAGER / ADMIN / SYS_ADMIN / SUPER_ADMIN | `listTenants` | - | `ApiResponse<List<UserAdminTenantResponse>>` |
| GET | `/api/v1/user-admin/users` | ORG_MANAGER / ADMIN / SYS_ADMIN / SUPER_ADMIN | `findUserPage` | `@RequestParam username: String`，否<br>`@RequestParam status: String`，否<br>`@RequestParam tenantId: Long`，否<br>`@RequestParam groupId: Long`，否<br>`@RequestParam page: Int`，默认值<br>`@RequestParam size: Int`，默认值 | `ApiResponse<PageResponse<UserAdminUserSummaryResponse>>` |
| POST | `/api/v1/user-admin/users` | ORG_MANAGER / ADMIN / SYS_ADMIN / SUPER_ADMIN | `createUser` | `@RequestBody request: CreateUserAdminUserRequest`，是 | `ApiResponse<UserAdminUserSummaryResponse>` |
| POST | `/api/v1/user-admin/users/{userId}/password/reset` | ORG_MANAGER / ADMIN / SYS_ADMIN / SUPER_ADMIN | `resetPassword` | `@PathVariable userId: Long`，是<br>`@RequestBody request: ResetUserPasswordRequest`，是 | `ApiResponse<Boolean> {` |
| POST | `/api/v1/user-admin/users/{userId}/roles` | ORG_MANAGER / ADMIN / SYS_ADMIN / SUPER_ADMIN | `assignRoles` | `@PathVariable userId: Long`，是<br>`@RequestBody request: AssignUserRolesRequest`，是 | `ApiResponse<UserAdminUserSummaryResponse>` |
| POST | `/api/v1/user-admin/users/{userId}/status` | ORG_MANAGER / ADMIN / SYS_ADMIN / SUPER_ADMIN | `updateStatus` | `@PathVariable userId: Long`，是<br>`@RequestBody request: UpdateUserStatusRequest`，是 | `ApiResponse<UserAdminUserSummaryResponse>` |

### 3.13 warning

#### SafetyResponsePolicyController

- 基础路径：`/api/v1/safety-response-policies`
- 源码：`lx-boot/backend/src/main/kotlin/org/sainm/psy/warning/api/SafetyResponsePolicyController.kt`

| 方法 | 路径 | 权限 | 处理器 | 参数 | 返回 |
| --- | --- | --- | --- | --- | --- |
| GET | `/api/v1/safety-response-policies` | ASSESSMENT_ADMIN / ORG_MANAGER / COUNSELOR / ADMIN / SYS_ADMIN / SUPER_ADMIN | `findAll` | - | `ApiResponse<List<SafetyResponsePolicy>>` |
| POST | `/api/v1/safety-response-policies` | ASSESSMENT_ADMIN / ORG_MANAGER / ADMIN / SYS_ADMIN / SUPER_ADMIN | `create` | `@RequestBody request: CreateSafetyResponsePolicyRequest`，是 | `ApiResponse<SafetyResponsePolicy>` |
| POST | `/api/v1/safety-response-policies/{id}/approve` | ASSESSMENT_ADMIN / ORG_MANAGER / ADMIN / SYS_ADMIN / SUPER_ADMIN | `approve` | `@PathVariable id: Long`，是 | `ApiResponse<SafetyResponsePolicy>` |
| POST | `/api/v1/safety-response-policies/{id}/professional-review` | COUNSELOR | `professionalReview` | `@PathVariable id: Long`，是 | `ApiResponse<SafetyResponsePolicy>` |

#### WarningController

- 基础路径：`/api/v1/warnings`
- 源码：`lx-boot/backend/src/main/kotlin/org/sainm/psy/warning/api/WarningController.kt`

| 方法 | 路径 | 权限 | 处理器 | 参数 | 返回 |
| --- | --- | --- | --- | --- | --- |
| GET | `/api/v1/warnings` | COUNSELOR / ASSESSMENT_ADMIN / ADMIN / SUPER_ADMIN | `findPage` | `@RequestParam status: String`，否<br>`@RequestParam warningLevel: String`，否<br>`@RequestParam page: Int`，默认值<br>`@RequestParam size: Int`，默认值 | `ApiResponse<PageResponse<WarningSummary>>` |
| GET | `/api/v1/warnings/assignee-options` | COUNSELOR / ASSESSMENT_ADMIN / ORG_MANAGER / ADMIN / SYS_ADMIN / SUPER_ADMIN | `assigneeOptions` | - | `ApiResponse<List<WarningAssigneeOption>>` |
| POST | `/api/v1/warnings/{id}/assign` | ASSESSMENT_ADMIN / ADMIN / SUPER_ADMIN | `assign` | `@PathVariable id: Long`，是<br>`@RequestBody request: AssignWarningRequest`，是 | `ApiResponse<WarningActionResult>` |
| POST | `/api/v1/warnings/{id}/claim` | COUNSELOR / ASSESSMENT_ADMIN / ADMIN / SUPER_ADMIN | `claim` | `@PathVariable id: Long`，是 | `ApiResponse<WarningActionResult>` |
| POST | `/api/v1/warnings/{id}/policy-resolution` | COUNSELOR / ASSESSMENT_ADMIN / ORG_MANAGER / ADMIN / SYS_ADMIN / SUPER_ADMIN | `resolvePolicy` | `@PathVariable id: Long`，是 | `ApiResponse<WarningPolicyResolution>` |

## 4. 认证端点（auth-starter）

| 方法 | 路径 | 权限 | 处理器 | 参数 | 返回 | 源码 |
| --- | --- | --- | --- | --- | --- | --- |
| POST | `/api/v1/wechat/menu/sync` | SYS_ADMIN | `syncMenu` | `@RequestBody menuJson: String`，是 | `ResponseEntity<Map<String, String>> {` | `auth-starter/auth-security/src/main/kotlin/org/sainm/auth/security/web/WechatManagementController.kt` |
| GET | `/auth/admin/ping` | 未声明 | `adminPing` | - | `ApiResponse<Map<String, Boolean>>` | `auth-starter/auth-security/src/main/kotlin/org/sainm/auth/security/web/AuthController.kt` |
| GET | `/auth/email-verify` | 未声明 | `emailVerify` | `@RequestParam token: String`，是 | `ApiResponse<Map<String, String>> {` | `auth-starter/auth-security/src/main/kotlin/org/sainm/auth/security/web/AuthController.kt` |
| POST | `/auth/external-register` | 未声明 | `externalRegister` | `@RequestBody request: ExternalRegisterRequest`，是 | `ApiResponse<Map<String, Any>> {` | `auth-starter/auth-security/src/main/kotlin/org/sainm/auth/security/web/AuthController.kt` |
| POST | `/auth/external-register/resend` | 未声明 | `resendActivation` | `@RequestBody request: ResendActivationRequest`，是 | `ApiResponse<Map<String, String>> {` | `auth-starter/auth-security/src/main/kotlin/org/sainm/auth/security/web/AuthController.kt` |
| GET | `/auth/groups` | api:GET:/auth/groups | `groups` | `@AuthenticationPrincipal principalUserId: Long`<br>`@RequestParam tenantId: Long`，否 | `ApiResponse<Any>` | `auth-starter/auth-security/src/main/kotlin/org/sainm/auth/security/web/AuthController.kt` |
| POST | `/auth/groups` | api:POST:/auth/groups | `createGroup` | `@AuthenticationPrincipal principalUserId: Long`<br>`@RequestBody request: CreateGroupRequest`，是 | `ApiResponse<Any>` | `auth-starter/auth-security/src/main/kotlin/org/sainm/auth/security/web/AuthController.kt` |
| POST | `/auth/groups/{groupId}/roles` | api:POST:/auth/groups/roles | `assignGroupRoles` | `@PathVariable groupId: Long`，是<br>`@RequestBody request: GroupRoleAssignRequest`，是 | `ApiResponse<Any>` | `auth-starter/auth-security/src/main/kotlin/org/sainm/auth/security/web/AuthController.kt` |
| GET | `/auth/login-logs` | ASSESSMENT_ADMIN / ORG_MANAGER / ADMIN / SYS_ADMIN / SUPER_ADMIN | `loginLogs` | `@RequestParam page: Int`，默认值<br>`@RequestParam size: Int`，默认值<br>`@RequestParam principal: String`，否<br>`@RequestParam result: String`，否 | `ApiResponse<Any>` | `auth-starter/auth-security/src/main/kotlin/org/sainm/auth/security/web/AuthController.kt` |
| POST | `/auth/login/password` | 未声明 | `passwordLogin` | `@RequestBody request: PasswordLoginRequest`，是<br>`servletRequest: HttpServletRequest` | `ApiResponse<AuthResponse> {` | `auth-starter/auth-security/src/main/kotlin/org/sainm/auth/security/web/AuthController.kt` |
| POST | `/auth/logout` | 未声明 | `logout` | `@RequestBody request: LogoutRequest`，是<br>`@RequestHeader authorization: String` | `ApiResponse<Boolean> {` | `auth-starter/auth-security/src/main/kotlin/org/sainm/auth/security/web/AuthController.kt` |
| GET | `/auth/me` | 未声明 | `me` | `@AuthenticationPrincipal userId: Long` | `ApiResponse<CurrentUserProfileResponse> {` | `auth-starter/auth-security/src/main/kotlin/org/sainm/auth/security/web/AuthController.kt` |
| GET | `/auth/me/devices` | 未声明 | `myDevices` | `@AuthenticationPrincipal userId: Long` | `ApiResponse<List<UserDeviceSummaryResponse>>` | `auth-starter/auth-security/src/main/kotlin/org/sainm/auth/security/web/DeviceGovernanceController.kt` |
| POST | `/auth/me/devices` | 未声明 | `registerMyDevice` | `@AuthenticationPrincipal userId: Long`<br>`@RequestBody request: DeviceRegistrationRequest`，是 | `ApiResponse<UserDeviceSummaryResponse>` | `auth-starter/auth-security/src/main/kotlin/org/sainm/auth/security/web/DeviceGovernanceController.kt` |
| POST | `/auth/me/devices/{deviceId}/deactivate` | 未声明 | `deactivateMyDevice` | `@AuthenticationPrincipal userId: Long`<br>`@PathVariable deviceId: String`，是 | `ApiResponse<UserDeviceSummaryResponse>` | `auth-starter/auth-security/src/main/kotlin/org/sainm/auth/security/web/DeviceGovernanceController.kt` |
| GET | `/auth/me/login-activities` | 未声明 | `myLoginActivities` | `@AuthenticationPrincipal userId: Long` | `ApiResponse<List<LoginActivityResponse>> {` | `auth-starter/auth-security/src/main/kotlin/org/sainm/auth/security/web/AuthController.kt` |
| GET | `/auth/me/security-events` | 未声明 | `mySecurityEvents` | `@AuthenticationPrincipal userId: Long` | `ApiResponse<List<SecurityEventResponse>> {` | `auth-starter/auth-security/src/main/kotlin/org/sainm/auth/security/web/AuthController.kt` |
| GET | `/auth/me/session-policy` | 未声明 | `mySessionPolicy` | `@AuthenticationPrincipal userId: Long` | `ApiResponse<SessionPolicyResponse>` | `auth-starter/auth-security/src/main/kotlin/org/sainm/auth/security/web/AuthController.kt` |
| POST | `/auth/me/session-policy` | 未声明 | `updateMySessionPolicy` | `@AuthenticationPrincipal userId: Long`<br>`@RequestBody request: UpdateSessionPolicyRequest`，是 | `ApiResponse<SessionPolicyResponse> {` | `auth-starter/auth-security/src/main/kotlin/org/sainm/auth/security/web/AuthController.kt` |
| GET | `/auth/me/sessions` | 未声明 | `mySessions` | `@AuthenticationPrincipal userId: Long` | `ApiResponse<List<SessionSummaryResponse>> {` | `auth-starter/auth-security/src/main/kotlin/org/sainm/auth/security/web/AuthController.kt` |
| POST | `/auth/me/sessions/revoke-others` | 未声明 | `revokeOtherMySessions` | `@AuthenticationPrincipal userId: Long` | `ApiResponse<Map<String, Int>> {` | `auth-starter/auth-security/src/main/kotlin/org/sainm/auth/security/web/AuthController.kt` |
| POST | `/auth/me/sessions/{sessionId}/revoke` | 未声明 | `revokeMySession` | `@AuthenticationPrincipal userId: Long`<br>`@PathVariable sessionId: String`，是 | `ApiResponse<Boolean>` | `auth-starter/auth-security/src/main/kotlin/org/sainm/auth/security/web/AuthController.kt` |
| POST | `/auth/password/change` | 未声明 | `changePassword` | `@AuthenticationPrincipal userId: Long`<br>`@RequestBody request: ChangePasswordRequest`，是 | `ApiResponse<Boolean> {` | `auth-starter/auth-security/src/main/kotlin/org/sainm/auth/security/web/AuthController.kt` |
| POST | `/auth/password/reset` | 未声明 | `resetPassword` | `@RequestBody request: ResetPasswordRequest`，是 | `ApiResponse<Boolean> {` | `auth-starter/auth-security/src/main/kotlin/org/sainm/auth/security/web/AuthController.kt` |
| GET | `/auth/permissions` | api:GET:/auth/permissions | `permissions` | `@AuthenticationPrincipal principalUserId: Long`<br>`@RequestParam tenantId: Long`，否 | `ApiResponse<Any>` | `auth-starter/auth-security/src/main/kotlin/org/sainm/auth/security/web/AuthController.kt` |
| POST | `/auth/qr/cancel` | 未声明 | `cancelQrScene` | `@AuthenticationPrincipal userId: Long`<br>`@RequestBody request: QrCancelRequest`，是<br>`servletRequest: HttpServletRequest` | `ApiResponse<QrSceneResponse> {` | `auth-starter/auth-security/src/main/kotlin/org/sainm/auth/security/web/AuthController.kt` |
| POST | `/auth/qr/confirm` | 未声明 | `confirmQrScene` | `@AuthenticationPrincipal userId: Long`<br>`@RequestBody request: QrConfirmRequest`，是<br>`servletRequest: HttpServletRequest` | `ApiResponse<QrSceneResponse> {` | `auth-starter/auth-security/src/main/kotlin/org/sainm/auth/security/web/AuthController.kt` |
| POST | `/auth/qr/scan` | 未声明 | `scanQrScene` | `@AuthenticationPrincipal userId: Long`<br>`@RequestBody request: QrScanRequest`，是<br>`servletRequest: HttpServletRequest` | `ApiResponse<QrSceneResponse> {` | `auth-starter/auth-security/src/main/kotlin/org/sainm/auth/security/web/AuthController.kt` |
| POST | `/auth/qr/scene` | 未声明 | `createQrScene` | - | `ApiResponse<QrSceneResponse>` | `auth-starter/auth-security/src/main/kotlin/org/sainm/auth/security/web/AuthController.kt` |
| GET | `/auth/qr/scene/{sceneCode}` | 未声明 | `getQrScene` | `@PathVariable sceneCode: String`，是<br>`servletRequest: HttpServletRequest` | `ApiResponse<QrSceneResponse> {` | `auth-starter/auth-security/src/main/kotlin/org/sainm/auth/security/web/AuthController.kt` |
| POST | `/auth/register` | 未声明 | `register` | `@RequestBody request: RegisterRequest`，是 | `ApiResponse<RegisterResponse> {` | `auth-starter/auth-security/src/main/kotlin/org/sainm/auth/security/web/AuthController.kt` |
| GET | `/auth/register/options` | 未声明 | `registrationOptions` | - | `ApiResponse<RegistrationOptionsResponse>` | `auth-starter/auth-security/src/main/kotlin/org/sainm/auth/security/web/AuthController.kt` |
| GET | `/auth/roles` | api:GET:/auth/roles | `roles` | `@AuthenticationPrincipal principalUserId: Long`<br>`@RequestParam tenantId: Long`，否 | `ApiResponse<Any>` | `auth-starter/auth-security/src/main/kotlin/org/sainm/auth/security/web/AuthController.kt` |
| GET | `/auth/security-events` | ASSESSMENT_ADMIN / ORG_MANAGER / ADMIN / SYS_ADMIN / SUPER_ADMIN | `securityEvents` | `@RequestParam page: Int`，默认值<br>`@RequestParam size: Int`，默认值<br>`@RequestParam eventType: String`，否 | `ApiResponse<Any>` | `auth-starter/auth-security/src/main/kotlin/org/sainm/auth/security/web/AuthController.kt` |
| POST | `/auth/social/google` | 未声明 | `googleLogin` | `@RequestBody request: SocialLoginRequest`，是<br>`servletRequest: HttpServletRequest` | `ApiResponse<AuthResponse> {` | `auth-starter/auth-security/src/main/kotlin/org/sainm/auth/security/web/AuthController.kt` |
| POST | `/auth/social/wechat` | 未声明 | `wechatLogin` | `@RequestBody request: SocialLoginRequest`，是<br>`servletRequest: HttpServletRequest` | `ApiResponse<AuthResponse> {` | `auth-starter/auth-security/src/main/kotlin/org/sainm/auth/security/web/AuthController.kt` |
| POST | `/auth/sso/token` | 未声明 | `ssoTokenExchange` | `@RequestBody request: SsoTicketExchangeRequest`，是<br>`servletRequest: HttpServletRequest` | `ApiResponse<AuthResponse> {` | `auth-starter/auth-security/src/main/kotlin/org/sainm/auth/security/web/AuthController.kt` |
| GET | `/auth/sso/{provider}/authorize` | 未声明 | `ssoAuthorize` | `@PathVariable provider: String`，是<br>`@RequestParam returnTo: String`，否 | `org.springframework.http.ResponseEntity<Void> {` | `auth-starter/auth-security/src/main/kotlin/org/sainm/auth/security/web/AuthController.kt` |
| GET | `/auth/sso/{provider}/callback` | 未声明 | `ssoCallback` | `@PathVariable provider: String`，是<br>`@RequestParam code: String`，否<br>`@RequestParam ticket: String`，否<br>`@RequestParam state: String`，否<br>`servletRequest: HttpServletRequest` | `org.springframework.http.ResponseEntity<Void> {` | `auth-starter/auth-security/src/main/kotlin/org/sainm/auth/security/web/AuthController.kt` |
| GET | `/auth/tenants` | api:GET:/auth/tenants | `tenants` | `@AuthenticationPrincipal principalUserId: Long`<br>`@RequestParam tenantId: Long`，否 | `ApiResponse<Any>` | `auth-starter/auth-security/src/main/kotlin/org/sainm/auth/security/web/AuthController.kt` |
| POST | `/auth/tenants` | api:POST:/auth/tenants | `createTenant` | `@RequestBody request: CreateTenantRequest`，是 | `ApiResponse<Any>` | `auth-starter/auth-security/src/main/kotlin/org/sainm/auth/security/web/AuthController.kt` |
| POST | `/auth/token/refresh` | 未声明 | `refreshToken` | `@RequestBody request: RefreshTokenRequest`，是 | `ApiResponse<Map<String, Any>> {` | `auth-starter/auth-security/src/main/kotlin/org/sainm/auth/security/web/AuthController.kt` |
| GET | `/auth/users` | api:GET:/auth/users | `users` | `@AuthenticationPrincipal principalUserId: Long`<br>`@RequestParam page: Int`，默认值<br>`@RequestParam size: Int`，默认值<br>`@RequestParam tenantId: Long`，否 | `ApiResponse<Any>` | `auth-starter/auth-security/src/main/kotlin/org/sainm/auth/security/web/AuthController.kt` |
| GET | `/auth/users/{userId}/devices` | 未声明 | `userDevices` | `@AuthenticationPrincipal principalUserId: Long`<br>`@PathVariable userId: Long`，是 | `ApiResponse<List<UserDeviceSummaryResponse>> {` | `auth-starter/auth-security/src/main/kotlin/org/sainm/auth/security/web/DeviceGovernanceController.kt` |
| POST | `/auth/users/{userId}/devices/{deviceId}/deactivate` | 未声明 | `deactivateUserDevice` | `@AuthenticationPrincipal principalUserId: Long`<br>`@PathVariable userId: Long`，是<br>`@PathVariable deviceId: String`，是 | `ApiResponse<UserDeviceDeactivationResponse> {` | `auth-starter/auth-security/src/main/kotlin/org/sainm/auth/security/web/DeviceGovernanceController.kt` |
| POST | `/auth/users/{userId}/roles` | api:POST:/auth/users/roles | `assignRoles` | `@PathVariable userId: Long`，是<br>`@RequestBody request: RoleAssignRequest`，是 | `ApiResponse<Any>` | `auth-starter/auth-security/src/main/kotlin/org/sainm/auth/security/web/AuthController.kt` |
| GET | `/auth/users/{userId}/sessions` | 未声明 | `userSessions` | `@AuthenticationPrincipal principalUserId: Long`<br>`@PathVariable userId: Long`，是 | `ApiResponse<List<SessionSummaryResponse>> {` | `auth-starter/auth-security/src/main/kotlin/org/sainm/auth/security/web/AuthController.kt` |
| POST | `/auth/users/{userId}/sessions/revoke-all` | 未声明 | `revokeAllUserSessions` | `@AuthenticationPrincipal principalUserId: Long`<br>`@PathVariable userId: Long`，是 | `ApiResponse<SessionRevokeResponse> {` | `auth-starter/auth-security/src/main/kotlin/org/sainm/auth/security/web/AuthController.kt` |
| POST | `/auth/users/{userId}/sessions/{sessionId}/revoke` | 未声明 | `revokeUserSession` | `@AuthenticationPrincipal principalUserId: Long`<br>`@PathVariable userId: Long`，是<br>`@PathVariable sessionId: String`，是 | `ApiResponse<Boolean> {` | `auth-starter/auth-security/src/main/kotlin/org/sainm/auth/security/web/AuthController.kt` |
| POST | `/wechat/jssdk/config` | 未声明 | `jssdkConfig` | `@RequestBody body: Map<String, String>`，是 | `ResponseEntity<Map<String, String>> {` | `auth-starter/auth-security/src/main/kotlin/org/sainm/auth/security/web/WechatManagementController.kt` |
| GET | `/wechat/portal` | 未声明 | `verify` | `@RequestParam signature: String`，是<br>`@RequestParam timestamp: String`，是<br>`@RequestParam nonce: String`，是<br>`@RequestParam echostr: String`，是 | `String {` | `auth-starter/auth-security/src/main/kotlin/org/sainm/auth/security/web/WechatPortalController.kt` |
| POST | `/wechat/portal` | 未声明 | `receive` | `@RequestBody body: String`，是 | `String {` | `auth-starter/auth-security/src/main/kotlin/org/sainm/auth/security/web/WechatPortalController.kt` |

## 5. 权限标注分布（本仓库）

| 权限 | 端点数 |
| --- | ---: |
| ASSESSMENT_ADMIN / ADMIN / SYS_ADMIN / SUPER_ADMIN | 36 |
| COUNSELOR / ASSESSMENT_ADMIN / ORG_MANAGER / ADMIN / SYS_ADMIN / SUPER_ADMIN | 21 |
| ASSESSMENT_ADMIN / ORG_MANAGER / ADMIN / SYS_ADMIN / SUPER_ADMIN | 17 |
| 登录用户 | 14 |
| ORG_MANAGER / ADMIN / SYS_ADMIN / SUPER_ADMIN | 8 |
| COUNSELOR / ASSESSMENT_ADMIN / ADMIN / SUPER_ADMIN | 5 |
| COUNSELOR / ASSESSMENT_ADMIN / ORG_MANAGER / SCHOOL_LEADER / ADMIN / SYS_ADMIN / SUPER_ADMIN | 3 |
| USER | 3 |
| USER / COUNSELOR / ASSESSMENT_ADMIN / ORG_MANAGER / ADMIN / SYS_ADMIN / SUPER_ADMIN | 3 |
| ADMIN / SYS_ADMIN / SUPER_ADMIN | 2 |
| COUNSELOR | 2 |
| ASSESSMENT_ADMIN / ADMIN / SUPER_ADMIN | 1 |
| ASSESSMENT_ADMIN / ORG_MANAGER / COUNSELOR / ADMIN / SYS_ADMIN / SUPER_ADMIN | 1 |
