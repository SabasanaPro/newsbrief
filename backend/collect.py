"""분야별 화제 뉴스와 복권 당첨번호를 모아 news.json 을 만든다."""

import argparse
import json
import sys
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timedelta, timezone
from pathlib import Path

import rank
from feeds import CATEGORIES, FEEDS
from fetch import fetch_feed

KST = timezone(timedelta(hours=9))

WINDOW_HOURS = 18  # 기본 수집 범위
FALLBACK_HOURS = 36  # 기사가 너무 적을 때 넓히는 범위
MIN_ARTICLES_PER_CATEGORY = 25
STORIES_PER_CATEGORY = 3


def collect_articles() -> list[dict]:
    def load(entry):
        source, category, url = entry
        try:
            items = fetch_feed(url)
        except Exception as exc:
            print(f"  [경고] {source} {category} 수집 실패: {type(exc).__name__}", file=sys.stderr)
            return []
        for item in items:
            item["source"] = source
            item["category"] = category
        return items

    with ThreadPoolExecutor(max_workers=16) as pool:
        batches = pool.map(load, FEEDS)

    articles = []
    seen_links = set()
    for batch in batches:
        for item in batch:
            if item["link"] in seen_links or rank.should_exclude(item["title"]):
                continue
            seen_links.add(item["link"])
            item["keywords"] = rank.keywords(item["title"])
            item["bigrams"] = rank.bigrams(item["title"])
            articles.append(item)
    return articles


def within(articles: list[dict], now: datetime, hours: int) -> list[dict]:
    cutoff = now - timedelta(hours=hours)
    # 발행시각이 없는 피드도 있어 그런 기사는 버리지 않고 포함시킨다
    return [a for a in articles if a["published"] is None or a["published"] >= cutoff]


def build_categories(articles: list[dict], now: datetime) -> list[dict]:
    result = []
    already_picked: list[dict] = []
    source_usage: dict[str, int] = {}

    for category_id, category_name in CATEGORIES.items():
        pool = [a for a in articles if a["category"] == category_id]
        recent = within(pool, now, WINDOW_HOURS)
        if len(recent) < MIN_ARTICLES_PER_CATEGORY:
            recent = within(pool, now, FALLBACK_HOURS)

        # 앞 분야에서 이미 뽑힌 사건은 제외해 중복 노출을 막는다
        candidates = [
            a for a in recent
            if not any(rank.is_same_event(a, picked) for picked in already_picked)
        ]

        stories = rank.top_stories(candidates, STORIES_PER_CATEGORY, now, source_usage)
        for story in stories:
            already_picked.append(
                {"keywords": rank.keywords(story["title"]), "bigrams": rank.bigrams(story["title"])}
            )

        result.append({"id": category_id, "name": category_name, "items": stories})
        print(f"  {category_name}: 후보 {len(recent)}건 → {len(stories)}건 선정", file=sys.stderr)

    return result


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--out", default="../data/news.json", help="출력 JSON 경로")
    parser.add_argument("--no-lottery", action="store_true", help="복권 조회 생략")
    args = parser.parse_args()

    now = datetime.now(timezone.utc)
    print("기사 수집 중...", file=sys.stderr)
    articles = collect_articles()
    print(f"총 {len(articles)}건 수집", file=sys.stderr)

    payload = {
        "generatedAt": now.astimezone(KST).isoformat(timespec="seconds"),
        "categories": build_categories(articles, now),
    }

    if not args.no_lottery:
        import lottery

        payload["lottery"] = lottery.fetch_all()

    out_path = Path(args.out)
    if not out_path.is_absolute():
        out_path = (Path(__file__).parent / out_path).resolve()
    out_path.parent.mkdir(parents=True, exist_ok=True)
    out_path.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"저장 완료: {out_path}", file=sys.stderr)
    return 0


if __name__ == "__main__":
    sys.exit(main())
