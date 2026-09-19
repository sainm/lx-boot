"""Shared helpers for the manual-test check modules.

The individual check modules used to re-implement the same HTTP helper and the
same polling loop (review finding #10).  Keeping one implementation here makes
timeout/error handling consistent across modules.
"""

from __future__ import annotations

import time
from typing import Any, Callable

from harness import CheckFailure, Context


def api(ctx: Context, method: str, path: str, user: str = "assessor", **kwargs: Any):
    """Authenticated request against the manual-test target."""
    return ctx.http(method, path, token=ctx.token(user), **kwargs)


def wait_for(
    predicate: Callable[[], Any],
    *,
    timeout: float,
    interval: float = 2.0,
    label: str,
) -> Any:
    """Poll ``predicate`` until it returns a truthy value or the timeout ends."""
    deadline = time.time() + timeout
    last: Any = None
    while time.time() < deadline:
        last = predicate()
        if last:
            return last
        time.sleep(interval)
    raise CheckFailure(f"timed out waiting for {label} (last={last!r})")
