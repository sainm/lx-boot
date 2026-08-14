#!/usr/bin/env python3
"""Generate the reviewable PHQ-9 severity source package.

The nine-item instrument and 0..3 scoring convention follow the Pfizer/PHQ
public materials.  The Chinese and Japanese strings in this repository are
project drafts: the Japanese electronic-use statement and all project-specific
population, crisis-response, professional-review and business-use decisions
remain external gates.  This file creates a technical package only; it does
not grant clinical approval or authorize production use.
"""

from __future__ import annotations

import json
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "doc" / "scale-packages" / "phq9-v1-source-draft.json"
LOCALES = ("zh-CN", "ja-JP", "en")

QUESTIONS = [
    (
        "做事时提不起劲或没有兴趣",
        "物事に対してほとんど興味がない、または楽しめない",
        "Little interest or pleasure in doing things",
    ),
    (
        "感到心情低落、沮丧或绝望",
        "気分が落ち込む、憂うつになる、または希望が持てない",
        "Feeling down, depressed, or hopeless",
    ),
    (
        "入睡困难、睡不安稳或睡眠过多",
        "寝つきが悪い、途中で目が覚める、または眠りすぎる",
        "Trouble falling or staying asleep, or sleeping too much",
    ),
    (
        "感到疲倦或没有活力",
        "疲れを感じる、または気力がない",
        "Feeling tired or having little energy",
    ),
    (
        "食欲不振或吃得太多",
        "食欲がない、または食べ過ぎる",
        "Poor appetite or overeating",
    ),
    (
        "对自己感觉不好，或者觉得自己是个失败者，或者让自己或家人失望",
        "自分は悪い人間だ、失敗者だ、自分や家族を失望させていると感じる",
        "Feeling bad about yourself—or that you are a failure or have let yourself or your family down",
    ),
    (
        "注意力不集中，例如看报纸或看电视时",
        "新聞を読む、テレビを見るなどのときに集中するのが難しい",
        "Trouble concentrating on things, such as reading the newspaper or watching television",
    ),
    (
        "行动或说话速度缓慢到别人已经察觉，或者相反，烦躁不安、走动比平时多",
        "動作や話し方が遅くなり、他の人が気づくほどになる、または反対に、落ち着かず普段より動き回る",
        "Moving or speaking so slowly that other people could have noticed, or the opposite—being so fidgety or restless that you have been moving around a lot more than usual",
    ),
    (
        "有不如死掉或用某种方式伤害自己的念头",
        "死んだほうがよい、または何らかの方法で自分を傷つけたいと思う",
        "Thoughts that you would be better off dead or of hurting yourself in some way",
    ),
]

OPTION_LABELS = {
    "zh-CN": ["完全没有", "有几天", "超过一半的天数", "几乎每天"],
    "ja-JP": ["全くない", "数日", "半分以上", "ほとんど毎日"],
    "en": ["Not at all", "Several days", "More than half the days", "Nearly every day"],
}

INSTRUCTIONS = {
    "zh-CN": "在过去两周里，您有多少次受到以下问题的困扰？请为每项选择最符合的频率。PHQ-9用于筛查和症状严重程度评估，不能单独用于临床诊断。第9题出现任何阳性回答都必须由指定专业人员进一步人工评估。",
    "ja-JP": "この2週間、次のような問題にどのくらい頻繁に悩まされましたか。各項目について最も近い頻度を選んでください。PHQ-9はスクリーニングと症状の重症度評価に用いるもので、単独で臨床診断を確定するものではありません。9番に少しでも該当する場合は、指定された専門職による追加の確認が必要です。",
    "en": "Over the last two weeks, how often have you been bothered by the following problems? Select the closest frequency for each item. The PHQ-9 supports screening and symptom-severity assessment and cannot establish a clinical diagnosis on its own. Any positive response to item 9 requires further human assessment by the designated professional workflow.",
}

SCALE_TRANSLATIONS = {
    "zh-CN": {
        "scaleName": "患者健康问卷抑郁量表（PHQ-9）",
        "purposeText": "用于筛查过去两周的抑郁症状并记录症状严重程度。",
        "resultVisibilityText": "结果可供被测者及获授权的专业人员查看。",
        "nonDiagnosticText": "PHQ-9用于筛查和症状严重程度评估，不能单独用于临床诊断。第9题阳性只表示需要人工进一步评估，不表示诊断或即时风险结论。",
        "helpResourceText": "如果第9题有任何阳性回答，或您担心自身安全，请立即联系指定专业人员和当地紧急援助；系统结果不能替代人工危机评估。",
    },
    "ja-JP": {
        "scaleName": "患者健康質問票（PHQ-9）",
        "purposeText": "過去2週間の抑うつ症状をスクリーニングし、その重症度を記録します。",
        "resultVisibilityText": "結果は回答者本人および権限を付与された専門職が閲覧できます。",
        "nonDiagnosticText": "PHQ-9はスクリーニングと症状の重症度評価に用いるもので、単独で臨床診断を確定するものではありません。9番の陽性は人的な追加確認が必要であることを示すだけで、診断や即時リスクの結論ではありません。",
        "helpResourceText": "9番に少しでも該当する場合、または安全上の不安がある場合は、指定された専門職と地域の緊急支援に直ちに連絡してください。システム結果は人的な危機評価に代わりません。",
    },
    "en": {
        "scaleName": "Patient Health Questionnaire-9 (PHQ-9)",
        "purposeText": "Screens depressive symptoms over the past two weeks and records symptom severity.",
        "resultVisibilityText": "Results are visible to the respondent and authorized professionals.",
        "nonDiagnosticText": "The PHQ-9 supports screening and symptom-severity assessment and cannot establish a clinical diagnosis on its own. A positive item 9 response requires human follow-up; it is not a diagnosis or an immediate-risk conclusion.",
        "helpResourceText": "For any positive item 9 response, or an immediate safety concern, contact the designated professional workflow and local emergency support promptly. A system result cannot replace a human crisis assessment.",
    },
}


def answers(scores: list[int]) -> list[dict[str, object]]:
    return [{"questionNo": number, "optionCodes": [str(score)]} for number, score in enumerate(scores, 1)]


def expected(total: int, risk: str, *, high_risk: bool = False, rule_code: str | None = None) -> dict[str, object]:
    result: dict[str, object] = {
        "valid": True,
        "totalScore": total,
        "riskLevel": risk,
        "metrics": {},
    }
    if high_risk:
        result["highRiskTriggered"] = True
        result["highRiskRuleCode"] = rule_code
    return result


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


def high_risk_rule() -> dict[str, object]:
    translations = {
        "zh-CN": {
            "resultTitle": "第9题阳性信号（需人工复核）",
            "resultDescription": "第9题出现任何阳性回答。这不是诊断或即时风险结论，必须由指定专业人员按照已批准的人工危机响应流程进一步评估。",
            "suggestionText": "请立即由指定专业人员人工复核并记录处置；如存在即时安全风险，请联系当地紧急援助。",
            "reviewStatus": "DRAFT",
        },
        "ja-JP": {
            "resultTitle": "9番の陽性シグナル（人的確認が必要）",
            "resultDescription": "9番に少しでも該当しました。これは診断や即時リスクの結論ではなく、承認済みの人的な危機対応手順に沿った追加評価が必要です。",
            "suggestionText": "指定された専門職が直ちに確認し、対応記録を残してください。即時の安全上の懸念がある場合は地域の緊急支援に連絡してください。",
            "reviewStatus": "DRAFT",
        },
        "en": {
            "resultTitle": "Positive item 9 signal (human review required)",
            "resultDescription": "Item 9 received a positive response. This is not a diagnosis or an immediate-risk conclusion; the designated professional workflow must perform further human assessment.",
            "suggestionText": "Have the designated professional review it immediately and record the response; use local emergency support for an immediate safety concern.",
            "reviewStatus": "DRAFT",
        },
    }
    return {
        "ruleCode": "PHQ9_ITEM9_POSITIVE",
        "questionNo": 9,
        "scoreThreshold": 1,
        "warningLevel": "HIGH",
        "reviewStatus": "PENDING_PROFESSIONAL_REVIEW",
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
            "dimensionCode": "PHQ9_TOTAL",
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
            "scaleCode": "PHQ9_FREE_USE",
            "scaleName": "Patient Health Questionnaire-9 (PHQ-9)",
            "versionNo": "pfizer-public-domain-severity-v1",
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
            "reportTemplate": "RISK_TRIAGE",
            "algorithmBinding": {
                "algorithmCode": "GENERIC_SCORE_CALCULATOR",
                "algorithmVersion": "1",
                "implementationType": "BUILTIN",
            },
            "instruction": INSTRUCTIONS,
        },
        "governance": {
            "sourceTitle": "PHQ-9 public-domain instrument, instructions and validation literature",
            "publisherName": "Pfizer Inc. and original instrument authors",
            "copyrightStatus": "PUBLIC_DOMAIN",
            "rightsHolder": "Robert L. Spitzer, Janet B.W. Williams, Kurt Kroenke and colleagues; developed with a Pfizer educational grant",
            "authorizationStatus": "NOT_REQUIRED",
            "authorizationType": "PUBLIC_DOMAIN_NO_PERMISSION_REQUIRED",
            "authorizationScope": "The PHQ family is described in the official instructions as public domain with no permission required to reproduce, translate, display or distribute. Exact project translations, electronic-use rights, population scope, crisis workflow and business presentation still require review.",
            "authorizedLanguages": "en original; zh-CN and ja-JP strings are project drafts pending language and rights review",
            "governanceStatus": "DRAFT",
            "targetPopulation": "Adult self-report screening in the original primary-care validation context; adolescent, older-adult, inpatient and other population use requires separate review.",
            "nonDiagnosticStatement": "PHQ-9 supports screening and symptom-severity assessment and does not establish a clinical diagnosis. Item 9 is a signal for human follow-up, not an automated crisis decision.",
            "reviewStatus": "PENDING_REVIEW",
        },
        "translations": {
            locale: {**SCALE_TRANSLATIONS[locale], "reviewStatus": "DRAFT"}
            for locale in LOCALES
        },
        "dimensions": [{
            "dimensionCode": "PHQ9_TOTAL",
            "questionNos": list(range(1, 10)),
            "translations": {
                "zh-CN": {"name": "PHQ-9总分", "description": "九个项目0–3分的总和。", "reviewStatus": "DRAFT"},
                "ja-JP": {"name": "PHQ-9合計得点", "description": "9項目の0～3点の合計です。", "reviewStatus": "DRAFT"},
                "en": {"name": "PHQ-9 total", "description": "Sum of the nine item scores from 0 to 3.", "reviewStatus": "DRAFT"},
            },
        }],
        "questions": questions,
        "scoring": {
            "canonicalConvention": "0_TO_3",
            "indices": {},
            "dimensionAggregation": "SIMPLE_SUM",
            "dimensionRule": "sum of all nine item scores",
        },
        "norms": {
            "status": "NOT_APPLICABLE",
            "interpretation": "Severity bands are screening score ranges, not population norms or a diagnosis.",
        },
        "resultRules": [
            result_rule(
                "PHQ9_MINIMAL", "NORMAL", 0, 4,
                ("无或极少症状", "症状なし・ごく軽度", "none or minimal symptoms"),
                ("如仍感到困扰，请结合专业访谈和其他信息评估。", "困りごとが続く場合は、専門職との面接や他の情報を併せて評価してください。", "If concerns continue, combine this result with a professional interview and other information."),
            ),
            result_rule(
                "PHQ9_MILD", "ATTENTION", 5, 9,
                ("轻度症状", "軽度の症状", "mild symptoms"),
                ("建议关注症状变化；持续或影响生活时请咨询合格专业人员。", "症状の変化に注意し、持続する場合や生活に影響する場合は資格のある専門職に相談してください。", "Monitor changes and consult a qualified professional if symptoms persist or affect daily life."),
            ),
            result_rule(
                "PHQ9_MODERATE", "MEDIUM", 10, 14,
                ("中度症状", "中等度の症状", "moderate symptoms"),
                ("建议由合格专业人员结合功能影响进行进一步评估。", "生活機能への影響を含め、資格のある専門職による追加評価を受けてください。", "Seek further assessment by a qualified professional, including functional impact."),
            ),
            result_rule(
                "PHQ9_MODERATELY_SEVERE", "HIGH", 15, 19,
                ("中重度症状", "中等度から重度の症状", "moderately severe symptoms"),
                ("建议尽快联系合格专业人员进行评估；该结果不是诊断。", "早めに資格のある専門職へ相談して評価を受けてください。この結果は診断ではありません。", "Contact a qualified professional promptly for assessment; this result is not a diagnosis."),
            ),
            result_rule(
                "PHQ9_SEVERE", "HIGH", 20, 27,
                ("重度症状", "重度の症状", "severe symptoms"),
                ("建议尽快联系合格专业人员；如存在即时安全风险，请联系当地紧急援助。", "早めに資格のある専門職へ相談し、緊急の安全上のリスクがある場合は地域の緊急支援に連絡してください。", "Contact a qualified professional promptly; use local emergency support for an immediate safety concern."),
            ),
        ],
        "highRiskRules": [high_risk_rule()],
        "goldenCases": [
            {"caseCode": "PHQ9_ALL_ZERO", "caseType": "NORMAL", "sourceReference": "Official PHQ-9 0..3 scoring; all nine responses at 0", "input": {"answers": answers([0] * 9)}, "expected": expected(0, "NORMAL")},
            {"caseCode": "PHQ9_BOUNDARY_4", "caseType": "BOUNDARY", "sourceReference": "Upper boundary of the 0..4 minimal band", "input": {"answers": answers([1, 1, 1, 1, 0, 0, 0, 0, 0])}, "expected": expected(4, "NORMAL")},
            {"caseCode": "PHQ9_BOUNDARY_5", "caseType": "BOUNDARY", "sourceReference": "Lower boundary of the 5..9 mild band", "input": {"answers": answers([1, 1, 1, 1, 1, 0, 0, 0, 0])}, "expected": expected(5, "ATTENTION")},
            {"caseCode": "PHQ9_BOUNDARY_10", "caseType": "BOUNDARY", "sourceReference": "Lower boundary of the 10..14 moderate band", "input": {"answers": answers([2, 2, 2, 1, 1, 1, 1, 0, 0])}, "expected": expected(10, "MEDIUM")},
            {"caseCode": "PHQ9_BOUNDARY_15", "caseType": "BOUNDARY", "sourceReference": "Lower boundary of the 15..19 moderately severe band", "input": {"answers": answers([2, 2, 2, 2, 2, 2, 1, 1, 1])}, "expected": expected(15, "HIGH", high_risk=True, rule_code="PHQ9_ITEM9_POSITIVE")},
            {"caseCode": "PHQ9_BOUNDARY_20", "caseType": "BOUNDARY", "sourceReference": "Lower boundary of the 20..27 severe band", "input": {"answers": answers([3, 3, 3, 3, 2, 2, 2, 1, 1])}, "expected": expected(20, "HIGH", high_risk=True, rule_code="PHQ9_ITEM9_POSITIVE")},
            {"caseCode": "PHQ9_ITEM9_SIGNAL", "caseType": "HIGH_RISK", "sourceReference": "Official instructions require human suicide-risk assessment for any positive item 9 response", "input": {"answers": answers([0, 0, 0, 0, 0, 0, 0, 0, 1])}, "expected": expected(1, "HIGH", high_risk=True, rule_code="PHQ9_ITEM9_POSITIVE")},
            {"caseCode": "PHQ9_ALL_HIGH", "caseType": "NORMAL", "sourceReference": "PHQ-9 0..3 scoring; all nine responses at 3", "input": {"answers": answers([3] * 9)}, "expected": expected(27, "HIGH", high_risk=True, rule_code="PHQ9_ITEM9_POSITIVE")},
            {"caseCode": "PHQ9_MISSING_REQUIRED", "caseType": "MISSING", "sourceReference": "Required-answer policy", "input": {"answers": answers([0] * 8)}, "expected": {"valid": False, "errorCode": "MISSING_REQUIRED_ANSWER"}},
            {"caseCode": "PHQ9_INVALID_OPTION", "caseType": "INVALID", "sourceReference": "Controlled option-code regression case", "input": {"answers": [{"questionNo": 1, "optionCodes": ["9"]}] + answers([0] * 9)[1:]}, "expected": {"valid": False, "errorCode": "OPTION_NOT_FOUND"}},
        ],
        "sourceReferences": [
            {"title": "Pfizer free public access announcement", "url": "https://www.pfizer.com/news/press-release/press-release-detail/pfizer_to_offer_free_public_access_to_mental_health_assessment_tools_to_improve_diagnosis_and_patient_care", "use": "Primary source for the PHQ family public-access and no-copyright-restriction statement."},
            {"title": "Pfizer FAQ: Patient Health Questionnaires", "url": "https://www.pfizer.com/contact/faqs", "use": "Primary source for PHQ-9 access and approved translation availability; exact project translation still requires review."},
            {"title": "PHQ and GAD-7 Instructions", "url": "https://www.uab.edu/medicine/pcp-sci/images/SCIMS/PHQ-9_Instruction_Manual.pdf", "use": "Scoring, 0..27 range, severity cutpoints, non-diagnostic use and item 9 human-assessment guidance."},
            {"title": "Original PHQ-9 validation", "url": "https://doi.org/10.1001/jama.288.22.2796", "use": "Original adult primary-care validation and nine-item structure."},
            {"title": "NIH PHQ-9 Common Data Element", "url": "https://www.nih.gov/node/19946", "use": "Government repository entry confirming the PHQ-9 instrument and a Traditional Chinese source file; not used as proof of this project's translation authorization."},
            {"title": "Japanese PHQ-9 translation rights notice", "url": "https://jpsad.jp/other_work/research-2012/files/jpsad_phq9.pdf", "use": "Evidence that a published Japanese version carries a separate no-electronic-use notice; this package therefore uses project-draft Japanese text and remains blocked pending rights review."},
        ],
        "publicationBlockers": [
            "PROFESSIONAL_REVIEW_PENDING",
            "TRILINGUAL_TRANSLATION_REVIEW_PENDING",
            "JAPANESE_ELECTRONIC_RIGHTS_REVIEW_PENDING",
            "POPULATION_SCOPE_REVIEW_PENDING",
            "ITEM9_CRISIS_RESPONDER_SLA_PENDING",
            "RESULT_INTERPRETATION_REVIEW_PENDING",
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
