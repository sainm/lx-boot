#!/usr/bin/env python3
"""HTTP fixture that leaves write calls blocked after receiving their body."""

from __future__ import annotations

import argparse
import pathlib
import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer


class HangingStorageHandler(BaseHTTPRequestHandler):
    marker_path: pathlib.Path
    release = threading.Event()

    def do_PUT(self) -> None:  # noqa: N802
        self._receive_and_hang()

    def do_POST(self) -> None:  # noqa: N802
        self._receive_and_hang()

    def _receive_and_hang(self) -> None:
        remaining = int(self.headers.get("Content-Length", "0"))
        while remaining > 0:
            chunk = self.rfile.read(min(remaining, 64 * 1024))
            if not chunk:
                break
            remaining -= len(chunk)
        self.marker_path.touch()
        self.release.wait(timeout=600)

    def log_message(self, format: str, *args: object) -> None:
        return


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--port", type=int, required=True)
    parser.add_argument("--marker", type=pathlib.Path, required=True)
    args = parser.parse_args()
    args.marker.parent.mkdir(parents=True, exist_ok=True)
    HangingStorageHandler.marker_path = args.marker
    ThreadingHTTPServer(("127.0.0.1", args.port), HangingStorageHandler).serve_forever()


if __name__ == "__main__":
    main()
