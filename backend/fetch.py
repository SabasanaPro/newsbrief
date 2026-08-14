"""RSS 다운로드 및 파싱 공통 모듈."""

import calendar
import html
import re
from datetime import datetime, timezone

import feedparser
import requests

USER_AGENT = (
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/126.0 Safari/537.36"
)
TIMEOUT = 12

_TAG_RE = re.compile(r"<[^>]+>")
_WS_RE = re.compile(r"\s+")


def clean_text(raw: str) -> str:
    if not raw:
        return ""
    # 일부 피드는 엔티티를 이중 인코딩해서 내보내므로 태그 제거 전후로 두 번 푼다
    text = _TAG_RE.sub(" ", html.unescape(raw))
    text = html.unescape(text)
    text = text.replace("\xa0", " ")
    return _WS_RE.sub(" ", text).strip()


def _published(entry) -> datetime | None:
    for key in ("published_parsed", "updated_parsed"):
        struct = entry.get(key)
        if struct:
            return datetime.fromtimestamp(calendar.timegm(struct), tz=timezone.utc)
    return None


def fetch_feed(url: str) -> list[dict]:
    """RSS 주소에서 기사 목록을 읽어 정규화된 dict 리스트로 반환."""
    response = requests.get(
        url,
        timeout=TIMEOUT,
        headers={"User-Agent": USER_AGENT, "Accept": "application/rss+xml, application/xml, text/xml, */*"},
    )
    response.raise_for_status()
    parsed = feedparser.parse(response.content)

    items = []
    for entry in parsed.entries:
        title = clean_text(entry.get("title", ""))
        link = entry.get("link", "")
        if not title or not link:
            continue
        items.append(
            {
                "title": title,
                "link": link,
                "summary": clean_text(entry.get("summary", "") or entry.get("description", "")),
                "published": _published(entry),
            }
        )
    return items
