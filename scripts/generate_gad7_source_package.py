#!/usr/bin/env python3
"""Generate the reviewable GAD-7 source package.

The instrument and scoring rules follow the GAD-7 materials released for use
without copyright restriction by Pfizer.  The Chinese and Japanese text in
this repository remains DRAFT until the project's named reviewers approve the
exact wording and intended population.  Generating this artifact is therefore
not a clinical approval or a production release decision.
"""

from __future__ import annotations

import json
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "doc" / "scale-packages" / "gad7-v1-source-draft.json"
LOCALES = ("zh-CN", "ja-JP", "en")

QUESTIONS = [
    ("感到紧张、焦虑或不安", "緊張感、不安感または神経過敏を感じる", "Feeling nervous, anxious, or on edge"),
    ("无法停止或控制担忧", "心配することを止められない、または心配をコントロールできない", "Not being able to stop or control worrying"),
    ("对各种各样的事情担忧过多", "いろいろなことを心配しすぎる", "Worrying too much about different things"),
    ("难以放松", "くつろぐことが難しい", "Trouble relaxing"),
    ("由于不安而难以静坐", "じっとしていることができないほど落ち着かない", "Being so restless that it is hard to sit still"),
    ("容易心烦或易怒", "いらいらしがちで、怒りっぽい", "Becoming easily annoyed or irritable"),
    ("感到害怕，好像会发生可怕的事情", "何か恐ろしいことが起こるのではないかと恐れを感じる", "Feeling afraid as if something awful might happen"),
]

OPTION_LABELS = {
    "zh-CN": ["完全没有", "有几天", "超过一半的天数", "几乎每天"],
    "ja-JP": ["全くない", "数日", "半分以上", "ほとんど毎日"],
    "en": ["Not at all", "Several days", "More than half the days", "Nearly every day"],
}

INSTRUCTIONS = {
    "zh-CN": "在过去两周里，您有多少次受到以下问题困扰？请为每项选择最符合的频率。GAD-7是筛查与症状严重程度量表，不等同于临床诊断。",
    "ja-JP": "この2週間、次のような問題にどのくらい頻繁に悩まされましたか。各項目について最も近い頻度を選んでください。GAD-7はスクリーニングと症状の重症度評価に用いる尺度で、臨床診断ではありません。",
    "en": "Over the last two weeks, how often have you been bothered by the following problems? Select the closest frequency for each item. The GAD-7 is a screening and symptom-severity measure, not a clinical diagnosis.",
}

SCALE_TRANSLATIONS = {
    "zh-CN": {
        "scaleName": "广泛性焦虑障碍量表（GAD-7）",
        "purposeText": "用于筛查过去两周的焦虑症状并记录症状严重程度。",
        "resultVisibilityText": "结果可供被测者及获授权的专业人员查看。",
        "nonDiagnosticText": "GAD-7用于筛查和症状严重程度评估，不能单独用于临床诊断。",
        "helpResourceText": "如果症状持续、影响日常功能或您担心自身安全，请联系合格的专业人员或当地紧急援助。",
    },
    "ja-JP": {
        "scaleName": "全般性不安障害尺度（GAD-7）",
        "purposeText": "過去2週間の不安症状をスクリーニングし、その重症度を記録します。",
        "resultVisibilityText": "結果は回答者本人および権限を付与された専門職が閲覧できます。",
        "nonDiagnosticText": "GAD-7はスクリーニングと症状の重症度評価に用いるもので、単独で臨床診断を確定するものではありません。",
        "helpResourceText": "症状が続く場合、日常生活に影響する場合、または安全上の不安がある場合は、資格のある専門職または地域の緊急支援に連絡してください。",
    },
    "en": {
        "scaleName": "Generalized Anxiety Disorder 7-item scale (GAD-7)",
        "purposeText": "Screens anxiety symptoms over the past two weeks and records symptom severity.",
        "resultVisibilityText": "Results are visible to the respondent and authorized professionals.",
        "nonDiagnosticText": "The GAD-7 supports screening and symptom-severity assessment and cannot establish a clinical diagnosis on its own.",
        "helpResourceText": "If symptoms persist, affect daily functioning, or you are concerned about immediate safety, contact a qualified professional or local emergency support.",
    },
}


def answers(scores: list[int]) -> list[dict[str, object]]:
    return [{"questionNo": number, "optionCodes": [str(score)]} for number, score in enumerate(scores, 1)]


def expected(total: int, risk: str) -> dict[str, object]:
    return {"valid": True, "totalScore": total, "riskLevel": risk, "metrics": {}}


def result_rule(
    code: str,
    risk: str,
    minimum: int,
    maximum: int,
    severity: tuple[str, str, str],
    suggestion: tuple[str, str, str],
) -> dict[str, object]:
    translations: dict[str, object] = {}
    for locale, severity_text, suggestion_text in zip(LOCALES, severity, suggestion):
        if locale == "zh-CN":
            description = f"总分为{minimum}–{maximum}，属于{severity_text}范围。该分级用于筛查与症状严重程度描述，不是临床诊断。"
        elif locale == "ja-JP":
            description = f"合計得点は{minimum}～{maximum}で、{severity_text}の範囲です。この区分はスクリーニングと症状の重症度記述用で、臨床診断ではありません。"
        else:
            description = f"The total score is {minimum}–{maximum}, within the {severity_text} range. This band describes screening symptom severity and is not a clinical diagnosis."
        translations[locale] = {
            "resultTitle": severity_text,
            "resultDescription": description,
            "suggestionText": suggestion_text,
            "reviewStatus": "DRAFT",
        }
    return {
        "ruleCode": code,
        "riskLevel": risk,
        "scoreMin": minimum,
        "scoreMax": maximum,
        "scoreSource": "RAW_SCORE",
        "translations": translations,
    }


def build_package() -> dict[str, object]:
    options = [
        {
            "code": str(score),
            "score": score,
            "translations": {locale: OPTION_LABELS[locale][score] for locale in LOCALES},
        }
        for score in range(4)
    ]
    questions = [
        {
            "questionNo": number,
            "dimensionCode": "GAD7_TOTAL",
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

    return {
        "format": "PSY_SCALE_SOURCE_PACKAGE",
        "schemaVersion": 1,
        "scale": {
            "scaleCode": "GAD7_FREE_USE",
            "scaleName": "Generalized Anxiety Disorder 7-item scale (GAD-7)",
            "versionNo": "pfizer-free-use-v1",
            "applicableTarget": "GENERAL_SELF_REPORT_ADULT_REVIEW_REQUIRED",
            "scoreMethod": "SIMPLE_SUM",
            "scoreCoefficient": 1,
            "assessmentMode": "SELF",
            "responseScale": {"min": 0, "max": 3, "labels": OPTION_LABELS["en"]},
            "qualityPolicy": {
                "missingAnswerPolicy": "REJECT",
                "maxMissingRatio": 0,
                "invalidResultAction": "INVALIDATE",
                "requireAllRequiredAnswers": True,
            },
            "reportTemplate": "SINGLE_SCORE",
            "algorithmBinding": {
                "algorithmCode": "GENERIC_SCORE_CALCULATOR",
                "algorithmVersion": "1",
                "implementationType": "BUILTIN",
            },
            "instruction": INSTRUCTIONS,
        },
        "governance": {
            "sourceTitle": "GAD-7 free-use instrument, instructions and validation literature",
            "publisherName": "Pfizer Inc. and original instrument authors",
            "copyrightStatus": "AUTHORIZED",
            "rightsHolder": "Robert L. Spitzer, Janet B.W. Williams, Kurt Kroenke and colleagues; developed with a Pfizer educational grant",
            "authorizationStatus": "AUTHORIZED",
            "authorizationType": "FREE_USE_NO_PERMISSION_REQUIRED",
            "authorizationScope": "Pfizer states that the PHQ and GAD-7 may be accessed without copyright restriction and at no charge. Project use, exact translations, population scope and presentation still require legal/business review.",
            "authorizedLanguages": "en; Pfizer provides approved translations, but the exact zh-CN and ja-JP package text remains subject to project review",
            "governanceStatus": "DRAFT",
            "targetPopulation": "Adult self-report screening in the original validation context; use for adolescents or other populations requires separate review.",
            "nonDiagnosticStatement": "GAD-7 supports screening and symptom-severity assessment and does not establish a clinical diagnosis.",
            "reviewStatus": "PENDING_REVIEW",
        },
        "translations": {
            locale: {**SCALE_TRANSLATIONS[locale], "reviewStatus": "DRAFT"}
            for locale in LOCALES
        },
        "dimensions": [{
            "dimensionCode": "GAD7_TOTAL",
            "questionNos": list(range(1, 8)),
            "translations": {
                "zh-CN": {"name": "GAD-7总分", "description": "七个项目0–3分的总和。", "reviewStatus": "DRAFT"},
                "ja-JP": {"name": "GAD-7合計得点", "description": "7項目の0～3点の合計です。", "reviewStatus": "DRAFT"},
                "en": {"name": "GAD-7 total", "description": "Sum of the seven item scores from 0 to 3.", "reviewStatus": "DRAFT"},
            },
        }],
        "questions": questions,
        "scoring": {
            "canonicalConvention": "0_TO_3",
            "indices": {},
            "dimensionAggregation": "SIMPLE_SUM",
            "dimensionRule": "sum of all seven item scores",
        },
        "norms": {
            "status": "NOT_APPLICABLE",
            "interpretation": "Severity bands are screening score ranges, not population norms or a diagnosis.",
        },
        "resultRules": [
            result_rule(
                "GAD7_MINIMAL", "NORMAL", 0, 4,
                ("极轻或无明显焦虑症状", "最小限の不安症状", "minimal anxiety symptoms"),
                ("如仍感到困扰，请结合专业访谈和其他信息评估。", "困りごとが続く場合は、専門職との面接や他の情報を併せて評価してください。", "If concerns continue, combine this result with a professional interview and other information."),
            ),
            result_rule(
                "GAD7_MILD", "ATTENTION", 5, 9,
                ("轻度焦虑症状", "軽度の不安症状", "mild anxiety symptoms"),
                ("建议关注症状变化；持续或影响生活时请咨询合格专业人员。", "症状の変化に注意し、持続する場合や生活に影響する場合は資格のある専門職に相談してください。", "Monitor changes and consult a qualified professional if symptoms persist or affect daily life."),
            ),
            result_rule(
                "GAD7_MODERATE", "MEDIUM", 10, 14,
                ("中度焦虑症状", "中等度の不安症状", "moderate anxiety symptoms"),
                ("建议由合格专业人员结合功能影响进行进一步评估。", "生活機能への影響を含め、資格のある専門職による追加評価を受けてください。", "Seek further assessment by a qualified professional, including functional impact."),
            ),
            result_rule(
                "GAD7_SEVERE", "HIGH", 15, 21,
                ("重度焦虑症状", "重度の不安症状", "severe anxiety symptoms"),
                ("建议尽快联系合格专业人员；如存在紧急安全风险，请联系当地紧急援助。", "早めに資格のある専門職へ相談し、緊急の安全上のリスクがある場合は地域の緊急支援に連絡してください。", "Contact a qualified professional promptly; use local emergency support for an immediate safety concern."),
            ),
        ],
        "highRiskRules": [],
        "goldenCases": [
            {"caseCode": "GAD7_ALL_ZERO", "caseType": "NORMAL", "sourceReference": "GAD-7 0..3 scoring; all seven responses at 0", "input": {"answers": answers([0, 0, 0, 0, 0, 0, 0])}, "expected": expected(0, "NORMAL")},
            {"caseCode": "GAD7_BOUNDARY_4", "caseType": "BOUNDARY", "sourceReference": "Upper boundary of the 0..4 minimal band", "input": {"answers": answers([1, 1, 1, 1, 0, 0, 0])}, "expected": expected(4, "NORMAL")},
            {"caseCode": "GAD7_BOUNDARY_5", "caseType": "BOUNDARY", "sourceReference": "Lower boundary of the 5..9 mild band", "input": {"answers": answers([1, 1, 1, 1, 1, 0, 0])}, "expected": expected(5, "ATTENTION")},
            {"caseCode": "GAD7_BOUNDARY_10", "caseType": "BOUNDARY", "sourceReference": "Original validation cutoff and lower boundary of the 10..14 moderate band", "input": {"answers": answers([2, 2, 2, 1, 1, 1, 1])}, "expected": expected(10, "MEDIUM")},
            {"caseCode": "GAD7_BOUNDARY_15", "caseType": "BOUNDARY", "sourceReference": "Lower boundary of the 15..21 severe band", "input": {"answers": answers([3, 2, 2, 2, 2, 2, 2])}, "expected": expected(15, "HIGH")},
            {"caseCode": "GAD7_ALL_HIGH", "caseType": "NORMAL", "sourceReference": "GAD-7 0..3 scoring; all seven responses at 3", "input": {"answers": answers([3, 3, 3, 3, 3, 3, 3])}, "expected": expected(21, "HIGH")},
            {"caseCode": "GAD7_MISSING_REQUIRED", "caseType": "MISSING", "sourceReference": "Required-answer policy", "input": {"answers": answers([0, 0, 0, 0, 0, 0])}, "expected": {"valid": False, "errorCode": "MISSING_REQUIRED_ANSWER"}},
            {"caseCode": "GAD7_INVALID_OPTION", "caseType": "INVALID", "sourceReference": "Controlled option-code regression case", "input": {"answers": [{"questionNo": 1, "optionCodes": ["9"]}] + answers([0, 0, 0, 0, 0, 0, 0])[1:]}, "expected": {"valid": False, "errorCode": "OPTION_NOT_FOUND"}},
        ],
        "sourceReferences": [
            {"title": "Pfizer free public access announcement", "url": "https://www.pfizer.com/news/press-release/press-release-detail/pfizer_to_offer_free_public_access_to_mental_health_assessment_tools_to_improve_diagnosis_and_patient_care", "use": "Primary evidence that PHQ and GAD-7 are available without copyright restriction and at no charge."},
            {"title": "GAD-7 English instrument", "url": "https://www.phqscreeners.com/images/sites/g/files/g10060481/f/201412/GAD-7_English.pdf", "use": "English wording, 0..3 response convention and seven-item structure."},
            {"title": "Instructions for PHQ and GAD-7 measures", "url": "https://www.ons.org/sites/default/files/PHQandGAD7_InstructionManual.pdf", "use": "Scoring and interpretation bands; external mirror of the instrument instruction manual."},
            {"title": "Original GAD-7 validation", "url": "https://doi.org/10.1001/archinte.166.10.1092", "use": "Original adult primary-care validation and cutoff evidence."},
            {"title": "Japanese GAD-7 diagnostic performance validation", "url": "https://doi.org/10.18103/mra.v13i1.6247", "use": "Evidence for the back-translated Japanese version and the cutoff in Japanese primary care; exact project wording remains draft."},
            {"title": "Chinese GAD-7 validation in general hospital outpatients", "url": "https://pmc.ncbi.nlm.nih.gov/articles/PMC8701121/", "use": "Evidence that the Chinese translation has been validated; exact project wording and population scope remain draft."},
        ],
        "publicationBlockers": [
            "PROFESSIONAL_REVIEW_PENDING",
            "TRILINGUAL_TRANSLATION_REVIEW_PENDING",
            "POPULATION_SCOPE_REVIEW_PENDING",
            "RESULT_INTERPRETATION_REVIEW_PENDING",
            "LICENSE_SCOPE_REVIEW_PENDING",
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
