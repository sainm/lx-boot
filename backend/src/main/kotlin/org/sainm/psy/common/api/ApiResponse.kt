package org.sainm.psy.common.api

data class ApiResponse<T>(
    val code: String,
    val message: String,
    val data: T?
) {
    companion object {
        fun <T> ok(data: T): ApiResponse<T> = ApiResponse(code = "0", message = "OK", data = data)

        fun ok(): ApiResponse<Map<String, Any>> = ok(emptyMap())

        fun fail(code: String, message: String): ApiResponse<Nothing> =
            ApiResponse(code = code, message = message, data = null)
    }
}
