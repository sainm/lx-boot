#!/usr/bin/env python3
"""Minimal CAS test identity provider for MT-NET-003 / MT-AUTH-027 / MT-SEC-014.

Implements the protocol subset the CAS provider in auth-starter uses:

* ``GET /cas/login?service=<url>[&user=<name>]`` -> 302 back to ``service``
  with a single-use ``ticket=ST-…``.
* ``GET /cas/p3/serviceValidate?ticket=&service=&format=JSON`` -> JSON
  ``serviceResponse/authenticationSuccess`` (or ``authenticationFailure`` for
  unknown/replayed tickets or a service mismatch).

Every request is appended to ``build/reports/manual-test/cas-idp.jsonl``.
"""

from __future__ import annotations

import argparse
import json
import time
import uuid
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import parse_qs, quote, urlparse

ROOT = Path(__file__).resolve().parents[2]
LOG = ROOT / "build" / "reports" / "manual-test" / "cas-idp.jsonl"

DEFAULT_USER = "mtcasuser"
TICKETS: dict[str, dict[str, str]] = {}


def record(entry: dict[str, object]) -> None:
    LOG.parent.mkdir(parents=True, exist_ok=True)
    with LOG.open("a", encoding="utf-8") as handle:
        handle.write(json.dumps({"at": time.strftime("%Y-%m-%dT%H:%M:%S%z"), **entry}, ensure_ascii=False) + "\n")


class Handler(BaseHTTPRequestHandler):
    def _query(self) -> dict[str, list[str]]:
        return parse_qs(urlparse(self.path).query)

    def _json(self, status: int, payload: dict[str, object]) -> None:
        body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json;charset=UTF-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self) -> None:  # noqa: N802 - http.server API
        path = urlparse(self.path).path.rstrip("/") or "/"
        query = self._query()

        if path == "/cas/login":
            service = (query.get("service") or [""])[0]
            user = (query.get("user") or [DEFAULT_USER])[0]
            if not service:
                self._json(400, {"error": "service is required"})
                record({"path": path, "status": 400, "reason": "service missing"})
                return
            ticket = f"ST-{uuid.uuid4().hex}"
            TICKETS[ticket] = {"user": user, "service": service}
            separator = "&" if "?" in service else "?"
            location = f"{service}{separator}ticket={quote(ticket)}"
            self.send_response(302)
            self.send_header("Location", location)
            self.send_header("Content-Length", "0")
            self.end_headers()
            record({"path": path, "status": 302, "user": user, "service": service, "ticket": ticket, "location": location})
            return

        if path == "/cas/p3/serviceValidate":
            ticket = (query.get("ticket") or [""])[0]
            service = (query.get("service") or [""])[0]
            issue = TICKETS.get(ticket)
            if issue is None:
                self._json(200, {"serviceResponse": {"authenticationFailure": {"code": "INVALID_TICKET"}}})
                record({"path": path, "status": 200, "ticket": ticket, "result": "INVALID_TICKET"})
                return
            if service and issue["service"] != service:
                self._json(200, {"serviceResponse": {"authenticationFailure": {"code": "INVALID_SERVICE"}}})
                record({"path": path, "status": 200, "ticket": ticket, "result": "INVALID_SERVICE"})
                return
            # Single use: CAS tickets are consumed by the first successful validation.
            TICKETS.pop(ticket, None)
            user = issue["user"]
            self._json(
                200,
                {
                    "serviceResponse": {
                        "authenticationSuccess": {
                            "user": user,
                            "attributes": {
                                "displayName": ["MT CAS User"],
                                "mail": [f"{user}@example.local"],
                                "schoolId": [user],
                            },
                        }
                    }
                },
            )
            record({"path": path, "status": 200, "ticket": ticket, "user": user, "result": "SUCCESS"})
            return

        self._json(404, {"error": f"unsupported path {path}"})
        record({"path": path, "status": 404})

    def log_message(self, *args: object) -> None:
        return


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=9200)
    args = parser.parse_args()
    print(f"CAS test IdP listening on http://{args.host}:{args.port}/cas -> {LOG}")
    ThreadingHTTPServer((args.host, args.port), Handler).serve_forever()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
