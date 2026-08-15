#!/usr/bin/env python3
"""Generate the reviewable official Self-Compassion Scale Short Form package.

The SCS-SF information sheet grants permission to use the 12-item short form
for research, clinical work and teaching, and permits translation with the
specified validation approach.  This package locks the official English
short-form items and selects the corresponding items from the posted Chinese
and Japanese full-scale translations.  It remains a governed draft: the
informal comparative bands are not clinical norms and still require review.
"""

from __future__ import annotations

import json
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "doc" / "scale-packages" / "scs-sf-v1-source-official-draft.json"
LOCALES = ("zh-CN", "ja-JP", "en")


# The English item order and wording follow the official ShortSCS form.  The
# Chinese and Japanese entries are the corresponding items (1->6, 2->26,
# 3->14, 4->13, 5->15, 6->12, 7->9, 8->25, 9->2, 10->10, 11->1, 12->11)
# selected from the official full-form translations.
QUESTIONS = [
    (
        "当我在一些对自己来说重要的事情上失败后，我会不断地想自己的不足。",
        "自分にとって重要なことを失敗したとき，無力感で頭がいっぱいになる。",
        "When I fail at something important to me I become consumed by feelings of inadequacy.",
        True,
        "SCS_SF_OVER_IDENTIFICATION",
    ),
    (
        "我尽量去理解和包容自己性格中自己不喜欢的方面。",
        "自分のパーソナリティの好きでないところについては理解し，やさしい目で見るようにしている。",
        "I try to be understanding and patient towards those aspects of my personality I don’t like.",
        False,
        "SCS_SF_SELF_KINDNESS",
    ),
    (
        "当一些令人痛苦的事情发生时，我尽量用平和的心态来面对。",
        "何か苦痛を感じることが起こったとき，その状況についてバランスのとれた見方をするようにする。",
        "When something painful happens I try to take a balanced view of the situation.",
        False,
        "SCS_SF_MINDFULNESS",
    ),
    (
        "当情绪低落时，我会觉得大多数人可能比我快乐。",
        "気分が落ち込んだとき，多くの人がおそらく自分より幸せであるという気持ちになりがちである。",
        "When I’m feeling down, I tend to feel like most other people are probably happier than I am.",
        True,
        "SCS_SF_ISOLATION",
    ),
    (
        "我尽量把自己的失败看成人生经历的一部分。",
        "自分の失敗は，人間のありようの１つであると考えるようにしている。",
        "I try to see my failings as part of the human condition.",
        False,
        "SCS_SF_COMMON_HUMANITY",
    ),
    (
        "当我经历艰难困苦时，我会关心自己、善待自己。",
        "苦労を経験しているとき，必要とする程度に自分自身をいたわり，やさしくする。",
        "When I’m going through a very hard time, I give myself the caring and tenderness I need.",
        False,
        "SCS_SF_SELF_KINDNESS",
    ),
    (
        "遇到烦心事时，我会尽量让自己的情绪保持稳定。",
        "何かで苦しい思いをしたときには，感情を適度なバランスに保つようにする。",
        "When something upsets me I try to keep my emotions in balance.",
        False,
        "SCS_SF_MINDFULNESS",
    ),
    (
        "在一些对自己重要的事情上失败时，我容易觉得是自己一个人在承受失败，感到孤独。",
        "自分にとって大切な何かに失敗したとき，自分の失敗の中でひとりぼっちでいるように感じる傾向がある。",
        "When I fail at something that’s important to me, I tend to feel alone in my failure",
        True,
        "SCS_SF_ISOLATION",
    ),
    (
        "当我情绪低落时，我容易纠结于不顺心的事情。",
        "気分が落ち込んだときには，間違ったことすべてについて，くよくよと心配し，こだわる傾向にある。",
        "When I’m feeling down I tend to obsess and fixate on everything that’s wrong.",
        True,
        "SCS_SF_OVER_IDENTIFICATION",
    ),
    (
        "当我感到自己在某些方面不足时，我尽量提醒自己：大部分人和我一样，都不完美。",
        "自分自身にどこか不十分なところがあると感じると，多くの人も不十分であるという気持ちを共有していることを思い出すようにする。",
        "When I feel inadequate in some way, I try to remind myself that feelings of inadequacy are shared by most people.",
        False,
        "SCS_SF_COMMON_HUMANITY",
    ),
    (
        "对自己的缺点和不足，我持不满和批判的态度。",
        "自分自身の欠点や不十分なところについて，不満に思っているし，批判的である。",
        "I’m disapproving and judgmental about my own flaws and inadequacies.",
        True,
        "SCS_SF_SELF_JUDGMENT",
    ),
    (
        "对于我性格中那些自己不喜欢的方面，我不能容忍。",
        "自分のパーソナリティの好きでないところについては，やさしくなれないしいらだちを感じる。",
        "I’m intolerant and impatient towards those aspects of my personality I don’t like.",
        True,
        "SCS_SF_SELF_JUDGMENT",
    ),
]


OPTION_LABELS = {
    "zh-CN": ["从不如此", "很少如此", "有时如此", "经常如此", "总是如此"],
    "ja-JP": ["まったくそうしない", "ほとんどそうしない", "そうすることもある", "よくそうする", "ほとんどいつもそうする"],
    "en": ["Almost never", "Rarely", "Sometimes", "Often", "Almost always"],
}

INSTRUCTIONS = {
    "zh-CN": "请仔细阅读每个陈述，并根据您最近通常以该方式行事的频率，从“从不如此”到“总是如此”选择一个选项。SCS-SF 用于研究和个人自我观察，不是临床诊断，也没有临床常模。",
    "ja-JP": "各項目を注意深く読み、書かれている行動を最近どの程度行うかを「まったくそうしない」から「ほとんどいつもそうする」までで選んでください。SCS-SFは研究と個人の自己観察用であり、臨床診断や臨床標準値ではありません。",
    "en": "Read each statement carefully and select how often you usually act in the stated way, from Almost never to Almost always. The SCS-SF is for research and personal self-observation; it is not a clinical diagnosis and has no clinical norms.",
}

SCALE_TRANSLATIONS = {
    "zh-CN": {
        "scaleName": "自我关怀量表简版（SCS-SF）",
        "purposeText": "用于研究或个人自我观察中的自我关怀特征描述。",
        "resultVisibilityText": "结果可供被测者及获授权的专业人员查看。",
        "nonDiagnosticText": "SCS-SF 是研究/自我观察工具，不等同于临床诊断，也不提供临床常模。",
        "helpResourceText": "如果自我观察引发明显痛苦或安全担忧，请联系合格的专业人员或当地紧急援助。",
    },
    "ja-JP": {
        "scaleName": "セルフ・コンパッション尺度短縮版（SCS-SF）",
        "purposeText": "研究または個人の自己観察におけるセルフ・コンパッションの特徴を記述します。",
        "resultVisibilityText": "結果は回答者本人および権限を付与された専門職が閲覧できます。",
        "nonDiagnosticText": "SCS-SFは研究・自己観察用で、臨床診断や臨床標準値を提供するものではありません。",
        "helpResourceText": "自己観察で強い苦痛や安全上の不安が生じた場合は、資格のある専門職または地域の緊急支援に連絡してください。",
    },
    "en": {
        "scaleName": "Self-Compassion Scale – Short Form (SCS-SF)",
        "purposeText": "Describes self-compassion characteristics for research or personal self-observation.",
        "resultVisibilityText": "Results are visible to the respondent and authorized professionals.",
        "nonDiagnosticText": "The SCS-SF is a research/self-observation measure; it does not establish a clinical diagnosis or provide clinical norms.",
        "helpResourceText": "If self-observation causes marked distress or safety concerns, contact a qualified professional or local emergency support.",
    },
}


DIMENSIONS = [
    (
        "SCS_SF_SELF_KINDNESS",
        [2, 6],
        ("善待自己", "自分への優しさ", "Self-kindness"),
        ("对自己采取理解、耐心和关怀的倾向。", "自分に理解、忍耐、いたわりを向ける傾向です。", "The tendency to respond to oneself with understanding, patience and care."),
    ),
    (
        "SCS_SF_SELF_JUDGMENT",
        [11, 12],
        ("自我批评", "自己批判", "Self-judgment"),
        ("对自己的缺点和不足采取批判或不耐烦态度的负向维度，计分前反向。", "自分の欠点に批判的・不寛容になる負の下位尺度で、採点前に逆転します。", "A negative subscale reflecting judgment or impatience toward one’s flaws; reverse-scored before calculation."),
    ),
    (
        "SCS_SF_COMMON_HUMANITY",
        [5, 10],
        ("共同人性", "共通の人間性", "Common humanity"),
        ("把个人失败和不足看作人类共同经历的倾向。", "失敗や不十分さを人間に共通する経験として捉える傾向です。", "The tendency to view failures and inadequacies as part of shared human experience."),
    ),
    (
        "SCS_SF_ISOLATION",
        [4, 8],
        ("自我隔离", "孤独感", "Isolation"),
        ("在困难中觉得自己孤立或孤独的负向维度，计分前反向。", "困難の中で孤立・孤独に感じる負の下位尺度で、採点前に逆転します。", "A negative subscale reflecting feeling isolated or alone in difficulty; reverse-scored before calculation."),
    ),
    (
        "SCS_SF_MINDFULNESS",
        [3, 7],
        ("静观当下", "マインドフルネス", "Mindfulness"),
        ("以平衡、开放的方式面对痛苦情绪的倾向。", "苦痛の感情にバランスよく開かれた態度で向き合う傾向です。", "The tendency to approach painful emotions with balance and openness."),
    ),
    (
        "SCS_SF_OVER_IDENTIFICATION",
        [1, 9],
        ("过度沉迷", "過剰同一性", "Over-identification"),
        ("被不足、错误或负面情绪反复困住的负向维度，计分前反向。", "不足や失敗、否定的感情にとらわれやすい負の下位尺度で、採点前に逆転します。", "A negative subscale reflecting becoming absorbed in inadequacy, mistakes or negative emotion; reverse-scored before calculation."),
    ),
]


def localized(values: tuple[str, str, str]) -> dict[str, str]:
    return {locale: values[index] for index, locale in enumerate(LOCALES)}


def raw_option_code(effective_score: int, reverse: bool) -> str:
    return str(6 - effective_score if reverse else effective_score)


def answers(effective_scores: list[int]) -> list[dict[str, object]]:
    return [
        {
            "questionNo": question_no,
            "optionCodes": [raw_option_code(score, QUESTIONS[question_no - 1][3])],
        }
        for question_no, score in enumerate(effective_scores, start=1)
    ]


def expected(total: float, risk: str) -> dict[str, object]:
    return {"valid": True, "totalScore": total, "riskLevel": risk, "metrics": {}}


def result_rule(
    code: str,
    risk: str,
    minimum: float,
    maximum: float,
    title: dict[str, str],
    description: dict[str, str],
    suggestion: dict[str, str],
) -> dict[str, object]:
    return {
        "ruleCode": code,
        "riskLevel": risk,
        "scoreMin": minimum,
        "scoreMax": maximum,
        "scoreSource": "RAW_SCORE",
        "translations": {
            locale: {
                "resultTitle": title[locale],
                "resultDescription": description[locale],
                "suggestionText": suggestion[locale],
                "reviewStatus": "DRAFT",
            }
            for locale in LOCALES
        },
    }


def build_package() -> dict[str, object]:
    options = [
        {
            "code": str(score),
            "score": score,
            "translations": {locale: OPTION_LABELS[locale][score - 1] for locale in LOCALES},
        }
        for score in range(1, 6)
    ]
    questions = [
        {
            "questionNo": question_no,
            "dimensionCode": dimension_code,
            "questionType": "SINGLE_CHOICE",
            "required": True,
            "reverseScore": reverse,
            "translations": {
                locale: {"text": text, "reviewStatus": "DRAFT"}
                for locale, text in zip(LOCALES, (zh, ja, en))
            },
            "options": options,
        }
        for question_no, (zh, ja, en, reverse, dimension_code) in enumerate(QUESTIONS, start=1)
    ]

    low = result_rule(
        "SCS_SF_LOW",
        "ATTENTION",
        1.0,
        2.49,
        localized(("自我关怀得分较低", "自分への思いやり得点が低い", "Lower self-compassion score")),
        localized((
            "平均分为1.00–2.49。该区间是官方信息页给出的非正式比较性描述，不是临床常模或诊断。",
            "平均得点は1.00～2.49です。公式情報にある非公式の比較用目安であり、臨床標準値や診断ではありません。",
            "The mean is 1.00–2.49. This is the informal comparative description in the official information sheet, not a clinical norm or diagnosis.",
        )),
        localized((
            "结合个人背景和生活影响继续自我观察；如痛苦持续，请咨询合格的专业人员。",
            "個人の背景と生活への影響を踏まえて観察を続け、苦痛が続く場合は資格のある専門職に相談してください。",
            "Continue observing this result in personal context; if distress persists, consult a qualified professional.",
        )),
    )
    moderate = result_rule(
        "SCS_SF_MODERATE",
        "NORMAL",
        2.5,
        3.5,
        localized(("中等自我关怀得分", "中程度のセルフ・コンパッション得点", "Moderate self-compassion score")),
        localized((
            "平均分为2.50–3.50。该区间是非正式比较性描述，不是临床常模或诊断。",
            "平均得点は2.50～3.50です。非公式の比較用目安であり、臨床標準値や診断ではありません。",
            "The mean is 2.50–3.50. This is an informal comparative description, not a clinical norm or diagnosis.",
        )),
        localized((
            "将结果与自身经历和研究目的结合解读，不要把它当作临床结论。",
            "自身の経験や研究目的と併せて解釈し、臨床的な結論として扱わないでください。",
            "Interpret the result alongside personal experience and the research purpose; do not treat it as a clinical conclusion.",
        )),
    )
    high = result_rule(
        "SCS_SF_HIGH",
        "NORMAL",
        3.51,
        5.0,
        localized(("自我关怀得分较高", "自分への思いやり得点が高い", "Higher self-compassion score")),
        localized((
            "平均分为3.51–5.00。该区间是非正式比较性描述，不是临床常模，也不代表不存在心理困扰。",
            "平均得点は3.51～5.00です。非公式の比較用目安であり、臨床標準値や苦痛がないことを意味しません。",
            "The mean is 3.51–5.00. This is an informal comparative description, not a clinical norm and not evidence that distress is absent.",
        )),
        localized((
            "结合自身情境理解结果；如有持续困扰或安全担忧，请联系专业人员。",
            "自分の状況に照らして解釈し、苦痛が続く場合や安全が心配な場合は専門職に連絡してください。",
            "Interpret this in context; contact a professional if distress persists or safety is a concern.",
        )),
    )

    all_low = [1] * 12
    boundary_low = [5, 4] + [2] * 10
    boundary_moderate = [2] * 6 + [3] * 6
    boundary_upper = [3] * 6 + [4] * 6
    reverse_case = [4, 2, 3, 4, 2, 5, 1, 3, 4, 2, 1, 5]
    all_high = [5] * 12

    return {
        "format": "PSY_SCALE_SOURCE_PACKAGE",
        "schemaVersion": 1,
        "scale": {
            "scaleCode": "SCS_SF_OFFICIAL_RESEARCH",
            "scaleName": "Self-Compassion Scale – Short Form (SCS-SF)",
            "versionNo": "official-research-5point-v1",
            "applicableTarget": "GENERAL_SELF_OBSERVATION_AGE_14_PLUS",
            "scoreMethod": "AVERAGE",
            "scoreCoefficient": 1,
            "assessmentMode": "SELF",
            "responseScale": {
                "min": 1,
                "max": 5,
                "labels": OPTION_LABELS["en"],
            },
            "qualityPolicy": {
                "missingAnswerPolicy": "REJECT",
                "maxMissingRatio": 0,
                "invalidResultAction": "INVALIDATE",
                "requireAllRequiredAnswers": True,
            },
            "reportTemplate": "DIMENSION_PROFILE",
            "algorithmBinding": {
                "algorithmCode": "GENERIC_SCORE_CALCULATOR",
                "algorithmVersion": "1",
                "implementationType": "BUILTIN",
            },
            "instruction": INSTRUCTIONS,
        },
        "governance": {
            "sourceTitle": "Official SCS researcher page, SCS-SF information sheet and posted translations",
            "publisherName": "Kristin Neff / self-compassion.org",
            "copyrightStatus": "AUTHORIZED",
            "rightsHolder": "Kristin Neff",
            "authorizationStatus": "AUTHORIZED",
            "authorizationType": "OFFICIAL_RESEARCH_PERMISSION",
            "authorizationScope": "The official SCS-SF information sheet grants permission for any purpose including research, clinical work and teaching, and permits translation using the stated validation approach. This draft uses the official English short form and corresponding items from the posted Chinese and Japanese full-form translations; no clinical norms or professional sign-off is implied.",
            "authorizedLanguages": "en,zh-CN,ja-JP",
            "governanceStatus": "DRAFT",
            "targetPopulation": "Self-report research or personal self-observation for people aged 14 and above; not a diagnostic instrument.",
            "nonDiagnosticStatement": "The SCS-SF describes self-compassion characteristics and does not establish a clinical diagnosis or clinical norm.",
            "reviewStatus": "PENDING_REVIEW",
        },
        "translations": {
            locale: {**translation, "reviewStatus": "DRAFT"}
            for locale, translation in SCALE_TRANSLATIONS.items()
        },
        "dimensions": [
            {
                "dimensionCode": code,
                "questionNos": question_nos,
                "translations": {
                    locale: {
                        "name": names[index],
                        "description": descriptions[index],
                        "reviewStatus": "DRAFT",
                    }
                    for index, locale in enumerate(LOCALES)
                },
            }
            for code, question_nos, names, descriptions in DIMENSIONS
        ],
        "questions": questions,
        "scoring": {
            "canonicalConvention": "1_TO_5_MEAN_AFTER_NEGATIVE_RECODE",
            "dimensionAggregation": "AVERAGE",
            "dimensionRule": "mean of the two effective item scores for each subscale; total is the mean of the six subscale means",
            "indices": {},
        },
        "norms": {
            "status": "NOT_LOADED",
            "interpretation": "The official information sheet provides only an informal comparative rubric (1.0–2.49 low, 2.5–3.5 moderate, 3.51–5 high); it states that there are no clinical norms.",
        },
        "resultRules": [low, moderate, high],
        "highRiskRules": [],
        "goldenCases": [
            {
                "caseCode": "SCS_SF_ALL_LOW",
                "caseType": "NORMAL",
                "sourceReference": "Official SCS-SF 1–5 convention; every effective item score is 1 after reversing negative items.",
                "input": {"answers": answers(all_low)},
                "expected": expected(1.0, "ATTENTION"),
            },
            {
                "caseCode": "SCS_SF_BOUNDARY_LOW",
                "caseType": "NORMAL",
                "sourceReference": "Official informal rubric lower band; effective total is 29/12 = 2.4167.",
                "input": {"answers": answers(boundary_low)},
                "expected": expected(2.4167, "ATTENTION"),
            },
            {
                "caseCode": "SCS_SF_BOUNDARY_25",
                "caseType": "BOUNDARY",
                "sourceReference": "Official informal rubric moderate band lower boundary at 2.5.",
                "input": {"answers": answers(boundary_moderate)},
                "expected": expected(2.5, "NORMAL"),
            },
            {
                "caseCode": "SCS_SF_BOUNDARY_35",
                "caseType": "BOUNDARY",
                "sourceReference": "Official informal rubric moderate band upper boundary at 3.5.",
                "input": {"answers": answers(boundary_upper)},
                "expected": expected(3.5, "NORMAL"),
            },
            {
                "caseCode": "SCS_SF_REVERSE_RECODE",
                "caseType": "REVERSE",
                "sourceReference": "Official SCS-SF scoring key; self-judgment, isolation and over-identification items are reverse-scored before the mean.",
                "input": {"answers": answers(reverse_case)},
                "expected": expected(3.0, "NORMAL"),
            },
            {
                "caseCode": "SCS_SF_ALL_HIGH",
                "caseType": "NORMAL",
                "sourceReference": "Official SCS-SF 1–5 convention; every effective item score is 5 after reversing negative items.",
                "input": {"answers": answers(all_high)},
                "expected": expected(5.0, "NORMAL"),
            },
            {
                "caseCode": "SCS_SF_MISSING_REQUIRED",
                "caseType": "MISSING",
                "sourceReference": "All twelve SCS-SF items are required for a valid mean.",
                "input": {"answers": answers(all_high[:-1])},
                "expected": {"valid": False, "errorCode": "MISSING_REQUIRED_ANSWER"},
            },
            {
                "caseCode": "SCS_SF_INVALID_OPTION",
                "caseType": "INVALID",
                "sourceReference": "Controlled response-option regression case.",
                "input": {
                    "answers": [
                        {"questionNo": 1, "optionCodes": ["9"]},
                        *answers(all_high)[1:],
                    ]
                },
                "expected": {"valid": False, "errorCode": "OPTION_NOT_FOUND"},
            },
        ],
        "sourceReferences": [
            {
                "title": "Self-Compassion Scales for Researchers",
                "url": "https://self-compassion.org/self-compassion-scales-for-researchers/",
                "use": "Official permission notice, age guidance and translation links.",
            },
            {
                "title": "SCS-SF information sheet",
                "url": "https://self-compassion.org/wp-content/uploads/2021/03/SCS-SF-information.pdf",
                "use": "Permission for research/clinical/teaching use, translation permission, exact 12-item scoring key, informal bands and absence of clinical norms.",
            },
            {
                "title": "Official Short SCS form",
                "url": "https://self-compassion.org/wp-content/uploads/2020/01/ShortSCS.pdf",
                "use": "Version-locked English short-form item wording and 1–5 response convention.",
            },
            {
                "title": "Official Chinese Self-Compassion Scale",
                "url": "https://self-compassion.org/wp-content/uploads/2018/06/ChineseSCS.pdf",
                "use": "Published Chinese full-form wording; the 12 corresponding items are selected using the official short-to-long mapping.",
            },
            {
                "title": "Official Japanese Self-Compassion Scale",
                "url": "https://self-compassion.org/wp-content/uploads/2018/05/JapaneseSCS.pdf",
                "use": "Published Japanese full-form wording; the 12 corresponding items are selected using the official short-to-long mapping.",
            },
            {
                "title": "Raes et al. 2011 SCS-SF publication",
                "url": "https://doi.org/10.1016/j.cpr.2011.03.003",
                "use": "Primary short-form development and validation citation requested by the official information sheet.",
            },
        ],
        "publicationBlockers": [
            "PROFESSIONAL_REVIEW_PENDING",
            "TRILINGUAL_TRANSLATION_REVIEW_PENDING",
            "NO_CLINICAL_NORMS",
            "POPULATION_SCOPE_REVIEW_PENDING",
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
