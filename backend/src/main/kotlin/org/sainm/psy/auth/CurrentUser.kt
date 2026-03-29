package org.sainm.psy.auth

data class CurrentUser(
    val userId: Long,
    val username: String,
    val displayName: String?,
    val tenantId: Long?,
    val groupId: Long?,
    val roles: Set<String>,
    val permissions: Set<String>
)
