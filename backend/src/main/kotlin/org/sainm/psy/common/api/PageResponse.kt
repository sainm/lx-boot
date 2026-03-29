package org.sainm.psy.common.api

data class PageResponse<T>(
    val list: List<T>,
    val page: Int,
    val size: Int,
    val total: Long
)
