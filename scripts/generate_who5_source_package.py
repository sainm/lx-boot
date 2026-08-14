#!/usr/bin/env python3
"""Generate the reviewable WHO-5 source package.

The package follows the WHO-5 five-item, 0..5 response convention and keeps
translation, licensing scope, cutoff interpretation, and professional review
explicit.  It is a technical source artifact, not a claim of clinical
approval or commercial licensing.
"""

from __future__ import annotations

import json
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "doc" / "scale-packages" / "who5-v1-source-draft.json"
LOCALES = ("zh-CN", "ja-JP", "en")

QUESTIONS = [
    ("我感觉快乐、心情舒畅", "明るく，楽しい気分で過ごした。", "I have felt cheerful and in good spirits"),
    ("我感觉宁静和放松", "落ち着いた、リラックスした気分で過ごした。", "I have felt calm and relaxed"),
    ("我感觉充满活力、精力充沛", "意欲的で，活動的に過ごした。", "I have felt active and vigorous"),
    ("我睡醒时感到清新、得到了足够休息", "ぐっすりと休め，気持ちよくめざめた。", "I woke up feeling fresh and rested"),
    ("我每天生活充满了有趣的事情", "日常生活の中に，興味のあることがたくさんあった。", "My daily life has been filled with things that interest me"),
]

OPTION_LABELS = {
    "zh-CN": ["从未有过", "有时候", "少于一半的时间", "超过一半的时间", "大部分时间", "所有时间"],
    "ja-JP": ["まったくない", "ほんのたまに", "半分以下の期間を", "半分以上の期間を", "ほとんどいつも", "いつも"],
    "en": ["At no time", "Some of the time", "Less than half of the time", "More than half of the time", "Most of the time", "All of the time"],
}

INSTRUCTIONS = {
    "zh-CN": "请根据过去两星期的状态，为下面五个句子选择最接近的频率。数字越大表示身心健康程度越高。WHO-5 是筛查工具，不等同于临床诊断。",
    "ja-JP": "過去2週間の状態に最も近い頻度を、次の5項目ごとに選んでください。数字が大きいほど心身の健康度が高いことを示します。WHO-5はスクリーニング用で、臨床診断ではありません。",
    "en": "For each of the five statements, select the frequency that best describes your feelings during the past two weeks. Higher numbers indicate better well-being. The WHO-5 is a screening measure, not a clinical diagnosis.",
}

SCALE_TRANSLATIONS = {
    "zh-CN": {
        "scaleName": "世界卫生组织五项身心健康指标（WHO-5）",
        "purposeText": "用于筛查过去两星期的主观身心健康状况。",
        "resultVisibilityText": "结果可供被测者及获授权的专业人员查看。",
        "nonDiagnosticText": "WHO-5 用于身心健康筛查，不等同于临床诊断。",
        "helpResourceText": "如持续感到低落或担心自身安全，请联系合格的专业人员或当地紧急援助。",
    },
    "ja-JP": {
        "scaleName": "世界保健機関（WHO）五項目幸福度指標（WHO-5）",
        "purposeText": "過去2週間の主観的な心身の健康状態をスクリーニングします。",
        "resultVisibilityText": "結果は回答者本人および権限を付与された専門職が閲覧できます。",
        "nonDiagnosticText": "WHO-5 は心身の健康のスクリーニング用であり、臨床診断を確定するものではありません。",
        "helpResourceText": "気分の落ち込みが続く場合や安全上の不安がある場合は、資格のある専門職または地域の緊急支援に連絡してください。",
    },
    "en": {
        "scaleName": "WHO-Five Well-Being Index (WHO-5)",
        "purposeText": "Screens subjective mental well-being during the past two weeks.",
        "resultVisibilityText": "Results are visible to the respondent and authorized professionals.",
        "nonDiagnosticText": "The WHO-5 supports well-being screening and does not establish a clinical diagnosis.",
        "helpResourceText": "If low mood persists or you are concerned about immediate safety, contact a qualified professional or local emergency support.",
    },
}


def localized(zh: str, ja: str, en: str) -> dict[str, str]:
    return {"zh-CN": zh, "ja-JP": ja, "en": en}


def answers(scores: list[int]) -> list[dict[str, object]]:
    return [{"questionNo": index, "optionCodes": [str(score)]} for index, score in enumerate(scores, 1)]


def expected(total: int, risk: str) -> dict[str, object]:
    return {
        "valid": True,
        "totalScore": total,
        "riskLevel": risk,
        "metrics": {"WHO5_PERCENTAGE_SCORE": total * 4},
    }


def result_rule(code: str, risk: str, minimum: int, maximum: int, texts: dict[str, dict[str, str]]) -> dict[str, object]:
    return {
        "ruleCode": code,
        "riskLevel": risk,
        "scoreMin": minimum,
        "scoreMax": maximum,
        "scoreSource": "RAW_SCORE",
        "translations": {locale: {**texts[locale], "reviewStatus": "DRAFT"} for locale in LOCALES},
    }


def build_package() -> dict[str, object]:
    options = [
        {
            "code": str(score),
            "score": score,
            "translations": {locale: OPTION_LABELS[locale][score] for locale in LOCALES},
        }
        for score in range(6)
    ]
    questions = [
        {
            "questionNo": number,
            "dimensionCode": "WHO5_TOTAL",
            "questionType": "SINGLE_CHOICE",
            "required": True,
            "reverseScore": False,
            "translations": {
                locale: {"text": text, "reviewStatus": "DRAFT"}
                for locale, text in zip(LOCALES, question_texts)
            },
            "options": options,
        }
        for number, question_texts in enumerate(QUESTIONS, 1)
    ]

    low_texts = {
        "zh-CN": {"resultTitle": "身心健康得分偏低（建议进一步评估）", "resultDescription": "原始分为0–12（百分比分为0–48）。该结果提示需要结合个人情况进一步了解，不等同于抑郁症诊断。", "suggestionText": "请由合格的专业人员结合近期状态、功能影响和安全风险进行进一步评估。"},
        "ja-JP": {"resultTitle": "心身の健康度が低い（追加評価を推奨）", "resultDescription": "素点は0～12（百分率換算は0～48）です。個別の状況を踏まえた追加確認が必要で、うつ病の診断を意味しません。", "suggestionText": "最近の状態、生活機能、安全上のリスクを含め、資格のある専門職による追加評価を受けてください。"},
        "en": {"resultTitle": "Lower well-being score (further assessment suggested)", "resultDescription": "The raw score is 0–12 (0–48 after percentage conversion). This suggests further contextual assessment; it is not a diagnosis of depression.", "suggestionText": "A qualified professional should consider recent status, functional impact and safety risk in further assessment."},
    }
    normal_texts = {
        "zh-CN": {"resultTitle": "未低于建议筛查切点", "resultDescription": "原始分为13–25（百分比分为52–100）。该结果仅表示未低于本包采用的建议切点，不代表没有心理困扰，也不是临床诊断。", "suggestionText": "如仍感到困扰，请结合专业人员访谈和其他信息评估。"},
        "ja-JP": {"resultTitle": "推奨スクリーニングカットオフ未満ではない", "resultDescription": "素点は13～25（百分率換算は52～100）です。本パッケージの推奨カットオフ未満ではないことだけを示し、問題がないことや臨床診断を意味しません。", "suggestionText": "困りごとが続く場合は、専門職との面接や他の情報を併せて評価してください。"},
        "en": {"resultTitle": "Not below the suggested screening cut point", "resultDescription": "The raw score is 13–25 (52–100 after percentage conversion). This only indicates that it is not below the suggested cut point used by this package; it does not establish the absence of distress or a clinical diagnosis.", "suggestionText": "If concerns continue, combine this result with a professional interview and other information."},
    }

    return {
        "format": "PSY_SCALE_SOURCE_PACKAGE",
        "schemaVersion": 1,
        "scale": {
            "scaleCode": "WHO5_WELL_BEING",
            "scaleName": "WHO-5 Well-Being Index",
            "versionNo": "who-2024-open-access-v1",
            "applicableTarget": "GENERAL_SELF_REPORT",
            "scoreMethod": "SIMPLE_SUM",
            "scoreCoefficient": 1,
            "assessmentMode": "SELF",
            "responseScale": {"min": 0, "max": 5, "labels": OPTION_LABELS["en"]},
            "qualityPolicy": {"missingAnswerPolicy": "REJECT", "maxMissingRatio": 0, "invalidResultAction": "INVALIDATE", "requireAllRequiredAnswers": True},
            "reportTemplate": "SINGLE_SCORE",
            "algorithmBinding": {"algorithmCode": "GENERIC_SCORE_CALCULATOR", "algorithmVersion": "1", "implementationType": "BUILTIN"},
            "instruction": INSTRUCTIONS,
        },
        "governance": {
            "sourceTitle": "WHO-5 official publication and licensed open-access materials",
            "publisherName": "World Health Organization",
            "copyrightStatus": "AUTHORIZED",
            "rightsHolder": "World Health Organization",
            "authorizationStatus": "AUTHORIZED",
            "authorizationType": "CC-BY-NC-SA-3.0-IGO",
            "authorizationScope": "Non-commercial adaptation and translation with attribution and ShareAlike under CC BY-NC-SA 3.0 IGO; commercial use, WHO endorsement, logos and population-specific clinical claims are not cleared.",
            "authorizedLanguages": "en,zh-CN,ja-JP",
            "governanceStatus": "DRAFT",
            "targetPopulation": "Self-report mental well-being screening; cutoff scope and professional use require review.",
            "nonDiagnosticStatement": "WHO-5 supports well-being screening and does not establish a clinical diagnosis.",
            "reviewStatus": "PENDING_REVIEW",
        },
        "translations": {locale: {**SCALE_TRANSLATIONS[locale], "reviewStatus": "DRAFT"} for locale in LOCALES},
        "dimensions": [{
            "dimensionCode": "WHO5_TOTAL",
            "questionNos": [1, 2, 3, 4, 5],
            "translations": {
                "zh-CN": {"name": "WHO-5 身心健康总分", "description": "五个项目的0–5原始分总和。", "reviewStatus": "DRAFT"},
                "ja-JP": {"name": "WHO-5 心身の健康度合計", "description": "5項目の0～5素点の合計です。", "reviewStatus": "DRAFT"},
                "en": {"name": "WHO-5 well-being total", "description": "Sum of the five item scores from 0 to 5.", "reviewStatus": "DRAFT"},
            },
        }],
        "questions": questions,
        "scoring": {
            "canonicalConvention": "0_TO_5",
            "indices": {"WHO5_PERCENTAGE_SCORE": "raw total score multiplied by 4 (0-100)"},
            "dimensionAggregation": "SIMPLE_SUM",
            "dimensionRule": "sum of the five item scores",
        },
        "norms": {
            "status": "NOT_APPLICABLE",
            "interpretation": "The suggested raw-score cutoff is not a population-universal diagnosis; population, language and clinical-use scope require professional review.",
        },
        "resultRules": [
            result_rule("WHO5_LOW_WELLBEING", "ATTENTION", 0, 12, low_texts),
            result_rule("WHO5_NOT_LOW_WELLBEING", "NORMAL", 13, 25, normal_texts),
        ],
        "highRiskRules": [],
        "goldenCases": [
            {"caseCode": "WHO5_ALL_ZERO", "caseType": "NORMAL", "sourceReference": "WHO-5 scoring convention; all five responses at 0", "input": {"answers": answers([0, 0, 0, 0, 0])}, "expected": expected(0, "ATTENTION")},
            {"caseCode": "WHO5_BOUNDARY_12", "caseType": "BOUNDARY", "sourceReference": "WHO-5 suggested raw-score boundary below 13", "input": {"answers": answers([2, 2, 2, 3, 3])}, "expected": expected(12, "ATTENTION")},
            {"caseCode": "WHO5_CUTOFF_13", "caseType": "BOUNDARY", "sourceReference": "WHO-5 suggested raw-score boundary at 13", "input": {"answers": answers([3, 2, 3, 2, 3])}, "expected": expected(13, "NORMAL")},
            {"caseCode": "WHO5_ALL_HIGH", "caseType": "NORMAL", "sourceReference": "WHO-5 scoring convention; all five responses at 5", "input": {"answers": answers([5, 5, 5, 5, 5])}, "expected": expected(25, "NORMAL")},
            {"caseCode": "WHO5_MISSING_REQUIRED", "caseType": "MISSING", "sourceReference": "Required-answer policy", "input": {"answers": answers([5, 5, 5, 5])}, "expected": {"valid": False, "errorCode": "MISSING_REQUIRED_ANSWER"}},
            {"caseCode": "WHO5_INVALID_OPTION", "caseType": "INVALID", "sourceReference": "Controlled option-code regression case", "input": {"answers": [{"questionNo": 1, "optionCodes": ["9"]}] + answers([0, 0, 0, 0, 0])[1:]}, "expected": {"valid": False, "errorCode": "OPTION_NOT_FOUND"}},
        ],
        "sourceReferences": [
            {"title": "WHO-5 official publication page", "url": "https://www.who.int/publications/m/item/WHO-UCN-MSD-MHE-2024.01", "use": "Official publication metadata, translations and license context."},
            {"title": "WHO-5 English original and scoring", "url": "https://cdn.who.int/media/docs/default-source/mental-health/who-5_english-original4da539d6ed4b49389e3afe47cda2326a.pdf", "use": "Five statements, 0–5 response labels, raw score and percentage conversion."},
            {"title": "WHO-5 Chinese translation", "url": "https://cdn.who.int/media/docs/default-source/mental-health/five-well-being-index-%28who-5%29/who5_chinese_pr.pdf?sfvrsn=a6a33639_5", "use": "Chinese wording and translation disclaimer; English original remains binding."},
            {"title": "WHO-5 Japanese translation", "url": "https://cdn.who.int/media/docs/default-source/mental-health/five-well-being-index-%28who-5%29/who-5_japanese.pdf?sfvrsn=f3362e57_5", "use": "Japanese wording and translation disclaimer; English original remains binding."},
            {"title": "WHO-5 license notice", "url": "https://creativecommons.org/licenses/by-nc-sa/3.0/igo/", "use": "Non-commercial attribution/share-alike scope; commercial use and endorsement remain blocked."},
        ],
        "publicationBlockers": [
            "PROFESSIONAL_REVIEW_PENDING",
            "TRILINGUAL_TRANSLATION_REVIEW_PENDING",
            "LICENSE_SCOPE_REVIEW_PENDING",
            "POPULATION_SPECIFIC_CUTOFF_REVIEW_PENDING",
            "BUSINESS_ACCEPTANCE_PENDING",
        ],
    }


def main() -> None:
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    payload = build_package()
    OUTPUT.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"wrote {OUTPUT} ({len(payload['questions'])} questions, {len(payload['goldenCases'])} Golden Cases)")


if __name__ == "__main__":
    main()
