#!/usr/bin/env python3
"""Measure real HTTP cases against an isolated psy-backend instance.

The script deliberately reports client-observed latency. It does not claim a
production capacity number; the output is a reproducible case comparison for
the exact PostgreSQL fixture and application build used by the run.
"""

from __future__ import annotations

import argparse
import json
import math
import os
import statistics
import time
import urllib.error
import urllib.request
from pathlib import Path
from typing import Any


def request(
    base_url: str,
    path: str,
    *,
    method: str = "GET",
    payload: Any | None = None,
    token: str | None = None,
) -> tuple[int, bytes, float]:
    headers = {
        "Accept": "application/json",
        "Accept-Language": "en-US",
        "X-Correlation-Id": "psy-performance-baseline",
    }
    body = None
    if payload is not None:
        body = json.dumps(payload).encode("utf-8")
        headers["Content-Type"] = "application/json"
    if token:
        headers["Authorization"] = f"Bearer {token}"
    started = time.perf_counter_ns()
    try:
        req = urllib.request.Request(
            base_url.rstrip("/") + path,
            data=body,
            headers=headers,
            method=method,
        )
        with urllib.request.urlopen(req, timeout=30) as response:
            response_body = response.read()
            status = response.status
    except urllib.error.HTTPError as error:
        response_body = error.read()
        status = error.code
    except (urllib.error.URLError, TimeoutError, OSError):
        response_body = b""
        status = 599
    elapsed_ms = (time.perf_counter_ns() - started) / 1_000_000
    return status, response_body, elapsed_ms


def login(base_url: str, principal: str) -> str:
    status, body, _ = request(
        base_url,
        "/auth/login/password",
        method="POST",
        payload={
            "principal": principal,
            "password": "ChangeMe123",
            "deviceId": f"psy-performance-{principal}",
            "deviceType": "OPS",
            "deviceName": "Performance baseline",
        },
    )
    if status != 200:
        raise SystemExit(f"login failed for {principal}: HTTP {status}")
    try:
        token = json.loads(body)["data"]["accessToken"]
    except (KeyError, TypeError, json.JSONDecodeError) as error:
        raise SystemExit(f"login response for {principal} did not contain an access token") from error
    return token


def percentile(values: list[float], fraction: float) -> float | None:
    if not values:
        return None
    ordered = sorted(values)
    rank = max(1, math.ceil(fraction * len(ordered)))
    return round(ordered[rank - 1], 3)


def summarize(name: str, samples: list[float], statuses: list[int], errors: list[int]) -> dict[str, Any]:
    successful = len(samples)
    total = len(statuses)
    mean_ms = statistics.fmean(samples) if samples else None
    return {
        "name": name,
        "sampleCount": total,
        "successCount": successful,
        "errorCount": len(errors),
        "statusCounts": {str(status): statuses.count(status) for status in sorted(set(statuses))},
        "p50Ms": percentile(samples, 0.50),
        "p95Ms": percentile(samples, 0.95),
        "p99Ms": percentile(samples, 0.99),
        "meanMs": round(mean_ms, 3) if mean_ms is not None else None,
        "throughputPerSecond": round(1000 / mean_ms, 3) if mean_ms and mean_ms > 0 else None,
        "errorStatuses": errors,
    }


def run_read_case(
    base_url: str,
    name: str,
    path: str,
    token: str,
    repetitions: int,
    warmups: int,
) -> dict[str, Any]:
    for _ in range(warmups):
        request(base_url, path, token=token)
    samples: list[float] = []
    statuses: list[int] = []
    errors: list[int] = []
    for _ in range(repetitions):
        status, _, elapsed_ms = request(base_url, path, token=token)
        statuses.append(status)
        if 200 <= status < 300:
            samples.append(elapsed_ms)
        else:
            errors.append(status)
    return summarize(name, samples, statuses, errors)


def answer_payload(questions: list[dict[str, Any]]) -> list[dict[str, Any]]:
    return [
        {
            "questionId": question["questionId"],
            "optionId": question["optionId"],
        }
        for question in questions
    ]


def run_mutation_cases(
    base_url: str,
    token: str,
    task_ids: list[int],
    scale_id: int,
    questions: list[dict[str, Any]],
) -> dict[str, Any]:
    save_samples: list[float] = []
    save_statuses: list[int] = []
    save_errors: list[int] = []
    submit_samples: list[float] = []
    submit_statuses: list[int] = []
    submit_errors: list[int] = []
    answers = answer_payload(questions)
    for index, task_id in enumerate(task_ids):
        save_status, save_body, save_elapsed_ms = request(
            base_url,
            "/api/v1/answer-sheets/save",
            method="POST",
            token=token,
            payload={"taskId": task_id, "scaleId": scale_id, "answers": answers},
        )
        save_statuses.append(save_status)
        if not (200 <= save_status < 300):
            save_errors.append(save_status)
            continue
        save_samples.append(save_elapsed_ms)
        try:
            save_data = json.loads(save_body)["data"]
            answer_sheet_id = save_data["answerSheetId"]
            version_no = save_data["versionNo"]
        except (KeyError, TypeError, json.JSONDecodeError):
            save_errors.append(598)
            continue

        submit_status, _, submit_elapsed_ms = request(
            base_url,
            "/api/v1/answer-sheets/submit",
            method="POST",
            token=token,
            payload={
                "taskId": task_id,
                "scaleId": scale_id,
                "answerSheetId": answer_sheet_id,
                "versionNo": version_no,
                "submitToken": f"psy-performance-submit-{task_id}-{index}",
                "answers": answers,
            },
        )
        submit_statuses.append(submit_status)
        if 200 <= submit_status < 300:
            submit_samples.append(submit_elapsed_ms)
        else:
            submit_errors.append(submit_status)

    return {
        "answerSave": summarize("answer_save", save_samples, save_statuses, save_errors),
        "answerSubmitAndScore": summarize(
            "answer_submit_and_score", submit_samples, submit_statuses, submit_errors
        ),
    }


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-url", required=True)
    parser.add_argument("--output", required=True)
    parser.add_argument("--factor", required=True)
    parser.add_argument("--target-rows", type=int, required=True)
    parser.add_argument("--deep-page", type=int, required=True)
    parser.add_argument("--respondent-id", type=int, required=True)
    parser.add_argument("--scale-id", type=int, required=True)
    parser.add_argument("--questions-json", required=True)
    parser.add_argument("--live-task-ids", required=True)
    parser.add_argument("--requests", type=int, default=int(os.environ.get("PSY_PERF_REQUESTS", "15")))
    parser.add_argument("--warmups", type=int, default=int(os.environ.get("PSY_PERF_WARMUPS", "2")))
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    output_path = Path(args.output)
    output_path.parent.mkdir(parents=True, exist_ok=True)
    questions = json.loads(args.questions_json)
    live_task_ids = [int(value) for value in args.live_task_ids.split(",") if value]
    if not live_task_ids:
        raise SystemExit("no live tasks were supplied for answer save/submit measurement")

    assessor_token = login(args.base_url, "assessor")
    respondent_token = login(args.base_url, "respondent")
    read_cases = [
        ("task_list", f"/api/v1/tasks?page=1&size=20", assessor_token),
        ("task_list_deep_page", f"/api/v1/tasks?page={args.deep_page}&size=20", assessor_token),
        ("report_list", "/api/v1/reports?page=1&size=20", assessor_token),
        ("warning_list", "/api/v1/warnings?page=1&size=20", assessor_token),
        ("statistics_dashboard", "/api/v1/statistics/dashboard", assessor_token),
        (
            "statistics_group_reports",
            f"/api/v1/statistics/group-reports?page=1&size=20&compareUserId={args.respondent_id}",
            assessor_token,
        ),
        ("export_jobs", "/api/v1/exports/reports/jobs?limit=100", assessor_token),
        ("my_tasks", "/api/v1/my/tasks", respondent_token),
        ("my_reports", "/api/v1/reports/my", respondent_token),
        ("my_notifications", "/api/v1/my/notifications", respondent_token),
        ("my_appointments", "/api/v1/appointments/my", respondent_token),
        (
            "task_questions",
            f"/api/v1/my/tasks/{live_task_ids[0]}/questions",
            respondent_token,
        ),
    ]
    cases = {
        name: run_read_case(args.base_url, name, path, token, args.requests, args.warmups)
        for name, path, token in read_cases
    }
    cases.update(
        run_mutation_cases(
            args.base_url,
            respondent_token,
            live_task_ids[: max(1, min(len(live_task_ids), args.requests))],
            args.scale_id,
            questions,
        )
    )

    result = {
        "factor": args.factor,
        "targetRows": args.target_rows,
        "samplePolicy": {"requestsPerReadCase": args.requests, "warmupsPerReadCase": args.warmups},
        "cases": cases,
        "note": "Client-observed latency against disposable technical fixture; not a production capacity claim.",
    }
    output_path.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(result, ensure_ascii=False))


if __name__ == "__main__":
    main()
