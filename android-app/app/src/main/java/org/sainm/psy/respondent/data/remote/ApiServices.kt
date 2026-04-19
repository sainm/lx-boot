package org.sainm.psy.respondent.data.remote

import kotlinx.serialization.json.Json
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Authenticator
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import okhttp3.logging.HttpLoggingInterceptor
import org.sainm.psy.respondent.core.AppConfig
import org.sainm.psy.respondent.data.local.SessionStorage
import org.sainm.psy.respondent.data.model.ApiEnvelope
import org.sainm.psy.respondent.data.model.AppointmentActionResult
import org.sainm.psy.respondent.data.model.CounselorOption
import org.sainm.psy.respondent.data.model.CounselorSchedule
import org.sainm.psy.respondent.data.model.CreateAppointmentRequest
import org.sainm.psy.respondent.data.model.LogoutRequest
import org.sainm.psy.respondent.data.model.MyAssessmentTask
import org.sainm.psy.respondent.data.model.MyNotification
import org.sainm.psy.respondent.data.model.MyReportSummary
import org.sainm.psy.respondent.data.model.NotificationActionResult
import org.sainm.psy.respondent.data.model.PasswordLoginRequest
import org.sainm.psy.respondent.data.model.PasswordLoginResponse
import org.sainm.psy.respondent.data.model.RefreshTokenRequest
import org.sainm.psy.respondent.data.model.RefreshTokenResponse
import org.sainm.psy.respondent.data.model.ReportDetail
import org.sainm.psy.respondent.data.model.SaveAnswerSheetRequest
import org.sainm.psy.respondent.data.model.SaveAnswerSheetResult
import org.sainm.psy.respondent.data.model.SessionTokens
import org.sainm.psy.respondent.data.model.SubmitAnswerSheetRequest
import org.sainm.psy.respondent.data.model.SubmitAnswerSheetResult
import org.sainm.psy.respondent.data.model.TaskQuestionPayload
import retrofit2.Retrofit
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

private val json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}

interface AuthApiService {
    @POST("auth/login/password")
    suspend fun passwordLogin(@Body request: PasswordLoginRequest): ApiEnvelope<PasswordLoginResponse>

    @POST("auth/token/refresh")
    suspend fun refresh(@Body request: RefreshTokenRequest): ApiEnvelope<RefreshTokenResponse>

    @POST("auth/logout")
    suspend fun logout(@Body request: LogoutRequest): ApiEnvelope<Unit>
}

interface RespondentApiService {
    @GET("api/v1/my/tasks")
    suspend fun fetchMyTasks(): ApiEnvelope<List<MyAssessmentTask>>

    @GET("api/v1/my/tasks/{taskId}/questions")
    suspend fun fetchTaskQuestions(@Path("taskId") taskId: Long): ApiEnvelope<TaskQuestionPayload>

    @POST("api/v1/answer-sheets/save")
    suspend fun saveAnswerSheet(@Body request: SaveAnswerSheetRequest): ApiEnvelope<SaveAnswerSheetResult>

    @POST("api/v1/answer-sheets/submit")
    suspend fun submitAnswerSheet(@Body request: SubmitAnswerSheetRequest): ApiEnvelope<SubmitAnswerSheetResult>

    @GET("api/v1/reports/my")
    suspend fun fetchMyReports(): ApiEnvelope<List<MyReportSummary>>

    @GET("api/v1/reports/{reportId}")
    suspend fun fetchReportDetail(@Path("reportId") reportId: Long): ApiEnvelope<ReportDetail>

    @GET("api/v1/counselors")
    suspend fun fetchCounselors(): ApiEnvelope<List<CounselorOption>>

    @GET("api/v1/counselors/{counselorId}/schedules")
    suspend fun fetchCounselorSchedules(@Path("counselorId") counselorId: Long): ApiEnvelope<List<CounselorSchedule>>

    @GET("api/v1/appointments/my")
    suspend fun fetchMyAppointments(): ApiEnvelope<List<org.sainm.psy.respondent.data.model.AppointmentSummary>>

    @POST("api/v1/appointments")
    suspend fun createAppointment(@Body request: CreateAppointmentRequest): ApiEnvelope<AppointmentActionResult>

    @POST("api/v1/appointments/{appointmentId}/cancel")
    suspend fun cancelAppointment(@Path("appointmentId") appointmentId: Long): ApiEnvelope<AppointmentActionResult>

    @GET("api/v1/my/notifications")
    suspend fun fetchMyNotifications(): ApiEnvelope<List<MyNotification>>

    @POST("api/v1/my/notifications/{notificationId}/read")
    suspend fun markNotificationRead(@Path("notificationId") notificationId: Long): ApiEnvelope<NotificationActionResult>
}

class AuthHeaderInterceptor(private val sessionStorage: SessionStorage) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = sessionStorage.readTokens()?.accessToken
        val request = if (token.isNullOrBlank()) {
            chain.request()
        } else {
            chain.request().newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        }
        return chain.proceed(request)
    }
}

class TokenRefreshAuthenticator(
    private val sessionStorage: SessionStorage
) : Authenticator {
    override fun authenticate(route: Route?, response: Response): Request? {
        if (responseCount(response) >= 2) {
            sessionStorage.clearTokens()
            return null
        }
        val current = sessionStorage.readTokens() ?: return null
        val latestAuth = response.request.header("Authorization")
        if (latestAuth != null && latestAuth != "Bearer ${current.accessToken}") {
            return response.request.newBuilder()
                .header("Authorization", "Bearer ${current.accessToken}")
                .build()
        }

        val refreshed = runCatching { refreshTokens(current) }.getOrNull() ?: return null
        sessionStorage.writeTokens(refreshed)
        return response.request.newBuilder()
            .header("Authorization", "Bearer ${refreshed.accessToken}")
            .build()
    }

    private fun refreshTokens(current: SessionTokens): SessionTokens {
        val client = OkHttpClient.Builder().build()
        val retrofit = Retrofit.Builder()
            .baseUrl(AppConfig.baseUrl)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
        val api = retrofit.create(AuthApiService::class.java)
        val result = runBlocking {
            api.refresh(RefreshTokenRequest(current.refreshToken))
        }
        return SessionTokens(
            accessToken = result.data.accessToken,
            refreshToken = result.data.refreshToken,
            expiresIn = result.data.expiresIn,
            username = current.username,
            displayName = current.displayName
        )
    }

    private fun responseCount(response: Response): Int {
        var current = response.priorResponse
        var count = 1
        while (current != null) {
            count += 1
            current = current.priorResponse
        }
        return count
    }
}

class ApiFactory(private val sessionStorage: SessionStorage) {
    private val converterFactory = json.asConverterFactory("application/json".toMediaType())

    val authApi: AuthApiService by lazy {
        Retrofit.Builder()
            .baseUrl(AppConfig.baseUrl)
            .client(baseClientBuilder().build())
            .addConverterFactory(converterFactory)
            .build()
            .create(AuthApiService::class.java)
    }

    val respondentApi: RespondentApiService by lazy {
        Retrofit.Builder()
            .baseUrl(AppConfig.baseUrl)
            .client(
                baseClientBuilder()
                    .addInterceptor(AuthHeaderInterceptor(sessionStorage))
                    .authenticator(TokenRefreshAuthenticator(sessionStorage))
                    .build()
            )
            .addConverterFactory(converterFactory)
            .build()
            .create(RespondentApiService::class.java)
    }

    private fun baseClientBuilder(): OkHttpClient.Builder {
        return OkHttpClient.Builder()
            .addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BASIC
                }
            )
    }
}
