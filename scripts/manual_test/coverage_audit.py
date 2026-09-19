#!/usr/bin/env python3
"""Audit manual-test coverage against the API design doc and the frontend routes.

The audit answers two questions mechanically:

1. Does every endpoint documented in ``doc/13-api-design-detailed.md`` have at
   least one harness case that exercises it through real HTTP?
2. Is every frontend route referenced by the manual-test procedure so the UI
   surface stays covered as pages are added?

Outputs (regenerated on every run):

* ``doc/manual-test/coverage-matrix.md`` - human readable gap list
* ``doc/manual-test/case-registry.json`` - machine readable registry used by the
  execution record to prove completeness
"""

from __future__ import annotations

import ast
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
CHECK_DIR = ROOT / "scripts/manual_test"
API_DOC = ROOT / "doc" / "13-api-design-detailed.md"
ROUTE_CONFIG = ROOT / "admin-web" / "src" / "app" / "route-config.tsx"
PROCEDURE_DOCS = [ROOT / "doc" / "30-manual-test-procedure.md", ROOT / "doc" / "31-manual-test-procedure-full.md"]
CATALOG = ROOT / "build" / "reports" / "manual-test" / "cases.json"
OUT_DIR = ROOT / "doc" / "manual-test"


def parse_endpoints() -> list[dict[str, str]]:
    endpoints: list[dict[str, str]] = []
    for line in API_DOC.read_text(encoding="utf-8").splitlines():
        match = re.match(r"\|\s*(GET|POST|PUT|DELETE|PATCH)\s*\|\s*`([^`]+)`\s*\|\s*([^|]*)\|", line)
        if not match:
            continue
        endpoints.append({"method": match.group(1), "path": match.group(2).strip(), "roles": match.group(3).strip()})
    return endpoints


def parse_routes() -> list[dict[str, object]]:
    text = ROUTE_CONFIG.read_text(encoding="utf-8")
    routes: list[dict[str, object]] = []
    for match in re.finditer(
        r"\{\s*key:\s*\"([^\"]+)\",\s*path:\s*\"([^\"]+)\",\s*labelKey:\s*\"([^\"]+)\",(.*?)menu:\s*(true|false)",
        text,
        re.S,
    ):
        body = match.group(4)
        roles = re.search(r"roles:\s*\[([^\]]*)\]", body)
        routes.append(
            {
                "key": match.group(1),
                "path": match.group(2),
                "labelKey": match.group(3),
                "roles": [role.strip().strip('"') for role in roles.group(1).split(",") if role.strip()] if roles else [],
                "menu": match.group(5) == "true",
            }
        )
    return routes


def normalise(path: str) -> str:
    path = path.split("?")[0]
    path = re.sub(r"\{[^}]*\}", "*", path)
    return path.rstrip("/") or "/"


HTTP_METHODS = {"GET", "POST", "PUT", "DELETE", "PATCH"}


def function_http_calls(node: ast.AST) -> set[tuple[str, str]]:
    calls: set[tuple[str, str]] = set()

    # ``for path in ("/auth/roles", ...): ctx.http("GET", path)`` hides the
    # literal paths behind a loop variable, so record the loop binding first.
    bindings: dict[str, list[str]] = {}
    for sub in ast.walk(node):
        if isinstance(sub, ast.For) and isinstance(sub.target, ast.Name):
            values = [
                item.value
                for item in ast.walk(sub.iter)
                if isinstance(item, ast.Constant) and isinstance(item.value, str)
            ]
            if values:
                bindings[sub.target.id] = values

    def expand(path_node: ast.AST) -> list[str]:
        if isinstance(path_node, ast.Name):
            return bindings.get(path_node.id, [])
        rendered = render_path(path_node)
        return [rendered] if rendered else []

    for sub in ast.walk(node):
        if not isinstance(sub, ast.Call):
            continue
        func = sub.func
        if isinstance(func, ast.Attribute) and func.attr == "http" and len(sub.args) >= 2:
            method_node, path_node = sub.args[0], sub.args[1]
            if isinstance(method_node, ast.Constant) and isinstance(method_node.value, str):
                calls.update((method_node.value.upper(), path) for path in expand(path_node))
            continue
        # Wrapper calls such as ``_api(ctx, "GET", f"/api/v1/tasks/{id}")`` pass
        # the method/path straight through to ``ctx.http``; take them from the
        # call site because the wrapper body only sees variables.
        positional = {index: value for index, value in enumerate(sub.args)}
        method_node = positional.get(1) or next((kw.value for kw in sub.keywords if kw.arg == "method"), None)
        path_node = positional.get(2) or next((kw.value for kw in sub.keywords if kw.arg == "path"), None)
        if isinstance(method_node, ast.Constant) and isinstance(method_node.value, str) and path_node is not None:
            if method_node.value.upper() in HTTP_METHODS:
                for rendered in expand(path_node):
                    if rendered.startswith("/"):
                        calls.add((method_node.value.upper(), rendered))
    return calls


def render_path(node: ast.AST) -> str:
    if isinstance(node, ast.Constant) and isinstance(node.value, str):
        return node.value
    if isinstance(node, ast.JoinedStr):
        rendered = ""
        for value in node.values:
            if isinstance(value, ast.Constant) and isinstance(value.value, str):
                rendered += value.value
            else:
                rendered += "*"
        return rendered
    return ""


def called_names(node: ast.AST) -> set[str]:
    names: set[str] = set()
    for sub in ast.walk(node):
        if isinstance(sub, ast.Call) and isinstance(sub.func, ast.Name):
            names.add(sub.func.id)
    return names


def qualified_calls(node: ast.AST) -> set[str]:
    """Return ``module.function`` / ``function`` names called inside ``node``."""
    names: set[str] = set()
    for sub in ast.walk(node):
        if not isinstance(sub, ast.Call):
            continue
        func = sub.func
        if isinstance(func, ast.Name):
            names.add(func.id)
        elif isinstance(func, ast.Attribute) and isinstance(func.value, ast.Name):
            names.add(f"{func.value.id}.{func.attr}")
    return names


def module_functions(path: Path) -> tuple[dict[str, set[tuple[str, str]]], dict[str, set[str]], dict[str, str], dict[str, str]]:
    """Parse one check module.

    Returns ``(http_calls, calls, cases, imports)`` where ``imports`` maps a
    local alias to the target ``module.function`` (or ``module`` for module
    aliases such as ``import scale_factory as sf``).
    """
    tree = ast.parse(path.read_text(encoding="utf-8"))
    direct: dict[str, set[tuple[str, str]]] = {}
    calls: dict[str, set[str]] = {}
    cases: dict[str, str] = {}
    imports: dict[str, str] = {}
    for node in tree.body:
        if isinstance(node, ast.Import):
            for alias in node.names:
                imports[alias.asname or alias.name] = alias.name
        elif isinstance(node, ast.ImportFrom) and node.module:
            for alias in node.names:
                imports[alias.asname or alias.name] = f"{node.module}.{alias.name}"
        elif isinstance(node, ast.FunctionDef):
            direct[node.name] = function_http_calls(node)
            calls[node.name] = qualified_calls(node)
            for decorator in node.decorator_list:
                if (
                    isinstance(decorator, ast.Call)
                    and isinstance(decorator.func, ast.Name)
                    and decorator.func.id == "case"
                    and decorator.args
                    and isinstance(decorator.args[0], ast.Constant)
                ):
                    cases[str(decorator.args[0].value)] = node.name
    return direct, calls, cases, imports


def build_case_endpoint_map() -> tuple[dict[str, set[tuple[str, str]]], dict[str, list[str]]]:
    modules: dict[str, dict[str, object]] = {}
    for path in sorted(CHECK_DIR.glob("*.py")):
        modules[path.stem] = {"direct": None}
        modules[path.stem] = dict(
            zip(("direct", "calls", "cases", "imports"), module_functions(path)),
            path=path.name,
        )

    case_endpoints: dict[str, set[tuple[str, str]]] = {}
    case_files: dict[str, list[str]] = {}
    for module_name, module in modules.items():
        for case_id, function in (module["cases"] or {}).items():  # type: ignore[union-attr]
            seen: set[tuple[str, str]] = set()
            stack: list[tuple[str, str]] = [(module_name, function)]
            endpoints: set[tuple[str, str]] = set()
            while stack:
                current_module, current = stack.pop()
                if (current_module, current) in seen:
                    continue
                seen.add((current_module, current))
                container = modules.get(current_module)
                if container is None:
                    continue
                endpoints |= {
                    (method, normalise(path_value))
                    for method, path_value in (container["direct"] or {}).get(current, set())  # type: ignore[union-attr]
                    if path_value
                }
                imports: dict[str, str] = container["imports"] or {}  # type: ignore[assignment]
                for called in (container["calls"] or {}).get(current, set()):  # type: ignore[union-attr]
                    if "." in called:
                        alias, attribute = called.split(".", 1)
                        target = imports.get(alias)
                        if target and "." not in target:
                            stack.append((target, attribute))
                        continue
                    target = imports.get(called)
                    if target and "." in target:
                        target_module, target_function = target.split(".", 1)
                        stack.append((target_module, target_function))
                    else:
                        stack.append((current_module, called))
            case_endpoints[case_id] = endpoints
            case_files.setdefault(case_id, []).append(str(module["path"]))
    return case_endpoints, case_files


def procedure_text() -> str:
    return "\n".join(path.read_text(encoding="utf-8") for path in PROCEDURE_DOCS if path.exists())


def route_referenced(route_path: str, text: str) -> bool:
    """Routes may be written as ``/reports/:id`` or ``/reports/{id}``."""
    if route_path in text:
        return True
    normalized = re.sub(r"/:[A-Za-z0-9_]+", "/*", route_path)
    normalized_text = re.sub(r"/\{[^}]+\}", "/*", text)
    return normalized in normalized_text


def endpoint_matches(documented: str, exercised: str) -> bool:
    """Match a documented endpoint path against a path a case really called.

    A placeholder segment in the design document (``{id}`` becomes ``*``)
    accepts any concrete value, so a case that calls ``/api/v1/tasks/12``
    counts as exercising ``/api/v1/tasks/{id}``. Wildcards coming from the
    harness side (an f-string over a variable) still have to line up with a
    placeholder, which keeps the audit from crediting an endpoint as covered
    by a call it never made.
    """
    documented_segments = documented.strip("/").split("/")
    exercised_segments = exercised.strip("/").split("/")
    if len(documented_segments) != len(exercised_segments):
        return False
    for documented_segment, exercised_segment in zip(documented_segments, exercised_segments):
        if documented_segment == exercised_segment or documented_segment == "*":
            continue
        return False
    return True


def cases_for(
    method: str, path: str, endpoint_cases: dict[tuple[str, str], list[str]]
) -> list[str]:
    """Case ids that exercise ``method path``, exact match first."""
    exact = endpoint_cases.get((method, path))
    if exact:
        return sorted(set(exact))
    matched: set[str] = set()
    for (case_method, case_path), case_ids in endpoint_cases.items():
        if case_method == method and endpoint_matches(path, case_path):
            matched.update(case_ids)
    return sorted(matched)


def declared_endpoints() -> dict[tuple[str, str], list[str]]:
    """Endpoints declared by the procedure documents (doc/31 MT-API rows)."""
    declared: dict[tuple[str, str], list[str]] = {}
    if not CATALOG.exists():
        return declared
    for item in json.loads(CATALOG.read_text(encoding="utf-8")):
        for endpoint in item.get("endpoints", []):
            method, _, path = endpoint.partition(" ")
            declared.setdefault((method.upper(), normalise(path.strip("`"))), []).append(str(item["id"]))
    return declared


def main() -> int:
    endpoints = parse_endpoints()
    routes = parse_routes()
    case_endpoints, case_files = build_case_endpoint_map()
    declared = declared_endpoints()
    text = procedure_text()

    endpoint_cases: dict[tuple[str, str], list[str]] = {}
    for case_id, covered in case_endpoints.items():
        for endpoint in covered:
            endpoint_cases.setdefault(endpoint, []).append(case_id)

    exercised: dict[tuple[str, str], list[str]] = {
        (endpoint["method"], normalise(endpoint["path"])): cases_for(
            endpoint["method"], normalise(endpoint["path"]), endpoint_cases
        )
        for endpoint in endpoints
    }

    uncovered_endpoints = [
        endpoint
        for endpoint in endpoints
        if not exercised[(endpoint["method"], normalise(endpoint["path"]))]
        and (endpoint["method"], normalise(endpoint["path"])) not in declared
    ]
    manual_only_endpoints = [
        endpoint
        for endpoint in endpoints
        if not exercised[(endpoint["method"], normalise(endpoint["path"]))]
        and (endpoint["method"], normalise(endpoint["path"])) in declared
    ]
    uncovered_routes = [route for route in routes if not route_referenced(str(route["path"]), text)]

    OUT_DIR.mkdir(parents=True, exist_ok=True)
    registry = {
        "endpointTotal": len(endpoints),
        "endpointCovered": len(endpoints) - len(uncovered_endpoints),
        "endpointAutomated": len(endpoints) - len(uncovered_endpoints) - len(manual_only_endpoints),
        "endpointProcedureOnly": len(manual_only_endpoints),
        "endpoints": [
            {
                **endpoint,
                "cases": exercised[(endpoint["method"], normalise(endpoint["path"]))],
                "procedureCases": sorted(declared.get((endpoint["method"], normalise(endpoint["path"])), [])),
            }
            for endpoint in endpoints
        ],
        "routes": [
            {**route, "documented": str(route["path"]) in text}
            for route in routes
        ],
        "cases": {case_id: sorted(endpoints) for case_id, endpoints in sorted(case_endpoints.items())},
    }
    (OUT_DIR / "case-registry.json").write_text(
        json.dumps(registry, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )

    lines = [
        "# 手动测试覆盖矩阵（生成物）",
        "",
        "> 由 `python3 scripts/manual_test/coverage_audit.py` 生成；请勿手工编辑。",
        "> 判定口径：方法一致且路径逐段匹配；设计文档中的占位段（`{id}`）视为通配，"
        "因此用例实际请求 `/api/v1/tasks/12` 即视为覆盖 `/api/v1/tasks/{id}`；"
        "用例侧由变量拼出的 `*` 必须与占位段对齐，未实际请求的固定路径不会被计入。",
        f"> 接口总数 {len(endpoints)}，被 API 用例覆盖 {len(endpoints) - len(uncovered_endpoints)}；"
        f"其中自动执行 {len(endpoints) - len(uncovered_endpoints) - len(manual_only_endpoints)}、"
        f"仅在手顺中声明 {len(manual_only_endpoints)}；"
        f"前端路由 {len(routes)}，在手顺中被引用 {len(routes) - len(uncovered_routes)}。",
        "",
        "## 1. 接口覆盖",
        "",
        "| 方法 | 路径 | 自动用例 | 手顺用例 |",
        "| --- | --- | --- | --- |",
    ]
    for endpoint in endpoints:
        cases = exercised[(endpoint["method"], normalise(endpoint["path"]))]
        procedure_cases = sorted(declared.get((endpoint["method"], normalise(endpoint["path"])), []))
        coverage = "**未覆盖**" if not cases and not procedure_cases else ""
        lines.append(
            f"| {endpoint['method']} | `{endpoint['path']}` | "
            f"{', '.join(cases[:4]) + ('…' if len(cases) > 4 else '') if cases else '—'} | "
            f"{', '.join(procedure_cases[:4]) + ('…' if len(procedure_cases) > 4 else '') if procedure_cases else '—'} {coverage} |"
        )
    lines += ["", "## 2. 前端路由覆盖", "", "| 路由 | 菜单 | 角色 | 手顺引用 |", "| --- | --- | --- | --- |"]
    for route in routes:
        documented = "是" if str(route["path"]) in text else "**否**"
        lines.append(f"| `{route['path']}` | {'是' if route['menu'] else '否'} | {', '.join(route['roles'])} | {documented} |")
    if uncovered_endpoints or uncovered_routes:
        lines += ["", "## 3. 缺口", ""]
        for endpoint in uncovered_endpoints:
            lines.append(f"- 接口未覆盖：`{endpoint['method']} {endpoint['path']}`")
        for route in uncovered_routes:
            lines.append(f"- 路由未在手顺中引用：`{route['path']}`")
    (OUT_DIR / "coverage-matrix.md").write_text("\n".join(lines) + "\n", encoding="utf-8")

    print(
        f"endpoints: {len(endpoints) - len(uncovered_endpoints)}/{len(endpoints)} covered "
        f"(automated {len(endpoints) - len(uncovered_endpoints) - len(manual_only_endpoints)}, "
        f"procedure-only {len(manual_only_endpoints)})"
    )
    print(f"routes: {len(routes) - len(uncovered_routes)}/{len(routes)} referenced")
    for endpoint in uncovered_endpoints:
        print(f"  uncovered endpoint: {endpoint['method']} {endpoint['path']}")
    for route in uncovered_routes:
        print(f"  undocked route: {route['path']}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
