package org.sainm.psy.scale.domain

import java.time.LocalDateTime

data class ScaleSummary(
    val id: Long,
    val scaleCode: String,
    val scaleName: String,
    val applicableTarget: String?,
    val versionNo: String?,
    val status: String,
    val anonymousSupported: Boolean,
    val createdAt: LocalDateTime
)

data class ScaleDimension(
    val id: Long,
    val scaleId: Long,
    val dimensionCode: String,
    val dimensionName: String,
    val description: String?,
    val sortNo: Int
)

data class ScaleDetail(
    val id: Long,
    val scaleCode: String,
    val scaleName: String,
    val description: String?,
    val applicableTarget: String?,
    val versionNo: String?,
    val status: String,
    val anonymousSupported: Boolean,
    val reportTemplate: String?,
    val createdBy: Long?,
    val createdAt: LocalDateTime,
    val updatedBy: Long?,
    val updatedAt: LocalDateTime,
    val dimensions: List<ScaleDimension>
)
