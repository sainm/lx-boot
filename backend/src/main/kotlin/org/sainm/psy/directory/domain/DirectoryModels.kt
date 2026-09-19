package org.sainm.psy.directory.domain

/** Lightweight user entry used by pickers (booking, reports, audit filters). */
data class DirectoryUser(
    val userId: Long,
    val username: String,
    val displayName: String,
    val status: String
)

/** Lightweight group entry used by report/report-search pickers. */
data class DirectoryGroup(
    val groupId: Long,
    val groupCode: String,
    val groupName: String
)

/** Assessment task entry used by the group-report filter. */
data class DirectoryTask(
    val taskId: Long,
    val taskName: String,
    val scaleId: Long,
    val scaleName: String
)

/** Scale entry used by the publication/governance/report pickers. */
data class DirectoryScale(
    val scaleId: Long,
    val scaleCode: String,
    val scaleName: String,
    val status: String
)
