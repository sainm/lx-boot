package org.sainm.psy.respondent.data

import org.sainm.psy.respondent.core.AppConfig
import org.sainm.psy.respondent.data.local.SessionStorage
import org.sainm.psy.respondent.data.model.CreateAppointmentRequest
import org.sainm.psy.respondent.data.model.RescheduleAppointmentRequest
import org.sainm.psy.respondent.data.model.PasswordLoginRequest
import org.sainm.psy.respondent.BuildConfig
import org.sainm.psy.respondent.data.model.RegisterDeviceRequest
import org.sainm.psy.respondent.data.model.SaveAnswerSheetRequest
import org.sainm.psy.respondent.data.model.SessionTokens
import org.sainm.psy.respondent.data.model.SubmitAnswerSheetRequest
import org.sainm.psy.respondent.data.remote.ApiFactory

class AuthRepository(
    private val apiFactory: ApiFactory,
    private val sessionStorage: SessionStorage
) {
    fun currentSession(): SessionTokens? = sessionStorage.readTokens()

    suspend fun login(username: String, password: String) = apiFactory.authApi.passwordLogin(
        PasswordLoginRequest(
            principal = username,
            password = password,
            deviceId = sessionStorage.getOrCreateDeviceId(),
            deviceType = AppConfig.deviceType,
            deviceName = AppConfig.deviceName
        )
    ).data.also { response ->
        sessionStorage.writeTokens(
            SessionTokens(
                accessToken = response.accessToken,
                refreshToken = response.refreshToken,
                expiresIn = response.expiresIn,
                username = response.user.username,
                displayName = response.user.displayName
            )
        )
    }

    suspend fun logout() {
        val refreshToken = sessionStorage.readTokens()?.refreshToken
        if (!refreshToken.isNullOrBlank()) {
            runCatching {
                apiFactory.authApi.logout(org.sainm.psy.respondent.data.model.LogoutRequest(refreshToken))
            }
        }
        sessionStorage.clearTokens()
    }
}

class RespondentRepository(
    private val apiFactory: ApiFactory,
    private val sessionStorage: SessionStorage? = null
) {
    suspend fun registerPushToken(pushToken: String) = apiFactory.respondentApi.registerDevice(
        RegisterDeviceRequest(
            deviceType = AppConfig.deviceType,
            deviceId = requireNotNull(sessionStorage) { "Session storage is required for device registration" }.getOrCreateDeviceId(),
            pushToken = pushToken,
            appVersion = BuildConfig.VERSION_NAME
        )
    ).data
    suspend fun reportDeliveryReceived(deliveryId: Long) = apiFactory.respondentApi.reportDeliveryReceived(deliveryId).data
    suspend fun fetchMyTasks() = apiFactory.respondentApi.fetchMyTasks().data
    suspend fun fetchTaskQuestions(taskId: Long) = apiFactory.respondentApi.fetchTaskQuestions(taskId).data
    suspend fun saveAnswerSheet(request: SaveAnswerSheetRequest) = apiFactory.respondentApi.saveAnswerSheet(request).data
    suspend fun submitAnswerSheet(request: SubmitAnswerSheetRequest) = apiFactory.respondentApi.submitAnswerSheet(request).data
    suspend fun fetchMyReports() = apiFactory.respondentApi.fetchMyReports().data
    suspend fun fetchReportDetail(reportId: Long) = apiFactory.respondentApi.fetchReportDetail(reportId).data
    suspend fun fetchCounselors() = apiFactory.respondentApi.fetchCounselors().data
    suspend fun fetchCounselorSchedules(counselorId: Long) = apiFactory.respondentApi.fetchCounselorSchedules(counselorId).data
    suspend fun fetchMyAppointments() = apiFactory.respondentApi.fetchMyAppointments().data
    suspend fun createAppointment(request: CreateAppointmentRequest) = apiFactory.respondentApi.createAppointment(request).data
    suspend fun cancelAppointment(appointmentId: Long) = apiFactory.respondentApi.cancelAppointment(appointmentId).data
    suspend fun rescheduleAppointment(appointmentId: Long, request: RescheduleAppointmentRequest) =
        apiFactory.respondentApi.rescheduleAppointment(appointmentId, request).data
    suspend fun fetchAppointmentHistory(appointmentId: Long) = apiFactory.respondentApi.fetchAppointmentHistory(appointmentId).data
    suspend fun fetchMyNotifications() = apiFactory.respondentApi.fetchMyNotifications().data
    suspend fun markNotificationRead(notificationId: Long) = apiFactory.respondentApi.markNotificationRead(notificationId).data
}
