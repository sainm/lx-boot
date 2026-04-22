package org.sainm.psy.common.jdbc

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource

fun params(block: MapSqlParameterSource.() -> Unit): MapSqlParameterSource =
    MapSqlParameterSource().apply(block)

fun MapSqlParameterSource.addIfNotNull(name: String, value: Any?) = apply {
    value?.let { addValue(name, it) }
}

fun whereClause(vararg conditions: String?): String = buildString {
    append(" where 1 = 1 ")
    conditions.filterNotNull().forEach { condition ->
        append(" and ").append(condition).append(' ')
    }
}
