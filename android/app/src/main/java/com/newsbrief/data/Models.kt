package com.newsbrief.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/* ---------- 백엔드가 만들어 두는 news.json ---------- */

@Serializable
data class Brief(
    val generatedAt: String = "",
    val categories: List<Category> = emptyList(),
    val topics: List<Topic> = emptyList(),
    /** 설정 화면에 띄울 전체 주제 목록. 주제가 늘어도 앱을 다시 깔지 않도록 서버가 함께 내려준다. */
    val topicCatalog: List<TopicOption> = emptyList(),
    val lottery: Lottery? = null,
)

/** 백엔드가 주제별로 미리 뽑아 둔 재료. 설정에서 켠 주제만 골라 브리핑 문단을 만든다. */
@Serializable
data class Topic(
    val id: String = "",
    val name: String = "",
    /** 한 문단으로 엮기 좋은 짧은 구. 예: "반도체·SSD 수출 급증" */
    val phrase: String = "",
    val sentence: String = "",
    val articleCount: Int = 0,
    val link: String = "",
)

@Serializable
data class TopicOption(val id: String = "", val name: String = "")

@Serializable
data class Category(
    val id: String = "",
    val name: String = "",
    val items: List<Story> = emptyList(),
)

@Serializable
data class Story(
    val title: String = "",
    val summary: String = "",
    val source: String = "",
    val link: String = "",
    val publishedAt: String? = null,
    val sourceCount: Int = 1,
    val otherSources: List<String> = emptyList(),
)

@Serializable
data class Lottery(
    val lotto: Lotto? = null,
    val pension: Pension? = null,
)

@Serializable
data class Lotto(
    val round: Int = 0,
    val drawDate: String = "",
    val numbers: List<Int> = emptyList(),
    val bonus: Int = 0,
    val firstPrizeWinners: Int? = null,
    val firstPrizeAmount: Long? = null,
    val link: String = "https://www.dhlottery.co.kr/lt645/result",
)

@Serializable
data class Pension(
    val round: Int = 0,
    val drawDate: String = "",
    val group: String? = null,
    val number: String? = null,
    val bonus: String? = null,
    val firstPrizeWinners: Int? = null,
    val firstPrizeAmount: Long? = null,
    val link: String = "https://www.dhlottery.co.kr/pt720/result",
)

/* ---------- 네이버 금융 실시간 지수 ---------- */

@Serializable
data class NaverIndexResponse(val datas: List<NaverIndex> = emptyList())

@Serializable
data class NaverIndex(
    val stockName: String = "",
    val closePriceRaw: String = "0",
    val compareToPreviousClosePriceRaw: String = "0",
    val fluctuationsRatioRaw: String = "0",
    val marketStatus: String = "",
    val compareToPreviousPrice: NaverDirection = NaverDirection(),
)

@Serializable
data class NaverDirection(val code: String = "", val text: String = "", val name: String = "")

/* ---------- 업비트 시세 ---------- */

@Serializable
data class UpbitTicker(
    val market: String = "",
    @SerialName("trade_price") val tradePrice: Double = 0.0,
    @SerialName("signed_change_price") val signedChangePrice: Double = 0.0,
    @SerialName("signed_change_rate") val signedChangeRate: Double = 0.0,
)

/* ---------- 화면에서 쓰는 공통 시세 모델 ---------- */

data class Quote(
    val name: String,
    val price: String,
    val change: String,
    val rate: String,
    /** 1 상승, -1 하락, 0 보합 */
    val direction: Int,
    /** 지수에만 있음. 장 마감 등 상태 표시용 */
    val status: String? = null,
)
