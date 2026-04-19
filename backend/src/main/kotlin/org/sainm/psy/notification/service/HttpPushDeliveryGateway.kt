package org.sainm.psy.notification.service

import org.sainm.psy.notification.domain.PendingPushDelivery
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestClientResponseException

data class HttpPushDeliveryRequest(
    val provider: String,
    val deliveryId: Long,
    val notificationId: Long,
    val receiverUserId: Long,
    val deviceId: Long?,
    val pushToken: String,
    val title: String?,
    val content: String,
    val deepLink: String?,
    val payloadJson: String?
)

@Component
@ConditionalOnProperty(prefix = "psy.notification.push.http", name = ["enabled"], havingValue = "true")
class HttpPushDeliveryGateway(
    restClientBuilder: RestClient.Builder,
    @Value("\${psy.notification.push.http.endpoint-url}")
    private val endpointUrl: String,
    @Value("\${psy.notification.push.http.authorization-token:}")
    private val authorizationToken: String,
    @Value("\${psy.notification.push.http.provider-name:http}")
    private val providerName: String
) : PushDeliveryGateway {

    private val restClient = restClientBuilder.build()

    override fun send(delivery: PendingPushDelivery): PushDeliveryAttemptResult {
        if (endpointUrl.isBlank()) {
            return PushDeliveryAttemptResult(success = false, errorMessage = "HTTP_PUSH_ENDPOINT_MISSING")
        }
        val token = delivery.pushTokenSnapshot?.trim()
        if (token.isNullOrEmpty()) {
            return PushDeliveryAttemptResult(success = false, errorMessage = "PUSH_TOKEN_MISSING")
        }

        val request = HttpPushDeliveryRequest(
            provider = providerName,
            deliveryId = delivery.id,
            notificationId = delivery.notificationId,
            receiverUserId = delivery.receiverUserId,
            deviceId = delivery.deviceId,
            pushToken = token,
            title = delivery.title,
            content = delivery.content,
            deepLink = delivery.deepLink,
            payloadJson = delivery.payloadJson
        )

        return try {
            restClient.post()
                .uri(endpointUrl)
                .headers { headers ->
                    headers.set("X-Provider", providerName)
                    authorizationToken.trim()
                        .takeIf { it.isNotEmpty() }
                        ?.let { headers.setBearerAuth(it) }
                }
                .body(request)
                .retrieve()
                .toBodilessEntity()
            PushDeliveryAttemptResult(success = true, providerName = providerName)
        } catch (ex: RestClientResponseException) {
            PushDeliveryAttemptResult(
                success = false,
                errorMessage = "HTTP_PUSH_${ex.statusCode.value()}: ${ex.responseBodyAsString.take(200)}"
            )
        } catch (ex: RestClientException) {
            PushDeliveryAttemptResult(success = false, errorMessage = "HTTP_PUSH_ERROR: ${ex.message}".take(240))
        }
    }
}
