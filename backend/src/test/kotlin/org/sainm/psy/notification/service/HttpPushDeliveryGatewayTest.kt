package org.sainm.psy.notification.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.sainm.psy.notification.domain.PendingPushDelivery
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withServerError
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient

class HttpPushDeliveryGatewayTest {

    @Test
    fun `send posts delivery to configured endpoint`() {
        val builder = RestClient.builder()
        val server = MockRestServiceServer.bindTo(builder).build()
        val gateway = HttpPushDeliveryGateway(
            restClientBuilder = builder,
            endpointUrl = "https://push.example.test/deliveries",
            authorizationToken = "secret-token",
            providerName = "vendor-proxy"
        )

        server.expect(requestTo("https://push.example.test/deliveries"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("Authorization", "Bearer secret-token"))
            .andExpect(header("X-Provider", "vendor-proxy"))
            .andExpect(header("Idempotency-Key", "notification-delivery-11"))
            .andExpect(jsonPath("$.deliveryId").value(11))
            .andExpect(jsonPath("$.pushToken").value("token-demo"))
            .andExpect(jsonPath("$.deepLink").value("/my/tasks/1"))
            .andRespond(withSuccess("""{"ok":true}""", MediaType.APPLICATION_JSON))

        val result = gateway.send(sampleDelivery())

        assertTrue(result.success)
        server.verify()
    }

    @Test
    fun `send returns failure when endpoint rejects request`() {
        val builder = RestClient.builder()
        val server = MockRestServiceServer.bindTo(builder).build()
        val gateway = HttpPushDeliveryGateway(
            restClientBuilder = builder,
            endpointUrl = "https://push.example.test/deliveries",
            authorizationToken = "",
            providerName = "vendor-proxy"
        )

        server.expect(requestTo("https://push.example.test/deliveries"))
            .andRespond(withServerError().body("provider down"))

        val result = gateway.send(sampleDelivery())

        assertFalse(result.success)
        assertEquals("HTTP_PUSH_500: provider down", result.errorMessage)
        server.verify()
    }

    @Test
    fun `send returns failure when token is missing`() {
        val builder = RestClient.builder()
        val gateway = HttpPushDeliveryGateway(
            restClientBuilder = builder,
            endpointUrl = "https://push.example.test/deliveries",
            authorizationToken = "",
            providerName = "vendor-proxy"
        )

        val result = gateway.send(sampleDelivery(pushTokenSnapshot = " "))

        assertFalse(result.success)
        assertEquals("PUSH_TOKEN_MISSING", result.errorMessage)
    }

    private fun sampleDelivery(pushTokenSnapshot: String? = "token-demo") = PendingPushDelivery(
        id = 11L,
        notificationId = 1001L,
        receiverUserId = 2001L,
        deviceId = 3001L,
        pushTokenSnapshot = pushTokenSnapshot,
        title = "title",
        content = "content",
        deepLink = "/my/tasks/1",
        payloadJson = """{"taskId":1}"""
    )
}
