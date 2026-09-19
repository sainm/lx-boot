#!/usr/bin/env python3
"""Minimal harness for executing the manual-test procedure through API + DB.

The harness records one status per case in
``build/reports/manual-test/execution.json`` and writes per-case evidence under
``build/reports/manual-test/evidence``.
"""

from __future__ import annotations

import json
import os
import subprocess
import time
import urllib.error
import urllib.request
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Callable

ROOT = Path(__file__).resolve().parents[2]
REPORT_DIR = ROOT / "build/reports/manual-test"
EVIDENCE_DIR = REPORT_DIR / "evidence"
CASES_FILE = REPORT_DIR / "cases.json"
EXECUTION_FILE = REPORT_DIR / "execution.json"

BASE_URL = os.environ.get("MT_BASE_URL", "http://127.0.0.1:8090")
DB_HOST = os.environ.get("MT_DB_HOST", "127.0.0.1")
DB_PORT = os.environ.get("MT_DB_PORT", "5432")
DB_NAME = os.environ.get("MT_DB_NAME", "lx")
DB_USER = os.environ.get("MT_DB_USER", "lx")
DB_PASSWORD = os.environ.get("MT_DB_PASSWORD", "lx")
DEFAULT_PASSWORD = os.environ.get("MT_DEFAULT_PASSWORD", "ChangeMe123")

STATUS_PASS = "PASS"
STATUS_FAIL = "FAIL"
STATUS_BLOCKED = "BLOCKED"
STATUS_NOT_EXECUTED = "NOT_EXECUTED"


class CheckFailure(AssertionError):
    """Raised when the observed behaviour does not match the expectation."""


class CheckBlocked(RuntimeError):
    """Raised when the case cannot be executed in this environment."""


@dataclass
class Response:
    status: int
    payload: Any
    raw: bytes
    headers: dict[str, str] | None = None

    def code(self) -> str:
        if isinstance(self.payload, dict):
            return str(self.payload.get("code", ""))
        return ""

    def data(self) -> Any:
        if isinstance(self.payload, dict):
            return self.payload.get("data")
        return None


class Context:
    def __init__(self) -> None:
        self._tokens: dict[str, str] = {}
        self._refresh_tokens: dict[str, str] = {}
        self.store: dict[str, Any] = {}
        self._sequence = 0

    def http(
        self,
        method: str,
        path: str,
        token: str | None = None,
        body: Any | None = None,
        headers: dict[str, str] | None = None,
        timeout: int = 30,
        raw_body: bytes | None = None,
        content_type: str | None = None,
    ) -> Response:
        url = BASE_URL + path
        request = urllib.request.Request(url, method=method)
        if token:
            request.add_header("Authorization", "Bearer " + token)
        for key, value in (headers or {}).items():
            request.add_header(key, value)
        payload = None
        if raw_body is not None:
            payload = raw_body
            request.add_header("Content-Type", content_type or "application/octet-stream")
        elif body is not None:
            payload = json.dumps(body).encode("utf-8")
            request.add_header("Content-Type", "application/json")
        try:
            with urllib.request.urlopen(request, data=payload, timeout=timeout) as response:
                raw = response.read()
                parsed = json.loads(raw.decode("utf-8")) if raw[:1] in (b"{", b"[") else raw
                return Response(response.status, parsed, raw, dict(response.headers.items()))
        except urllib.error.HTTPError as error:
            raw = error.read()
            try:
                parsed = json.loads(raw.decode("utf-8"))
            except Exception:
                parsed = {"raw": raw.decode("utf-8", errors="replace")[:400]}
            return Response(error.code, parsed, raw, dict(error.headers.items()) if error.headers else None)

    def login(
        self,
        username: str,
        password: str | None = None,
        *,
        refresh: bool = False,
        device_id: str | None = None,
    ) -> str:
        status, payload = self.login_full(username, password, device_id=device_id)
        if status != 200 or payload.get("code") != "0":
            raise CheckFailure(f"login failed for {username}: HTTP {status} {payload}")
        data = payload["data"]
        self._tokens[username] = data["accessToken"]
        self._refresh_tokens[username] = data["refreshToken"]
        return self._refresh_tokens[username] if refresh else data["accessToken"]

    def login_full(
        self,
        username: str,
        password: str | None = None,
        *,
        device_id: str | None = None,
        device_type: str | None = None,
    ) -> tuple[int, dict[str, Any]]:
        body: dict[str, Any] = {"principal": username, "password": password or DEFAULT_PASSWORD}
        if device_id:
            body["deviceId"] = device_id
            body["deviceType"] = device_type or "WEB"
            body["deviceName"] = device_id
        response = self.http(
            "POST",
            "/auth/login/password",
            body=body,
        )
        payload = response.payload if isinstance(response.payload, dict) else {}
        return response.status, payload

    def put_tokens(self, username: str, access_token: str, refresh_token: str | None = None) -> None:
        self._tokens[username] = access_token
        if refresh_token is not None:
            self._refresh_tokens[username] = refresh_token

    def token(self, username: str) -> str:
        if username not in self._tokens:
            self.login(username)
        return self._tokens[username]

    def refresh_token(self, username: str) -> str:
        if username not in self._refresh_tokens:
            self.login(username)
        return self._refresh_tokens[username]

    def sql(self, query: str, *, schema: str | None = None) -> str:
        environment = dict(os.environ)
        environment["PGPASSWORD"] = DB_PASSWORD
        command = [
            "psql",
            "-X",
            "-A",
            "-t",
            "-h",
            DB_HOST,
            "-p",
            DB_PORT,
            "-U",
            DB_USER,
            "-d",
            DB_NAME,
            "-c",
            query,
        ]
        if schema:
            command[1:1] = ["-v", f"ON_ERROR_STOP=1"]
            environment["PGOPTIONS"] = f"-c search_path={schema}"
        result = subprocess.run(command, capture_output=True, text=True, env=environment)
        if result.returncode != 0:
            raise CheckFailure(f"psql failed: {result.stderr.strip()}")
        return result.stdout.strip()

    def sql_one(self, query: str, *, schema: str | None = None) -> str:
        return self.sql(query, schema=schema).splitlines()[0].strip()

    def unique(self, prefix: str) -> str:
        self._sequence += 1
        return f"{prefix}{int(time.time() * 1000) % 10_000_000}{self._sequence % 10}"


CHECKS: dict[str, Callable[[Context], str]] = {}


def case(case_id: str):
    def decorator(function: Callable[[Context], str]) -> Callable[[Context], str]:
        CHECKS[case_id] = function
        return function

    return decorator


def require(condition: bool, message: str) -> None:
    if not condition:
        raise CheckFailure(message)


def require_code(response: Response, expected_status: int, expected_code: str = "0") -> Any:
    require(
        response.status == expected_status,
        f"expected HTTP {expected_status}, got {response.status}: {response.payload}",
    )
    if expected_code is not None:
        require(
            response.code() == expected_code,
            f"expected code {expected_code}, got {response.code()}: {response.payload}",
        )
    return response.data()


def load_cases() -> list[dict[str, Any]]:
    return json.loads(CASES_FILE.read_text(encoding="utf-8"))


def load_execution() -> dict[str, dict[str, Any]]:
    if EXECUTION_FILE.exists():
        return json.loads(EXECUTION_FILE.read_text(encoding="utf-8"))
    return {}


def save_execution(execution: dict[str, dict[str, Any]]) -> None:
    REPORT_DIR.mkdir(parents=True, exist_ok=True)
    EXECUTION_FILE.write_text(
        json.dumps(execution, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )


def write_evidence(case_id: str, status: str, detail: str) -> None:
    EVIDENCE_DIR.mkdir(parents=True, exist_ok=True)
    path = EVIDENCE_DIR / f"{case_id}.json"
    path.write_text(
        json.dumps(
            {"caseId": case_id, "status": status, "detail": detail, "at": time.strftime("%Y-%m-%dT%H:%M:%S%z")},
            ensure_ascii=False,
            indent=2,
        ),
        encoding="utf-8",
    )


def run_cases(modules: list[str], only: list[str]) -> int:
    cases = load_cases()
    execution = load_execution()
    selected = [
        item
        for item in cases
        if (not modules or item["module"] in modules) and (not only or item["id"] in only)
    ]
    context = Context()
    passed = failed = blocked = skipped = 0
    for item in selected:
        case_id = item["id"]
        check = CHECKS.get(case_id)
        if check is None:
            skipped += 1
            execution[case_id] = {
                **item,
                "status": STATUS_NOT_EXECUTED,
                "detail": "no automated check registered in this batch",
                "at": time.strftime("%Y-%m-%dT%H:%M:%S%z"),
            }
            save_execution(execution)
            continue
        try:
            detail = check(context)
            status = STATUS_PASS
            passed += 1
        except CheckBlocked as error:
            status = STATUS_BLOCKED
            detail = str(error)
            blocked += 1
        except Exception as error:  # noqa: BLE001 - recorded as a failing case
            status = STATUS_FAIL
            detail = f"{type(error).__name__}: {error}"
            failed += 1
        execution[case_id] = {
            **item,
            "status": status,
            "detail": detail,
            "at": time.strftime("%Y-%m-%dT%H:%M:%S%z"),
        }
        write_evidence(case_id, status, detail)
        save_execution(execution)
        print(f"{status:6} {case_id} {item['title']} :: {detail[:160]}")
    print(f"\nselected={len(selected)} passed={passed} failed={failed} blocked={blocked} skipped={skipped}")
    return 0 if failed == 0 else 1
