#!/usr/bin/env python3
"""Generate the reviewable SCL-90 source package used by the closure checks.

This is a source/draft artifact, not a claim that the instrument is licensed or
clinically approved.  The package intentionally keeps all translations and
interpretations in DRAFT/PENDING_REVIEW state until the rights holder and a
qualified mental-health professional approve them.
"""

from __future__ import annotations

import json
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "doc" / "scale-packages" / "scl90-v1-source-draft.json"


DIMENSIONS = [
    ("SOM", "躯体化", "Somatization", "身体化", "身体不适、疼痛及心血管、胃肠道和呼吸系统相关的主诉。"),
    ("OCD", "强迫症状", "Obsessive-compulsive", "強迫症状", "难以摆脱的思想、冲动、行为及相关认知困难。"),
    ("INT", "人际关系敏感", "Interpersonal sensitivity", "対人過敏", "人际交往中的不自在、自卑和消极期待。"),
    ("DEP", "抑郁", "Depression", "抑うつ", "苦闷、兴趣减退、动力下降、悲观和死亡相关想法。"),
    ("ANX", "焦虑", "Anxiety", "不安", "烦躁、紧张、坐立不安、恐惧和惊恐相关体验。"),
    ("HOS", "敌对", "Hostility", "敵意", "敌对性思想、情绪和行为，包括争论、冲动和摔物。"),
    ("PHOB", "恐怖", "Phobic anxiety", "恐怖", "对出门、交通工具、人群、公共场所或独处的恐惧。"),
    ("PAR", "偏执", "Paranoid ideation", "被害念慮", "猜疑、投射、关系观念、被动体验和夸大想法。"),
    ("PSY", "精神病性", "Psychoticism", "精神病性", "急性精神病性体验、社会疏离和相关行为指征。"),
    ("OTHER", "附加项目（睡眠及饮食）", "Additional items (sleep and eating)", "追加項目（睡眠・食事）", "不归入九个主因子的睡眠、饮食及其他附加症状。"),
]


# The Chinese column is the normalized working copy of the user-provided
# text.  sourceTextCorrections below records the few obvious OCR/typing fixes.
ITEMS = [
    (1, "SOM", "头痛", "Headaches", "頭痛"),
    (2, "ANX", "神经过敏，心中不踏实", "Nervousness or shakiness inside", "神経過敏で、心が落ち着かない"),
    (3, "OCD", "头脑中有不必要的想法或字句盘旋", "Unwanted thoughts or words keep circling in your mind", "頭の中で必要のない考えや言葉がぐるぐるする"),
    (4, "SOM", "头昏或昏倒", "Dizziness or fainting", "めまいや失神"),
    (5, "DEP", "对异性的兴趣减退", "Less interest in the opposite sex", "異性への関心が減った"),
    (6, "INT", "对旁人责备求全", "Being critical of others", "他人に対して厳しく批判する"),
    (7, "PSY", "感到别人能控制自己的思想", "Feeling that other people can control your thoughts", "他人に自分の考えを操られていると感じる"),
    (8, "PAR", "责怪别人制造麻烦", "Blaming others for most of your troubles", "自分の困りごとは他人が起こしたと責める"),
    (9, "OCD", "忘性大", "Forgetting things easily", "物忘れが多い"),
    (10, "OCD", "担心自己的衣饰整齐及仪态的端正", "Worrying about neatness of clothing and appearance", "服装や身だしなみが整っているか気になる"),
    (11, "HOS", "容易烦恼和激动", "Becoming easily annoyed or irritated", "悩みやすく、興奮しやすい"),
    (12, "SOM", "胸痛", "Pains in the chest", "胸の痛み"),
    (13, "PHOB", "害怕空旷的场所或街道", "Fear of open spaces or streets", "広い場所や通りが怖い"),
    (14, "DEP", "感到自己的精力下降，活动减慢", "Feeling low in energy and slowed down", "気力が低下し、動作が遅く感じる"),
    (15, "DEP", "想结束自己的生命", "Thoughts of ending your life", "自分の命を終わらせたいと思う"),
    (16, "PSY", "听到旁人听不到的声音", "Hearing voices that other people do not hear", "他の人には聞こえない声が聞こえる"),
    (17, "ANX", "发抖", "Trembling", "震える"),
    (18, "PAR", "感到大多数人都不可信任", "Feeling that most people cannot be trusted", "ほとんどの人は信用できないと感じる"),
    (19, "OTHER", "胃口不好", "Poor appetite", "食欲がない"),
    (20, "DEP", "容易哭泣", "Crying easily", "涙もろい"),
    (21, "INT", "同异性相处时感到害羞不自在", "Feeling shy or uncomfortable with the opposite sex", "異性と一緒にいると恥ずかしく居心地が悪い"),
    (22, "DEP", "感到受骗，中了圈套或有人想抓住自己", "Feeling deceived, trapped, or that someone wants to catch you", "だまされた、罠にかかった、誰かに捕まえられそうだと感じる"),
    (23, "ANX", "无缘无故地突然感到害怕", "Suddenly feeling afraid for no reason", "理由なく突然怖くなる"),
    (24, "HOS", "自己不能控制地大发脾气", "Outbursts of anger that you cannot control", "自分で抑えられないほど怒る"),
    (25, "PHOB", "怕单独出门", "Fear of going out alone", "一人で外出するのが怖い"),
    (26, "DEP", "经常责怪自己", "Blaming yourself often", "自分をよく責める"),
    (27, "SOM", "腰痛", "Low back pain", "腰痛"),
    (28, "OCD", "感到难以完成任务", "Feeling that it is difficult to complete tasks", "課題を終えるのが難しいと感じる"),
    (29, "DEP", "感到孤独", "Feeling lonely", "孤独を感じる"),
    (30, "DEP", "感到苦闷", "Feeling blue or depressed", "憂うつで苦しい"),
    (31, "DEP", "过分担忧", "Worrying too much", "心配しすぎる"),
    (32, "DEP", "对事物不感兴趣", "Losing interest in things", "物事に興味がわかない"),
    (33, "ANX", "感到害怕", "Feeling afraid", "怖いと感じる"),
    (34, "INT", "我的感情容易受到伤害", "Being easily hurt emotionally", "感情が傷つきやすい"),
    (35, "PSY", "旁人能知道自己的私下想法", "Feeling that others know your private thoughts", "他人に自分の内心を知られていると感じる"),
    (36, "INT", "感到别人不理解自己、不同情自己", "Feeling that people do not understand or sympathize with you", "他人は自分を理解せず同情もしないと感じる"),
    (37, "INT", "感到人们对自己不友好、不喜欢自己", "Feeling that people are unfriendly or dislike you", "人々が自分に友好的でなく、嫌われていると感じる"),
    (38, "OCD", "做事必须做得很慢，以保证做得正确", "Having to do things very slowly to make sure they are correct", "正しく行うために物事をとてもゆっくりしなければならない"),
    (39, "ANX", "心跳得很厉害", "Your heart pounding hard", "心臓が激しく鼓動する"),
    (40, "SOM", "恶心或胃部不舒服", "Nausea or an upset stomach", "吐き気や胃の不快感"),
    (41, "INT", "感到比不上他人", "Feeling inferior to others", "他人より劣っていると感じる"),
    (42, "SOM", "肌肉酸痛", "Sore muscles", "筋肉の痛み"),
    (43, "PAR", "感到有人在监视自己、谈论自己", "Feeling that someone is watching or talking about you", "誰かに監視され、話題にされていると感じる"),
    (44, "OTHER", "难以入睡", "Difficulty falling asleep", "寝つきが悪い"),
    (45, "OCD", "做事必须反复检查", "Having to check things repeatedly", "何度も確認しなければならない"),
    (46, "OCD", "难以作出决定", "Difficulty making decisions", "決めるのが難しい"),
    (47, "PHOB", "怕乘电车、公共汽车、地铁或火车", "Fear of riding trams, buses, subways, or trains", "電車、バス、地下鉄、列車に乗るのが怖い"),
    (48, "SOM", "呼吸有困难", "Difficulty breathing", "息苦しい"),
    (49, "SOM", "一阵阵发冷或发热", "Hot or cold spells", "寒気やほてりが一時的に起こる"),
    (50, "PHOB", "因为感到害怕而避开某些东西、场合或活动", "Avoiding things, places, or activities because of fear", "怖いために物事、場所、活動を避ける"),
    (51, "OCD", "脑子变空了", "Your mind going blank", "頭の中が真っ白になる"),
    (52, "SOM", "身体发麻或刺痛", "Numbness or tingling in parts of your body", "体の一部がしびれたりチクチクしたりする"),
    (53, "SOM", "喉咙有梗塞感", "A lump or blockage in your throat", "喉が詰まる感じ"),
    (54, "DEP", "感到前途没有希望", "Feeling hopeless about the future", "将来に希望がないと感じる"),
    (55, "OCD", "不能集中注意", "Trouble concentrating", "集中できない"),
    (56, "SOM", "感到身体的某一部分软弱无力", "Feeling weak in parts of your body", "体の一部が弱く力が入らないと感じる"),
    (57, "ANX", "感到紧张或容易紧张", "Feeling tense or easily nervous", "緊張している、または緊張しやすい"),
    (58, "SOM", "感到手或脚发重", "Feeling heavy in your arms or legs", "手足が重いと感じる"),
    (59, "OTHER", "想到死亡的事", "Thinking about death", "死について考える"),
    (60, "OTHER", "吃得太多", "Eating too much", "食べすぎる"),
    (61, "INT", "当别人看着自己或谈论自己时感到不自在", "Feeling uncomfortable when people look at or talk about you", "人に見られたり話題にされたりすると居心地が悪い"),
    (62, "PSY", "有一些不属于自己的想法", "Having thoughts that do not feel like your own", "自分のものではないような考えがある"),
    (63, "HOS", "有想打人或伤害他人的冲动", "Having urges to hit or hurt someone", "人を殴ったり傷つけたりしたい衝動がある"),
    (64, "OTHER", "醒得太早", "Waking too early", "朝早く目が覚める"),
    (65, "OCD", "必须反复洗手、点数目或触摸某些东西", "Having to wash, count, or touch things repeatedly", "何度も手を洗ったり数えたり触ったりしなければならない"),
    (66, "OTHER", "睡得不稳不深", "Restless or shallow sleep", "眠りが浅く安定しない"),
    (67, "HOS", "有想摔坏或破坏东西的冲动", "Having urges to break or destroy things", "物を壊したり破壊したりしたい衝動がある"),
    (68, "PAR", "有一些别人没有的想法或念头", "Having ideas or thoughts that other people do not have", "他人にはない考えや思いがある"),
    (69, "INT", "感到对别人神经过敏", "Being overly sensitive around other people", "他人に対して神経過敏だと感じる"),
    (70, "PHOB", "在商店或电影院等人多的地方感到不自在", "Feeling uncomfortable in crowded places such as stores or cinemas", "店や映画館など人の多い場所で居心地が悪い"),
    (71, "DEP", "感到任何事情都很困难", "Feeling that everything is difficult", "何をするにもとても難しいと感じる"),
    (72, "ANX", "一阵阵恐惧或惊恐", "Spells of terror or panic", "恐怖やパニックが突然起こる"),
    (73, "INT", "感到公共场合吃东西很不舒服", "Feeling uncomfortable eating in public", "人前で食事をするのがとても不快"),
    (74, "HOS", "经常与人争论", "Arguing with people a lot", "人とよく口論する"),
    (75, "PHOB", "单独一人时神经很紧张", "Feeling very nervous when alone", "一人になるととても緊張する"),
    (76, "PAR", "别人对我的成绩没有作出恰当的评价", "Feeling that others do not properly appreciate your achievements", "他人が自分の成績を適切に評価していないと感じる"),
    (77, "PSY", "即使和别人在一起也感到孤单", "Feeling lonely even when you are with other people", "人と一緒にいても孤独を感じる"),
    (78, "ANX", "感到坐立不安心神不定", "Feeling restless or unable to sit still", "落ち着かず、そわそわする"),
    (79, "DEP", "感到自己没有什么价值", "Feeling worthless", "自分には価値がないと感じる"),
    (80, "ANX", "感到熟悉的东西变成陌生或不像真的", "Feeling that familiar things seem strange or unreal", "慣れたものが見知らぬもの、現実でないものに感じる"),
    (81, "HOS", "大叫或摔东西", "Shouting or throwing things", "大声で叫んだり物を投げたりする"),
    (82, "PHOB", "害怕会在公共场合昏倒", "Fear of fainting in public", "人前で気を失うのが怖い"),
    (83, "PAR", "感到别人想占自己的便宜", "Feeling that people want to take advantage of you", "人が自分につけ込もうとしていると感じる"),
    (84, "PSY", "为一些有关性的想法而苦恼", "Being troubled by thoughts about sex", "性に関する考えに悩まされる"),
    (85, "PSY", "认为应该因为自己的过错而受到惩罚", "Believing that you should be punished for your mistakes", "自分の過ちで罰を受けるべきだと思う"),
    (86, "ANX", "感到要很快把事情做完", "Feeling that you must finish things quickly", "物事を急いで終えなければならないと感じる"),
    (87, "PSY", "感到自己的身体有严重问题", "Feeling that something is seriously wrong with your body", "体に深刻な問題があると感じる"),
    (88, "PSY", "从未感到和其他人很亲近", "Never feeling close to another person", "他人と親密になれたと感じたことがない"),
    (89, "OTHER", "感到自己有罪", "Feeling guilty", "罪悪感がある"),
    (90, "PSY", "感到自己的脑子有毛病", "Feeling that something is wrong with your mind", "自分の頭に問題があると感じる"),
]


def tr(zh: str, ja: str, en: str) -> dict[str, str]:
    return {"zh-CN": zh, "ja-JP": ja, "en": en}


def all_answers(score: int) -> list[dict[str, object]]:
    return [{"questionNo": no, "optionCodes": [str(score)]} for no, *_ in ITEMS]


def expected_metrics(_total: str, gsi: str, pst: str, psdi: str) -> dict[str, str]:
    return {
        "GSI": gsi,
        "PST": pst,
        "PSDI": psdi,
        "POSITIVE_SYMPTOM_COUNT": pst,
        "POSITIVE_SYMPTOM_AVERAGE": psdi,
        "ANSWERED_ITEM_COUNT": "90",
    }


def build_package() -> dict[str, object]:
    dimensions = []
    for code, zh, en, ja, description in DIMENSIONS:
        question_nos = [no for no, dim, *_ in ITEMS if dim == code]
        dimensions.append(
            {
                "dimensionCode": code,
                "questionNos": question_nos,
                "translations": {
                    "zh-CN": {"name": zh, "description": description, "reviewStatus": "DRAFT"},
                    "ja-JP": {"name": {"SOM": "身体化", "OCD": "強迫症状", "INT": "対人過敏", "DEP": "抑うつ", "ANX": "不安", "HOS": "敵意", "PHOB": "恐怖", "PAR": "被害念慮", "PSY": "精神病性", "OTHER": "追加項目（睡眠・食事）"}[code], "description": "機械翻訳による草稿。専門家レビュー待ち。", "reviewStatus": "DRAFT"},
                    "en": {"name": en, "description": "Draft translation; professional review required.", "reviewStatus": "DRAFT"},
                },
            }
        )

    questions = []
    for no, code, zh, en, ja in ITEMS:
        questions.append(
            {
                "questionNo": no,
                "dimensionCode": code,
                "questionType": "SINGLE_CHOICE",
                "required": True,
                "reverseScore": False,
                "translations": {
                    "zh-CN": {"text": zh, "reviewStatus": "DRAFT"},
                    "ja-JP": {"text": ja, "reviewStatus": "DRAFT"},
                    "en": {"text": en, "reviewStatus": "DRAFT"},
                },
                "options": [
                    {"code": "0", "score": 0, "translations": tr("从无", "まったくない", "Not at all")},
                    {"code": "1", "score": 1, "translations": tr("轻度", "少し", "A little bit")},
                    {"code": "2", "score": 2, "translations": tr("中度", "中程度", "Moderately")},
                    {"code": "3", "score": 3, "translations": tr("相当重", "かなり", "Quite a bit")},
                    {"code": "4", "score": 4, "translations": tr("严重", "非常に", "Extremely")},
                ],
            }
        )

    zero_expected = {
        "valid": True,
        "totalScore": "0",
        "riskLevel": "NORMAL",
        "metrics": expected_metrics("0", "0", "0", "0"),
    }
    four_expected = {
        "valid": True,
        "totalScore": "360",
        "riskLevel": "HIGH",
        "highRiskTriggered": True,
        "highRiskRuleCode": "SCL90_SELF_HARM_IDEA",
        "metrics": expected_metrics("360", "4", "90", "4"),
        "dimensions": {code: {"score": "4"} for code, *_ in DIMENSIONS},
    }
    self_harm_answers = all_answers(0)
    self_harm_answers[14] = {"questionNo": 15, "optionCodes": ["4"]}
    invalid_answers = all_answers(0)
    invalid_answers[0] = {"questionNo": 1, "optionCodes": ["9"]}

    return {
        "format": "PSY_SCALE_SOURCE_PACKAGE",
        "schemaVersion": 1,
        "scale": {
            "scaleCode": "SCL90_USER_DRAFT",
            "scaleName": "症状自评量表（SCL-90）",
            "versionNo": "v1",
            "applicableTarget": "GENERAL",
            "scoreMethod": "SIMPLE_SUM",
            "responseScale": {"min": 0, "max": 4, "labels": ["从无", "轻度", "中度", "相当重", "严重"]},
            "reportTemplate": "NORMATIVE_PROFILE",
            "algorithmBinding": {"algorithmCode": "SCL90_PROFILE", "algorithmVersion": "1", "implementationType": "RESTRICTED_EXTENSION"},
            "instruction": {
                "zh-CN": "请根据最近一星期以内（或过去）每项问题使你感到苦恼的程度作答；不得漏答。",
                "ja-JP": "直近1週間（または過去）に各項目がどの程度苦痛だったかを回答します。未回答は避けてください。",
                "en": "Rate how much each problem has distressed you during the past week (or previously); do not omit items.",
            },
        },
        "governance": {
            "sourceTitle": "用户提供的 SCL-90 中文资料（公开资料交叉核对草稿）",
            "publisherName": "待版权/授权核验",
            "copyrightStatus": "RESTRICTED",
            "authorizationStatus": "PENDING_REVIEW",
            "governanceStatus": "DRAFT",
            "targetPopulation": "待专业人员确认",
            "nonDiagnosticStatement": "本量表仅用于筛查和随访辅助，不等同于临床诊断。",
            "reviewStatus": "PENDING_REVIEW",
        },
        "translations": {
            "zh-CN": {"scaleName": "症状自评量表（SCL-90）", "nonDiagnosticText": "本量表仅用于筛查和随访辅助，不等同于临床诊断。", "reviewStatus": "DRAFT"},
            "ja-JP": {"scaleName": "症状自評尺度（SCL-90）", "nonDiagnosticText": "本尺度はスクリーニングと経過観察の補助であり、臨床診断ではありません。", "reviewStatus": "DRAFT"},
            "en": {"scaleName": "Symptom Checklist-90 (SCL-90)", "nonDiagnosticText": "This scale supports screening and follow-up and does not establish a clinical diagnosis.", "reviewStatus": "DRAFT"},
        },
        "dimensions": dimensions,
        "questions": questions,
        "scoring": {
            "canonicalConvention": "0_TO_4",
            "positiveSymptomRule": "score > 0",
            "indices": {
                "GSI": "sum(all answered item scores) / answered item count",
                "PST": "count(answered items with score > 0)",
                "PSDI": "sum(all answered item scores) / PST; 0 when PST is 0",
            },
            "dimensionAggregation": "AVERAGE",
            "dimensionRule": "sum(dimension item scores) / answered item count in dimension",
            "oneToFiveVariant": {"status": "PENDING_REVIEW", "note": "User material mentions a 1–5 variant; it is not mixed into this 0–4 package."},
        },
        "norms": {
            "status": "PENDING_REVIEW",
            "sourceReference": "https://pmc.ncbi.nlm.nih.gov/articles/PMC7873442/",
            "factorReferenceFromUserText": {
                "SOM": {"mean": "1.37", "sd": "0.48"},
                "OCD": {"mean": "1.62", "sd": "0.58"},
                "INT": {"mean": "1.65", "sd": "0.61"},
                "DEP": {"mean": "1.50", "sd": "0.59"},
                "ANX": {"mean": "1.39", "sd": "0.43"},
                "PHOB": {"mean": "1.23", "sd": "0.41"},
                "PAR": {"mean": "1.43", "sd": "0.57"},
                "PSY": {"mean": "1.29", "sd": "0.42"},
            },
            "interpretation": "A factor above its approved, population-specific norm may be flagged for professional review; no diagnosis is inferred.",
        },
        "resultInterpretation": {
            "global": {"status": "PENDING_PROFESSIONAL_REVIEW", "rule": "Do not publish hard total-score bands until population and manual are approved."},
            "dimension": {"status": "PENDING_PROFESSIONAL_REVIEW", "rule": "Show the factor profile and reference comparison only after norm approval."},
            "safety": {"item15": "self-harm/suicidal ideation signal requires configured human response workflow", "item63": "harm-to-others impulse signal requires configured human response workflow"},
        },
        "highRiskRules": [
            {
                "ruleCode": "SCL90_SELF_HARM_IDEA", "questionNo": 15, "scoreThreshold": 3, "warningLevel": "HIGH", "reviewStatus": "PENDING_PROFESSIONAL_REVIEW",
                "translations": {
                    "zh-CN": {"resultTitle": "自伤/自杀想法信号（需人工复核）", "resultDescription": "第15题达到高风险阈值；这不是诊断，必须按已批准的危机响应流程人工联系和升级。", "suggestionText": "请立即由指定专业人员人工复核并记录处置。", "reviewStatus": "DRAFT"},
                    "ja-JP": {"resultTitle": "自傷・自殺念慮のシグナル（専門職の確認が必要）", "resultDescription": "15番が高リスク閾値に達しました。診断ではなく、承認済みの危機対応手順による人的確認とエスカレーションが必要です。", "suggestionText": "指定された専門職が直ちに確認し、対応記録を残してください。", "reviewStatus": "DRAFT"},
                    "en": {"resultTitle": "Self-harm/suicidal-ideation signal (human review required)", "resultDescription": "Item 15 reached the high-risk threshold. This is not a diagnosis; an approved crisis-response workflow must review and escalate it.", "suggestionText": "Have the designated professional review it immediately and record the response.", "reviewStatus": "DRAFT"}
                }
            },
            {
                "ruleCode": "SCL90_HARM_OTHERS_IDEA", "questionNo": 63, "scoreThreshold": 3, "warningLevel": "HIGH", "reviewStatus": "PENDING_PROFESSIONAL_REVIEW",
                "translations": {
                    "zh-CN": {"resultTitle": "伤害他人冲动信号（需人工复核）", "resultDescription": "第63题达到高风险阈值；这不是诊断，必须按已批准的危机响应流程人工联系和升级。", "suggestionText": "请立即由指定专业人员人工复核并记录处置。", "reviewStatus": "DRAFT"},
                    "ja-JP": {"resultTitle": "他者を傷つける衝動のシグナル（専門職の確認が必要）", "resultDescription": "63番が高リスク閾値に達しました。診断ではなく、承認済みの危機対応手順による人的確認とエスカレーションが必要です。", "suggestionText": "指定された専門職が直ちに確認し、対応記録を残してください。", "reviewStatus": "DRAFT"},
                    "en": {"resultTitle": "Harm-to-others impulse signal (human review required)", "resultDescription": "Item 63 reached the high-risk threshold. This is not a diagnosis; an approved crisis-response workflow must review and escalate it.", "suggestionText": "Have the designated professional review it immediately and record the response.", "reviewStatus": "DRAFT"}
                }
            },
        ],
        "goldenCases": [
            {"caseCode": "SCL90_ALL_ZERO", "caseType": "NORMAL", "sourceReference": "user-provided SCL-90 material; public structure cross-check", "input": {"answers": all_answers(0)}, "expected": zero_expected},
            {"caseCode": "SCL90_ALL_FOUR", "caseType": "BOUNDARY", "sourceReference": "user-provided SCL-90 material; public structure cross-check", "input": {"answers": all_answers(4)}, "expected": four_expected},
            {"caseCode": "SCL90_SELF_HARM_SIGNAL", "caseType": "HIGH_RISK", "sourceReference": "user-provided SCL-90 item 15; crisis rule pending professional review", "input": {"answers": self_harm_answers}, "expected": {"valid": True, "totalScore": "4", "riskLevel": "HIGH", "highRiskTriggered": True, "highRiskRuleCode": "SCL90_SELF_HARM_IDEA", "metrics": expected_metrics("4", "0.0444", "1", "4")}},
            {"caseCode": "SCL90_MISSING_REQUIRED", "caseType": "MISSING", "sourceReference": "required-answer policy derived from the supplied instructions", "input": {"answers": all_answers(0)[:-1]}, "expected": {"valid": False, "errorCode": "MISSING_REQUIRED_ANSWER"}},
            {"caseCode": "SCL90_INVALID_OPTION", "caseType": "INVALID", "sourceReference": "controlled invalid-option regression case; option code 9 is outside the 0–4 response scale", "input": {"answers": invalid_answers}, "expected": {"valid": False, "errorCode": "OPTION_NOT_FOUND"}},
        ],
        "sourceReferences": [
            {"title": "Pearson Q-global: Entering SCL-90-R Scores", "url": "https://qglobal.pearsonclinical.com/qg/static/Product/en/SCL-90-R/SCL-90-R_Enter_Scores.htm", "use": "0–4 response coding and required-item gate; licensing remains unresolved."},
            {"title": "Pearson SCL-90-R Scales", "url": "https://www.pearsonassessments.com/content/dam/school/global/clinical/us/assets/scl-90-r/scl-90-r-scales.pdf", "use": "Cross-checks the nine symptom scales and GSI/PSDI/PST names; the official profile/report remains protected."},
            {"title": "Pearson SCL-90-R product information", "url": "https://www.pearsonassessments.com/store/en/usd/p/100000645.html", "use": "Cross-checks the stated age range, administration modes and separate norm populations; it does not supply a reusable Chinese norm."},
            {"title": "NIH GAP: Symptom Checklist-90-R", "url": "https://www.ncbi.nlm.nih.gov/projects/gap/cgi-bin/document.cgi?phd=2412&study_id=phs000222.v3.p2", "use": "90 items, nine dimensions and three global indices; not an authorization."},
            {"title": "Scielo: Datos Normativos y Propiedades Psicométricas del SCL-90-R", "url": "https://www.scielo.cl/scielo.php?pid=S0718-48082008000100004&script=sci_arttext", "use": "0–4 formulas, 83 dimension items plus seven additional items; population norms cannot be reused without approval."},
            {"title": "Reliability and validity of a Japanese version of SCL-90-R", "url": "https://pmc.ncbi.nlm.nih.gov/articles/PMC2582234/", "use": "Supports a Japanese translation/back-translation and validation review requirement; its sample and norms cannot be silently reused for this package."},
            {"title": "Study of the SCL-90 Scale and Changes in the Chinese Norms", "url": "https://pmc.ncbi.nlm.nih.gov/articles/PMC7873442/", "use": "Shows that Chinese norm references and their application change across samples and periods; do not hard-code an undated Chinese norm."},
            {"title": "Undergraduate students’ norms for the Chinese version of SCL-90-R", "url": "https://pmc.ncbi.nlm.nih.gov/articles/PMC7579932/", "use": "Provides a population-specific Chinese undergraduate norm study; it cannot be used as a general or clinical norm without matching the population."},
            {"title": "ITC Guidelines for Translating and Adapting Tests (Second Edition)", "url": "https://www.intestcom.org/files/guideline_test_adaptation_2ed.pdf", "use": "Requires rights clearance before adaptation and documents translation, pilot data, validation, scoring/interpretation and user documentation gates for a trilingual version."},
            {"title": "Study of item text in the Chinese Symptom Checklist-90", "url": "https://pmc.ncbi.nlm.nih.gov/articles/PMC7982195/", "use": "Documents that Chinese item wording differs across versions and proposes wording review; it is evidence for reconciliation, not permission to copy or a replacement for the authorized source."},
            {"title": "Psychometric properties of the symptom check list 90 for Chinese undergraduate students", "url": "https://pubmed.ncbi.nlm.nih.gov/30465457/", "use": "Provides reliability and validity evidence for a specific Chinese undergraduate population; it cannot be generalized to other populations without validation."},
            {"title": "Pearson Global Permission Granting", "url": "https://www.pearson.com/en-us/global-permission-granting.html", "use": "Confirms that reproduction, adaptation or translation requires written rights clearance; authorization remains a publication blocker."},
            {"title": "NIMH Adult Outpatient Brief Suicide Safety Assessment Guide", "url": "https://www.nimh.nih.gov/research/research-conducted-at-nimh/asq-toolkit-materials/adult-outpatient/adult-outpatient-brief-suicide-safety-assessment-guide", "use": "Requires trained follow-up after a positive suicide-risk screen, including current-thoughts assessment, safety planning, disposition and follow-up; this is an external clinical workflow requirement, not an SCL-90 diagnostic rule."},
            {"title": "NIMH Clinical Pathway for Suicide Risk Screening in Adult Primary Care", "url": "https://www.nimh.nih.gov/news/science-updates/2022/a-clinical-pathway-for-suicide-risk-screening-in-adult-primary-care", "use": "Defines the operational sequence screening → safety assessment → course of action and supports requiring an owner, escalation path and response SLA before enabling high-risk alerts."},
        ],
        "sourceTextCorrections": [
            {"item": 4, "change": "将‘头昏/头晕’统一为‘头昏或昏倒’工作副本；需与授权版本核对。"},
            {"item": 13, "change": "因子清单中的 OCR ‘日’按题号语义校正为 13；需与原始手册核对。"},
            {"item": 64, "change": "将‘醒得太平’按上下文校正为‘醒得太早’；需与原始手册核对。"},
            {"item": 76, "change": "补全‘没有作出恰当的评价’；需与原始手册核对。"},
            {"item": 85, "change": "按语义整理为‘认为应该因为自己的过错而受到惩罚’；需与原始手册核对。"},
        ],
        "publicationBlockers": [
            "COPYRIGHT_AUTHORIZATION_PENDING",
            "PROFESSIONAL_REVIEW_PENDING",
            "TRILINGUAL_TRANSLATION_REVIEW_PENDING",
            "POPULATION_SPECIFIC_NORMS_PENDING",
            "GLOBAL_RESULT_BANDS_PENDING",
            "CRISIS_RESPONSE_OWNER_AND_SLA_PENDING",
        ],
    }


def main() -> None:
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    payload = build_package()
    OUTPUT.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"wrote {OUTPUT} ({len(payload['questions'])} questions, {len(payload['dimensions'])} dimensions)")


if __name__ == "__main__":
    main()
