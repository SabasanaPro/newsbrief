"""분야별 화제 뉴스와 복권 당첨번호를 모아 news.json 을 만든다."""

import argparse
import json
import sys
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timedelta, timezone
from pathlib import Path

import rank
import topics as topics_module
from feeds import CATEGORIES, FEEDS, SELECTION_ORDER
from fetch import fetch_feed

KST = timezone(timedelta(hours=9))

WINDOW_HOURS = 18  # 기본 수집 범위
FALLBACK_HOURS = 36  # 기사가 너무 적을 때 넓히는 범위
MIN_ARTICLES_PER_CATEGORY = 25
STORIES_PER_CATEGORY = 3

# 가상화폐 전문 매체가 둘뿐이라, 일반 매체 기사 중 코인 관련 기사도 끌어와 함께 순위를 매긴다
CRYPTO_KEYWORDS = (
    "비트코인", "이더리움", "리플", "가상자산", "암호화폐", "블록체인", "스테이블코인",
    "알트코인", "업비트", "빗썸", "코인베이스", "바이낸스", "NFT", "디파이", "가상화폐",
)


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


CRYPTO_PATTERN = topics_module.keyword_pattern(CRYPTO_KEYWORDS)


def is_crypto(article: dict) -> bool:
    return bool(CRYPTO_PATTERN.search(f"{article['title']} {article['summary']}"))


def category_pool(articles: list[dict], category_id: str) -> list[dict]:
    if category_id == "crypto":
        # 전용 매체도 일반 경제 기사를 함께 내보내므로 출처와 무관하게 키워드로 고른다.
        # 본문에 단어가 스치기만 한 기사(예: 블록체인 기업의 코스닥 상장 소식)가 섞이지 않도록
        # 제목에 키워드가 있는 기사를 우선하고, 그것만으로 부족할 때만 본문까지 본다.
        by_title = [a for a in articles if CRYPTO_PATTERN.search(a["title"])]
        return by_title if len(by_title) >= 12 else [a for a in articles if is_crypto(a)]
    # 가상화폐 전용 매체의 기사가 다른 분야로 새지 않게 한다
    return [a for a in articles if a["category"] == category_id]


def build_categories(articles: list[dict], now: datetime) -> list[dict]:
    picked_by_category: dict[str, list[dict]] = {}
    already_picked: list[dict] = []
    source_usage: dict[str, int] = {}

    for category_id in SELECTION_ORDER:
        category_name = CATEGORIES[category_id]
        pool = category_pool(articles, category_id)
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

        picked_by_category[category_id] = stories
        print(f"  {category_name}: 후보 {len(recent)}건 → {len(stories)}건 선정", file=sys.stderr)

    # 배정은 SELECTION_ORDER 로 했지만, 내보낼 때는 앱에 보여줄 순서를 따른다
    return [
        {"id": category_id, "name": name, "items": picked_by_category.get(category_id, [])}
        for category_id, name in CATEGORIES.items()
    ]


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--out", default="../data/news.json", help="출력 JSON 경로")
    parser.add_argument("--no-lottery", action="store_true", help="복권 조회 생략 (시험용)")
    args = parser.parse_args()

    out_path = Path(args.out)
    if not out_path.is_absolute():
        out_path = (Path(__file__).parent / out_path).resolve()

    # 복권 없이 만든 파일을 앱이 받아가는 위치에 덮어쓰면 복권 화면이 통째로 비어버린다.
    # 시험 실행 결과가 실수로 배포되지 않도록 막는다.
    if args.no_lottery and out_path.name == "news.json" and out_path.parent.name == "data":
        print(
            "거부: --no-lottery 결과는 data/news.json 에 쓸 수 없습니다.\n"
            "      시험용이면 --out 으로 다른 경로를 지정하세요.",
            file=sys.stderr,
        )
        return 1

    now = datetime.now(timezone.utc)
    print("기사 수집 중...", file=sys.stderr)
    articles = collect_articles()
    print(f"총 {len(articles)}건 수집", file=sys.stderr)

    categories = build_categories(articles, now)

    print("주제 문장 만드는 중...", file=sys.stderr)
    topic_pool = within(articles, now, FALLBACK_HOURS)

    payload = {
        "generatedAt": now.astimezone(KST).isoformat(timespec="seconds"),
        "categories": categories,
        "topics": topics_module.build_topics(topic_pool, now),
        "topicCatalog": topics_module.catalog(),
    }

    if not args.no_lottery:
        import lottery

        payload["lottery"] = lottery.fetch_all()

    out_path.parent.mkdir(parents=True, exist_ok=True)
    out_path.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"저장 완료: {out_path}", file=sys.stderr)
    return 0


if __name__ == "__main__":
    sys.exit(main())
