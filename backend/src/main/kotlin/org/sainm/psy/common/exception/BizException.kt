package org.sainm.psy.common.exception

class BizException(
    val code: String,
    override val message: String
) : RuntimeException(message)
