"""'오늘의 브리핑' 주제 분류.

앱 설정에서 체크박스로 고를 수 있도록, 백엔드는 주제별 문장을 미리 다 만들어 둔다.
앱은 사용자가 켠 주제의 문장만 이어 붙여 한 문단으로 보여준다.
"""

import re

from rank import normalize_title

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
    ("telecom", "통신", ("통신사", "SKT", "KT", "LG유플러스", "5G", "6G", "요금제", "알뜰폰")),
    ("game", "게임", ("게임", "넥슨", "엔씨소프트", "크래프톤", "넷마블", "스팀", "e스포츠")),
    ("retail", "유통·소비", ("백화점", "이마트", "편의점", "쿠팡", "소비심리", "유통", "온라인쇼핑")),
    ("health", "건강·의료", ("의료", "병원", "의대", "감염", "백신", "질병", "건강보험", "환자")),
    ("education", "교육", ("교육부", "대학", "입시", "수능", "학교", "학생", "등록금")),
    ("labor", "노동", ("노조", "파업", "노동자", "산업재해", "정년", "근로시간")),
    ("climate", "환경·기후", ("기후", "탄소", "폭염", "태풍", "미세먼지", "가뭄", "온실가스", "재생에너지")),
    ("science", "우주·과학", ("우주", "위성", "발사체", "누리호", "천문", "연구진", "논문")),
    ("entertain", "연예·문화", ("드라마", "영화", "아이돌", "배우", "가수", "공연", "예능", "영화제")),
    ("sports", "스포츠", ("야구", "축구", "KBO", "프로야구", "손흥민", "올림픽", "월드컵", "농구", "골프")),
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


# 제목을 토막 내는 구분자. '·' 는 '반도체·SSD' 처럼 한 낱말 안에서도 쓰여 제외한다.
_CLAUSE_RE = re.compile(r"…|\.\.\.|,|\||/")
_QUOTE_RE = re.compile(r"[\"'“”‘’]")
_TRIM_CHARS = " \"'“”‘’()[]<>《》·-"


def _phrase(title: str, pattern: re.Pattern, limit: int = 26) -> str:
    """제목에서 주제에 해당하는 토막만 뽑아 짧은 구로 만든다.

    문장을 통째로 가져오면 브리핑이 기사 나열처럼 읽혀서, 키워드가 든 부분만 남긴다.
    예: 'AI 열풍에 반도체·SSD 수출 급증… 7월 ICT 수출 역대 최대' → 'AI 열풍에 반도체·SSD 수출 급증'
    """
    # 따옴표를 남기면 뒤에 조사가 붙었을 때 문장이 깨진다
    cleaned = re.sub(r"\s+", " ", _QUOTE_RE.sub(" ", normalize_title(title))).strip()

    clauses = [c.strip(_TRIM_CHARS) for c in _CLAUSE_RE.split(cleaned)]
    clauses = [c for c in clauses if len(c) >= 8]

    # 키워드가 든 토막 중에서 고르되, '정성호 법무' 처럼 토막이 너무 짧으면 뜻이 안 통한다.
    # 길이 제한 안에 온전히 들어가는 것 중 가장 긴 토막을 쓴다.
    hits = [c for c in clauses if pattern.search(c)] or clauses
    fitting = [c for c in hits if len(c) <= limit]
    chosen = max(fitting or hits, key=len, default=cleaned)

    if len(chosen) > limit:
        # 낱말 중간에서 끊기면 어색하므로 띄어쓰기 위치에서 자른다
        cut = chosen[:limit]
        space = cut.rfind(" ")
        chosen = (cut[:space] if space >= limit // 2 else cut).rstrip()
    return chosen.strip(_TRIM_CHARS)


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


# 한 주제에서 순위를 매기기 전에 자르는 기사 수. 클러스터링 비용을 감당 가능한 선으로 묶는다.
_MAX_PER_TOPIC = 250


def build_topics(articles: list[dict], now) -> list[dict]:
    """분야별 상위 기사가 아니라 수집한 기사 전체에서 주제를 뽑는다.

    선정된 21건만 보면 주제 대부분이 비어 버려서, 설정에서 고른 주제가 하나도
    안 잡히는 일이 생긴다. 그래서 주제마다 전체 기사에서 다시 추려 순위를 매긴다.
    """
    import rank as rank_module

    result = []
    used_links: set[str] = set()

    for topic_id, name, _ in TOPICS:
        pattern = _TOPIC_PATTERNS[topic_id]
        matched = [
            article for article in articles
            if pattern.search(f"{article['title']} {article.get('summary', '')}")
        ]
        # 한두 건뿐이면 그날의 화제라고 보기 어렵다
        if len(matched) < 3:
            continue

        matched = sorted(
            matched,
            key=lambda a: a["published"] or now,
            reverse=True,
        )[:_MAX_PER_TOPIC]

        # 여러 매체가 함께 다룬 순으로 두 건을 받아, 다른 주제가 쓴 기사는 피한다
        candidates = rank_module.top_stories(matched, 2, now)
        if not candidates:
            continue
        lead = next((c for c in candidates if c["link"] not in used_links), candidates[0])
        used_links.add(lead["link"])

        result.append(
            {
                "id": topic_id,
                "name": name,
                # 앱이 여러 주제를 한 문단으로 엮을 때 쓰는 짧은 구
                "phrase": _phrase(lead["title"], pattern),
                # 참고용 전체 문장
                "sentence": _first_sentence(lead.get("summary") or "") or lead["title"],
                "articleCount": len(matched),
                "link": lead["link"],
            }
        )

    # 관련 기사가 많은 주제가 위로
    result.sort(key=lambda t: t["articleCount"], reverse=True)
    return result


def catalog() -> list[dict]:
    """설정 화면에 보여줄 전체 주제 목록.

    앱에 목록을 박아두면 주제를 늘릴 때마다 앱을 다시 설치해야 해서 여기서 함께 내려보낸다.
    """
    return [{"id": topic_id, "name": name} for topic_id, name, _ in TOPICS]
