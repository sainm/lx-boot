package org.sainm.psy.notification.service

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.sainm.psy.notification.domain.PendingPushDelivery
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.content
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import java.security.KeyPairGenerator
import java.util.Base64

class FcmPushDeliveryGatewayTest {

    @Test
    fun `send obtains access token and posts message to fcm api`() {
        val builder = RestClient.builder()
        val server = MockRestServiceServer.bindTo(builder).ignoreExpectOrder(true).build()
        val gateway = FcmPushDeliveryGateway(
            restClientBuilder = builder,
            objectMapper = ObjectMapper(),
            configuredProjectId = "",
            serviceAccountJson = sampleServiceAccountJson(),
            serviceAccountFile = "",
            apiBaseUrl = "https://fcm.googleapis.test",
            scope = "https://www.googleapis.com/auth/firebase.messaging"
        )

        server.expect(requestTo("https://oauth2.googleapis.test/token"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("grant_type=")))
            .andRespond(withSuccess("""{"access_token":"access-demo","token_type":"Bearer","expires_in":3600}""", MediaType.APPLICATION_JSON))

        server.expect(requestTo("https://fcm.googleapis.test/v1/projects/demo-project/messages:send"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer access-demo"))
            .andExpect(jsonPath("$.message.token").value("token-demo"))
            .andExpect(jsonPath("$.message.notification.body").value("content"))
            .andExpect(jsonPath("$.message.data.deepLink").value("/my/tasks/1"))
            .andRespond(withSuccess("""{"name":"projects/demo/messages/1"}""", MediaType.APPLICATION_JSON))

        val result = gateway.send(sampleDelivery())

        assertTrue(result.success)
        server.verify()
    }

    @Test
    fun `send returns failure when service account is missing`() {
        val gateway = FcmPushDeliveryGateway(
            restClientBuilder = RestClient.builder(),
            objectMapper = ObjectMapper(),
            configuredProjectId = "demo-project",
            serviceAccountJson = "",
            serviceAccountFile = "",
            apiBaseUrl = "https://fcm.googleapis.test",
            scope = "https://www.googleapis.com/auth/firebase.messaging"
        )

        val result = gateway.send(sampleDelivery())

        assertFalse(result.success)
        assertEquals("FCM_SERVICE_ACCOUNT_MISSING", result.errorMessage)
    }

    @Test
    fun `send returns provider error when fcm rejects request`() {
        val builder = RestClient.builder()
        val server = MockRestServiceServer.bindTo(builder).ignoreExpectOrder(true).build()
        val gateway = FcmPushDeliveryGateway(
            restClientBuilder = builder,
            objectMapper = ObjectMapper(),
            configuredProjectId = "",
            serviceAccountJson = sampleServiceAccountJson(),
            serviceAccountFile = "",
            apiBaseUrl = "https://fcm.googleapis.test",
            scope = "https://www.googleapis.com/auth/firebase.messaging"
        )

        server.expect(requestTo("https://oauth2.googleapis.test/token"))
            .andRespond(withSuccess("""{"access_token":"access-demo","token_type":"Bearer","expires_in":3600}""", MediaType.APPLICATION_JSON))

        server.expect(requestTo("https://fcm.googleapis.test/v1/projects/demo-project/messages:send"))
            .andRespond(withBadRequest().body("bad registration token"))

        val result = gateway.send(sampleDelivery())

        assertFalse(result.success)
        assertEquals("FCM_400: bad registration token", result.errorMessage)
        server.verify()
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

    private fun sampleServiceAccountJson(): String {
        val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val privateKeyPem = Base64.getEncoder().encodeToString(keyPair.private.encoded)
            .chunked(64)
            .joinToString("\n", prefix = "-----BEGIN PRIVATE KEY-----\n", postfix = "\n-----END PRIVATE KEY-----")
        return """
            {
              "type": "service_account",
              "project_id": "demo-project",
              "private_key_id": "demo-key-id",
              "private_key": ${ObjectMapper().writeValueAsString(privateKeyPem)},
              "client_email": "firebase-adminsdk@example.iam.gserviceaccount.com",
              "client_id": "1234567890",
              "token_uri": "https://oauth2.googleapis.test/token"
            }
        """.trimIndent()
    }
}
