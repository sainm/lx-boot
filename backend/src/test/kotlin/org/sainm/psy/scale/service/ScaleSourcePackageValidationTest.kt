package org.sainm.psy.scale.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.databind.DeserializationFeature
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.sainm.psy.scale.api.ScaleSourcePackageDocument
import org.sainm.psy.scale.api.SourceAlgorithmBinding
import org.sainm.psy.scale.api.SourceDimension
import org.sainm.psy.scale.api.SourceDimensionRecode
import org.sainm.psy.scale.api.SourceDimensionTranslation
import org.sainm.psy.scale.api.SourceGoldenCase
import org.sainm.psy.scale.api.SourceGovernance
import org.sainm.psy.scale.api.SourceNormFactor
import org.sainm.psy.scale.api.SourceNorms
import org.sainm.psy.scale.api.SourceOption
import org.sainm.psy.scale.api.SourceQualityPolicy
import org.sainm.psy.scale.api.SourceQuestion
import org.sainm.psy.scale.api.SourceQuestionTranslation
import org.sainm.psy.scale.api.SourceRecodeBand
import org.sainm.psy.scale.api.SourceReference
import org.sainm.psy.scale.api.SourceResponseScale
import org.sainm.psy.scale.api.SourceResultRule
import org.sainm.psy.scale.api.SourceResultRuleTranslation
import org.sainm.psy.scale.api.SourceScale
import org.sainm.psy.scale.api.SourceScaleTranslation
import org.sainm.psy.scale.api.SourceScoring
import org.sainm.psy.scale.api.SourceSkipRule
import java.math.BigDecimal
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate

class ScaleSourcePackageValidationTest {

    private val locales = ScaleSourcePackageValidation.REQUIRED_LOCALES
    private val mapper = jacksonObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)

    @Test
    fun `valid minimal generic source package has no errors`() {
        val problems = ScaleSourcePackageValidation.validate(validDocument())

        assertEquals(emptyList<SourcePackageProblem>(), problems)
    }

    @Test
    fun `unsupported format and schema are reported`() {
        val problems = ScaleSourcePackageValidation.validate(
            validDocument().copy(format = "WRONG", schemaVersion = 99)
        )

        assertTrue(problems.any { it.code == "PACKAGE_FORMAT_UNSUPPORTED" })
        assertTrue(problems.any { it.code == "PACKAGE_SCHEMA_UNSUPPORTED" })
    }

    @Test
    fun `scl90 profile requires exactly ninety questions`() {
        val document = validDocument()
        val problems = ScaleSourcePackageValidation.validate(
            document.copy(
                scale = document.scale.copy(
                    algorithmBinding = SourceAlgorithmBinding("SCL90_PROFILE", "1", "RESTRICTED_EXTENSION")
                )
            )
        )

        assertTrue(problems.any { it.code == "SOURCE_PACKAGE_ALGORITHM_UNSUPPORTED" })
    }

    @Test
    fun `accepts one-based response scale and reverse-sum scoring`() {
        val document = validDocument().let { base ->
            base.copy(
                scale = base.scale.copy(
                    scoreMethod = "REVERSE_SUM",
                    responseScale = SourceResponseScale(min = 1, max = 4, labels = listOf("1", "2", "3", "4"))
                ),
                questions = base.questions.map { question ->
                    question.copy(
                        reverseScore = true,
                        options = listOf(
                            SourceOption("A", BigDecimal.ONE, locales.associateWith { "No" }),
                            SourceOption("B", BigDecimal(4), locales.associateWith { "Yes" })
                        )
                    )
                }
            )
        }

        assertEquals(emptyList<SourcePackageProblem>(), ScaleSourcePackageValidation.validate(document))
    }

    @Test
    fun `accepts weighted scoring with coefficient and question weights`() {
        val document = validDocument().let { base ->
            base.copy(
                scale = base.scale.copy(scoreMethod = "WEIGHTED_SUM", scoreCoefficient = BigDecimal("1.25")),
                questions = base.questions.map { question -> question.copy(weightValue = BigDecimal("2.0")) }
            )
        }

        assertEquals(emptyList<SourcePackageProblem>(), ScaleSourcePackageValidation.validate(document))
    }

    @Test
    fun `rejects option score outside declared response scale`() {
        val document = validDocument().let { base ->
            base.copy(
                scale = base.scale.copy(
                    responseScale = SourceResponseScale(min = 0, max = 3, labels = listOf("0", "1", "2", "3"))
                ),
                questions = base.questions.map { question ->
                    question.copy(
                        options = listOf(
                            SourceOption("A", BigDecimal.ZERO, locales.associateWith { "No" }),
                            SourceOption("B", BigDecimal(4), locales.associateWith { "Yes" })
                        )
                    )
                }
            )
        }

        assertTrue(ScaleSourcePackageValidation.validate(document).any { it.code == "SOURCE_PACKAGE_OPTION_INVALID" })
    }

    @Test
    fun `rejects unsupported score method`() {
        val document = validDocument().let { base ->
            base.copy(scale = base.scale.copy(scoreMethod = "PERCENTILE"))
        }

        assertTrue(ScaleSourcePackageValidation.validate(document).any { it.code == "SOURCE_PACKAGE_SCORE_METHOD_UNSUPPORTED" })
    }

    @Test
    fun `rejects invalid quality policy`() {
        val document = validDocument().let { base ->
            base.copy(scale = base.scale.copy(qualityPolicy = SourceQualityPolicy(missingAnswerPolicy = "FOO")))
        }

        assertTrue(ScaleSourcePackageValidation.validate(document).any { it.code == "SOURCE_PACKAGE_QUALITY_POLICY_INVALID" })
    }

    @Test
    fun `rejects invalid norm validity period`() {
        val document = validDocument().copy(
            norms = SourceNorms(
                sourceReference = "https://example.com/norms",
                factorReferenceFromUserText = mapOf(
                    "D1" to SourceNormFactor(
                        mean = BigDecimal("1.0"),
                        sd = BigDecimal("0.5"),
                        validFrom = LocalDate.of(2024, 1, 1),
                        validTo = LocalDate.of(2023, 1, 1)
                    )
                )
            )
        )

        assertTrue(ScaleSourcePackageValidation.validate(document).any { it.code == "SOURCE_PACKAGE_NORM_INVALID" })
    }

    @Test
    fun `rejects derived indices for non scl90 algorithm`() {
        val document = validDocument().copy(
            scoring = SourceScoring(indices = mapOf("GSI" to "global severity index"))
        )

        assertTrue(ScaleSourcePackageValidation.validate(document).any { it.code == "SOURCE_PACKAGE_INDICES_UNSUPPORTED" })
    }

    @Test
    fun `scl90 derived indices are not blocked by index whitelist`() {
        val document = validDocument().let { base ->
            base.copy(
                scale = base.scale.copy(
                    algorithmBinding = SourceAlgorithmBinding("SCL90_PROFILE", "1", "RESTRICTED_EXTENSION")
                ),
                scoring = SourceScoring(indices = mapOf("GSI" to "global severity index"))
            )
        }

        val problems = ScaleSourcePackageValidation.validate(document)
        assertTrue(problems.none { it.code == "SOURCE_PACKAGE_INDICES_UNSUPPORTED" })
        assertTrue(problems.any { it.code == "SOURCE_PACKAGE_ALGORITHM_UNSUPPORTED" })
    }

    @Test
    fun `accepts complete norm metadata`() {
        val document = validDocument().copy(
            norms = SourceNorms(
                sourceReference = "https://example.com/norms",
                factorReferenceFromUserText = mapOf(
                    "D1" to SourceNormFactor(
                        mean = BigDecimal("1.0"),
                        sd = BigDecimal("0.5"),
                        ageMin = 18,
                        ageMax = 25,
                        gender = "MIXED",
                        sampleSize = 500,
                        normVersion = "2024v1",
                        regionCode = "CN",
                        languageCode = "zh-CN",
                        validFrom = LocalDate.of(2024, 1, 1),
                        validTo = LocalDate.of(2026, 1, 1)
                    )
                )
            )
        )

        assertEquals(emptyList<SourcePackageProblem>(), ScaleSourcePackageValidation.validate(document))
    }

    @Test
    fun `rejects unsupported dimension recode rule`() {
        val document = validDocument().let { base ->
            base.copy(
                dimensions = base.dimensions.map { dimension ->
                    dimension.copy(recode = SourceDimensionRecode("ARBITRARY_SCRIPT", emptyList()))
                }
            )
        }

        assertTrue(ScaleSourcePackageValidation.validate(document).any { it.code == "SOURCE_PACKAGE_RECODE_UNSUPPORTED" })
    }

    @Test
    fun `rejects unsupported question type`() {
        val document = validDocument().let { base ->
            base.copy(questions = base.questions.map { question -> question.copy(questionType = "INTERVIEW") })
        }

        assertTrue(ScaleSourcePackageValidation.validate(document).any { it.code == "SOURCE_PACKAGE_QUESTION_TYPE_UNSUPPORTED" })
    }

    @Test
    fun `rejects invalid skip rule`() {
        val document = validDocument().copy(
            skipRules = listOf(SourceSkipRule(whenQuestionNo = 1, whenOptionCode = "A", skipQuestionNos = emptyList()))
        )

        assertTrue(ScaleSourcePackageValidation.validate(document).any { it.code == "SOURCE_PACKAGE_SKIP_RULE_INVALID" })
    }

    @Test
    fun `rejects unsupported dimension aggregation`() {
        val document = validDocument().copy(scoring = SourceScoring(dimensionAggregation = "MEDIAN"))

        assertTrue(
            ScaleSourcePackageValidation.validate(document)
                .any { it.code == "SOURCE_PACKAGE_DIMENSION_AGGREGATION_UNSUPPORTED" }
        )
    }

    @Test
    fun `rejects skip rule whose trigger option is not defined by the trigger question`() {
        val document = withSecondQuestion(validDocument()).copy(
            skipRules = listOf(SourceSkipRule(whenQuestionNo = 1, whenOptionCode = "UNKNOWN", skipQuestionNos = listOf(2)))
        )

        assertTrue(ScaleSourcePackageValidation.validate(document).any { it.code == "SOURCE_PACKAGE_SKIP_RULE_INVALID" })
    }

    @Test
    fun `rejects backward skip rules to prevent branch cycles`() {
        val document = withSecondQuestion(validDocument()).copy(
            skipRules = listOf(SourceSkipRule(whenQuestionNo = 2, whenOptionCode = "A", skipQuestionNos = listOf(1)))
        )

        assertTrue(ScaleSourcePackageValidation.validate(document).any { it.code == "SOURCE_PACKAGE_SKIP_RULE_INVALID" })
    }

    @Test
    fun `rejects rater assessment mode`() {
        val document = validDocument().let { base ->
            base.copy(scale = base.scale.copy(assessmentMode = "RATER"))
        }

        assertTrue(ScaleSourcePackageValidation.validate(document).any { it.code == "SOURCE_PACKAGE_ASSESSMENT_MODE_UNSUPPORTED" })
    }

    @Test
    fun `rejects overlapping dimension recode bands`() {
        val document = validDocument().let { base ->
            base.copy(
                dimensions = base.dimensions.map { dimension ->
                    dimension.copy(
                        recode = SourceDimensionRecode(
                            rule = "RECODE_SUM_TO_0_3",
                            bands = listOf(
                                SourceRecodeBand(BigDecimal.ZERO, BigDecimal(2), BigDecimal.ZERO),
                                SourceRecodeBand(BigDecimal.ONE, BigDecimal(3), BigDecimal.ONE)
                            )
                        )
                    )
                }
            )
        }

        assertTrue(ScaleSourcePackageValidation.validate(document).any { it.code == "SOURCE_PACKAGE_RECODE_INVALID" })
    }

    @Test
    fun `rejects sleep duration recode without question references`() {
        val document = validDocument().let { base ->
            base.copy(
                dimensions = base.dimensions.map { dimension ->
                    dimension.copy(
                        recode = SourceDimensionRecode(
                            rule = "SLEEP_DURATION_RECODE_0_3",
                            bands = listOf(SourceRecodeBand(BigDecimal.ZERO, BigDecimal(360), BigDecimal(3)))
                        )
                    )
                }
            )
        }

        assertTrue(ScaleSourcePackageValidation.validate(document).any { it.code == "SOURCE_PACKAGE_RECODE_INVALID" })
    }

    @Test
    fun `accepts supported dimension recode rule`() {
        val document = validDocument().let { base ->
            base.copy(
                dimensions = base.dimensions.map { dimension ->
                    dimension.copy(
                        recode = SourceDimensionRecode(
                            rule = "RECODE_SUM_TO_0_3",
                            bands = listOf(
                                SourceRecodeBand(BigDecimal.ZERO, BigDecimal.ONE, BigDecimal.ZERO),
                                SourceRecodeBand(BigDecimal(2), BigDecimal(3), BigDecimal.ONE)
                            )
                        )
                    )
                }
            )
        }

        assertTrue(ScaleSourcePackageValidation.validate(document).none { it.code.startsWith("SOURCE_PACKAGE_RECODE") })
    }

    @Test
    fun `scl90 profile requires canonical zero-to-four response scale`() {
        val document = validDocument().let { base ->
            base.copy(
                scale = base.scale.copy(
                    algorithmBinding = SourceAlgorithmBinding("SCL90_PROFILE", "1", "RESTRICTED_EXTENSION"),
                    responseScale = SourceResponseScale(min = 1, max = 4, labels = listOf("1", "2", "3", "4"))
                )
            )
        }

        assertTrue(ScaleSourcePackageValidation.validate(document).any { it.code == "SOURCE_PACKAGE_ALGORITHM_UNSUPPORTED" })
    }

    @Test
    fun `missing optional scoring fields fall back to defaults on deserialization`() {
        val json = """
            {
              "scale": {
                "scaleCode": "K6",
                "scaleName": "K6",
                "scoreMethod": "SIMPLE_SUM"
              },
              "governance": {},
              "questions": [
                { "questionNo": 1, "dimensionCode": "D1" }
              ]
            }
        """.trimIndent()

        val document = mapper.readValue(json, ScaleSourcePackageDocument::class.java)

        assertEquals(BigDecimal.ONE, document.scale.scoreCoefficient)
        assertEquals(BigDecimal.ONE, document.questions.first().weightValue)
        assertEquals("REJECT", document.scale.qualityPolicy.missingAnswerPolicy)
    }

    @Test
    fun `existing scl90 source package passes generic validation`() {
        val json = Files.readString(Path.of("../doc/scale-packages/scl90-v1-source-draft.json"))
        val document = mapper.readValue(json, ScaleSourcePackageDocument::class.java)

        val problems = ScaleSourcePackageValidation.validate(document)

        assertTrue(problems.isEmpty(), problems.joinToString("\n") { "${it.path}: ${it.code}" })
    }

    @Test
    fun `official free use k6 source package passes generic validation`() {
        val json = Files.readString(Path.of("../doc/scale-packages/k6-v1-source-official-draft.json"))
        val document = mapper.readValue(json, ScaleSourcePackageDocument::class.java)

        val problems = ScaleSourcePackageValidation.validate(document)

        assertTrue(problems.isEmpty(), problems.joinToString("\n") { "${it.path}: ${it.code}" })
        assertEquals("NOT_REQUIRED", document.governance.authorizationStatus)
        assertEquals(setOf("zh-CN", "ja-JP", "en"), document.translations.keys)
        assertEquals(setOf("NORMAL", "BOUNDARY", "REVERSE", "MISSING", "INVALID"), document.goldenCases.map { it.caseType }.toSet())
    }

    private fun validDocument(): ScaleSourcePackageDocument {
        val emptyObject = mapper.readTree("{}")
        return ScaleSourcePackageDocument(
            scale = SourceScale(
                scaleCode = "K6",
                scaleName = "K6",
                responseScale = SourceResponseScale(labels = listOf("0", "1", "2", "3", "4")),
                algorithmBinding = SourceAlgorithmBinding("GENERIC_SCORE_CALCULATOR", "1", "BUILTIN"),
                instruction = locales.associateWith { "Instruction" }
            ),
            governance = SourceGovernance(
                sourceTitle = "K6 manual",
                copyrightStatus = "AUTHORIZED",
                authorizationStatus = "AUTHORIZED",
                nonDiagnosticStatement = "Screening only"
            ),
            translations = locales.associateWith {
                SourceScaleTranslation("K6", nonDiagnosticText = "Screening only")
            },
            dimensions = listOf(
                SourceDimension(
                    dimensionCode = "D1",
                    questionNos = listOf(1),
                    translations = locales.associateWith { SourceDimensionTranslation("Dimension") }
                )
            ),
            questions = listOf(
                SourceQuestion(
                    questionNo = 1,
                    dimensionCode = "D1",
                    translations = locales.associateWith { SourceQuestionTranslation("Question") },
                    options = listOf(
                        SourceOption("A", BigDecimal.ZERO, locales.associateWith { "No" }),
                        SourceOption("B", BigDecimal.ONE, locales.associateWith { "Yes" })
                    )
                )
            ),
            resultRules = listOf(
                SourceResultRule(
                    ruleCode = "R1",
                    dimensionCode = null,
                    riskLevel = "NORMAL",
                    scoreMin = BigDecimal.ZERO,
                    scoreMax = BigDecimal(4),
                    translations = locales.associateWith {
                        SourceResultRuleTranslation("Normal", "Normal range", "No action")
                    }
                )
            ),
            goldenCases = listOf(
                SourceGoldenCase("CASE-1", "NORMAL", null, emptyObject, emptyObject)
            ),
            sourceReferences = listOf(SourceReference("K6 manual", "https://example.com/k6"))
        )
    }

    private fun withSecondQuestion(document: ScaleSourcePackageDocument): ScaleSourcePackageDocument {
        val firstQuestion = document.questions.single()
        return document.copy(
            dimensions = document.dimensions.map { it.copy(questionNos = listOf(1, 2)) },
            questions = document.questions + firstQuestion.copy(questionNo = 2)
        )
    }
}
