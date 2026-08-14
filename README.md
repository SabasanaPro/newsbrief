# 뉴스브리프 백엔드

국내 주요 언론사 RSS를 모아 분야별 화제 뉴스를 뽑고, 동행복권 당첨번호를 붙여
`data/news.json` 하나로 만들어 두는 스크립트. GitHub Actions가 하루 두 번 실행한다.

## 동작 방식

1. 20개 매체 55개 RSS에서 최근 18시간 기사를 모은다 (약 3,000건)
2. 제목이 비슷한 기사끼리 같은 사건으로 묶는다
3. **몇 개 매체가 동시에 보도했는지**를 1순위로 점수를 매긴다
4. 광고·지역행사·연예가십은 감점, 분야별 상위 3건을 고른다
5. 로또6/45와 연금복권720+ 최신 회차를 붙여 JSON으로 저장한다

분야: 정치 / 경제 / 사회 / 국제 / IT·과학 / 문화·스포츠

## 로컬 실행

```bash
python -m venv .venv
.venv/Scripts/pip install -r backend/requirements.txt
cd backend && ../.venv/Scripts/python collect.py
```

`--no-lottery` 를 붙이면 복권 조회를 건너뛴다.

RSS 주소가 살아있는지 확인하려면:

```bash
cd backend && ../.venv/Scripts/python check_feeds.py
```

## 출력 형식

```json
{
  "generatedAt": "2026-08-14T08:00:00+09:00",
  "categories": [
    {
      "id": "politics",
      "name": "정치",
      "items": [
        {
          "title": "제목",
          "summary": "1~2줄 요약",
          "source": "연합뉴스",
          "link": "https://...",
          "publishedAt": "2026-08-14T05:30:00+00:00",
          "sourceCount": 5,
          "otherSources": ["한겨레", "조선일보"]
        }
      ]
    }
  ],
  "lottery": {
    "lotto": { "round": 1236, "drawDate": "2026-08-08", "numbers": [12,18,21,29,34,38], "bonus": 10 },
    "pension": { "round": 328, "drawDate": "2026-08-13", "group": "3", "number": "644513" }
  }
}
```

## 갱신 시각

`.github/workflows/update-news.yml` 의 cron은 UTC 기준이며, GitHub가 실행을
5~15분 미루는 일이 잦아 목표 시각보다 20분 일찍 잡아두었다.

| cron (UTC) | 한국 시각 | 목적 |
| --- | --- | --- |
| `40 22 * * *` | 매일 07:40 | 아침 브리핑 |
| `40 10 * * *` | 매일 19:40 | 저녁 갱신 |
| `10 11 * * 4` | 목요일 20:10 | 연금복권 추첨(19:05) 반영 |
| `10 12 * * 6` | 토요일 21:10 | 로또 추첨(20:35) 반영 |

Actions 탭에서 **Run workflow** 로 언제든 수동 실행할 수 있다.
