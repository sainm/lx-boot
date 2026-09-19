"""Shared helpers for the external-channel (MT-NET) manual tests.

Every helper talks to a real network peer (SMTP sink, CAS identity provider,
push receiver, HTTP object store) and reads back the peer's own JSONL log as
evidence. When a peer is missing the helpers raise ``CheckBlocked`` with the
exact missing condition instead of pretending the channel passed.
"""

from __future__ import annotations

import http.client
import json
import socket
import time
import urllib.parse
from pathlib import Path
from typing import Any, Callable

from harness import BASE_URL, CheckBlocked, CheckFailure

ROOT = Path(__file__).resolve().parents[2]
REPORT_DIR = ROOT / "build" / "reports" / "manual-test"
SMTP_LOG = REPORT_DIR / "smtp-sink.jsonl"
PUSH_LOG = REPORT_DIR / "push-receiver.jsonl"
CAS_LOG = REPORT_DIR / "cas-idp.jsonl"
OBJECT_STORE_DIR = REPORT_DIR / "object-store"
OBJECT_STORE_LOG = REPORT_DIR / "object-store.jsonl"


def timestamp() -> str:
    return time.strftime("%Y-%m-%dT%H:%M:%S%z")


def tcp_reachable(host: str, port: int, timeout: float = 2.0) -> bool:
    try:
        with socket.create_connection((host, port), timeout=timeout):
            return True
    except OSError:
        return False


def host_port(url: str) -> tuple[str, int]:
    parsed = urllib.parse.urlsplit(url)
    if parsed.scheme not in {"http", "https"}:
        raise CheckFailure(f"unsupported url scheme: {url}")
    return parsed.hostname or "", parsed.port or (443 if parsed.scheme == "https" else 80)


def raw_request(
    url: str,
    method: str = "GET",
    body: Any = None,
    headers: dict[str, str] | None = None,
    timeout: int = 10,
) -> tuple[int, dict[str, str], bytes]:
    """HTTP request that never follows redirects, so 302 targets stay visible."""
    parsed = urllib.parse.urlsplit(url)
    host, port = host_port(url)
    connection_cls = http.client.HTTPSConnection if parsed.scheme == "https" else http.client.HTTPConnection
    connection = connection_cls(host, port, timeout=timeout)
    path = parsed.path or "/"
    if parsed.query:
        path = f"{path}?{parsed.query}"
    payload = body.encode("utf-8") if isinstance(body, str) else body
    try:
        connection.request(method, path, body=payload, headers=headers or {})
        response = connection.getresponse()
        raw = response.read()
        response_headers = {key.lower(): value for key, value in response.getheaders()}
        return response.status, response_headers, raw
    finally:
        connection.close()


def read_jsonl(path: Path) -> list[dict[str, Any]]:
    if not path.exists():
        return []
    entries: list[dict[str, Any]] = []
    for line in path.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line:
            continue
        try:
            entries.append(json.loads(line))
        except json.JSONDecodeError:
            continue
    return entries


def log_offset(path: Path) -> int:
    """Number of entries currently in a JSONL log (used as 'since' marker)."""
    return len(read_jsonl(path))


def wait_for_log_entry(
    path: Path,
    predicate: Callable[[dict[str, Any]], bool],
    *,
    since: int = 0,
    timeout: float = 20.0,
    interval: float = 0.5,
    label: str = "peer log entry",
) -> dict[str, Any]:
    deadline = time.time() + timeout
    while True:
        entries = read_jsonl(path)
        for entry in entries[since:]:
            if predicate(entry):
                return entry
        if time.time() >= deadline:
            raise CheckBlocked(
                f"timed out after {timeout:.0f}s waiting for {label} in {path.relative_to(ROOT)} "
                f"(entries={len(entries)}, expected the peer to receive the request)"
            )
        time.sleep(interval)


def base_url() -> str:
    return BASE_URL
