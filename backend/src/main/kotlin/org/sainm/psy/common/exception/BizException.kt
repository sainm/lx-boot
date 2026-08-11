package org.sainm.psy.common.exception

open class BizException(
    val code: String,
    override val message: String
) : RuntimeException(message)

class NotFoundBizException(
    code: String,
    message: String
) : BizException(code, message)
