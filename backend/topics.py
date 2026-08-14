"""'오늘의 브리핑' 주제 분류.

앱 설정에서 체크박스로 고를 수 있도록, 백엔드는 주제별 문장을 미리 다 만들어 둔다.
앱은 사용자가 켠 주제의 문장만 이어 붙여 한 문단으로 보여준다.
"""

import re

# (주제 id, 표시 이름, 키워드) — 키워드가 제목·요약에 나오면 그 주제로 본다
TOPICS = [
    ("semiconductor", "반도체", (
        "반도체", "삼성전자", "SK하이닉스", "하이닉스", "HBM", "파운드리", "D램", "디램",
        "낸드", "웨이퍼", "TSMC", "마이크론",
    )),
    ("stock", "증시", (
        "코스피", "코스닥", "증시", "주가", "지수", "상장", "공모주", "외국인 순매수",
        "시가총액", "뉴욕증시", "나스닥", "S&P",
    )),
    ("fx", "환율", ("환율", "원/달러", "원달러", "달러화", "외환", "엔화", "위안화")),
    ("rate", "금리", ("금리", "기준금리", "한국은행", "연준", "FOMC", "통화정책", "채권")),
    ("ai", "AI·인공지능", (
        "인공지능", "AI", "챗GPT", "생성형", "LLM", "오픈AI", "엔비디아", "데이터센터", "GPU",
    )),
    ("crypto", "가상자산", (
        "비트코인", "이더리움", "리플", "가상자산", "암호화폐", "코인", "블록체인",
        "업비트", "빗썸", "스테이블코인", "알트코인",
    )),
    ("realestate", "부동산", (
        "부동산", "아파트", "집값", "전세", "월세", "청약", "분양가", "재건축", "재개발", "주택",
    )),
    ("oil", "유가·에너지", ("유가", "원유", "정유", "휘발유", "가스요금", "전기요금", "에너지")),
    ("trade", "수출·관세", ("관세", "수출", "수입", "무역", "통상", "FTA", "보호무역", "덤핑")),
    ("price", "물가·고용", (
        "물가", "소비자물가", "인플레이션", "고용", "실업", "일자리", "임금", "최저임금",
    )),
    ("politics", "정치", (
        "대통령", "국회", "여당", "야당", "국민의힘", "민주당", "특검", "개헌", "장관", "청와대",
    )),
    ("world", "국제정세", (
        "트럼프", "백악관", "중국", "일본", "러시아", "우크라이나", "북한", "유럽연합", "EU",
    )),
    ("industry", "산업·기업", (
        "조선업", "조선소", "자동차", "현대차", "기아", "배터리", "이차전지", "철강",
        "항공사", "항공업", "제약", "바이오", "실적 발표", "영업이익",
    )),
]

_SENTENCE_END_RE = re.compile(r"(?<=[.!?])\s+")

# 다른 낱말 속에 통째로 들어가 오탐을 내는 키워드.
# 예: '트리플A'·'트리플더블' 의 '리플', '비트코인' 이 아닌 '코인노래방' 의 '코인'.
# 앞 글자가 한글이면 다른 낱말의 일부로 보고 세지 않는다.
_AMBIGUOUS = {"리플", "코인", "증시", "지수", "상장"}


def keyword_pattern(keywords: tuple[str, ...]) -> re.Pattern:
    parts = [
        rf"(?<![가-힣]){re.escape(word)}" if word in _AMBIGUOUS else re.escape(word)
        for word in keywords
    ]
    return re.compile("|".join(parts))


_TOPIC_PATTERNS = {topic_id: keyword_pattern(words) for topic_id, _, words in TOPICS}


def _first_sentence(text: str, limit: int = 90) -> str:
    text = text.strip()
    if not text:
        return ""
    first = _SENTENCE_END_RE.split(text)[0].strip()
    if not first:
        first = text
    if len(first) > limit:
        first = first[:limit].rstrip() + "…"
    return first


def build_topics(stories: list[dict]) -> list[dict]:
    """선정된 기사들을 주제별로 묶어 한 문장씩 만든다.

    문장은 기사 요약의 첫 문장을 그대로 쓴다. 기계로 다시 쓰면 어색해지기 때문에,
    이미 사람이 쓴 문장을 골라 오는 편이 자연스럽다.
    """
    result = []
    used_links: set[str] = set()

    for topic_id, name, _ in TOPICS:
        pattern = _TOPIC_PATTERNS[topic_id]
        matched = [
            story for story in stories
            if pattern.search(f"{story['title']} {story['summary']}")
        ]
        if not matched:
            continue

        # 여러 매체가 다룬 기사를 대표로 삼되, 다른 주제가 이미 쓴 기사는 피해 문장 중복을 막는다
        ranked = sorted(
            matched,
            key=lambda s: (s["link"] in used_links, -s.get("sourceCount", 1), -len(s.get("summary", ""))),
        )
        lead = ranked[0]
        if lead["link"] in used_links and len(result) >= 3:
            continue
        used_links.add(lead["link"])

        sentence = _first_sentence(lead.get("summary") or "") or f"{lead['title']} 소식이 전해졌습니다."

        result.append(
            {
                "id": topic_id,
                "name": name,
                "sentence": sentence,
                "articleCount": len(matched),
                "link": lead["link"],
            }
        )

    # 관련 기사가 많은 주제가 위로
    result.sort(key=lambda t: t["articleCount"], reverse=True)
    return result
