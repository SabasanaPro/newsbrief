package com.newsbrief.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

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

        val fresh = cached.isUsable &&
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
            ).also { table ->
                prefs.edit().putString(KEY, json.encodeToString(RateTable.serializer(), table)).apply()
            }
        }.getOrElse { cached }
    }

    private companion object {
        const val URL = "https://open.er-api.com/v6/latest/USD"
        const val KEY = "table"

        /** 제공처가 하루 한 번 갱신하므로 그보다 자주 부를 이유가 없다. */
        const val MIN_INTERVAL_SEC = 24L * 60 * 60
    }
}
