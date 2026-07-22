package org.sainm.psy.notification.service

import org.sainm.auth.core.spi.WechatTemplateMessageService
import org.sainm.psy.notification.domain.PendingPushDelivery
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

/**
 * [PushDeliveryGateway] that delivers notifications as WeChat Official Account
 * template messages. The `pushTokenSnapshot` on a delivery record is treated as
 * the WeChat openid (set at device-registration time for WeChat OAuth users).
 */
@Component
@ConditionalOnProperty(prefix = "psy.notification.wechat", name = ["enabled"], havingValue = "true")
class WechatTemplateMessageGateway(
    private val templateMessageService: WechatTemplateMessageService?,
    @Value("\${psy.notification.wechat.template-id:}")
    private val templateId: String?
) : PushDeliveryGateway {

    private val log = LoggerFactory.getLogger(WechatTemplateMessageGateway::class.java)

    override fun send(delivery: PendingPushDelivery): PushDeliveryAttemptResult {
        val svc = templateMessageService
        val tpl = templateId
        if (svc == null || tpl == null) {
            return PushDeliveryAttemptResult(success = false, errorMessage = "WECHAT_NOT_CONFIGURED")
        }
        val openId = delivery.pushTokenSnapshot?.trim()
        if (openId.isNullOrBlank()) {
            return PushDeliveryAttemptResult(success = false, errorMessage = "WECHAT_OPENID_MISSING")
        }
        return try {
            svc.send(
                openId = openId,
                templateId = tpl,
                data = mapOf(
                    "first" to mapOf("value" to delivery.title, "color" to "#173177"),
                    "keyword1" to mapOf("value" to delivery.content, "color" to "#173177"),
                    "remark" to mapOf(
                        "value" to (delivery.deepLink ?: ""),
                        "color" to "#888888"
                    )
                )
            )
            PushDeliveryAttemptResult(success = true)
        } catch (ex: Exception) {
            log.error("WeChat template message send failed: {}", ex.message, ex)
            PushDeliveryAttemptResult(success = false, errorMessage = ex.message)
        }
    }
}
