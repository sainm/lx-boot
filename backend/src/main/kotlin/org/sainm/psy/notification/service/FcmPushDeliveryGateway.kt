package org.sainm.psy.notification.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.annotation.JsonProperty
import org.sainm.psy.notification.domain.PendingPushDelivery
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestClientResponseException
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.time.Instant
import java.util.Base64

data class FcmAccessTokenResponse(
    val access_token: String,
    val token_type: String,
    val expires_in: Long
)

data class FcmNotificationPayload(
    val title: String?,
    val body: String
)

data class FcmAndroidNotificationPayload(
    @field:JsonProperty("click_action")
    val clickAction: String? = null
)

data class FcmAndroidPayload(
    val priority: String = "HIGH",
    val notification: FcmAndroidNotificationPayload? = null
)

data class FcmMessagePayload(
    val token: String,
    val notification: FcmNotificationPayload,
    val data: Map<String, String> = emptyMap(),
    val android: FcmAndroidPayload? = null
)

data class FcmSendRequest(
    val message: FcmMessagePayload
)

data class FcmSendResponse(
    val name: String? = null
)

private data class FcmServiceAccount(
    val projectId: String,
    val clientEmail: String,
    val privateKey: String,
    val tokenUri: String
)

private data class FcmCachedAccessToken(
    val token: String,
    val expireAtEpochSecond: Long
)

@Component
@ConditionalOnProperty(prefix = "psy.notification.push.fcm", name = ["enabled"], havingValue = "true")
class FcmPushDeliveryGateway(
    restClientBuilder: RestClient.Builder,
    private val objectMapper: ObjectMapper,
    @Value("\${psy.notification.push.fcm.project-id:}")
    private val configuredProjectId: String,
    @Value("\${psy.notification.push.fcm.service-account-json:}")
    private val serviceAccountJson: String,
    @Value("\${psy.notification.push.fcm.service-account-file:}")
    private val serviceAccountFile: String,
    @Value("\${psy.notification.push.fcm.api-base-url:https://fcm.googleapis.com}")
    private val apiBaseUrl: String,
    @Value("\${psy.notification.push.fcm.scope:https://www.googleapis.com/auth/firebase.messaging}")
    private val scope: String
) : PushDeliveryGateway {

    private val restClient = restClientBuilder.build()

    @Volatile
    private var cachedAccessToken: FcmCachedAccessToken? = null

    override fun send(delivery: PendingPushDelivery): PushDeliveryAttemptResult {
        val pushToken = delivery.pushTokenSnapshot?.trim()
        if (pushToken.isNullOrEmpty()) {
            return PushDeliveryAttemptResult(success = false, errorMessage = "PUSH_TOKEN_MISSING")
        }

        val account = loadServiceAccount()
            ?: return PushDeliveryAttemptResult(success = false, errorMessage = "FCM_SERVICE_ACCOUNT_MISSING")
        val projectId = configuredProjectId.trim().ifEmpty { account.projectId }
        if (projectId.isBlank()) {
            return PushDeliveryAttemptResult(success = false, errorMessage = "FCM_PROJECT_ID_MISSING")
        }

        return try {
            val accessToken = resolveAccessToken(account)
            val request = FcmSendRequest(
                message = FcmMessagePayload(
                    token = pushToken,
                    notification = FcmNotificationPayload(
                        title = delivery.title.ifBlank { null },
                        body = delivery.content
                    ),
                    data = buildDataPayload(delivery),
                    android = FcmAndroidPayload(
                        notification = delivery.deepLink
                            ?.takeIf { it.isNotBlank() }
                            ?.let { FcmAndroidNotificationPayload(clickAction = it) }
                    )
                )
            )

            val response = restClient.post()
                .uri("${apiBaseUrl.trimEnd('/')}/v1/projects/$projectId/messages:send")
                .contentType(MediaType.APPLICATION_JSON)
                .headers { headers -> headers.setBearerAuth(accessToken) }
                .body(request)
                .retrieve()
                .body(FcmSendResponse::class.java)

            PushDeliveryAttemptResult(
                success = true,
                providerName = "fcm",
                providerMessageId = response?.name?.trim()?.ifEmpty { null }
            )
        } catch (ex: RestClientResponseException) {
            PushDeliveryAttemptResult(
                success = false,
                errorMessage = "FCM_${ex.statusCode.value()}: ${ex.responseBodyAsString.take(200)}"
            )
        } catch (ex: RestClientException) {
            PushDeliveryAttemptResult(success = false, errorMessage = "FCM_HTTP_ERROR: ${ex.message}".take(240))
        } catch (ex: Exception) {
            PushDeliveryAttemptResult(success = false, errorMessage = "FCM_CONFIG_ERROR: ${ex.message}".take(240))
        }
    }

    private fun buildDataPayload(delivery: PendingPushDelivery): Map<String, String> =
        buildMap {
            put("notificationId", delivery.notificationId.toString())
            put("deliveryId", delivery.id.toString())
            put("receiverUserId", delivery.receiverUserId.toString())
            delivery.deviceId?.let { put("deviceId", it.toString()) }
            delivery.deepLink?.takeIf { it.isNotBlank() }?.let { put("deepLink", it) }
            delivery.payloadJson?.takeIf { it.isNotBlank() }?.let { put("payloadJson", it) }
        }

    private fun loadServiceAccount(): FcmServiceAccount? {
        val rawJson = when {
            serviceAccountJson.isNotBlank() -> serviceAccountJson
            serviceAccountFile.isNotBlank() -> java.io.File(serviceAccountFile).takeIf { it.isFile }?.readText(StandardCharsets.UTF_8)
            else -> null
        }?.trim()?.takeIf { it.isNotEmpty() } ?: return null

        val root = objectMapper.readTree(rawJson)
        return FcmServiceAccount(
            projectId = root.requiredText("project_id"),
            clientEmail = root.requiredText("client_email"),
            privateKey = root.requiredText("private_key"),
            tokenUri = root.requiredText("token_uri")
        )
    }

    @Synchronized
    private fun resolveAccessToken(account: FcmServiceAccount): String {
        val now = Instant.now().epochSecond
        cachedAccessToken
            ?.takeIf { it.expireAtEpochSecond - 60 > now }
            ?.let { return it.token }

        val assertion = buildJwtAssertion(account, now)
        val response = restClient.post()
            .uri(account.tokenUri)
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(
                "grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Ajwt-bearer" +
                    "&assertion=${urlEncode(assertion)}"
            )
            .retrieve()
            .body(FcmAccessTokenResponse::class.java)
            ?: throw IllegalStateException("FCM access token response is empty")

        val cached = FcmCachedAccessToken(
            token = response.access_token,
            expireAtEpochSecond = now + response.expires_in
        )
        cachedAccessToken = cached
        return cached.token
    }

    private fun buildJwtAssertion(account: FcmServiceAccount, issuedAtEpochSecond: Long): String {
        val headerJson = """{"alg":"RS256","typ":"JWT"}"""
        val claimJson = objectMapper.writeValueAsString(
            mapOf(
                "iss" to account.clientEmail,
                "scope" to scope,
                "aud" to account.tokenUri,
                "iat" to issuedAtEpochSecond,
                "exp" to issuedAtEpochSecond + 3600
            )
        )
        val encodedHeader = base64Url(headerJson.toByteArray(StandardCharsets.UTF_8))
        val encodedClaims = base64Url(claimJson.toByteArray(StandardCharsets.UTF_8))
        val signingInput = "$encodedHeader.$encodedClaims"
        val signature = Signature.getInstance("SHA256withRSA").apply {
            initSign(parsePrivateKey(account.privateKey))
            update(signingInput.toByteArray(StandardCharsets.UTF_8))
        }.sign()
        return "$signingInput.${base64Url(signature)}"
    }

    private fun parsePrivateKey(pem: String): PrivateKey {
        val normalized = pem
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replace("\\n", "")
            .replace("\n", "")
            .replace("\r", "")
            .trim()
        val spec = PKCS8EncodedKeySpec(Base64.getDecoder().decode(normalized))
        return KeyFactory.getInstance("RSA").generatePrivate(spec)
    }

    private fun JsonNode.requiredText(fieldName: String): String =
        path(fieldName).asText("").trim().ifEmpty { throw IllegalArgumentException("FCM service account missing $fieldName") }

    private fun base64Url(bytes: ByteArray): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    private fun urlEncode(value: String): String =
        java.net.URLEncoder.encode(value, StandardCharsets.UTF_8)
}
