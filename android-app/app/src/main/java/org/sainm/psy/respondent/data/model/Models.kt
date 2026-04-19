package org.sainm.psy.respondent.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ApiEnvelope<T>(
    val code: String,
    val message: String,
    val data: T
)

@Serializable
data class AuthUser(
    val userId: Long,
    val username: String,
    val displayName: String? = null
)

@Serializable
data class PasswordLoginRequest(
    val principal: String,
    val password: String,
    val deviceId: String? = null,
    val deviceType: String? = null,
    val deviceName: String? = null
)

@Serializable
data class PasswordLoginResponse(
    val accessToken: String,
    val refreshToken: String,
    val tokenType: String,
    val expiresIn: Long,
    val user: AuthUser
)

@Serializable
data class RefreshTokenRequest(
    val refreshToken: String
)

@Serializable
data class RefreshTokenResponse(
    val accessToken: String,
    val refreshToken: String,
    val tokenType: String,
    val expiresIn: Long
)

@Serializable
data class LogoutRequest(
    val refreshToken: String
)

@Serializable
data class SessionTokens(
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Long,
    val username: String? = null,
    val displayName: String? = null
)

@Serializable
data class MyAssessmentTask(
    val taskId: Long,
    val taskName: String,
    val scaleId: Long,
    val scaleName: String,
    val endTime: String,
    val status: String
)

@Serializable
data class TaskQuestionOption(
    val optionId: Long,
    val optionCode: String,
    val optionLabel: String,
    val scoreValue: Int,
    val exclusiveFlag: Boolean = false,
    val optionGroupCode: String? = null
)

@Serializable
data class TaskQuestionItem(
    val questionId: Long,
    val questionNo: Int,
    val questionTitle: String,
    val questionType: String,
    val requiredFlag: Boolean,
    val optionSelectionLimit: Int? = null,
    val sliderMin: Int? = null,
    val sliderMax: Int? = null,
    val sliderStep: Int? = null,
    val textInputEnabled: Boolean? = null,
    val textInputPlaceholder: String? = null,
    val matrixGroupCode: String? = null,
    val rowCode: String? = null,
    val columnCode: String? = null,
    val options: List<TaskQuestionOption> = emptyList()
)

@Serializable
data class TaskQuestionPayload(
    val taskId: Long,
    val scaleId: Long,
    val scaleName: String,
    val allowSaveFlag: Boolean,
    val completedFlag: Boolean = false,
    val completedReportId: Long? = null,
    val completedResultId: Long? = null,
    val completedRiskLevel: String? = null,
    val draftAnswerSheetId: Long? = null,
    val draftVersionNo: Long? = null,
    val questions: List<TaskQuestionItem> = emptyList()
)

@Serializable
data class AnswerItemRequest(
    val questionId: Long,
    val optionId: Long? = null,
    val answerText: String? = null,
    val answerValue: Double? = null
)

@Serializable
data class SaveAnswerSheetRequest(
    val taskId: Long,
    val scaleId: Long,
    val answerSheetId: Long? = null,
    val versionNo: Long? = null,
    val answers: List<AnswerItemRequest>
)

@Serializable
data class SubmitAnswerSheetRequest(
    val taskId: Long,
    val scaleId: Long,
    val answerSheetId: Long? = null,
    val versionNo: Long? = null,
    val submitToken: String? = null,
    val answers: List<AnswerItemRequest>
)

@Serializable
data class SaveAnswerSheetResult(
    val answerSheetId: Long,
    val status: String,
    val versionNo: Long
)

@Serializable
data class SubmitAnswerSheetResult(
    val answerSheetId: Long,
    val resultId: Long,
    val reportId: Long,
    val riskLevel: String,
    val versionNo: Long? = null
)

@Serializable
data class MyReportSummary(
    val reportId: Long,
    val resultId: Long,
    val taskId: Long,
    val taskName: String,
    val scaleId: Long,
    val scaleName: String,
    val reportType: String,
    val totalScore: Int,
    val riskLevel: String,
    val scoreSource: String? = null,
    val standardScore: Double? = null,
    val createdAt: String
)

@Serializable
data class ReportAnswerDetail(
    val questionId: Long,
    val questionNo: Int,
    val questionTitle: String,
    val questionType: String,
    val optionCode: String? = null,
    val optionLabel: String? = null,
    val answerText: String? = null,
    val answerValue: Double? = null,
    val scoreValue: Double? = null
)

@Serializable
data class ReportDetail(
    val reportId: Long,
    val resultId: Long,
    val reportType: String,
    val totalScore: Int,
    val riskLevel: String,
    val content: String,
    val answerDetails: List<ReportAnswerDetail> = emptyList()
)

@Serializable
data class CounselorOption(
    val userId: Long,
    val username: String,
    val displayName: String
)

@Serializable
data class CounselorSchedule(
    val id: Long,
    val counselorUserId: Long,
    val scheduleDate: String,
    val startTime: String,
    val endTime: String,
    val quotaCount: Int,
    val status: String
)

@Serializable
data class AppointmentSummary(
    val id: Long,
    val userId: Long,
    val counselorUserId: Long,
    val counselorDisplayName: String? = null,
    val warningId: Long? = null,
    val scheduleId: Long? = null,
    val appointmentStatus: String,
    val sourceType: String,
    val remark: String? = null,
    val scheduleDate: String? = null,
    val startTime: String? = null,
    val endTime: String? = null,
    val createdAt: String
)

@Serializable
data class CreateAppointmentRequest(
    val counselorUserId: Long,
    val scheduleId: Long,
    val warningId: Long? = null,
    val remark: String? = null
)

@Serializable
data class AppointmentActionResult(
    @SerialName("appointmentId")
    val id: Long,
    val status: String
)

@Serializable
data class MyNotification(
    val id: Long,
    val notificationType: String,
    val title: String,
    val content: String,
    val bizType: String? = null,
    val bizId: Long? = null,
    val targetPath: String? = null,
    val readFlag: Boolean,
    val readTime: String? = null,
    val createdAt: String
)

@Serializable
data class NotificationActionResult(
    val notificationId: Long,
    val readFlag: Boolean
)
