#!/usr/bin/env python3
"""Minimal real HTTP receiver for the push channel (MT-NET-005).

Listens on ``--port`` (default 9099), records every POST body to
``build/reports/manual-test/push-receiver.jsonl`` and answers
``{"code":"0"}`` so the notification worker marks the delivery as sent.
"""

from __future__ import annotations

import argparse
import json
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
LOG = ROOT / "build" / "reports" / "manual-test" / "push-receiver.jsonl"


class Handler(BaseHTTPRequestHandler):
    def _read_body(self) -> bytes:
        """Read the body for both fixed-length and chunked requests."""
        if "chunked" in (self.headers.get("Transfer-Encoding") or "").lower():
            body = b""
            while True:
                size_line = self.rfile.readline().strip().split(b";")[0]
                if not size_line:
                    break
                try:
                    size = int(size_line, 16)
                except ValueError:
                    break
                if size == 0:
                    self.rfile.readline()
                    break
                body += self.rfile.read(size)
                self.rfile.readline()
            return body
        length = int(self.headers.get("Content-Length") or 0)
        return self.rfile.read(length)

    def do_POST(self) -> None:  # noqa: N802 - http.server API
        body = self._read_body().decode("utf-8", "replace")
        LOG.parent.mkdir(parents=True, exist_ok=True)
        with LOG.open("a", encoding="utf-8") as handle:
            handle.write(
                json.dumps(
                    {
                        "at": time.strftime("%Y-%m-%dT%H:%M:%S%z"),
                        "path": self.path,
                        "authorization": self.headers.get("Authorization", ""),
                        "headers": {key: value for key, value in self.headers.items()},
                        "body": body,
                    },
                    ensure_ascii=False,
                )
                + "\n"
            )
        payload = b'{"code":"0","message":"ok"}'
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(payload)))
        self.end_headers()
        self.wfile.write(payload)

    def log_message(self, *args: object) -> None:  # keep the console quiet
        return


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=9099)
    args = parser.parse_args()
    print(f"push receiver listening on http://{args.host}:{args.port} -> {LOG}")
    ThreadingHTTPServer((args.host, args.port), Handler).serve_forever()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
