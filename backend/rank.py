"""기사 클러스터링 및 화제성 랭킹.

핵심 아이디어: 같은 사건을 여러 매체가 동시에 보도할수록 화제성이 크다.
따라서 제목이 비슷한 기사끼리 묶은 뒤 '몇 개 매체가 보도했는가'를 1순위로 점수를 매긴다.
"""

import re
from datetime import datetime, timezone

# 제목 앞뒤의 말머리: [속보] (종합) 【단독】 <르포> 등
_BRACKET_RE = re.compile(r"[\[\(<【〈][^\]\)>】〉]{0,12}[\]\)>】〉]")
_NON_WORD_RE = re.compile(r"[^0-9A-Za-z가-힣]+")

# 조사/어미 — 토큰 끝에 붙어 같은 단어를 다르게 보이게 만든다
_JOSA = (
    "으로서", "으로써", "에서는", "에게서", "이라는", "라는", "에서", "에게", "으로", "부터",
    "까지", "이라", "라며", "라고", "이나", "든지", "처럼", "보다", "마저", "조차", "밖에",
    "은", "는", "이", "가", "을", "를", "에", "의", "와", "과", "도", "로", "만", "및",
)

_STOPWORDS = {
    "속보", "단독", "종합", "영상", "포토", "사진", "그래픽", "인터뷰", "칼럼", "사설", "기고",
    "오늘", "내일", "어제", "올해", "작년", "이번", "지난", "관련", "위해", "대해", "통해",
    "밝혀", "밝혔다", "전했다", "말했다", "했다", "한다", "된다", "됐다", "있다", "없다",
    "그는", "우리", "국내", "기자", "뉴스", "취재", "보도", "논란", "주장", "지적",
}

# 뉴스로 보기 어려운 항목 — 아예 제외
_EXCLUDE_PATTERNS = (
    "다시보기", "편성표", "오늘의 운세", "운세", "부고", "인사)", "동정", "화보",
    "포토뉴스", "만평", "카툰", "네 컷", "4컷", "주요 뉴스", "뉴스 브리핑",
    "지면보기", "구독 신청", "이벤트 당첨", "퀴즈 정답",
)

# 광고·홍보성 — 강한 감점
_AD_KEYWORDS = (
    "모집", "분양", "특가", "할인", "이벤트", "증정", "출시 기념", "런칭", "오픈 기념",
    "설명회", "세미나 개최", "채용", "공모전", "수상", "협약", "MOU", "업무협약",
)

# 지엽적 지역 뉴스 — 감점
_LOCAL_KEYWORDS = (
    "축제", "행사 개최", "체험행사", "주민센터", "기념식", "위촉", "표창",
    "봉사활동", "캠페인 전개", "간담회 개최", "현판식", "준공식", "개소식",
)
_LOCAL_GOV_RE = re.compile(r"[가-힣]{2,4}(시청|군청|구청|시장|군수|구청장|도지사)")

# 연예 가십 — 감점
_GOSSIP_KEYWORDS = (
    "열애", "결별", "이혼설", "근황", "심경", "목격담", "몸매", "미모", "충격 고백",
    "눈물 고백", "깜짝 공개", "SNS 게시", "인스타 공개", "댓글 화제",
)

# 요약문으로 삼기 좋은 순서 — 통신사·종합지가 사실 위주로 서술
_SOURCE_PREFERENCE = (
    "연합뉴스", "SBS", "국민일보", "서울신문", "동아일보", "한겨레", "경향신문",
    "조선일보", "매일경제", "노컷뉴스",
)


def normalize_title(title: str) -> str:
    return _BRACKET_RE.sub(" ", title).strip()


def _strip_josa(token: str) -> str:
    if len(token) < 3:
        return token
    for josa in _JOSA:
        if token.endswith(josa) and len(token) - len(josa) >= 2:
            return token[: -len(josa)]
    return token


def keywords(title: str) -> set[str]:
    tokens = set()
    for raw in _NON_WORD_RE.split(normalize_title(title)):
        if len(raw) < 2:
            continue
        token = _strip_josa(raw)
        if len(token) >= 2 and token not in _STOPWORDS:
            tokens.add(token)
    return tokens


def bigrams(title: str) -> set[str]:
    """복합어 표기 차이(윤석열 체포방해 / 尹체포 방해)를 흡수하기 위한 글자 2-gram."""
    flat = _NON_WORD_RE.sub("", normalize_title(title))
    return {flat[i : i + 2] for i in range(len(flat) - 1)}


def _jaccard(a: set, b: set) -> float:
    if not a or not b:
        return 0.0
    return len(a & b) / len(a | b)


def _dice(a: set, b: set) -> float:
    if not a or not b:
        return 0.0
    return 2 * len(a & b) / (len(a) + len(b))


def is_same_event(a: dict, b: dict) -> bool:
    return _jaccard(a["keywords"], b["keywords"]) >= 0.34 or _dice(a["bigrams"], b["bigrams"]) >= 0.50


def should_exclude(title: str) -> bool:
    return any(pattern in title for pattern in _EXCLUDE_PATTERNS)


def _penalty(title: str) -> float:
    score = 0.0
    if any(word in title for word in _AD_KEYWORDS):
        score += 8.0
    if any(word in title for word in _LOCAL_KEYWORDS) or _LOCAL_GOV_RE.search(title):
        score += 6.0
    if any(word in title for word in _GOSSIP_KEYWORDS):
        score += 7.0
    return score


def cluster(articles: list[dict]) -> list[list[dict]]:
    """제목이 비슷한 기사끼리 묶는다. 기사 수가 많지 않아 단순 탐욕적 병합으로 충분하다."""
    clusters: list[list[dict]] = []
    for article in articles:
        for group in clusters:
            if is_same_event(article, group[0]):
                group.append(article)
                break
        else:
            clusters.append([article])
    return clusters


def score_cluster(group: list[dict], now: datetime) -> float:
    sources = {article["source"] for article in group}

    # 1순위: 몇 개 매체가 동시에 다뤘는가
    score = len(sources) * 12.0

    # 같은 매체가 여러 건 쏟아낸 경우도 화제성 신호이나 가중치는 낮게
    score += min(len(group) - len(sources), 6) * 1.5

    newest = max(
        (article["published"] for article in group if article["published"]),
        default=now,
    )
    hours_old = max((now - newest).total_seconds() / 3600, 0)
    score += max(0.0, 8.0 - hours_old * 0.5)

    score -= min(_penalty(article["title"]) for article in group)
    return score


_SENTENCE_END_RE = re.compile(r"(?<=[.!?다요])\s")
# (서울=연합뉴스) 홍길동 기자 =  /  (워싱턴=연합뉴스) 박성민 특파원 =
_BYLINE_RE = re.compile(r"^\([^)]{1,30}\)\s*(?:[가-힣]{2,5}\s+){0,4}(?:기자|특파원|통신원)\s*=\s*")
_TRAILING_RE = re.compile(r"\s*(?:▶|◆|☞|\[출처|무단전재|저작권자).*$")
_CAPTION_MARK_RE = re.compile(r"^\s*[▲▶■◀◆△▽]\s*")
# 요약 맨 앞에 붙어 오는 사진 출처 표기. 뒤에 조사가 오면 본문이므로 건드리지 않는다.
_PHOTO_CREDIT_RE = re.compile(
    r"^\s*(?:[A-Z]{2,5})?"
    r"(?:국회사진기자단|사진공동취재단|게티이미지뱅크|게티이미지|연합뉴스TV|연합뉴스|뉴시스|뉴스1|로이터|AFP|EPA|AP)+"
    r"\s*(?![은는이가을를에의와과도로]\s)"
)
# 사진 설명은 대개 '…하고 있다.' '…있는 모습.' 으로 끝난다
_CAPTION_TAIL_RE = re.compile(r"(하고 있다|있는 모습|사진\s*=|연합뉴스 제공)\.?\s*$")


def _is_caption(text: str) -> bool:
    return bool(_CAPTION_MARK_RE.match(text) or _CAPTION_TAIL_RE.search(text))


def _summary_quality(article: dict) -> int:
    """0 = 본문 요약, 1 = 짧거나 사진 설명, 2 = 사실상 없음."""
    text = _CAPTION_MARK_RE.sub("", article["summary"].strip())
    if len(text) < 40:
        return 2
    if _is_caption(article["summary"].strip()):
        return 1
    return 0


def pick_representative(group: list[dict], usage: dict[str, int] | None = None) -> dict:
    usage = usage or {}

    def sort_key(article):
        try:
            preference = _SOURCE_PREFERENCE.index(article["source"])
        except ValueError:
            preference = len(_SOURCE_PREFERENCE)
        # 같은 매체가 대표로 반복 선정되지 않도록 요약 품질 다음으로 사용 횟수를 본다
        return (_summary_quality(article), usage.get(article["source"], 0), preference, -len(article["summary"]))

    return sorted(group, key=sort_key)[0]


def build_summary(article: dict, limit: int = 110) -> str:
    """요약문에서 1~2문장을 뽑아 길이를 맞춘다."""
    text = _PHOTO_CREDIT_RE.sub("", _CAPTION_MARK_RE.sub("", article["summary"].strip()))
    text = _TRAILING_RE.sub("", _BYLINE_RE.sub("", text)).strip()
    if not text:
        return ""

    # 요약이 제목을 그대로 반복하면 쓸모가 없다
    if text.startswith(article["title"][:20]):
        text = text[len(article["title"]) :].strip(" -–—|·")

    if len(text) <= limit:
        return text

    sentences = [s for s in _SENTENCE_END_RE.split(text) if s.strip()]
    # 첫 문장이 짧은 사진 설명이면 건너뛴다
    if len(sentences) > 1 and (len(sentences[0]) < 25 or _is_caption(sentences[0])):
        sentences = sentences[1:]

    out = ""
    for sentence in sentences:
        if out and len(out) + len(sentence) > limit:
            break
        out = f"{out} {sentence}".strip()
        if len(out) >= 60:
            break
    if not out:
        out = sentences[0][:limit] if sentences else text[:limit]
    return out.rstrip() + ("…" if len(out) < len(text) else "")


def top_stories(
    articles: list[dict],
    count: int,
    now: datetime | None = None,
    usage: dict[str, int] | None = None,
) -> list[dict]:
    now = now or datetime.now(timezone.utc)
    usage = usage if usage is not None else {}
    groups = cluster(articles)
    ranked = sorted(groups, key=lambda g: score_cluster(g, now), reverse=True)

    stories = []
    for group in ranked[:count]:
        lead = pick_representative(group, usage)
        usage[lead["source"]] = usage.get(lead["source"], 0) + 1
        others = sorted({article["source"] for article in group} - {lead["source"]})
        stories.append(
            {
                "title": normalize_title(lead["title"]),
                "summary": build_summary(lead),
                "source": lead["source"],
                "link": lead["link"],
                "publishedAt": lead["published"].isoformat() if lead["published"] else None,
                "sourceCount": len({article["source"] for article in group}),
                "otherSources": others[:6],
            }
        )
    return stories
