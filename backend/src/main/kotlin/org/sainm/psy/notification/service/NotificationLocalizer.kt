package org.sainm.psy.notification.service

import org.sainm.psy.common.i18n.LocalizedMessages
import org.sainm.psy.common.i18n.SupportedContentLocale
import org.sainm.psy.notification.domain.MyNotificationSummary
import org.sainm.psy.notification.repository.NotificationContextRepository
import org.springframework.stereotype.Service

/**
 * Renders in-app notifications in the language of the reader.
 *
 * Notifications are stored with the text of whoever triggered them; a Japanese
 * respondent assigned by a Chinese operator would otherwise keep reading the
 * Chinese snapshot forever.  Each known type is re-rendered from the message
 * catalogs using the dispatcher payload; when a parameter is missing (legacy
 * rows written before the payload was complete) or the type/key is unknown the
 * stored text is kept as-is.
 */
@Service
class NotificationLocalizer(
    private val messages: LocalizedMessages,
    private val contextRepository: NotificationContextRepository? = null
) {

    private data class Spec(
        val titleKey: String,
        val contentKey: String,
        val params: (MyNotificationSummary, Map<String, Any?>) -> List<Any?>?
    )

    fun localize(summary: MyNotificationSummary): MyNotificationSummary {
        val spec = specs[summary.notificationType] ?: return summary
        val payload = parsePayload(summary.payloadJson)
        val params = spec.params(summary, payload) ?: return summary
        val locale = SupportedContentLocale.currentCode()
        val title = render(locale, spec.titleKey, params) ?: return summary
        val content = render(locale, spec.contentKey, params) ?: return summary
        return summary.copy(title = title, content = content)
    }

    private fun render(locale: String, key: String, params: List<Any?>): String? =
        runCatching { messages.getForLocale(locale, key, *params.toTypedArray()) }
            .getOrNull()
            ?.takeIf { it.isNotBlank() && it != key }

    private fun parsePayload(json: String?): Map<String, Any?> {
        if (json.isNullOrBlank()) {
            return emptyMap()
        }
        return runCatching {
            val mapper = com.fasterxml.jackson.databind.ObjectMapper()
            @Suppress("UNCHECKED_CAST")
            mapper.readValue(json, Map::class.java) as Map<String, Any?>
        }.getOrDefault(emptyMap())
    }

    private fun taskNameOf(summary: MyNotificationSummary, payload: Map<String, Any?>): String? =
        text(payload, "taskName")
            ?: (number(payload, "taskId") ?: summary.bizId)?.let { contextRepository?.findTaskName(it) }

    private fun warningIdOf(summary: MyNotificationSummary, payload: Map<String, Any?>): Long? =
        number(payload, "warningId")
            ?: (number(payload, "interventionId") ?: summary.bizId)?.let { contextRepository?.findInterventionWarningId(it) }

    private val specs: Map<String, Spec> = mapOf(
        "TASK_ASSIGNED" to Spec("task.assigned.title", "task.assigned.content") { summary, payload ->
            taskNameOf(summary, payload)?.let { listOf(it) }
        },
        "TASK_OVERDUE" to Spec("task.overdue.title", "task.overdue.content") { summary, payload ->
            taskNameOf(summary, payload)?.let { listOf(it) }
        },
        "REPORT_GENERATED" to Spec("report.generated.title", "report.generated.content") { _, _ -> emptyList() },
        "REPORT_AUTO_SUBMITTED" to Spec("report.auto_submitted.title", "report.auto_submitted.content") { _, _ -> emptyList() },
        "APPOINTMENT_CREATED" to Spec("appointment.created.title", "appointment.created.content") { summary, payload ->
            (number(payload, "appointmentId") ?: summary.bizId)?.let { listOf(it) }
        },
        "WARNING_CLAIMED" to Spec("warning.claimed.title", "warning.claimed.content") { summary, payload ->
            (number(payload, "warningId") ?: summary.bizId)?.let { listOf(it) }
        },
        "WARNING_ASSIGNED" to Spec("warning.assigned.title", "warning.assigned.content") { summary, payload ->
            (number(payload, "warningId") ?: summary.bizId)?.let { listOf(it) }
        },
        "WARNING_ESCALATED" to Spec("warning.escalated.title", "warning.escalated.content") { summary, payload ->
            (number(payload, "warningId") ?: summary.bizId)?.let { listOf(it) }
        },
        "WARNING_REMINDER" to Spec("warning.reminder.title", "warning.reminder.content") { summary, payload ->
            (number(payload, "warningId") ?: summary.bizId)?.let { listOf(it) }
        },
        "INTERVENTION_CREATED" to Spec("intervention.created.title", "intervention.created.content") { summary, payload ->
            warningIdOf(summary, payload)?.let { listOf(it) }
        },
        "INTERVENTION_CLOSED" to Spec("intervention.closed.title", "intervention.closed.content") { summary, payload ->
            val interventionId = number(payload, "interventionId") ?: summary.bizId
            val warningId = warningIdOf(summary, payload)
            if (interventionId == null || warningId == null) null else listOf(interventionId, warningId)
        },
        "RETEST_TASK_CREATED" to Spec("intervention.retest.created.title", "intervention.retest.created.content") { summary, payload ->
            taskNameOf(summary, payload)?.let { listOf(it) }
        }
    )

    private companion object {
        private fun text(payload: Map<String, Any?>, key: String): String? =
            (payload[key] as? String)?.takeIf { it.isNotBlank() }

        private fun number(payload: Map<String, Any?>, key: String): Long? =
            when (val value = payload[key]) {
                is Number -> value.toLong()
                is String -> value.toLongOrNull()
                else -> null
            }
    }
}
