package com.newsbrief.data

import android.content.Context
import com.newsbrief.BuildConfig
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.json.JSONObject

/** 오피넷 유종 코드. */
enum class FuelType(val code: String, val label: String) {
    Gasoline("B027", "휘발유"),
    Diesel("D047", "경유"),
    Premium("B034", "고급휘발유"),
    Lpg("K015", "LPG"),
}

@Serializable
data class Station(
    val name: String,
    val price: Int,
    val brand: String = "",
)

@Serializable
data class FuelPrices(
    /** 지역 이름. 위치를 못 얻으면 "전국". */
    val areaName: String = "전국",
    val nationalAverage: Map<String, Double> = emptyMap(),
    val areaAverage: Map<String, Double> = emptyMap(),
    /** 유종별 최저가 주유소. 키는 유종 코드. */
    val cheapest: Map<String, List<Station>> = emptyMap(),
    val fetchedEpochSec: Long = 0,
) {
    val isUsable: Boolean get() = nationalAverage.isNotEmpty()
}

/** 정유사 코드 → 표시 이름. */
private val BRANDS = mapOf(
    "SKE" to "SK에너지", "GSC" to "GS칼텍스", "HDO" to "현대오일뱅크",
    "SOL" to "S-OIL", "RTE" to "자영알뜰", "RTX" to "고속도로알뜰",
    "NHO" to "농협알뜰", "ETC" to "자가상표", "E1G" to "E1", "SKG" to "SK가스",
)

/**
 * 유가는 하루에 한두 번만 바뀌므로 받아온 값을 저장해 두고 재사용한다.
 * 오피넷 무료 키는 하루 1,500회 제한이라 아껴 쓸 이유도 있다.
 */
class FuelRepository(context: Context) {

    private val prefs = context.getSharedPreferences("fuel", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    fun cached(): FuelPrices {
        val raw = prefs.getString(KEY, null) ?: return FuelPrices()
        return runCatching { json.decodeFromString(FuelPrices.serializer(), raw) }
            .getOrDefault(FuelPrices())
    }

    suspend fun load(context: Context, useLocation: Boolean, force: Boolean = false): FuelPrices {
        if (BuildConfig.OPINET_KEY.isBlank()) return FuelPrices()

        val cached = cached()
        val now = System.currentTimeMillis() / 1000
        if (!force && cached.isUsable && now - cached.fetchedEpochSec < CACHE_SEC) return cached

        return runCatching { fetch(context, useLocation, now) }
            .onSuccess { fresh ->
                prefs.edit().putString(KEY, json.encodeToString(FuelPrices.serializer(), fresh)).apply()
            }
            .getOrElse { cached }
    }

    private suspend fun fetch(context: Context, useLocation: Boolean, now: Long): FuelPrices {
        val national = averages(call("avgAllPrice.do"))

        // 시도 목록을 받아 사용자의 행정구역 이름과 맞춰본다.
        // 코드를 앱에 박아두면 오피넷이 바꿀 때 같이 깨지므로 이름으로 찾는다.
        val sidoList = JSONObject(call("avgSidoPrice.do", "prodcd" to FuelType.Gasoline.code))
            .getJSONObject("RESULT").getJSONArray("OIL")

        val adminArea = if (useLocation) WeatherRepository.adminAreaName(context) else null
        var areaCode: String? = null
        var areaName = "전국"
        if (adminArea != null) {
            for (i in 0 until sidoList.length()) {
                val row = sidoList.getJSONObject(i)
                val name = row.optString("SIDONM")
                if (name.isNotBlank() && name != "전국" && adminArea.contains(name)) {
                    areaCode = row.optString("SIDOCD")
                    areaName = name
                    break
                }
            }
        }

        val areaAverage = if (areaCode == null) emptyMap() else buildMap {
            for (type in FuelType.entries) {
                val rows = JSONObject(call("avgSidoPrice.do", "prodcd" to type.code))
                    .getJSONObject("RESULT").getJSONArray("OIL")
                for (i in 0 until rows.length()) {
                    val row = rows.getJSONObject(i)
                    if (row.optString("SIDOCD") == areaCode) {
                        put(type.code, row.optDouble("PRICE", 0.0))
                        break
                    }
                }
            }
        }

        val cheapest = buildMap {
            for (type in listOf(FuelType.Gasoline, FuelType.Diesel)) {
                val stations = lowest(areaCode, type.code)
                if (stations.isNotEmpty()) put(type.code, stations)
            }
        }

        return FuelPrices(
            areaName = areaName,
            nationalAverage = national,
            areaAverage = areaAverage,
            cheapest = cheapest,
            fetchedEpochSec = now,
        )
    }

    private suspend fun lowest(areaCode: String?, prodCode: String): List<Station> {
        // 지역을 못 찾으면 전국 최저가로 대신한다
        val body = if (areaCode == null) {
            call("lowTop10.do", "area" to "01", "prodcd" to prodCode, "cnt" to "5")
        } else {
            call("lowTop10.do", "area" to areaCode, "prodcd" to prodCode, "cnt" to "5")
        }
        val rows = JSONObject(body).getJSONObject("RESULT").getJSONArray("OIL")
        return (0 until rows.length()).map { i ->
            val row = rows.getJSONObject(i)
            Station(
                name = row.optString("OS_NM"),
                price = row.optDouble("PRICE", 0.0).toInt(),
                brand = BRANDS[row.optString("POLL_DIV_CD")] ?: "",
            )
        }
    }

    private fun averages(body: String): Map<String, Double> {
        val rows = JSONObject(body).getJSONObject("RESULT").getJSONArray("OIL")
        return buildMap {
            for (i in 0 until rows.length()) {
                val row = rows.getJSONObject(i)
                put(row.optString("PRODCD"), row.optDouble("PRICE", 0.0))
            }
        }
    }

    private suspend fun call(path: String, vararg params: Pair<String, String>): String {
        val query = params.joinToString("") { (k, v) -> "&$k=$v" }
        return Network.getRaw("https://www.opinet.co.kr/api/$path?out=json&code=${BuildConfig.OPINET_KEY}$query")
    }

    private companion object {
        const val KEY = "prices"

        /** 유가는 하루 한두 번 바뀐다. 6시간이면 충분하다. */
        const val CACHE_SEC = 6L * 60 * 60
    }
}
