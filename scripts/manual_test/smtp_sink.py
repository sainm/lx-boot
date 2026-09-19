#!/usr/bin/env python3
"""Real SMTP receiver that records every message as JSONL.

The manual-test mail channel (MT-NET-002 / MT-AUTH-022) needs the activation
link that the application actually sent, so the sink parses the message and
stores it in ``build/reports/manual-test/smtp-sink.jsonl`` where the harness can
read it back. ``python3 -m smtpd -c DebuggingServer`` prints to a terminal,
which is not machine verifiable.

Usage:

    python3 scripts/manual_test/smtp_sink.py --port 2526
"""

from __future__ import annotations

import argparse
import asyncore
import json
import smtpd
import time
from email import message_from_string
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
DEFAULT_LOG = ROOT / "build" / "reports" / "manual-test" / "smtp-sink.jsonl"


def parse_message(raw: str) -> dict[str, object]:
    message = message_from_string(raw)

    def part_text(part) -> str:  # noqa: ANN001 - email.message.Message
        payload = part.get_payload(decode=True)
        if payload is None:
            return str(part.get_payload())
        return payload.decode(part.get_content_charset() or "utf-8", "replace")

    parts = [(part.get_content_type(), part_text(part)) for part in message.walk() if not part.is_multipart()]
    # Activation mail is multipart/mixed with an HTML alternative; the harness
    # only needs the link, so fall back to whichever part carries the text.
    body = next((text for kind, text in parts if kind == "text/plain"), "")
    if not body:
        body = next((text for _kind, text in parts if text), "")
    return {
        "subject": message.get("Subject", ""),
        "from": message.get("From", ""),
        "to": message.get("To", ""),
        "contentType": message.get_content_type(),
        "parts": [kind for kind, _ in parts],
        "body": body,
    }


class Sink(smtpd.SMTPServer):
    def __init__(self, localaddr, remoteaddr, log_path: Path):  # noqa: ANN001 - smtpd API
        super().__init__(localaddr, remoteaddr, decode_data=False)
        self.log_path = log_path
        log_path.parent.mkdir(parents=True, exist_ok=True)

    def process_message(self, peer, mailfrom, rcpttos, data, **kwargs):  # noqa: ANN001, ANN201
        raw = data.decode("utf-8", "replace") if isinstance(data, bytes) else str(data)
        entry: dict[str, object] = {
            "at": time.strftime("%Y-%m-%dT%H:%M:%S%z"),
            "peer": f"{peer[0]}:{peer[1]}" if peer else "",
            "mailFrom": mailfrom,
            "rcptTo": list(rcpttos),
            "bytes": len(data or b""),
            **parse_message(raw),
        }
        with self.log_path.open("a", encoding="utf-8") as handle:
            handle.write(json.dumps(entry, ensure_ascii=False) + "\n")
        print(f"[smtp-sink] {entry['mailFrom']} -> {entry['rcptTo']} subject={entry['subject']!r}")
        return None


def main() -> int:
    parser = argparse.ArgumentParser(description="JSONL SMTP sink for the manual-test mail channel")
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=2526)
    parser.add_argument("--log", type=Path, default=DEFAULT_LOG)
    args = parser.parse_args()

    Sink((args.host, args.port), None, args.log)
    print(f"smtp sink listening on {args.host}:{args.port} -> {args.log}")
    asyncore.loop()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
