package org.sainm.psy.common.web

import org.sainm.auth.core.exception.AuthException
import org.sainm.psy.common.api.ApiResponse
import org.sainm.psy.common.exception.BizException
import org.sainm.psy.common.exception.NotFoundBizException
import org.slf4j.LoggerFactory
import org.springframework.context.MessageSource
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.authorization.AuthorizationDeniedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.web.servlet.resource.NoResourceFoundException
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestControllerAdvice
import jakarta.servlet.http.HttpServletRequest

@RestControllerAdvice
class GlobalExceptionHandler(
    private val messageSource: MessageSource
) {
    private val logger = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    @ExceptionHandler(AuthException::class)
    fun handleAuthException(ex: AuthException, request: HttpServletRequest): ResponseEntity<ApiResponse<Nothing>> {
        val status = when {
            ex.code.startsWith("AUTH_400") -> HttpStatus.BAD_REQUEST
            ex.code.startsWith("AUTH_403") -> HttpStatus.FORBIDDEN
            ex.code.startsWith("AUTH_409") -> HttpStatus.CONFLICT
            else -> HttpStatus.UNAUTHORIZED
        }
        logger.warn(
            "Authentication request rejected on {} {}: code={}",
            request.method,
            request.requestURI,
            ex.code
        )
        val messageKey = ex.message ?: ex.code
        val localizedMessage = messageSource.getMessage(
            messageKey,
            ex.messageArgs,
            messageKey,
            LocaleContextHolder.getLocale()
        ) ?: messageKey
        return ResponseEntity.status(status).body(ApiResponse.fail(ex.code, localizedMessage))
    }

    @ExceptionHandler(NotFoundBizException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    fun handleNotFoundException(ex: NotFoundBizException, request: HttpServletRequest): ApiResponse<Nothing> {
        logger.warn(
            "Resource not found on {} {}: code={}",
            request.method,
            request.requestURI,
            ex.code
        )
        return ApiResponse.fail(ex.code, message(ex.code, ex.message))
    }

    @ExceptionHandler(BizException::class)
    fun handleBizException(ex: BizException, request: HttpServletRequest): ResponseEntity<ApiResponse<Nothing>> {
        val status = if (ex.code.endsWith("_NOT_FOUND")) HttpStatus.NOT_FOUND else HttpStatus.BAD_REQUEST
        logger.warn(
            "Business exception on {} {}: status={}, code={}, message={}",
            request.method,
            request.requestURI,
            status.value(),
            ex.code,
            ex.message
        )
        return ResponseEntity.status(status).body(ApiResponse.fail(ex.code, message(ex.code, ex.message)))
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
        val raw = ex.message
        // Auth-starter style message keys ("auth.*") must keep their specific
        // localized text instead of collapsing into the generic BAD_REQUEST.
        return if (raw != null && raw.startsWith("auth.")) {
            ApiResponse.fail("AUTH_400002", message(raw, raw))
        } else {
            ApiResponse.fail("BAD_REQUEST", message("BAD_REQUEST", raw ?: "Bad request"))
        }
    }

    /**
     * Missing or malformed query parameters are client errors: answer 400 with
     * the standard envelope instead of letting them fall through to the
     * catch-all 500 handler (e.g. ``GET /wechat/portal`` without signature).
     */
    @ExceptionHandler(MissingServletRequestParameterException::class, MethodArgumentTypeMismatchException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleRequestParameterErrors(ex: Exception, request: HttpServletRequest): ApiResponse<Nothing> {
        logger.warn(
            "Parameter binding failure on {} {}: {}",
            request.method,
            request.requestURI,
            ex.message
        )
        return ApiResponse.fail(
            "VALIDATION_ERROR",
            message("VALIDATION_ERROR", "Invalid request parameters")
        )
    }

    /**
     * Unknown API paths must fail with a 404 envelope instead of surfacing as a
     * 500 from the static-resource fallback.
     */
    @ExceptionHandler(NoResourceFoundException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    fun handleNoResource(ex: NoResourceFoundException, request: HttpServletRequest): ApiResponse<Nothing> {
        logger.warn("Unknown resource on {} {}: {}", request.method, request.requestURI, ex.message)
        return ApiResponse.fail("RESOURCE_NOT_FOUND", message("RESOURCE_NOT_FOUND", "Resource not found"))
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException::class)
    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    fun handleMethodNotSupported(
        ex: HttpRequestMethodNotSupportedException,
        request: HttpServletRequest
    ): ApiResponse<Nothing> {
        logger.warn("Method not allowed on {} {}: {}", request.method, request.requestURI, ex.message)
        return ApiResponse.fail("METHOD_NOT_ALLOWED", message("METHOD_NOT_ALLOWED", "Method not allowed"))
    }

    /**
     * Unreadable or incomplete request bodies (for example a missing mandatory
     * JSON field) are client errors, never 500s.
     */
    @ExceptionHandler(HttpMessageNotReadableException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleUnreadableMessage(
        ex: HttpMessageNotReadableException,
        request: HttpServletRequest
    ): ApiResponse<Nothing> {
        logger.warn("Unreadable request body on {} {}: {}", request.method, request.requestURI, ex.message)
        return ApiResponse.fail("VALIDATION_ERROR", message("VALIDATION_ERROR", "Invalid request body"))
    }

    @ExceptionHandler(DataIntegrityViolationException::class)
    @ResponseStatus(HttpStatus.CONFLICT)
    fun handleDataIntegrity(
        ex: DataIntegrityViolationException,
        request: HttpServletRequest
    ): ApiResponse<Nothing> {
        logger.warn("Data integrity conflict on {} {}: {}", request.method, request.requestURI, ex.message)
        return ApiResponse.fail("DATA_CONFLICT", message("DATA_CONFLICT", "Data conflict"))
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
