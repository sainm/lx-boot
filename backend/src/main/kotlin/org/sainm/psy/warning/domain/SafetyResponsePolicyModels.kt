package org.sainm.psy.warning.domain

import java.time.LocalDateTime

data class SafetyResponsePolicy(
    val id: Long,
    val tenantId: Long?,
    val policyCode: String,
    val versionNo: Int,
    val riskCategory: String,
    val firstResponseMinutes: Int,
    val escalationMinutes: Int,
    val followUpMinutes: Int?,
    val responsibleRole: String,
    val backupRole: String,
    val emergencyContactText: String,
    val status: String,
    val activeFlag: Boolean,
    val approvedBy: Long?,
    val professionalReviewerId: Long?,
    val approvedAt: LocalDateTime?,
    val createdAt: LocalDateTime
)
