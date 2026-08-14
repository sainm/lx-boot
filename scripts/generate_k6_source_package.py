#!/usr/bin/env python3
"""Generate the official-use Kessler K6 source package.

The package records the official free-use notice and the published Mandarin
and Japanese forms.  It remains an application DRAFT until the organisation's
qualified reviewer approves the translations, population scope and report
wording; the generator never fabricates that approval.
"""

from __future__ import annotations

import json
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "doc" / "scale-packages" / "k6-v1-source-official-draft.json"
LOCALES = ("zh-CN", "ja-JP", "en")


QUESTIONS = [
    (1, "在过去30天中，您经常会感到……紧张？", "過去30日の間にどれくらいしばしば…神経過敏に感じましたか", "During the past 30 days, about how often did you feel nervous?"),
    (2, "在过去30天中，您经常会感到……绝望？", "過去30日の間にどれくらいしばしば…絶望的だと感じましたか", "During the past 30 days, about how often did you feel hopeless?"),
    (3, "在过去30天中，您经常会感到……不安或烦躁？", "過去30日の間にどれくらいしばしば…そわそわしたり、落ち着きなく感じましたか", "During the past 30 days, about how often did you feel restless or fidgety?"),
    (4, "在过去30天中，您经常会感到……太沮丧以至于什么都不能让您愉快起来？", "過去30日の間にどれくらいしばしば…気分が沈みこんで、何が起こっても気が晴れないように感じましたか", "During the past 30 days, about how often did you feel so depressed that nothing could cheer you up?"),
    (5, "在过去30天中，您经常会感到……做每一件事情都很费劲？", "過去30日の間にどれくらいしばしば…何をするのも骨折りだと感じましたか", "During the past 30 days, about how often did you feel that everything was an effort?"),
    (6, "在过去30天中，您经常会感到……无价值？", "過去30日の間にどれくらいしばしば…自分は価値のない人間だと感じましたか", "During the past 30 days, about how often did you feel worthless?"),
]


def tr(zh: str, ja: str, en: str) -> dict[str, str]:
    return {"zh-CN": zh, "ja-JP": ja, "en": en}


OPTION_LABELS = [
    tr("全部时间", "いつも", "All of the time"),
    tr("大部分时间", "たいてい", "Most of the time"),
    tr("一部分时间", "ときどき", "Some of the time"),
    tr("偶尔", "少しだけ", "A little of the time"),
    tr("无", "全くない", "None of the time"),
]


def answer_for_effective_score(question_no: int, score: int) -> dict[str, object]:
    # The official form presents 1=all of the time through 5=none of the
    # time.  The package stores those response positions as raw 0..4 and
    # uses the existing reverse-score flag to produce 0=no distress..4=high.
    return {"questionNo": question_no, "optionCodes": [str(5 - score)]}


def answers(scores: list[int]) -> list[dict[str, object]]:
    return [answer_for_effective_score(no, score) for no, score in enumerate(scores, start=1)]


def result_rule(code: str, minimum: int, maximum: int, risk: str, title: tuple[str, str, str], description: tuple[str, str, str], suggestion: tuple[str, str, str]) -> dict[str, object]:
    return {
        "ruleCode": code,
        "riskLevel": risk,
        "scoreMin": minimum,
        "scoreMax": maximum,
        "scoreSource": "RAW_SCORE",
        "translations": {
            locale: {
                "resultTitle": title[index],
                "resultDescription": description[index],
                "suggestionText": suggestion[index],
                "reviewStatus": "DRAFT",
            }
            for index, locale in enumerate(LOCALES)
        },
    }


def build_package() -> dict[str, object]:
    options = [
        {"code": str(index + 1), "score": index, "translations": OPTION_LABELS[index]}
        for index in range(5)
    ]
    questions = [
        {
            "questionNo": no,
            "dimensionCode": "K6_TOTAL",
            "questionType": "SINGLE_CHOICE",
            "required": True,
            "reverseScore": True,
            "translations": {
                "zh-CN": {"text": zh, "reviewStatus": "DRAFT"},
                "ja-JP": {"text": ja, "reviewStatus": "DRAFT"},
                "en": {"text": en, "reviewStatus": "DRAFT"},
            },
            "options": options,
        }
        for no, zh, ja, en in QUESTIONS
    ]

    low_title = ("未达到筛查切点", "スクリーニングカットオフ未満", "Below the screening cut point")
    low_description = (
        "总分为0–12。该结果仅表示未达到本包采用的筛查切点，不等同于没有心理困扰，也不是临床诊断。",
        "合計得点は0～12です。この結果は本パッケージのスクリーニングカットオフ未満であることだけを示し、心理的苦痛がないことや臨床診断を意味しません。",
        "The total is 0–12. This only indicates that the score is below the cut point used by this package; it does not mean no distress and is not a clinical diagnosis.",
    )
    low_suggestion = (
        "如果仍感到困扰，请结合专业人员访谈和其他信息评估。",
        "困りごとが続く場合は、専門職との面接や他の情報を併せて評価してください。",
        "If distress continues, combine this result with a professional interview and other information.",
    )
    elevated_title = ("达到筛查切点（需进一步评估）", "スクリーニングカットオフ以上（追加評価が必要）", "At or above the screening cut point (further assessment needed)")
    elevated_description = (
        "总分为13–24。13+切点来自特定人群的筛查校准；它不是诊断，不能脱离适用人群和专业评估解释。",
        "合計得点は13～24です。13以上のカットオフは特定集団でのスクリーニング校正に基づくもので、診断ではなく、対象集団と専門的評価から切り離して解釈できません。",
        "The total is 13–24. The 13+ cut point is calibrated for screening in a specific population; it is not a diagnosis and must not be interpreted apart from population scope and professional assessment.",
    )
    elevated_suggestion = (
        "请由合格的专业人员结合适用人群、功能影响和安全风险进行进一步评估。",
        "適用集団、生活機能への影響、安全上のリスクを踏まえ、資格のある専門職による追加評価を受けてください。",
        "A qualified professional should conduct further assessment considering population scope, functional impact and safety risk.",
    )

    return {
        "format": "PSY_SCALE_SOURCE_PACKAGE",
        "schemaVersion": 1,
        "scale": {
            "scaleCode": "K6_OFFICIAL_FREE_USE",
            "scaleName": "Kessler Psychological Distress Scale (K6)",
            "versionNo": "official-self-admin-v1",
            "applicableTarget": "GENERAL_ADULT_SCREENING",
            "scoreMethod": "SIMPLE_SUM",
            "scoreCoefficient": 1,
            "assessmentMode": "SELF",
            "responseScale": {"min": 0, "max": 4, "labels": ["All of the time", "Most of the time", "Some of the time", "A little of the time", "None of the time"]},
            "qualityPolicy": {"missingAnswerPolicy": "REJECT", "maxMissingRatio": 0, "invalidResultAction": "INVALIDATE", "requireAllRequiredAnswers": True},
            "reportTemplate": "SINGLE_SCORE",
            "algorithmBinding": {"algorithmCode": "GENERIC_SCORE_CALCULATOR", "algorithmVersion": "1", "implementationType": "BUILTIN"},
            "instruction": {
                "zh-CN": "以下问题询问您过去30天的情绪。请选择每种情绪出现的频率。K6是筛查工具，不等同于临床诊断。",
                "ja-JP": "以下の質問は過去30日間の気持ちについて尋ねます。それぞれの気持ちを感じた頻度を選んでください。K6はスクリーニング用で、臨床診断ではありません。",
                "en": "The questions ask about feelings during the past 30 days. Select how often each feeling occurred. The K6 is a screening tool, not a clinical diagnosis.",
            },
        },
        "governance": {
            "sourceTitle": "Official K6/K10 scale page and posted forms",
            "publisherName": "Ronald C. Kessler, PhD / Harvard Medical School",
            "copyrightStatus": "AUTHORIZED",
            "rightsHolder": "Ronald C. Kessler, PhD",
            "authorizationStatus": "NOT_REQUIRED",
            "authorizationType": "OFFICIAL_FREE_USE_NOTICE",
            "authorizationScope": "The official page states that K6/K10 use is free without formal permission; cite the article and copyright. Posted translations are unrestricted with the same acknowledgement requirement.",
            "authorizedLanguages": "en,zh-CN,ja-JP",
            "governanceStatus": "DRAFT",
            "targetPopulation": "General-population screening; the 13+ cut point is population-specific and not diagnostic.",
            "nonDiagnosticStatement": "K6 is a screening measure of nonspecific psychological distress and does not establish a diagnosis.",
            "reviewStatus": "PENDING_REVIEW",
        },
        "translations": {
            "zh-CN": {
                "scaleName": "K6 心理困扰量表",
                "purposeText": "用于筛查过去30天的非特异性心理困扰。",
                "resultVisibilityText": "结果供被测者及获授权的专业人员查看。",
                "nonDiagnosticText": "K6是心理困扰筛查工具，不能据此作出临床诊断。",
                "helpResourceText": "如持续感到困扰或担心自身安全，请及时联系合格的专业人员或当地紧急援助。",
                "reviewStatus": "DRAFT",
            },
            "ja-JP": {
                "scaleName": "K6 心理的苦痛尺度",
                "purposeText": "過去30日間の非特異的な心理的苦痛をスクリーニングします。",
                "resultVisibilityText": "結果は回答者本人および権限を付与された専門職が閲覧できます。",
                "nonDiagnosticText": "K6は心理的苦痛のスクリーニング尺度であり、臨床診断を確定するものではありません。",
                "helpResourceText": "苦痛が続く場合や安全上の不安がある場合は、資格のある専門職または地域の緊急支援に連絡してください。",
                "reviewStatus": "DRAFT",
            },
            "en": {
                "scaleName": "Kessler Psychological Distress Scale (K6)",
                "purposeText": "Screens for nonspecific psychological distress during the past 30 days.",
                "resultVisibilityText": "Results are visible to the respondent and authorized professionals.",
                "nonDiagnosticText": "The K6 screens for psychological distress and does not establish a clinical diagnosis.",
                "helpResourceText": "If distress persists or you are concerned about immediate safety, contact a qualified professional or local emergency support.",
                "reviewStatus": "DRAFT",
            },
        },
        "dimensions": [{
            "dimensionCode": "K6_TOTAL",
            "questionNos": [1, 2, 3, 4, 5, 6],
            "translations": {
                "zh-CN": {"name": "心理困扰总分", "description": "六个项目反映过去30天的非特异性心理困扰频率。", "reviewStatus": "DRAFT"},
                "ja-JP": {"name": "心理的苦痛合計", "description": "6項目で過去30日間の非特異的な心理的苦痛の頻度を示します。", "reviewStatus": "DRAFT"},
                "en": {"name": "Psychological distress total", "description": "Six items describe the frequency of nonspecific psychological distress during the past 30 days.", "reviewStatus": "DRAFT"},
            },
        }],
        "questions": questions,
        "scoring": {"canonicalConvention": "0_TO_4_AFTER_RECODE", "dimensionAggregation": "SIMPLE_SUM", "dimensionRule": "sum of the six recoded item scores", "indices": {}},
        "norms": {"status": "NOT_APPLICABLE", "interpretation": "The official simple K6 score is used; the 13+ calibration must remain scoped to the validated population."},
        "resultRules": [
            result_rule("K6_BELOW_CUTOFF", 0, 12, "NORMAL", low_title, low_description, low_suggestion),
            result_rule("K6_AT_OR_ABOVE_CUTOFF", 13, 24, "ATTENTION", elevated_title, elevated_description, elevated_suggestion),
        ],
        "highRiskRules": [],
        "goldenCases": [
            {"caseCode": "K6_ALL_NONE", "caseType": "NORMAL", "sourceReference": "Official K6 scoring form; all responses none of the time", "input": {"answers": answers([0, 0, 0, 0, 0, 0])}, "expected": {"valid": True, "totalScore": 0, "riskLevel": "NORMAL"}},
            {"caseCode": "K6_CUTOFF_13", "caseType": "BOUNDARY", "sourceReference": "Official K6 scoring FAQ; 13+ population-specific screening cut point", "input": {"answers": answers([4, 4, 3, 2, 0, 0])}, "expected": {"valid": True, "totalScore": 13, "riskLevel": "ATTENTION"}},
            {"caseCode": "K6_REVERSE_RECODE", "caseType": "REVERSE", "sourceReference": "Official K6 scoring FAQ; recode frequency responses to 0–4", "input": {"answers": answers([4, 0, 0, 0, 0, 0])}, "expected": {"valid": True, "totalScore": 4, "riskLevel": "NORMAL"}},
            {"caseCode": "K6_ALL_HIGH", "caseType": "NORMAL", "sourceReference": "Official K6 scoring form; all responses all of the time", "input": {"answers": answers([4, 4, 4, 4, 4, 4])}, "expected": {"valid": True, "totalScore": 24, "riskLevel": "ATTENTION"}},
            {"caseCode": "K6_MISSING_REQUIRED", "caseType": "MISSING", "sourceReference": "Official form requires all six K6 items", "input": {"answers": answers([0, 0, 0, 0, 0])}, "expected": {"valid": False, "errorCode": "MISSING_REQUIRED_ANSWER"}},
            {
                "caseCode": "K6_INVALID_OPTION",
                "caseType": "INVALID",
                "sourceReference": "Official five-position response scale",
                "input": {
                    "answers": [
                        {"questionNo": 1, "optionCodes": ["9"]},
                        *[{"questionNo": question_no, "optionCodes": ["5"]} for question_no in range(2, 7)],
                    ]
                },
                "expected": {"valid": False, "errorCode": "OPTION_NOT_FOUND"},
            },
        ],
        "sourceReferences": [
            {"title": "Harvard Kessler official K6/K10 scale page", "url": "https://rckessler.scholars.harvard.edu/k10-and-k6-scales", "use": "Official free-use and translation notice, posted forms and citation requirement."},
            {"title": "Official K6/K10 scoring FAQ", "url": "https://rckessler.scholars.harvard.edu/sites/g/files/omnuum8166/files/2026-03/Scoring_K6_K10.pdf", "use": "0–24 recoding, 13+ cut point scope and missing-value caution."},
            {"title": "Official Chinese Mandarin K6 form", "url": "https://rckessler.scholars.harvard.edu/sites/g/files/omnuum8166/files/2026-03/Chinese_Mandarin_K6.pdf", "use": "Posted Mandarin wording and response order."},
            {"title": "Official Japanese K6 form", "url": "https://rckessler.scholars.harvard.edu/sites/g/files/omnuum8166/files/2026-03/Japanese_K6.pdf", "use": "Posted Japanese wording and response order."},
            {"title": "ITC Guidelines for Translating and Adapting Tests", "url": "https://www.intestcom.org/files/guideline_test_adaptation_2ed.pdf", "use": "Internal review checklist for trilingual deployment and documentation."},
            {"title": "Kessler et al. 2003 PubMed record", "url": "https://pubmed.ncbi.nlm.nih.gov/12578574/", "use": "Primary publication citation for the K6/K10 instrument."},
        ],
        "publicationBlockers": [
            "PROFESSIONAL_REVIEW_PENDING",
            "TRILINGUAL_TRANSLATION_REVIEW_PENDING",
            "POPULATION_CUTOFF_SCOPE_REVIEW_PENDING",
            "BUSINESS_ACCEPTANCE_PENDING",
        ],
    }


def main() -> None:
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    payload = build_package()
    OUTPUT.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"wrote {OUTPUT} ({len(payload['questions'])} questions, {len(payload['resultRules'])} result rules)")


if __name__ == "__main__":
    main()
