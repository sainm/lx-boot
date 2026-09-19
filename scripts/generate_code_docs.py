#!/usr/bin/env python3
"""Regenerate code-derived reference documentation.

The generator treats source code and the migrated PostgreSQL schema as the
authoritative facts:

* ``api``  -> ``doc/13-api-design-detailed.md`` from Kotlin ``@RestController`` classes
  in this repository and, when present, the sibling ``auth-starter`` repository.
* ``db``   -> ``doc/10-database-table-design.md`` from the live Flyway-migrated schema.
* ``all``  -> both documents.
* ``check`` -> fail when either generated document is stale.

Only the Python standard library and the ``psql`` client are required.
"""

from __future__ import annotations

import argparse
import datetime as dt
import json
import os
import re
import subprocess
import sys
from dataclasses import dataclass
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BACKEND_SRC = ROOT / "backend/src/main/kotlin"
AUTH_STARTER_ROOT = Path(
    os.environ.get("AUTH_STARTER_ROOT", ROOT.parent / "auth-starter")
)
API_DOC = ROOT / "doc/13-api-design-detailed.md"
DB_DOC = ROOT / "doc/10-database-table-design.md"


def today() -> str:
    return dt.date.today().isoformat()


def find_matching(text: str, open_index: int) -> int:
    """Return the index of the ``)`` matching ``text[open_index] == '('``."""
    if text[open_index] != "(":
        raise ValueError("open_index does not point at '('")
    depth = 0
    in_string = False
    escaped = False
    for index in range(open_index, len(text)):
        char = text[index]
        if in_string:
            if escaped:
                escaped = False
            elif char == "\\":
                escaped = True
            elif char == '"':
                in_string = False
            continue
        if char == '"':
            in_string = True
        elif char == "(":
            depth += 1
        elif char == ")":
            depth -= 1
            if depth == 0:
                return index
    raise ValueError("unbalanced parentheses")


def strip_block_comments(text: str) -> str:
    """Replace block comments with equivalent newlines so offsets stay stable."""

    def replacer(match: re.Match[str]) -> str:
        return "\n" * match.group(0).count("\n")

    return re.sub(r"/\*.*?\*/", replacer, text, flags=re.DOTALL)


@dataclass(frozen=True)
class Annotation:
    name: str
    args: str
    start: int
    end: int


def iter_annotations(text: str) -> list[Annotation]:
    annotations: list[Annotation] = []
    for match in re.finditer(r"@([A-Za-z_][A-Za-z0-9_.]*)", text):
        name = match.group(1)
        cursor = match.end()
        while cursor < len(text) and text[cursor] in " \t":
            cursor += 1
        args = ""
        end = match.end()
        if cursor < len(text) and text[cursor] == "(":
            closing = find_matching(text, cursor)
            args = text[cursor + 1 : closing]
            end = closing + 1
        annotations.append(Annotation(name, args, match.start(), end))
    return annotations


@dataclass
class Parameter:
    name: str
    type: str
    binding: str
    required: str


@dataclass
class Endpoint:
    source: str
    repository: str
    module: str
    controller: str
    base_path: str
    method: str
    path: str
    handler: str
    roles: str
    parameters: list[Parameter]
    return_type: str


def split_top_level(text: str) -> list[str]:
    parts: list[str] = []
    depth = 0
    in_string = False
    escaped = False
    current: list[str] = []
    for char in text:
        if in_string:
            current.append(char)
            if escaped:
                escaped = False
            elif char == "\\":
                escaped = True
            elif char == '"':
                in_string = False
            continue
        if char == '"':
            in_string = True
            current.append(char)
        elif char in "([{<":
            depth += 1
            current.append(char)
        elif char in ")]}>":
            depth -= 1
            current.append(char)
        elif char == "," and depth == 0:
            parts.append("".join(current).strip())
            current = []
        else:
            current.append(char)
    tail = "".join(current).strip()
    if tail:
        parts.append(tail)
    return parts


def parse_parameters(params_text: str) -> list[Parameter]:
    parameters: list[Parameter] = []
    for raw in split_top_level(params_text):
        if not raw or raw.startswith("//"):
            continue
        binding = ""
        for annotation in ("RequestBody", "PathVariable", "RequestParam", "RequestHeader", "AuthenticationPrincipal"):
            if re.search(rf"@{annotation}\b", raw):
                binding = annotation
                break
        required = ""
        if binding == "RequestParam":
            if re.search(r"required\s*=\s*false", raw):
                required = "否"
            elif re.search(r"defaultValue\s*=", raw):
                required = "默认值"
            else:
                required = "是"
        elif binding == "RequestBody":
            required = "否" if re.search(r"required\s*=\s*false", raw) else "是"
        elif binding == "PathVariable":
            required = "是"

        cleaned = raw
        while True:
            match = re.match(r"\s*@([A-Za-z_][A-Za-z0-9_.]*)\s*(?:\(([^()]*(?:\([^()]*\)[^()]*)*)\))?\s*", cleaned)
            if not match:
                break
            if match.group(2) is None and "(" in cleaned[: match.end()]:
                # Multi-line annotation argument such as @RequestParam(\n...\n)
                open_index = cleaned.find("(", match.start(1))
                if open_index >= 0:
                    closing = find_matching(cleaned, open_index)
                    cleaned = cleaned[closing + 1 :]
                    continue
            cleaned = cleaned[match.end() :]

        cleaned = re.sub(r"\b(?:val|var|private|public|internal|protected|crossinline|noinline)\b\s*", "", cleaned).strip()
        match = re.match(r"([A-Za-z_][A-Za-z0-9_]*)\s*:\s*([^=]+?)(?:\s*=\s*.*)?$", cleaned, flags=re.DOTALL)
        if not match:
            continue
        name = match.group(1)
        type_name = re.sub(r"\s+", " ", match.group(2)).strip().rstrip("?")
        parameters.append(Parameter(name=name, type=type_name, binding=binding, required=required))
    return parameters


def roles_from_preauthorize(args: str) -> str:
    args = args.strip()
    if not args:
        return "未声明"
    roles = re.findall(r"'([^']+)'", args)
    if "isAuthenticated" in args:
        label = "登录用户"
        return label if not roles else f"{label} + {' / '.join(roles)}"
    if roles:
        return " / ".join(roles)
    return re.sub(r"\s+", " ", args)


def mapping_paths(args: str) -> list[str]:
    literals = re.findall(r'"([^"]*)"', args)
    return [value for value in literals if value == "" or value.startswith("/")]


def mapping_methods(annotation: Annotation) -> list[str]:
    if annotation.name.endswith("Mapping") and annotation.name != "RequestMapping":
        return [annotation.name[: -len("Mapping")].upper()]
    return re.findall(r"RequestMethod\.([A-Z]+)", annotation.args)


def module_of(package: str) -> str:
    match = re.search(r"org\.sainm\.psy\.([A-Za-z0-9_]+)", package)
    return match.group(1) if match else package


def parse_controller(path: Path, repository: str) -> list[Endpoint]:
    text = strip_block_comments(path.read_text(encoding="utf-8"))
    if "@RestController" not in text:
        return []

    package_match = re.search(r"package\s+([A-Za-z0-9_.]+)", text)
    package = package_match.group(1) if package_match else ""

    annotations = iter_annotations(text)
    functions = list(re.finditer(r"\bfun\s+([A-Za-z_][A-Za-z0-9_]*)\s*\(", text))
    events: list[tuple[int, int, str, object]] = [(item.start, item.end, "annotation", item) for item in annotations]
    events.extend((item.start(), item.end(), "function", item) for item in functions)
    events.sort(key=lambda item: (item[0], item[1]))

    class_match = re.search(r"\bclass\s+([A-Za-z_][A-Za-z0-9_]*)", text)
    base_path = ""
    class_annotations: list[Annotation] = []
    class_preauthorize: Annotation | None = None
    if class_match:
        class_annotations = [item for item in annotations if item.end <= class_match.start()]
        request_mapping = next((item for item in reversed(class_annotations) if item.name == "RequestMapping"), None)
        if request_mapping:
            base_path = (mapping_paths(request_mapping.args) or [""])[0]
        class_preauthorize = next(
            (item for item in reversed(class_annotations) if item.name == "PreAuthorize"),
            None,
        )
    pending: list[Annotation] = []
    endpoints: list[Endpoint] = []
    controller_name = class_match.group(1) if class_match else path.stem

    class_start = class_match.start() if class_match else -1
    for start, end, kind, payload in events:
        if kind == "annotation":
            pending.append(payload)  # type: ignore[arg-type]
            continue

        function = payload  # type: ignore[assignment]
        assert isinstance(function, re.Match)
        # Class-level annotations must not be attached to the first method.
        function_annotations = [item for item in pending if item.start > class_start]
        pending = []
        mapping = next(
            (item for item in function_annotations if item.name in {"GetMapping", "PostMapping", "PutMapping", "DeleteMapping", "PatchMapping", "RequestMapping"}),
            None,
        )
        if mapping is None:
            continue
        methods = mapping_methods(mapping) or ["ANY"]
        paths = mapping_paths(mapping.args) or [""]
        preauthorize = next((item for item in function_annotations if item.name == "PreAuthorize"), None)
        if preauthorize:
            roles = roles_from_preauthorize(preauthorize.args)
        elif class_preauthorize:
            roles = roles_from_preauthorize(class_preauthorize.args)
        else:
            roles = "未声明"

        open_paren = function.end() - 1
        closing = find_matching(text, open_paren)
        parameters = parse_parameters(text[open_paren + 1 : closing])
        tail = text[closing + 1 :]
        return_match = re.match(r"\s*:\s*([^\n=]+)", tail)
        return_type = re.sub(r"\s+", " ", return_match.group(1)).strip() if return_match else ""

        for method in methods:
            for relative in paths:
                endpoints.append(
                    Endpoint(
                        source=path.relative_to(ROOT.parent).as_posix() if str(path).startswith(str(ROOT.parent)) else path.as_posix(),
                        repository=repository,
                        module=module_of(package),
                        controller=controller_name,
                        base_path=base_path,
                        method=method,
                        path=relative,
                        handler=function.group(1),
                        roles=roles,
                        parameters=parameters,
                        return_type=return_type,
                    )
                )

    if class_match:
        for endpoint in endpoints:
            endpoint.base_path = base_path
            endpoint.path = f"{base_path}{endpoint.path}" or "/"

    return endpoints


def collect_endpoints() -> list[Endpoint]:
    endpoints: list[Endpoint] = []
    for path in sorted(BACKEND_SRC.rglob("*.kt")):
        endpoints.extend(parse_controller(path, "lx-boot"))
    if AUTH_STARTER_ROOT.exists():
        for path in sorted(AUTH_STARTER_ROOT.rglob("*.kt")):
            endpoints.extend(parse_controller(path, "auth-starter"))
    endpoints.sort(key=lambda item: (item.repository, item.module, item.path, item.method))
    return endpoints


def parameter_summary(parameters: list[Parameter]) -> str:
    if not parameters:
        return "-"
    rendered = []
    for parameter in parameters:
        binding = f"@{parameter.binding} " if parameter.binding else ""
        required = f"，{parameter.required}" if parameter.required else ""
        rendered.append(f"`{binding}{parameter.name}: {parameter.type}`{required}")
    return "<br>".join(rendered)


def render_api_doc(endpoints: list[Endpoint]) -> str:
    generated_at = dt.datetime.now().astimezone().strftime("%Y-%m-%d %H:%M:%S %Z")
    backend = [item for item in endpoints if item.repository == "lx-boot"]
    auth = [item for item in endpoints if item.repository == "auth-starter"]

    lines: list[str] = [
        "# 接口详细设计（由代码生成）",
        "",
        "## 1. 文档说明",
        "",
        "本文档由 Kotlin 控制器源码直接生成，描述当前仓库实际暴露的 HTTP 契约；不再手工维护接口清单。",
        "",
        f"- 生成命令：`python3 scripts/generate_code_docs.py api`",
        f"- 生成时间：{generated_at}",
        f"- 业务端点（本仓库）：**{len(backend)}** 条路径定义，来源 `backend/src/main/kotlin/**/api/*.kt`",
        f"- 认证端点（相邻 `auth-starter` 仓库）：**{len(auth)}** 条路径定义"
        if AUTH_STARTER_ROOT.exists()
        else "- 认证端点：未找到相邻 `auth-starter` 仓库，未生成",
        "- 权限列来自 `@PreAuthorize`；`未声明` 表示控制器方法依赖全局安全配置或仅需登录，需以安全配置为准。",
        "- 请求参数仅列显式 `@PathVariable` / `@RequestParam` / `@RequestBody` / `@RequestHeader` / `@AuthenticationPrincipal` 绑定。",
        "",
        "## 2. 通用约定",
        "",
        "- 前缀：本仓库业务端点统一为 `/api/v1`；认证端点为 `/auth/**`。",
        '- 成功响应：`{"code":"0","message":"OK","data":...}`；错误响应沿用同一信封并返回业务码。',
        "- 分页参数：`page` / `size`；时间：ISO-8601（服务端时区 Asia/Shanghai）。",
        "",
        "## 3. 业务端点（lx-boot）",
        "",
    ]

    backend_modules: dict[str, list[Endpoint]] = {}
    for endpoint in backend:
        backend_modules.setdefault(endpoint.module, []).append(endpoint)

    lines.extend(
        [
            "| 模块 | 端点数 | 控制器 |",
            "| --- | ---: | --- |",
        ]
    )
    for module in sorted(backend_modules):
        controllers = sorted({item.controller for item in backend_modules[module]})
        lines.append(f"| {module} | {len(backend_modules[module])} | {', '.join(controllers)} |")
    lines.append("")

    for module in sorted(backend_modules):
        lines.extend([f"### 3.{sorted(backend_modules).index(module) + 1} {module}", ""])
        controllers: dict[str, list[Endpoint]] = {}
        for endpoint in backend_modules[module]:
            controllers.setdefault(endpoint.controller, []).append(endpoint)
        for controller in sorted(controllers):
            items = controllers[controller]
            lines.extend(
                [
                    f"#### {controller}",
                    "",
                    f"- 基础路径：`{items[0].base_path or '/'}`",
                    f"- 源码：`{items[0].source}`",
                    "",
                    "| 方法 | 路径 | 权限 | 处理器 | 参数 | 返回 |",
                    "| --- | --- | --- | --- | --- | --- |",
                ]
            )
            for endpoint in items:
                lines.append(
                    f"| {endpoint.method} | `{endpoint.path}` | {endpoint.roles} | `{endpoint.handler}` | "
                    f"{parameter_summary(endpoint.parameters)} | `{endpoint.return_type or '-'}` |"
                )
            lines.append("")

    if auth:
        lines.extend(["## 4. 认证端点（auth-starter）", "", "| 方法 | 路径 | 权限 | 处理器 | 参数 | 返回 | 源码 |", "| --- | --- | --- | --- | --- | --- | --- |"])
        for endpoint in auth:
            lines.append(
                f"| {endpoint.method} | `{endpoint.path}` | {endpoint.roles} | `{endpoint.handler}` | "
                f"{parameter_summary(endpoint.parameters)} | `{endpoint.return_type or '-'}` | `{endpoint.source}` |"
            )
        lines.append("")

    lines.extend(
        [
            "## 5. 权限标注分布（本仓库）",
            "",
        ]
    )
    role_counts: dict[str, int] = {}
    for endpoint in backend:
        role_counts[endpoint.roles] = role_counts.get(endpoint.roles, 0) + 1
    lines.extend(["| 权限 | 端点数 |", "| --- | ---: |"])
    for roles, count in sorted(role_counts.items(), key=lambda item: (-item[1], item[0])):
        lines.append(f"| {roles} | {count} |")
    lines.append("")
    return "\n".join(lines)


def psql_json(query: str) -> list[dict[str, object]]:
    command = [
        "psql",
        "-X",
        "-A",
        "-t",
        "-h",
        os.environ.get("PSY_DOC_DB_HOST", "127.0.0.1"),
        "-p",
        os.environ.get("PSY_DOC_DB_PORT", "5432"),
        "-U",
        os.environ.get("PSY_DOC_DB_USERNAME", "lx"),
        "-d",
        os.environ.get("PSY_DOC_DB_NAME", "lx"),
        "-c",
        query,
    ]
    environment = dict(os.environ)
    environment.setdefault("PGPASSWORD", os.environ.get("PSY_DOC_DB_PASSWORD", "lx"))
    result = subprocess.run(command, capture_output=True, text=True, env=environment, check=False)
    if result.returncode != 0:
        raise RuntimeError(result.stderr.strip() or "psql failed")
    payload = result.stdout.strip()
    if not payload:
        return []
    data = json.loads(payload)
    if not isinstance(data, list):
        raise RuntimeError("unexpected psql JSON payload")
    return data


def esc(value: object) -> str:
    text = "" if value is None else str(value)
    return text.replace("|", "\\|").replace("\n", " ").replace("\r", " ")


def collect_database() -> dict[str, object]:
    migrations = psql_json(
        "select coalesce(json_agg(t), '[]'::json) from ("
        "select version, description, success from flyway_schema_history order by installed_rank"
        ") t"
    )
    columns = psql_json(
        "select coalesce(json_agg(t), '[]'::json) from ("
        "select table_name, ordinal_position, column_name, data_type,"
        " character_maximum_length, numeric_precision, numeric_scale, is_nullable, column_default"
        " from information_schema.columns"
        " where table_schema = 'public' and table_name not like 'flyway%'"
        " order by table_name, ordinal_position) t"
    )
    constraints = psql_json(
        "select coalesce(json_agg(t), '[]'::json) from ("
        "select rel.relname as table_name, con.conname, con.contype, pg_get_constraintdef(con.oid) as definition"
        " from pg_constraint con"
        " join pg_class rel on rel.oid = con.conrelid"
        " join pg_namespace ns on ns.oid = rel.relnamespace"
        " where ns.nspname = 'public' and rel.relname not like 'flyway%'"
        " order by rel.relname, con.contype, con.conname) t"
    )
    indexes = psql_json(
        "select coalesce(json_agg(t), '[]'::json) from ("
        "select tablename as table_name, indexname, indexdef"
        " from pg_indexes"
        " where schemaname = 'public' and tablename not like 'flyway%'"
        " order by tablename, indexname) t"
    )
    table_count = psql_json(
        "select coalesce(json_agg(t), '[]'::json) from ("
        "select count(*) filter (where table_name like 'psy_%') as psy_tables,"
        " count(*) filter (where table_name like 'sys_%') as sys_tables"
        " from information_schema.tables where table_schema = 'public') t"
    )
    return {
        "migrations": migrations,
        "columns": columns,
        "constraints": constraints,
        "indexes": indexes,
        "table_count": table_count[0] if table_count else {},
    }


def table_domain(table: str) -> str:
    if table.startswith("sys_") or table == "psy_user_device":
        return "认证、权限与会话"
    if table.startswith("psy_scale"):
        return "量表与发布治理"
    if table.startswith("psy_assessment"):
        return "测评任务与作答"
    if table.startswith(("psy_warning", "psy_intervention", "psy_safety")):
        return "预警、干预与安全响应"
    if table.startswith(("psy_report", "psy_export")):
        return "报告与导出"
    if table.startswith("psy_notification"):
        return "通知与投递"
    if table.startswith(("psy_appointment", "psy_counseling", "psy_counselor")):
        return "预约与咨询"
    return "其他业务"


def render_database_doc(database: dict[str, object]) -> str:
    generated_at = dt.datetime.now().astimezone().strftime("%Y-%m-%d %H:%M:%S %Z")
    columns = database["columns"]
    constraints = database["constraints"]
    indexes = database["indexes"]
    migrations = database["migrations"]
    table_count = database["table_count"]
    assert isinstance(columns, list) and isinstance(constraints, list) and isinstance(indexes, list)
    assert isinstance(migrations, list) and isinstance(table_count, dict)

    by_table: dict[str, list[dict[str, object]]] = {}
    for column in columns:
        by_table.setdefault(str(column["table_name"]), []).append(column)
    constraints_by_table: dict[str, list[dict[str, object]]] = {}
    for constraint in constraints:
        constraints_by_table.setdefault(str(constraint["table_name"]), []).append(constraint)
    indexes_by_table: dict[str, list[dict[str, object]]] = {}
    for index in indexes:
        indexes_by_table.setdefault(str(index["table_name"]), []).append(index)

    domains: dict[str, list[str]] = {}
    for table in sorted(by_table):
        domains.setdefault(table_domain(table), []).append(table)

    lines: list[str] = [
        "# 数据库表结构设计（由 V1–V28 实际结构生成）",
        "",
        "## 1. 文档说明",
        "",
        "本文档由已完成 Flyway 迁移的 PostgreSQL `public` schema 直接生成，描述当前代码对应的真实表结构；历史 DDL 草案不再作为事实来源。",
        "",
        f"- 生成命令：`python3 scripts/generate_code_docs.py db`",
        f"- 生成时间：{generated_at}",
        f"- 迁移：V1–V{max((str(item['version']) for item in migrations), key=lambda value: int(value)) if migrations else '?'}，共 {len(migrations)} 条成功迁移",
        f"- 表数量：**{len(by_table)}**（`psy_*` {table_count.get('psy_tables', '?')} 张，`sys_*` {table_count.get('sys_tables', '?')} 张），不含 `flyway_schema_history`",
        "- 结构入口：`backend/src/main/resources/db/migration/`；生产默认关闭自动迁移，禁止 clean，失败前滚修复。",
        "",
        "## 2. 领域分组",
        "",
        "| 领域 | 表数 | 表清单 |",
        "| --- | ---: | --- |",
    ]
    for domain in sorted(domains):
        lines.append(f"| {domain} | {len(domains[domain])} | {', '.join('`' + table + '`' for table in domains[domain])} |")

    lines.extend(["", "## 3. 迁移清单", "", "| 版本 | 描述 | 成功 |", "| --- | --- | --- |"])
    for migration in migrations:
        lines.append(
            f"| V{esc(migration['version'])} | {esc(migration['description'])} | {'是' if migration['success'] else '否'} |"
        )

    lines.extend(["", "## 4. 表结构明细", ""])
    section = 0
    for domain in sorted(domains):
        section += 1
        lines.extend([f"### 4.{section} {domain}", ""])
        for table in domains[domain]:
            table_columns = by_table[table]
            lines.extend(
                [
                    f"#### `{table}`",
                    "",
                    "| 列 | 类型 | 可空 | 默认值 |",
                    "| --- | --- | --- | --- |",
                ]
            )
            for column in table_columns:
                data_type = str(column["data_type"])
                length = column.get("character_maximum_length")
                precision = column.get("numeric_precision")
                scale = column.get("numeric_scale")
                if length:
                    data_type = f"{data_type}({length})"
                elif precision and data_type in {"numeric", "decimal"}:
                    data_type = f"{data_type}({precision},{scale or 0})"
                lines.append(
                    f"| `{esc(column['column_name'])}` | `{esc(data_type)}` | "
                    f"{esc(column['is_nullable'])} | {esc(column.get('column_default') or '-')} |"
                )

            lines.append("")
            table_constraints = sorted(
                constraints_by_table.get(table, []),
                key=lambda item: (str(item["contype"]), str(item["conname"])),
            )
            if table_constraints:
                lines.append("约束：")
                lines.append("")
                for constraint in table_constraints:
                    kind = {
                        "p": "PRIMARY KEY",
                        "u": "UNIQUE",
                        "f": "FOREIGN KEY",
                        "c": "CHECK",
                        "x": "EXCLUDE",
                        "n": "NOT NULL",
                    }.get(str(constraint["contype"]), str(constraint["contype"]))
                    lines.append(f"- `{esc(constraint['conname'])}` ({kind}) `{esc(constraint['definition'])}`")
                lines.append("")
            table_indexes = sorted(indexes_by_table.get(table, []), key=lambda item: str(item["indexname"]))
            if table_indexes:
                lines.append("索引：")
                lines.append("")
                for index in table_indexes:
                    lines.append(f"- `{esc(index['indexname'])}`：`{esc(index['indexdef'])}`")
                lines.append("")

    lines.extend(
        [
            "## 5. 关键不变式（按当前约束与迁移语义）",
            "",
            "1. 直接租户表必须显式携带 `tenant_id`；子表通过父表继承归属，跨租户访问必须审计。",
            "2. `psy_assessment_result` 追加写入，不覆盖历史；每份答卷只有一个 `is_current=true` 结果。",
            "3. `psy_safety_response_policy` 生效策略必须满足 `APPROVED` 且审批人与专业复核人不同。",
            "4. ScalePackage 导入时授权、版权、治理状态强制为待审核/草稿，不能由源包声明直接推进。",
            "5. 量表发布必须绑定当前内容指纹、Golden Case 运行证据和发布评审证据。",
            "6. 导出与通知队列使用租约 + fencing；重试、退避、死信和人工重放均有状态约束。",
            "7. 迁移集合只增不改；已执行迁移不得修改，结构变更必须新增版本。",
            "",
        ]
    )
    return "\n".join(lines)


def write_or_check(path: Path, content: str, check: bool) -> bool:
    if check:
        current = path.read_text(encoding="utf-8") if path.exists() else ""
        generated_time = re.compile(r"^- 生成时间：.*$", re.MULTILINE)
        if generated_time.sub("", current) != generated_time.sub("", content):
            print(f"stale: {path.relative_to(ROOT)}", file=sys.stderr)
            return False
        print(f"ok: {path.relative_to(ROOT)}")
        return True
    path.write_text(content, encoding="utf-8")
    print(f"wrote {path.relative_to(ROOT)}")
    return True


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=["api", "db", "all", "check"])
    args = parser.parse_args()

    if args.command in {"api", "all", "check"}:
        api_content = render_api_doc(collect_endpoints())
    else:
        api_content = None
    if args.command in {"db", "all", "check"}:
        db_content = render_database_doc(collect_database())
    else:
        db_content = None

    if args.command == "check":
        assert api_content is not None and db_content is not None
        return 0 if all([write_or_check(API_DOC, api_content, True), write_or_check(DB_DOC, db_content, True)]) else 1
    if args.command in {"api", "all"}:
        assert api_content is not None
        write_or_check(API_DOC, api_content, False)
    if args.command in {"db", "all"}:
        assert db_content is not None
        write_or_check(DB_DOC, db_content, False)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
