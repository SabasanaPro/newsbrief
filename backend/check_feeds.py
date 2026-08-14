"""feeds.py 의 RSS 주소가 실제로 살아있는지 검사한다."""

import sys
from concurrent.futures import ThreadPoolExecutor

from feeds import FEEDS
from fetch import fetch_feed


def check(entry):
    source, category, url = entry
    try:
        items = fetch_feed(url)
    except Exception as exc:
        return (source, category, url, 0, f"ERROR {type(exc).__name__}: {exc}"[:90])
    if not items:
        return (source, category, url, 0, "빈 피드")
    return (source, category, url, len(items), items[0].get("title", "")[:50])


def main():
    with ThreadPoolExecutor(max_workers=16) as pool:
        results = list(pool.map(check, FEEDS))

    ok = [r for r in results if r[3] > 0]
    dead = [r for r in results if r[3] == 0]

    print(f"=== 정상 {len(ok)}개 ===")
    for source, category, url, count, sample in ok:
        print(f"  [{category:8}] {source:10} {count:3}건 | {sample}")

    print(f"\n=== 실패 {len(dead)}개 ===")
    for source, category, url, _, reason in dead:
        print(f"  [{category:8}] {source:10} {reason}")
        print(f"             {url}")

    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
