package org.sainm.psy.common.web

import org.sainm.psy.common.api.ApiResponse
import org.sainm.psy.common.exception.BizException
import org.springframework.http.HttpStatus
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(BizException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleBizException(ex: BizException): ApiResponse<Nothing> =
        ApiResponse.fail(ex.code, ex.message)

    @ExceptionHandler(MethodArgumentNotValidException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleValidationException(ex: MethodArgumentNotValidException): ApiResponse<Nothing> {
        val message = ex.bindingResult.fieldErrors
            .firstOrNull()
            ?.defaultMessage
            ?: "请求参数不合法"
        return ApiResponse.fail("VALIDATION_ERROR", message)
    }

    @ExceptionHandler(IllegalArgumentException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleIllegalArgument(ex: IllegalArgumentException): ApiResponse<Nothing> =
        ApiResponse.fail("BAD_REQUEST", ex.message ?: "请求错误")

    @ExceptionHandler(Exception::class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    fun handleOtherException(ex: Exception): ApiResponse<Nothing> =
        ApiResponse.fail("INTERNAL_ERROR", ex.message ?: "服务器异常")
}
