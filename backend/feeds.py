"""분야별 RSS 피드 목록. check_feeds.py 로 검증된 주소만 유지한다.

중앙일보/한국일보/KBS/MBC/YTN/한국경제/서울경제 등은 현재 공개 RSS를 제공하지
않거나 봇 차단이 걸려 있어 제외했다. 되살아나면 check_feeds.py 로 확인 후 추가.
"""

# 앱에 표시되는 순서
CATEGORIES = {
    "politics": "정치",
    "economy": "경제",
    "society": "사회",
    "world": "국제",
    "tech": "IT·과학",
    "crypto": "가상화폐",
    "culture": "문화·스포츠",
}

# 기사를 배정하는 순서. 먼저 오는 분야가 해당 사건을 가져간다.
# 가상화폐를 앞에 두어야 코인 뉴스가 경제·IT에 흡수되지 않고 전용 칸에 남는다.
SELECTION_ORDER = ("crypto", "politics", "economy", "society", "world", "tech", "culture")

# (매체명, 분야, RSS 주소)
FEEDS = [
    # 통신사
    ("연합뉴스", "politics", "https://www.yna.co.kr/rss/politics.xml"),
    ("연합뉴스", "economy", "https://www.yna.co.kr/rss/economy.xml"),
    ("연합뉴스", "society", "https://www.yna.co.kr/rss/society.xml"),
    ("연합뉴스", "world", "https://www.yna.co.kr/rss/international.xml"),
    ("연합뉴스", "tech", "https://www.yna.co.kr/rss/industry.xml"),
    ("연합뉴스", "culture", "https://www.yna.co.kr/rss/culture.xml"),
    ("연합뉴스", "culture", "https://www.yna.co.kr/rss/sports.xml"),
    # 종합일간지
    ("한겨레", "politics", "https://www.hani.co.kr/rss/politics/"),
    ("한겨레", "economy", "https://www.hani.co.kr/rss/economy/"),
    ("한겨레", "society", "https://www.hani.co.kr/rss/society/"),
    ("한겨레", "world", "https://www.hani.co.kr/rss/international/"),
    ("한겨레", "tech", "https://www.hani.co.kr/rss/science/"),
    ("한겨레", "culture", "https://www.hani.co.kr/rss/culture/"),
    ("한겨레", "culture", "https://www.hani.co.kr/rss/sports/"),
    ("경향신문", "politics", "https://www.khan.co.kr/rss/rssdata/politic_news.xml"),
    ("경향신문", "economy", "https://www.khan.co.kr/rss/rssdata/economy_news.xml"),
    ("경향신문", "society", "https://www.khan.co.kr/rss/rssdata/society_news.xml"),
    ("경향신문", "world", "https://www.khan.co.kr/rss/rssdata/kh_world.xml"),
    ("경향신문", "tech", "https://www.khan.co.kr/rss/rssdata/it_news.xml"),
    ("경향신문", "culture", "https://www.khan.co.kr/rss/rssdata/culture_news.xml"),
    ("경향신문", "culture", "https://www.khan.co.kr/rss/rssdata/kh_sports.xml"),
    ("동아일보", "politics", "https://rss.donga.com/politics.xml"),
    ("동아일보", "economy", "https://rss.donga.com/economy.xml"),
    ("동아일보", "society", "https://rss.donga.com/national.xml"),
    ("동아일보", "world", "https://rss.donga.com/international.xml"),
    ("동아일보", "tech", "https://rss.donga.com/science.xml"),
    ("동아일보", "culture", "https://rss.donga.com/culture.xml"),
    ("동아일보", "culture", "https://rss.donga.com/sports.xml"),
    ("조선일보", "politics", "https://www.chosun.com/arc/outboundfeeds/rss/category/politics/?outputType=xml"),
    ("조선일보", "economy", "https://www.chosun.com/arc/outboundfeeds/rss/category/economy/?outputType=xml"),
    ("조선일보", "society", "https://www.chosun.com/arc/outboundfeeds/rss/category/national/?outputType=xml"),
    ("조선일보", "world", "https://www.chosun.com/arc/outboundfeeds/rss/category/international/?outputType=xml"),
    ("조선일보", "culture", "https://www.chosun.com/arc/outboundfeeds/rss/category/sports/?outputType=xml"),
    ("서울신문", "politics", "https://www.seoul.co.kr/xml/rss/rss_politics.xml"),
    ("서울신문", "economy", "https://www.seoul.co.kr/xml/rss/rss_economy.xml"),
    ("서울신문", "society", "https://www.seoul.co.kr/xml/rss/rss_society.xml"),
    ("서울신문", "world", "https://www.seoul.co.kr/xml/rss/rss_international.xml"),
    ("국민일보", "society", "https://www.kmib.co.kr/rss/data/kmibRssAll.xml"),
    ("노컷뉴스", "society", "https://rss.nocutnews.co.kr/nocutnews.xml"),
    ("프레시안", "society", "https://www.pressian.com/api/v3/site/rss/news"),
    ("오마이뉴스", "society", "http://rss.ohmynews.com/rss/ohmynews.xml"),
    # 방송사
    ("SBS", "politics", "https://news.sbs.co.kr/news/SectionRssFeed.do?sectionId=01"),
    ("SBS", "economy", "https://news.sbs.co.kr/news/SectionRssFeed.do?sectionId=02"),
    ("SBS", "society", "https://news.sbs.co.kr/news/SectionRssFeed.do?sectionId=03"),
    ("SBS", "world", "https://news.sbs.co.kr/news/SectionRssFeed.do?sectionId=07"),
    ("SBS", "culture", "https://news.sbs.co.kr/news/SectionRssFeed.do?sectionId=08"),
    # 경제지
    ("매일경제", "politics", "https://www.mk.co.kr/rss/30200030/"),
    ("매일경제", "economy", "https://www.mk.co.kr/rss/30100041/"),
    ("매일경제", "society", "https://www.mk.co.kr/rss/50400012/"),
    ("매일경제", "world", "https://www.mk.co.kr/rss/30300018/"),
    ("매일경제", "tech", "https://www.mk.co.kr/rss/50200011/"),
    ("머니투데이", "economy", "https://rss.mt.co.kr/mt_news.xml"),
    ("아시아경제", "economy", "https://www.asiae.co.kr/rss/economy.htm"),
    # IT / 과학
    ("전자신문", "tech", "https://rss.etnews.com/Section901.xml"),
    ("ZDNet코리아", "tech", "https://feeds.feedburner.com/zdkorea"),
    ("블로터", "tech", "https://www.bloter.net/rss/allArticle.xml"),
    ("아이뉴스24", "tech", "https://www.inews24.com/rss/news_it.xml"),
    ("테크M", "tech", "https://www.techm.kr/rss/allArticle.xml"),
    # 가상화폐 — 전문 매체가 둘뿐이라 collect.py 에서 전체 피드의 키워드 추출을 함께 쓴다
    ("블록미디어", "crypto", "https://www.blockmedia.co.kr/feed"),
    ("토큰포스트", "crypto", "https://www.tokenpost.kr/rss"),
]
