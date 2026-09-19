#!/usr/bin/env python3
"""Minimal real HTTP object store for the export-artifact channel (MT-NET-006).

Implements the contract used by ``HttpObjectStorageExportArtifactStorage``:

* ``PUT /{bucket}/{key}``   -> stores the body (requires the API key header)
* ``GET /{bucket}/{key}``   -> returns the stored bytes (404 when absent)
* ``DELETE /{bucket}/{key}``-> removes the object (404 when absent)
* ``POST /__control``       -> ``{"failPut": true|false}`` toggles a fault
  injection mode that answers every PUT with 503, which is how MT-EXP-008
  drives a real export job into DEAD_LETTER without restarting the backend.

Objects are persisted under ``build/reports/manual-test/object-store/<bucket>/``
and every request is appended to ``object-store.jsonl``.
"""

from __future__ import annotations

import argparse
import json
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import unquote

ROOT = Path(__file__).resolve().parents[2]
BASE = ROOT / "build" / "reports" / "manual-test" / "object-store"
LOG = ROOT / "build" / "reports" / "manual-test" / "object-store.jsonl"


class Handler(BaseHTTPRequestHandler):
    api_key_header = "X-Api-Key"
    api_key = ""
    fail_put = False

    def _record(self, method: str, status: int, key: Path | None, size: int) -> None:
        LOG.parent.mkdir(parents=True, exist_ok=True)
        with LOG.open("a", encoding="utf-8") as handle:
            handle.write(
                json.dumps(
                    {
                        "at": time.strftime("%Y-%m-%dT%H:%M:%S%z"),
                        "method": method,
                        "path": self.path,
                        "status": status,
                        "object": str(key.relative_to(BASE)) if key else None,
                        "bytes": size,
                        "apiKeyPresent": bool(self.headers.get(self.api_key_header)),
                    },
                    ensure_ascii=False,
                )
                + "\n"
            )

    def _authorized(self) -> bool:
        if not self.api_key:
            return True
        return self.headers.get(self.api_key_header) == self.api_key

    def _target(self) -> Path | None:
        path = unquote(self.path.split("?")[0]).strip("/")
        if not path or ".." in path:
            return None
        target = (BASE / path).resolve()
        return target if str(target).startswith(str(BASE.resolve())) else None

    def _read_body(self) -> bytes:
        if "chunked" in (self.headers.get("Transfer-Encoding") or "").lower():
            body = b""
            while True:
                size_line = self.rfile.readline().strip().split(b";")[0]
                if not size_line:
                    break
                size = int(size_line, 16)
                if size == 0:
                    self.rfile.readline()
                    break
                body += self.rfile.read(size)
                self.rfile.readline()
            return body
        return self.rfile.read(int(self.headers.get("Content-Length") or 0))

    def _respond(self, status: int, payload: bytes = b"", content_type: str = "application/octet-stream") -> None:
        self.send_response(status)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(payload)))
        self.end_headers()
        if payload:
            self.wfile.write(payload)

    def do_PUT(self) -> None:  # noqa: N802 - http.server API
        target = self._target()
        body = self._read_body()
        if target is None:
            self._respond(400)
            return
        if not self._authorized():
            self._respond(401)
            self._record("PUT", 401, target, len(body))
            return
        if Handler.fail_put:
            self._respond(503, b'{"error":"injected failure"}', "application/json")
            self._record("PUT", 503, target, len(body))
            return
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(body)
        self._respond(200, b'{"ok":true}', "application/json")
        self._record("PUT", 200, target, len(body))

    def do_POST(self) -> None:  # noqa: N802 - http.server API
        path = unquote(self.path.split("?")[0]).strip("/")
        if path != "__control":
            self._respond(404)
            return
        body = self._read_body()
        try:
            payload = json.loads(body.decode("utf-8") or "{}")
        except json.JSONDecodeError:
            self._respond(400, b'{"error":"invalid json"}', "application/json")
            return
        if "failPut" in payload:
            Handler.fail_put = bool(payload["failPut"])
        self._respond(200, json.dumps({"failPut": Handler.fail_put}).encode("utf-8"), "application/json")
        self._record("POST", 200, None, len(body))

    def do_GET(self) -> None:  # noqa: N802 - http.server API
        target = self._target()
        if target is None or not target.exists():
            self._respond(404)
            self._record("GET", 404, target, 0)
            return
        if not self._authorized():
            self._respond(401)
            self._record("GET", 401, target, 0)
            return
        body = target.read_bytes()
        self._respond(200, body)
        self._record("GET", 200, target, len(body))

    def do_DELETE(self) -> None:  # noqa: N802 - http.server API
        target = self._target()
        if target is None or not target.exists():
            self._respond(404)
            self._record("DELETE", 404, target, 0)
            return
        target.unlink()
        self._respond(200, b'{"ok":true}', "application/json")
        self._record("DELETE", 200, target, 0)

    def log_message(self, *args: object) -> None:
        return


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=9100)
    parser.add_argument("--api-key", default="mt-object-key")
    args = parser.parse_args()
    Handler.api_key = args.api_key
    BASE.mkdir(parents=True, exist_ok=True)
    print(f"http object store listening on http://{args.host}:{args.port} -> {BASE}")
    ThreadingHTTPServer((args.host, args.port), Handler).serve_forever()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
