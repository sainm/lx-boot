package org.sainm.psy.auth.api

data class AuthProfileResponse(
    val userId: Long,
    val username: String,
    val displayName: String?,
    val roles: List<String>,
    val permissions: List<String>
)
