package org.sainm.psy.common.web

import org.sainm.psy.common.api.ApiResponse
import org.sainm.psy.common.exception.BizException
import org.slf4j.LoggerFactory
import org.springframework.context.MessageSource
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.http.HttpStatus
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.authorization.AuthorizationDeniedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestControllerAdvice
import jakarta.servlet.http.HttpServletRequest

@RestControllerAdvice
class GlobalExceptionHandler(
    private val messageSource: MessageSource
) {
    private val logger = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    @ExceptionHandler(BizException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleBizException(ex: BizException, request: HttpServletRequest): ApiResponse<Nothing> {
        logger.warn(
            "Business exception on {} {}: code={}, message={}",
            request.method,
            request.requestURI,
            ex.code,
            ex.message
        )
        return ApiResponse.fail(ex.code, message(ex.code, ex.message))
    }

    @ExceptionHandler(AccessDeniedException::class, AuthorizationDeniedException::class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    fun handleAccessDenied(request: HttpServletRequest): ApiResponse<Nothing> {
        logger.warn("Access denied on {} {}", request.method, request.requestURI)
        return ApiResponse.fail("AUTH_403001", message("AUTH_403001", "Forbidden"))
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleValidationException(ex: MethodArgumentNotValidException, request: HttpServletRequest): ApiResponse<Nothing> {
        val messageCodeOrDefault = ex.bindingResult.fieldErrors
            .firstOrNull()
            ?.defaultMessage
            ?: "VALIDATION_ERROR"
        logger.warn(
            "Validation exception on {} {}: {}",
            request.method,
            request.requestURI,
            messageCodeOrDefault
        )
        return ApiResponse.fail(
            "VALIDATION_ERROR",
            message(messageCodeOrDefault, message("VALIDATION_ERROR", "Invalid request parameters"))
        )
    }

    @ExceptionHandler(IllegalArgumentException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleIllegalArgument(ex: IllegalArgumentException, request: HttpServletRequest): ApiResponse<Nothing> {
        logger.warn(
            "Illegal argument on {} {}: {}",
            request.method,
            request.requestURI,
            ex.message
        )
        return ApiResponse.fail("BAD_REQUEST", message("BAD_REQUEST", ex.message ?: "Bad request"))
    }

    @ExceptionHandler(Exception::class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    fun handleOtherException(ex: Exception, request: HttpServletRequest): ApiResponse<Nothing> {
        logger.error("Unhandled exception on {} {}", request.method, request.requestURI, ex)
        return ApiResponse.fail("INTERNAL_ERROR", message("INTERNAL_ERROR", "Internal server error"))
    }

    private fun message(code: String, defaultMessage: String): String =
        messageSource.getMessage(code, null, defaultMessage, LocaleContextHolder.getLocale()) ?: defaultMessage
}
