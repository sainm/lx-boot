#!/usr/bin/env python3
"""Consolidate earlier manual/browser evidence into the harness execution log.

The harness only knows about cases executed through its own API checks.  Earlier
turns executed smoke/UI cases and long-running rehearsal scripts directly and
recorded them in ``doc/process/10-manual-test-execution-20260919.md`` sections
7.1-7.9.  This script folds those documented results (and this turn's shell
rehearsal logs) into ``build/reports/manual-test/execution.json`` so the final
tally has one source of truth.
"""

from __future__ import annotations

import json
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
REPORT_DIR = ROOT / "build/reports/manual-test"
EXECUTION_FILE = REPORT_DIR / "execution.json"
CASES_FILE = REPORT_DIR / "cases.json"
EVIDENCE_DIR = REPORT_DIR / "evidence"

RECORD = "doc/process/10-manual-test-execution-20260919.md"

SECTIONS = {
    "SMK": "§7.1 冒烟（浏览器逐页）",
    "ANS": "§7.1 作答与提交",
    "RPT": "§7.1 报告",
    "NOTI": "§7.1 通知",
    "APPT": "§7.1 预约",
    "HOME": "§7.1 个人资料",
    "I18N": "§7.1 三语",
    "TASK": "§7.1 任务管理",
    "SCALE": "§7.4 量表管理",
    "IMP": "§7.5 导入",
    "PUB": "§7.6 发布治理",
    "WARN": "§7.8 预警/干预/策略",
    "DB": "§7.9 数据库与运维",
    "OPS": "§7.9 数据库与运维",
    "EXP": "§7.9 导出链路",
    "STAT": "§7.7 统计与群体报告",
}

# case -> (status, detail)
DOCUMENTED: dict[str, tuple[str, str]] = {}


def _pass(case_id: str, detail: str) -> None:
    DOCUMENTED[case_id] = ("PASS", detail)


def _fail(case_id: str, detail: str) -> None:
    DOCUMENTED[case_id] = ("FAIL", detail)


def _blocked(case_id: str, detail: str) -> None:
    DOCUMENTED[case_id] = ("BLOCKED", detail)


for index in range(1, 11):
    _pass(f"MT-SMK-{index:03d}", "浏览器冒烟逐页通过（记录 §7.1）")

for index in (1, 2, 3, 4, 5, 6, 8):
    _pass(f"MT-ANS-{index:03d}", "作答/暂存/版本冲突/提交/重放实测通过（记录 §7.1）")
for index in (1, 3, 4):
    _pass(f"MT-RPT-{index:03d}", "报告自动生成/详情/高风险语义实测通过（记录 §7.1）")
for index in (1, 2, 3, 4):
    _pass(f"MT-NOTI-{index:03d}", "通知列表/未读/标记/跳转实测通过（记录 §7.1）")
for index in (1, 2, 4, 5):
    _pass(f"MT-APPT-{index:03d}", "排班查询/创建/我的预约/取消实测通过（记录 §7.1）")
_pass("MT-APPT-011", "预约创建/取消后的通知链接回到预约页（记录 §7.1，与 NOTI-004 合并验证）")
for index in (3, 4, 5):
    _pass(f"MT-HOME-{index:03d}", "个人资料展示/修改/校验实测通过（记录 §7.1）")
for index in (1, 2, 3, 7, 8):
    _pass(f"MT-I18N-{index:03d}", "三语登录/菜单/枚举/语言协商/键集合实测通过（记录 §7.1 与本轮）")

for index in (1, 2, 3):
    _pass(f"MT-TASK-{index:03d}", "任务创建/编辑/分配实测通过（记录 §7.1）")
_fail("MT-TASK-005", "关闭任务：API 成功（CLOSED），但界面没有关闭入口 -> F-15（记录 §7.2）")

_pass("MT-SCALE-001", "量表搜索可用但大小写敏感、不检索编码 -> F-17（记录 §7.4）")
_pass("MT-SCALE-002", "UI 创建量表成功（MT_SCALE_UI_1417）（记录 §7.4）")
_pass("MT-SCALE-004", "维度 D1/D2/D3 创建成功（记录 §7.4）")
_pass("MT-SCALE-005", "单选题 + 4 选项创建成功（记录 §7.4）")
_fail("MT-SCALE-007", "滑杆题 UI 提交 400 无反馈，同载荷直接调 API 成功 -> F-18（记录 §7.4）")
_fail("MT-SCALE-020", "发布阻断提示未显示、按钮卡 loading -> F-18（记录 §7.4）")

for index in (1, 2, 3, 4, 5):
    _pass(f"MT-IMP-{index:03d}", "模板下载/合法导入/非法导入阻断/历史查询实测通过（记录 §7.5）")
_fail("MT-IMP-010", "Excel 导入成功后未创建 governance 行，就绪页 GOVERNANCE_MISSING -> F-19（记录 §7.5）")
_pass("MT-PUB-002", "就绪页 37 个阻塞项 + 指纹 + 禁发布（记录 §7.5）")
_pass("MT-PUB-003", "Golden Case 修订 1 失败/修订 2 补齐通过，差异为空（记录 §7.6）")
_pass("MT-PUB-004", "counselor 审批他人创建的案例成功（记录 §7.6）")
_fail("MT-PUB-005", "证据不完整被 fail-closed 阻断（正确），但前端静默无提示 -> F-20；缺 reviewToken 返回 500 -> F-21（记录 §7.6）")

for index in (2, 3, 4, 5, 6, 8, 12, 13):
    _pass(f"MT-WARN-{index:03d}", "预警接单/指派/导出/干预/策略/结案/复测实测通过（记录 §7.8）")
_fail("MT-WARN-007", "策略快照 MISSING 的高风险预警仍可结案 -> F-23（记录 §7.8）")

for index in (1, 2, 3, 4, 7, 9, 10):
    _pass(f"MT-DB-{index:03d}", "迁移 validate/表数量/关键约束/baseline 守卫/文件不可变/文档一致性通过（记录 §7.9）")
for index in (7, 8, 9, 10):
    _pass(f"MT-OPS-{index:03d}", "Prometheus/correlation id/健康探针/告警规则通过（记录 §7.9）")
for index in (1, 2, 3, 4):
    _pass(f"MT-EXP-{index:03d}", "个体导出 PDF/WORD/TEXT 与异步任务流转实测通过（记录 §7.9）")
for index in (1, 2, 3, 4, 6, 8):
    _pass(f"MT-STAT-{index:03d}", "仪表盘/趋势/分布/群体报告/个人对比/租户范围实测通过（记录 §7.7）")
_fail("MT-STAT-007", "群体报告 Word 200、PDF 稳定 400，未限定范围时前端静默 -> F-22（记录 §7.7）")

for index in range(1, 7):
    _blocked(
        f"MT-AND-{index:03d}",
        "用户指示本次不做 Android 检查；无 Android SDK/模拟器，保留为后续单独执行",
    )


def apply_log_evidence(execution: dict) -> None:
    logs = {
        "MT-OPS-005": ("PASS", "MT-OPS-005.log：worker 阻塞于 Push POST 时被 SIGKILL，重启后 delivery=8 SENT/retry_count=1/单行"),
        "MT-OPS-006": ("PASS", "MT-OPS-006.log：导出 worker 阻塞于 PUT 时被 SIGKILL，重启后 job DONE/retry_count=1/单行"),
        "MT-NFR-001": (
            "PASS",
            "MT-NFR-001.log：1x/10x 基线成功，所有 HTTP Case 0 错误，schema psy_perf_* 用后删除（p50/p95/p99 见表）",
        ),
        "MT-NFR-008": (
            "PASS",
            "psy_e2e_/psy_migration_/psy_perf_/psy_recovery_/psy_notification_recovery_/psy_export_recovery_ 残留 schema = 0",
        ),
        "MT-DB-008": (
            "PASS",
            "F-25 已修复：assert-backup-restore-core.sql 改为读取 psy.expected_migration_count（wrapper 按迁移文件数注入，默认 28），"
            "run-backup-restore-rehearsal.sh 提交态整条通过（MT-DB-008-fixed.log：源/恢复库各校验 migrations=28、目录与数据 diff 一致、"
            "恢复库登录/任务/报告/预警/导出下载冒烟、备份 341,367B、RTO≈3.8s）",
        ),
    }
    for case_id, (status, detail) in logs.items():
        execution[case_id] = {
            "id": case_id,
            "status": status,
            "detail": detail,
            "at": time.strftime("%Y-%m-%dT%H:%M:%S%z"),
        }
        evidence = EVIDENCE_DIR / f"{case_id}.json"
        evidence.write_text(
            json.dumps({"caseId": case_id, "status": status, "detail": detail}, ensure_ascii=False, indent=2),
            encoding="utf-8",
        )


def main() -> int:
    cases = json.loads(CASES_FILE.read_text(encoding="utf-8"))
    execution = json.loads(EXECUTION_FILE.read_text(encoding="utf-8")) if EXECUTION_FILE.exists() else {}
    by_id = {case["id"]: case for case in cases}
    applied = 0
    for case_id, (status, detail) in sorted(DOCUMENTED.items()):
        if case_id not in by_id:
            continue
        existing = execution.get(case_id)
        # Live results win; documented rows are refreshed, and a placeholder
        # NOT_EXECUTED row (batch that had no check registered) is replaced.
        existing_status = existing.get("status") if existing else None
        if existing_status not in (None, "NOT_EXECUTED") and existing.get("source") != "documented":
            continue
        case = by_id[case_id]
        section = SECTIONS.get(case["module"], "§7 手工执行")
        execution[case_id] = {
            **case,
            "status": status,
            "detail": f"{detail}（证据：{RECORD} {section}）",
            "source": "documented",
            "at": time.strftime("%Y-%m-%dT%H:%M:%S%z"),
        }
        applied += 1
    apply_log_evidence(execution)
    EXECUTION_FILE.write_text(json.dumps(execution, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"consolidated {applied} documented cases + {len(execution)} total rows")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
