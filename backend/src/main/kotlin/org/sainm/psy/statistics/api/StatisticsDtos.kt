package org.sainm.psy.statistics.api

data class GroupReportListQuery(
    val taskId: Long? = null,
    val groupId: Long? = null,
    val scaleId: Long? = null,
    val compareUserId: Long? = null,
    val page: Int = 1,
    val size: Int = 20
)
