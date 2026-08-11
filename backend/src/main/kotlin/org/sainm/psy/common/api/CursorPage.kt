package org.sainm.psy.common.api

/**
 * A bounded, keyset-paginated response for append-only histories.
 *
 * `nextCursor` is the last returned row id. The next request must ask for
 * rows with an id smaller than that value because history endpoints are
 * ordered newest-first. Keeping the cursor as a server-generated identifier
 * avoids exposing offset-based scans to callers while preserving a small,
 * backwards-compatible JSON shape.
 */
data class CursorPage<T>(
    val list: List<T>,
    val nextCursor: Long?,
    val limit: Int
)
