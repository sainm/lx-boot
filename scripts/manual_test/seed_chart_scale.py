#!/usr/bin/env python3
"""Publish a synthetic scale that carries visualization configs and produce a
report whose detail page renders the radar/bar/pie charts (MT-RPT-012).

Prints the produced ids as JSON so the Playwright chart check can target them.
"""

from __future__ import annotations

import json
import sys
from pathlib import Path
from typing import Any

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))

from checks_common import api  # noqa: E402
from harness import Context, require  # noqa: E402
import checks_scoring  # noqa: E402
import scale_factory  # noqa: E402


def publish_chart_scale(ctx: Context) -> dict[str, Any]:
    """Publish a chart-bearing scale, answer one task and return the ids."""
    spec = scale_factory.private_spec(ctx, checks_scoring._spec(ctx, "norm_match"), "MT_CHART")
    scale_id = scale_factory.import_scale(ctx, spec)

    # Governance package first (it replaces dimensions/rules), then the chart
    # configuration while the scale is still DRAFT.
    scale_factory.put_package(ctx, scale_id, spec)
    visualizations = {
        "visualizations": [
            # The renderer resolves REPORT_DETAIL / GROUP_REPORT scopes; the
            # scale editor offers exactly these two values.
            {"chartType": "RADAR", "dataSource": "DIMENSION_SCORE", "viewScope": "REPORT_DETAIL", "chartTitle": "MT 雷达图", "sortNo": 1},
            {"chartType": "BAR", "dataSource": "DIMENSION_SCORE", "viewScope": "REPORT_DETAIL", "chartTitle": "MT 维度条形图", "sortNo": 2},
            {"chartType": "PIE", "dataSource": "RISK_DISTRIBUTION", "viewScope": "REPORT_DETAIL", "chartTitle": "MT 风险分布", "sortNo": 3},
        ]
    }
    configured = api(ctx, "POST", f"/api/v1/scales/{scale_id}/visualizations", body=visualizations)
    require(configured.status == 200, f"visualization config rejected: {configured.status} {configured.payload}")

    scale_factory.create_golden_cases(ctx, scale_id, spec)
    scale_factory.submit_reviews(ctx, scale_id, spec)
    scale_factory.publish_scale(ctx, scale_id)

    task_id = scale_factory.create_task(ctx, scale_id, f"MT-CHART-{ctx.unique('')}")
    golden = next(item for item in spec["goldenCases"] if item["type"] == "NORMAL")
    answers = {
        int(item["questionNo"]): item["optionCodes"][0]
        for item in golden["input"]["answers"]
    }
    status, payload, _ = scale_factory.submit_answers(ctx, task_id, scale_id, answers)
    require(status == 200 and payload.get("code") == "0", f"submit failed: HTTP {status} {payload}")
    data = payload["data"]
    return {
        "scaleId": scale_id,
        "taskId": task_id,
        "reportId": data.get("reportId"),
        "resultId": data.get("resultId"),
    }


def main() -> int:
    ctx = Context()
    print(
        json.dumps(
            publish_chart_scale(ctx),
            ensure_ascii=False,
        )
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
