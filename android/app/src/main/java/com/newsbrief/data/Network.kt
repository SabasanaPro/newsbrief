package com.newsbrief.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL
import java.text.DecimalFormat
import kotlin.math.abs
import kotlin.math.roundToLong

object Network {

    private const val BRIEF_URL =
        "https://raw.githubusercontent.com/SabasanaPro/newsbrief/main/data/news.json"

    private const val UPBIT_URL =
        "https://api.upbit.com/v1/ticker?markets=KRW-BTC,KRW-XRP"

    private fun naverIndexUrl(code: String) =
        "https://polling.finance.naver.com/api/realtime/domestic/index/$code"

    private val json = Json { ignoreUnknownKeys = true }

    /** 다른 모듈(날씨 등)에서 쓰는 단순 GET. */
    suspend fun getRaw(url: String): String = get(url)

    private suspend fun get(url: String, referer: String? = null): String = withContext(Dispatchers.IO) {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 15_000
            setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/126.0 Mobile Safari/537.36",
            )
            setRequestProperty("Accept", "application/json, text/plain, */*")
            referer?.let { setRequestProperty("Referer", it) }
        }
        try {
            if (connection.responseCode !in 200..299) {
                throw IllegalStateException("서버 응답 ${connection.responseCode}")
            }
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    /** 백엔드가 하루 두 번 만들어 두는 뉴스·복권 데이터. */
    suspend fun fetchBrief(): Brief {
        // raw 주소에는 CDN 캐시가 걸려 있어 값을 붙여 새로 받아오게 한다
        val url = "$BRIEF_URL?t=${System.currentTimeMillis() / 60_000}"
        return json.decodeFromString(Brief.serializer(), get(url))
    }

    /** 코스피·코스닥·비트코인·리플을 한 번에. 일부만 실패해도 나머지는 보여준다. */
    suspend fun fetchQuotes(): List<Quote> = coroutineScope {
        val kospi = async { runCatching { fetchIndex("KOSPI") }.getOrNull() }
        val kosdaq = async { runCatching { fetchIndex("KOSDAQ") }.getOrNull() }
        val crypto = async { runCatching { fetchCrypto() }.getOrDefault(emptyList()) }
        listOfNotNull(kospi.await(), kosdaq.await()) + crypto.await()
    }

    private suspend fun fetchIndex(code: String): Quote {
        val body = get(naverIndexUrl(code), referer = "https://finance.naver.com/")
        val data = json.decodeFromString(NaverIndexResponse.serializer(), body).datas.first()

        val direction = when {
            data.compareToPreviousPrice.name.contains("RIS") -> 1
            data.compareToPreviousPrice.name.contains("FALL") ||
                data.compareToPreviousPrice.name.contains("LOWER") -> -1
            else -> 0
        }
        val change = abs(data.compareToPreviousClosePriceRaw.toDoubleOrNull() ?: 0.0)
        val rate = abs(data.fluctuationsRatioRaw.toDoubleOrNull() ?: 0.0)

        return Quote(
            name = data.stockName.ifBlank { code },
            price = decimal2.format(data.closePriceRaw.toDoubleOrNull() ?: 0.0),
            change = decimal2.format(change),
            rate = "%.2f%%".format(rate),
            direction = direction,
            status = if (data.marketStatus.equals("OPEN", true)) "장중" else "장 마감",
        )
    }

    private suspend fun fetchCrypto(): List<Quote> {
        val tickers = json.decodeFromString(
            kotlinx.serialization.builtins.ListSerializer(UpbitTicker.serializer()),
            get(UPBIT_URL),
        )
        val names = mapOf("KRW-BTC" to "비트코인", "KRW-XRP" to "리플")
        return tickers.mapNotNull { ticker ->
            val name = names[ticker.market] ?: return@mapNotNull null
            Quote(
                name = name,
                price = "${integer.format(ticker.tradePrice.roundToLong())}원",
                change = "${integer.format(abs(ticker.signedChangePrice).roundToLong())}원",
                rate = "%.2f%%".format(abs(ticker.signedChangeRate) * 100),
                direction = when {
                    ticker.signedChangeRate > 0 -> 1
                    ticker.signedChangeRate < 0 -> -1
                    else -> 0
                },
            )
        }
    }

    private val decimal2 = DecimalFormat("#,##0.00")
    private val integer = DecimalFormat("#,##0")
}
