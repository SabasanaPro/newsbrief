"""동행복권 로또6/45 및 연금복권720+ 당첨번호 조회.

동행복권은 예전 gameResult.do 계열 주소를 봇 차단하지만, 개편된 결과 페이지와
그 페이지가 쓰는 조회 API 는 열려 있다. 결과 페이지에서 최신 회차 번호를 읽고
같은 API 를 호출하는 방식으로 가져온다.
"""

import re
import sys

import requests

BASE = "https://www.dhlottery.co.kr"
LOTTO_PAGE = f"{BASE}/lt645/result"
PENSION_PAGE = f"{BASE}/pt720/result"

_OPT_VAL_RE = re.compile(r'id="opt_val"[^>]*value="(\d+)"')
_OPTION_RE = re.compile(r'<option[^>]*value="(\d{2,5})"')

HEADERS = {
    "User-Agent": (
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
        "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
    ),
    "Accept": "application/json, text/javascript, */*; q=0.01",
    "Accept-Language": "ko-KR,ko;q=0.9",
    "X-Requested-With": "XMLHttpRequest",
}


def _session() -> requests.Session:
    session = requests.Session()
    session.headers.update(HEADERS)
    return session


def _latest_round(session: requests.Session, page_url: str) -> tuple[int, str]:
    html = session.get(page_url, timeout=15).text
    match = _OPT_VAL_RE.search(html)
    if match:
        return int(match.group(1)), html
    options = _OPTION_RE.findall(html)
    if not options:
        raise RuntimeError(f"회차 번호를 찾지 못했습니다: {page_url}")
    return int(options[0]), html


def _iso_date(raw) -> str:
    """20260813 / 2026.08.13 등 섞여 오는 표기를 2026-08-13 으로 통일."""
    digits = re.sub(r"\D", "", str(raw or ""))
    return f"{digits[:4]}-{digits[4:6]}-{digits[6:8]}" if len(digits) >= 8 else ""


def _api(session: requests.Session, path: str, params: dict, referer: str):
    response = session.get(f"{BASE}{path}", params=params, headers={"Referer": referer}, timeout=15)
    response.raise_for_status()
    return response.json().get("data") or {}


def fetch_lotto() -> dict | None:
    session = _session()
    try:
        latest, _ = _latest_round(session, LOTTO_PAGE)
        rows = _api(
            session,
            "/lt645/selectPstLt645InfoNew.do",
            {"srchDir": "center", "srchLtEpsd": str(latest)},
            LOTTO_PAGE,
        ).get("list", [])
        row = next((r for r in rows if r.get("ltEpsd") == latest), None)
        if row is None:
            raise RuntimeError(f"{latest}회 데이터를 찾지 못했습니다")

        return {
            "round": row["ltEpsd"],
            "drawDate": _iso_date(row["ltRflYmd"]),
            "numbers": [row[f"tm{i}WnNo"] for i in range(1, 7)],
            "bonus": row["bnsWnNo"],
            "firstPrizeWinners": row.get("rnk1WnNope"),
            "firstPrizeAmount": row.get("rnk1WnAmt"),
            "link": LOTTO_PAGE,
        }
    except Exception as exc:
        print(f"  [경고] 로또 조회 실패: {type(exc).__name__}: {exc}", file=sys.stderr)
        return None


def fetch_pension() -> dict | None:
    session = _session()
    try:
        latest, _ = _latest_round(session, PENSION_PAGE)
        params = {"srchPsltEpsd": str(latest)}

        draws = _api(session, "/pt720/selectPstPt720Info.do", params, PENSION_PAGE)
        rows = draws if isinstance(draws, list) else draws.get("result") or draws.get("list") or []
        rows = [r for r in rows if r.get("psltEpsd") == latest or r.get("ltEpsd") == latest]

        first = next((r for r in rows if r.get("wnSqNo") == 1), None)
        bonus = next((r for r in rows if r.get("wnSqNo") == 21), None)
        if first is None:
            raise RuntimeError(f"{latest}회 1등 번호를 찾지 못했습니다")

        prizes = _api(session, "/pt720/selectPstPt720WnInfo.do", params, PENSION_PAGE)
        prize_rows = prizes if isinstance(prizes, list) else prizes.get("result") or []
        first_prize = next((p for p in prize_rows if p.get("wnRnk") == 1), {})

        return {
            "round": latest,
            "drawDate": _iso_date(first.get("psltRflYmd")),
            "group": first.get("wnBndNo"),
            "number": first.get("wnRnkVl"),
            "bonus": bonus.get("wnRnkVl") if bonus else None,
            "firstPrizeWinners": first_prize.get("wnTotalCnt"),
            "firstPrizeAmount": first_prize.get("wnAmt"),
            "link": PENSION_PAGE,
        }
    except Exception as exc:
        print(f"  [경고] 연금복권 조회 실패: {type(exc).__name__}: {exc}", file=sys.stderr)
        return None


def fetch_all() -> dict:
    print("복권 당첨번호 조회 중...", file=sys.stderr)
    return {"lotto": fetch_lotto(), "pension": fetch_pension()}


if __name__ == "__main__":
    import json

    print(json.dumps(fetch_all(), ensure_ascii=False, indent=2))
