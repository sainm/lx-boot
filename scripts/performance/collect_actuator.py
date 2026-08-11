#!/usr/bin/env python3
"""Collect protected Actuator metrics without persisting an access token."""

from __future__ import annotations

import argparse
import json
import urllib.error
import urllib.request
from pathlib import Path


def call(base_url: str, path: str, *, method: str = "GET", payload: dict | None = None, token: str | None = None):
    headers = {"Accept": "application/json", "Accept-Language": "en-US"}
    body = None
    if payload is not None:
        body = json.dumps(payload).encode("utf-8")
        headers["Content-Type"] = "application/json"
    if token:
        headers["Authorization"] = f"Bearer {token}"
    request = urllib.request.Request(base_url.rstrip("/") + path, data=body, headers=headers, method=method)
    try:
        with urllib.request.urlopen(request, timeout=15) as response:
            return response.status, response.read()
    except urllib.error.HTTPError as error:
        return error.code, error.read()
    except (urllib.error.URLError, TimeoutError, OSError):
        return 599, b""


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-url", required=True)
    parser.add_argument("--output", required=True)
    args = parser.parse_args()
    output = Path(args.output)
    output.mkdir(parents=True, exist_ok=True)
    status, body = call(
        args.base_url,
        "/auth/login/password",
        method="POST",
        payload={
            "principal": "assessor",
            "password": "ChangeMe123",
            "deviceId": "psy-performance-actuator",
            "deviceType": "OPS",
            "deviceName": "Performance baseline actuator collector",
        },
    )
    if status != 200:
        raise SystemExit(f"actuator collector login failed: HTTP {status}")
    token = json.loads(body)["data"]["accessToken"]
    metrics = (
        "hikaricp.connections.active",
        "hikaricp.connections.pending",
        "hikaricp.connections.max",
        "jvm.memory.used",
        "process.cpu.usage",
    )
    for metric in metrics:
        status, body = call(args.base_url, f"/actuator/metrics/{metric}", token=token)
        path = output / f"{metric.replace('/', '_')}.json"
        if 200 <= status < 300:
            path.write_bytes(body)
        else:
            path.write_text(json.dumps({"available": False, "status": status, "metric": metric}) + "\n")


if __name__ == "__main__":
    main()
