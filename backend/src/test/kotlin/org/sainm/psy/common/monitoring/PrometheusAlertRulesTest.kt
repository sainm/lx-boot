package org.sainm.psy.common.monitoring

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.yaml.snakeyaml.Yaml
import java.nio.file.Files
import java.nio.file.Path

class PrometheusAlertRulesTest {

    @Test
    fun `alert rules are valid YAML and cover required low-cardinality signals`() {
        val rulesPath = Path.of("..", "ops", "prometheus", "psy-alert-rules.yml").normalize()
        assertTrue(Files.isRegularFile(rulesPath), "missing Prometheus alert rules: $rulesPath")
        val text = Files.readString(rulesPath)
        val document = Yaml().load<Map<String, Any>>(text)
        val groups = document["groups"] as List<*>
        val alerts = groups
            .flatMap { (it as Map<*, *>)["rules"] as List<*> }
            .map { (it as Map<*, *>)["alert"] as String }

        assertEquals(alerts.size, alerts.toSet().size)
        assertTrue(alerts.containsAll(REQUIRED_ALERTS))
        FORBIDDEN_HIGH_CARDINALITY_LABELS.forEach { label ->
            assertTrue(!Regex("[,{]\\s*$label=").containsMatchIn(text), "forbidden metric label: $label")
        }
    }

    companion object {
        private val REQUIRED_ALERTS = setOf(
            "PsyBackendHighServerErrorRate",
            "PsyDatabasePoolSaturation",
            "PsyAssessmentSubmissionFailureRate",
            "PsyScoringFailureDetected",
            "PsyWarningOverdue",
            "PsyNotificationDeadLetterBacklog",
            "PsySchedulerFailureDetected",
            "PsyExportFailureDetected"
        )
        private val FORBIDDEN_HIGH_CARDINALITY_LABELS = setOf(
            "tenant", "tenant_id", "user", "user_id", "task_id", "answer_sheet_id", "submit_token", "scale_code"
        )
    }
}
