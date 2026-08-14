#!/usr/bin/env python3
"""Generate the versioned SCL-90 technical profile package.

The existing ``scl90-v1-source-draft.json`` remains the historical draft
artifact.  This package is a new immutable version used to exercise the
restricted SCL90_PROFILE runtime closure.  It intentionally exposes one
profile-only overall result rule (0..360) instead of inventing clinical
cut-points or norm bands.  Governance is still blocked outside the disposable
technical schema until the project archives the actual rights scope and
obtains independent professional/business sign-off.
"""

from __future__ import annotations

import json
from pathlib import Path

from generate_scl90_source_package import build_package


ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "doc/scale-packages/scl90-v2-source-technical.json"


def result_translations() -> dict[str, dict[str, str]]:
    return {
        "zh-CN": {
            "resultTitle": "SCL-90 症状维度剖面（仅供剖面查看）",
            "resultDescription": "系统呈现九个症状维度及 GSI、PST、PSDI 指标；本结果不使用未经批准的临床切点，也不构成诊断。",
            "suggestionText": "请由合资格专业人员结合已批准的常模和临床资料解释，不要仅凭本剖面作出诊断或处置决定。",
            "reviewStatus": "DRAFT",
        },
        "ja-JP": {
            "resultTitle": "SCL-90 症状プロファイル（プロファイル表示のみ）",
            "resultDescription": "9つの症状次元と GSI・PST・PSDI を表示します。承認されていない臨床カットオフは使用せず、診断を示すものではありません。",
            "suggestionText": "承認済みのノルムと臨床情報を有資格の専門職が併せて解釈してください。このプロファイルだけで診断や処置を決めないでください。",
            "reviewStatus": "DRAFT",
        },
        "en": {
            "resultTitle": "SCL-90 symptom profile (profile display only)",
            "resultDescription": "The profile displays the nine symptom dimensions and GSI, PST, and PSDI. It uses no unapproved clinical cut-points and does not establish a diagnosis.",
            "suggestionText": "A qualified professional must interpret it with an approved norm set and clinical information; do not make a diagnosis or disposition from this profile alone.",
            "reviewStatus": "DRAFT",
        },
    }


def build_technical_package() -> dict[str, object]:
    package = build_package()
    scale = package["scale"]
    assert isinstance(scale, dict)
    scale["scaleCode"] = "SCL90_USER_AUTHORIZED"
    scale["versionNo"] = "authorized-profile-v1"

    governance = package["governance"]
    assert isinstance(governance, dict)
    governance.update(
        {
            "sourceTitle": "用户提供的 SCL-90 资料；Pearson SCL-90-R 官方产品与权限页面用于技术边界核对",
            "publisherName": "Derogatis / Pearson Assessments（官方技术参考）",
            # This is the user's stated internal-use authorization input.  It
            # is deliberately not treated as a public redistribution license.
            "copyrightStatus": "AUTHORIZED",
            "rightsHolder": "Leonard R. Derogatis / Pearson Assessments（范围需归档）",
            "authorizationStatus": "AUTHORIZED",
            "authorizationType": "INTERNAL_PERSONAL_RESEARCH_USER_CONFIRMED",
            "authorizationScope": "项目负责人确认仅限个人自我观察和非商用算法研究；不对外分发、不改编原始题目。公开网页仍显示复制、改编和翻译需书面权限；本包的 zh-CN/ja-JP 仅为技术验证草稿，不主张已获正式翻译授权。",
            "authorizedLanguages": "en（zh-CN/ja-JP 为未审校技术草稿）",
            "governanceStatus": "DRAFT",
            "targetPopulation": "Individuals 13 years and older; norm group and exclusion criteria require professional confirmation.",
            "reviewStatus": "PENDING_REVIEW",
        }
    )

    package["resultRules"] = [
        {
            "ruleCode": "SCL90_PROFILE_ONLY",
            "dimensionCode": None,
            "riskLevel": "NORMAL",
            "scoreMin": 0,
            "scoreMax": 360,
            "scoreSource": "RAW_SCORE",
            "translations": result_translations(),
        }
    ]
    # The restricted technical version intentionally carries no population
    # norm rows.  The historical draft keeps the user-provided factor values,
    # but this profile-only package must not turn unreviewed values into
    # runtime norm governance or imply that they are approved norms.
    norms = package.get("norms")
    assert isinstance(norms, dict)
    norms["status"] = "NOT_LOADED_PROFILE_ONLY"
    norms["factorReferenceFromUserText"] = {}
    norms["interpretation"] = (
        "Profile-only technical display; no population-specific norm rows or clinical cut-points are loaded."
    )
    package["publicationBlockers"] = [
        "AUTHORIZATION_SCOPE_ARCHIVE_PENDING",
        "PROFESSIONAL_REVIEW_PENDING",
        "TRILINGUAL_TRANSLATION_REVIEW_PENDING",
        "TRANSLATION_RIGHTS_AND_REVIEW_PENDING",
        "POPULATION_SPECIFIC_NORMS_PENDING",
        "CRISIS_RESPONSE_OWNER_AND_SLA_PENDING",
    ]

    references = package["sourceReferences"]
    assert isinstance(references, list)
    references[0] = {
        "title": "Pearson SCL-90-R product information",
        "url": "https://www.pearsonassessments.com/store/en/usd/p/100000645.html",
        "use": "Official age range, 90-item/5-point administration, nine dimensions, GSI and four norm groups; technical scope only.",
    }
    references.insert(
        1,
        {
            "title": "Pearson Global Permission Granting",
            "url": "https://www.pearson.com/global-permission-granting.html/",
            "use": "Official permission boundary: reproduction, adaptation and translation require written permission; project authorization scope remains an external governance record.",
        },
    )
    references.insert(
        2,
        {
            "title": "Pearson SCL-90-R Scales",
            "url": "https://www.pearsonassessments.com/content/dam/school/global/clinical/us/assets/scl-90-r/scl-90-r-scales.pdf",
            "use": "Official names of nine symptom scales and GSI/PSDI/PST; no clinical cut-points are inferred.",
        },
    )
    for reference in references:
        if isinstance(reference, dict) and "licensing remains unresolved" in str(reference.get("use", "")):
            reference["use"] = reference["use"].replace("licensing remains unresolved", "rights scope must be archived")

    for case in package["goldenCases"]:
        if case["caseCode"] == "SCL90_ALL_FOUR":
            case["sourceReference"] = "SCL-90 0–4 technical boundary; profile-only result range 0–360"
        elif case["caseCode"] == "SCL90_ALL_ZERO":
            case["sourceReference"] = "SCL-90 profile-only technical normal case"
        else:
            case["sourceReference"] = "SCL-90 restricted profile technical regression case"

    return package


def main() -> None:
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    payload = build_technical_package()
    OUTPUT.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"wrote {OUTPUT} ({len(payload['questions'])} questions, {len(payload['dimensions'])} dimensions, {len(payload['resultRules'])} result rule)")


if __name__ == "__main__":
    main()
