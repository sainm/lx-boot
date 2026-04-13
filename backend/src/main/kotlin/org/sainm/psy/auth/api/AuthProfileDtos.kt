package org.sainm.psy.auth.api

data class AuthProfileResponse(
    val userId: Long,
    val username: String,
    val displayName: String?,
    val roles: List<String>,
    val permissions: List<String>
)

data class LoginActivityResponse(
    val id: Long,
    val userId: Long?,
    val principal: String?,
    val loginType: String,
    val result: String,
    val ip: String?,
    val userAgent: String?,
    val location: String?,
    val reason: String?,
    val createdAt: String
)

data class SecurityEventResponse(
    val id: Long,
    val eventType: String,
    val userId: Long?,
    val tenantId: Long?,
    val detail: Map<String, Any?>,
    val ip: String?,
    val createdAt: String
)
