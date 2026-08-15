package com.newsbrief.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.json.JSONObject
import java.time.LocalDate

/** 미국 달러 1 단위에 대한 각 통화의 값. 모든 환산은 이 표를 거쳐 계산한다. */
@Serializable
data class RateTable(
    val base: String = "USD",
    val rates: Map<String, Double> = emptyMap(),
    /** 제공처가 알려준 갱신 시각 (표시용) */
    val updatedAt: String = "",
    /** 다음 갱신 예정 시각. 이 시각 전에는 다시 호출하지 않는다. */
    val nextUpdateEpochSec: Long = 0,
    /** 실제로 받아온 시각 */
    val fetchedEpochSec: Long = 0,
    /**
     * 원화로 환산했을 때의 전일 대비 등락률(%).
     * 주요 통화만 들어 있고, 유럽중앙은행 고시 기준이라 영업일 단위로 바뀐다.
     */
    val changes: Map<String, Double> = emptyMap(),
) {
    val isUsable: Boolean get() = rates.size > 10

    /** [from] 통화 [amount] 를 [to] 통화로. 표에 없는 통화면 null. */
    fun convert(amount: Double, from: String, to: String): Double? {
        val fromRate = rates[from] ?: return null
        val toRate = rates[to] ?: return null
        if (fromRate == 0.0) return null
        return amount / fromRate * toRate
    }
}

@Serializable
private data class ErApiResponse(
    val result: String = "",
    val base_code: String = "USD",
    val rates: Map<String, Double> = emptyMap(),
    val time_last_update_utc: String = "",
    val time_next_update_unix: Long = 0,
)

/**
 * 환율은 하루 한 번만 바뀌므로 받아온 표를 폰에 저장해 두고 재사용한다.
 * 덕분에 비행기 모드에서도 마지막 환율로 계산이 된다.
 */
class ExchangeRateRepository(context: Context) {

    private val prefs = context.getSharedPreferences("rates", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    fun cached(): RateTable {
        val raw = prefs.getString(KEY, null) ?: return RateTable()
        return runCatching { json.decodeFromString(RateTable.serializer(), raw) }
            .getOrDefault(RateTable())
    }

    /**
     * 저장된 표가 아직 유효하면 그대로 쓰고, 지났을 때만 새로 받아온다.
     * 받아오기에 실패하면 저장된 표를 그대로 돌려준다 — 오래된 값이라도 없는 것보다 낫다.
     */
    suspend fun load(force: Boolean = false): RateTable {
        val cached = cached()
        val now = System.currentTimeMillis() / 1000

        val fresh = cached.isUsable && cached.changes.isNotEmpty() &&
            (now < cached.nextUpdateEpochSec || now - cached.fetchedEpochSec < MIN_INTERVAL_SEC)
        if (fresh && !force) return cached

        return runCatching {
            val body = Network.getRaw(URL)
            val response = json.decodeFromString(ErApiResponse.serializer(), body)
            if (response.rates.isEmpty()) throw IllegalStateException("환율 응답이 비어 있습니다")

            RateTable(
                base = response.base_code,
                rates = response.rates,
                updatedAt = response.time_last_update_utc,
                // 제공처가 시각을 안 주면 24시간 뒤로 잡는다
                nextUpdateEpochSec = response.time_next_update_unix.takeIf { it > 0 } ?: (now + MIN_INTERVAL_SEC),
                fetchedEpochSec = now,
                // 등락률은 다른 곳에서 받는다. 실패해도 환율 자체는 쓸 수 있어야 한다.
                changes = runCatching { dailyChanges() }.getOrDefault(emptyMap()),
            ).also { table ->
                prefs.edit().putString(KEY, json.encodeToString(RateTable.serializer(), table)).apply()
            }
        }.getOrElse { cached }
    }

    /**
     * 원화 기준 전일 대비 등락률.
     *
     * 쓰는 곳이 과거 값을 주지 않아 유럽중앙은행 고시값(frankfurter)으로 따로 계산한다.
     * 같은 출처의 두 날짜를 견주므로 등락률 자체는 정확하다.
     */
    private suspend fun dailyChanges(): Map<String, Double> {
        val today = LocalDate.now()
        val from = today.minusDays(10)
        val body = Network.getRaw("$HISTORY_URL/$from..$today?base=USD")
        val root = JSONObject(body).getJSONObject("rates")

        val dates = root.keys().asSequence().toList().sorted()
        if (dates.size < 2) return emptyMap()

        val last = root.getJSONObject(dates.last())
        val prev = root.getJSONObject(dates[dates.size - 2])
        val krwLast = last.optDouble("KRW", 0.0)
        val krwPrev = prev.optDouble("KRW", 0.0)
        if (krwLast <= 0 || krwPrev <= 0) return emptyMap()

        return buildMap {
            // 원화는 기준 통화라 등락이 없다
            put("KRW", 0.0)
            for (code in last.keys()) {
                if (code == "KRW") continue
                val a = last.optDouble(code, 0.0)
                val b = prev.optDouble(code, 0.0)
                if (a <= 0 || b <= 0) continue
                // '1 통화 = 몇 원'으로 바꿔 견준다
                val nowKrw = krwLast / a
                val prevKrw = krwPrev / b
                put(code, (nowKrw / prevKrw - 1) * 100)
            }
            // USD 는 기준이라 위 반복에 없다
            put("USD", (krwLast / krwPrev - 1) * 100)
        }
    }

    private companion object {
        const val URL = "https://open.er-api.com/v6/latest/USD"
        const val HISTORY_URL = "https://api.frankfurter.app"
        const val KEY = "table"

        /** 제공처가 하루 한 번 갱신하므로 그보다 자주 부를 이유가 없다. */
        const val MIN_INTERVAL_SEC = 24L * 60 * 60
    }
}
