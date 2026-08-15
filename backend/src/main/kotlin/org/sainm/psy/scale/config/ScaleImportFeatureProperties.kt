package org.sainm.psy.scale.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties(prefix = "psy.scale.import")
data class ScaleImportFeatureProperties(
    var multiSelectEnabled: Boolean = true,
    var sliderEnabled: Boolean = true,
    var matrixEnabled: Boolean = false,
    var textWithOptionEnabled: Boolean = false,
    var timeEnabled: Boolean = true,
    var normScoringEnabled: Boolean = true,
    var highRiskRuleEnabled: Boolean = true
)
