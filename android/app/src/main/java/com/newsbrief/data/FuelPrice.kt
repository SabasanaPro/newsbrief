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

/** 반경 검색 단위. 오피넷 앱과 같게 셋으로 둔다. */
val RADIUS_OPTIONS = listOf(3000, 5000, 10000)

@Serializable
data class Station(
    val name: String,
    val gasoline: Int,
    /** 고급휘발유. 취급하지 않는 주유소가 많아 없을 수 있다. */
    val premium: Int? = null,
    val brand: String = "",
    /** 현재 위치에서의 직선거리(m) */
    val distance: Int = 0,
)

@Serializable
data class FuelPrices(
    /**
     * 저장해 둔 값의 구조 번호.
     * 앱을 고쳐 항목이 바뀌면 예전 저장분을 그대로 쓰면 안 되므로 번호로 걸러낸다.
     */
    val schema: Int = 0,
    /** 평균가 기준 광역 이름. 위치를 못 얻으면 "전국". */
    val areaName: String = "전국",
    val nationalAverage: Map<String, Double> = emptyMap(),
    val areaAverage: Map<String, Double> = emptyMap(),
    /** 반경 10km 안에서 값이 싼 순으로 모아둔 주유소. 화면에서 거리로 다시 걸러 쓴다. */
    val nearby: List<Station> = emptyList(),
    val locatedNearby: Boolean = false,
    val fetchedEpochSec: Long = 0,
) {
    val isUsable: Boolean get() = nationalAverage.isNotEmpty()

    fun within(radius: Int): List<Station> = nearby.filter { it.distance <= radius }
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
        val stored = runCatching { json.decodeFromString(FuelPrices.serializer(), raw) }
            .getOrDefault(FuelPrices())
        // 예전 구조로 저장된 것은 버린다. 안 그러면 새 항목이 빈 채로 계속 유효 취급된다.
        return if (stored.schema == SCHEMA) stored else FuelPrices()
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
        val sidoRows = JSONObject(call("avgSidoPrice.do", "prodcd" to FuelType.Gasoline.code))
            .getJSONObject("RESULT").getJSONArray("OIL")

        val (adminArea, _) =
            if (useLocation) WeatherRepository.administrativeNames(context) else null to null

        var areaCode: String? = null
        var areaName = "전국"
        if (adminArea != null) {
            for (i in 0 until sidoRows.length()) {
                val row = sidoRows.getJSONObject(i)
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

        val location = if (useLocation) WeatherRepository.currentLatLon(context) else null
        val nearby = if (location != null) {
            aroundStations(location.first, location.second)
        } else {
            // 위치를 못 얻어도 화면이 비지 않게 시·도 최저가라도 보여준다
            areaCode?.let { regionStations(it) }.orEmpty()
        }

        return FuelPrices(
            schema = SCHEMA,
            areaName = areaName,
            nationalAverage = national,
            areaAverage = areaAverage,
            nearby = nearby,
            locatedNearby = location != null && nearby.isNotEmpty(),
            fetchedEpochSec = now,
        )
    }

    /**
     * 반경 10km 를 한 번만 불러 놓고, 화면에서 3·5·10km 로 걸러 쓴다.
     * 반경을 바꿀 때마다 다시 부르면 호출 수만 늘고 값은 같기 때문이다.
     */
    private suspend fun aroundStations(latitude: Double, longitude: Double): List<Station> {
        val (x, y) = Katec.fromWgs84(latitude, longitude)
        val body = call(
            "aroundAll.do",
            "x" to "%.1f".format(x),
            "y" to "%.1f".format(y),
            "radius" to RADIUS_OPTIONS.max().toString(),
            "prodcd" to FuelType.Gasoline.code,
            "sort" to "1",
        )
        val rows = JSONObject(body).getJSONObject("RESULT").getJSONArray("OIL")

        val all = (0 until rows.length()).map { i ->
            val row = rows.getJSONObject(i)
            Triple(
                row.optString("UNI_ID"),
                Station(
                    name = row.optString("OS_NM"),
                    gasoline = row.optDouble("PRICE", 0.0).toInt(),
                    brand = BRANDS[row.optString("POLL_DIV_CD")] ?: "",
                    distance = row.optDouble("DISTANCE", 0.0).toInt(),
                ),
                row.optDouble("DISTANCE", 0.0).toInt(),
            )
        }

        // 반경마다 싼 곳 위주로 뽑아 합친다. 3km 안에 아무것도 안 남는 일을 막는다.
        val picked = LinkedHashMap<String, Station>()
        for (radius in RADIUS_OPTIONS) {
            all.filter { it.third <= radius }.take(PER_RADIUS).forEach { (id, station, _) ->
                picked.putIfAbsent(id, station)
            }
        }

        // 고급휘발유 가격은 목록 API 에 없어 주유소마다 상세를 한 번씩 더 봐야 한다
        return picked.entries.map { (id, station) ->
            val premium = runCatching { premiumPrice(id) }.getOrNull()
            station.copy(premium = premium)
        }.sortedBy { it.gasoline }
    }

    /** 위치를 못 얻었을 때 쓰는 시·도 단위 최저가. 거리는 알 수 없다. */
    private suspend fun regionStations(areaCode: String): List<Station> {
        val body = call("lowTop10.do", "area" to areaCode, "prodcd" to FuelType.Gasoline.code, "cnt" to "5")
        val rows = JSONObject(body).getJSONObject("RESULT").getJSONArray("OIL")
        return (0 until rows.length()).map { i ->
            val row = rows.getJSONObject(i)
            val id = row.optString("UNI_ID")
            Station(
                name = row.optString("OS_NM"),
                gasoline = row.optDouble("PRICE", 0.0).toInt(),
                premium = runCatching { premiumPrice(id) }.getOrNull(),
                brand = BRANDS[row.optString("POLL_DIV_CD")] ?: "",
                distance = 0,
            )
        }
    }

    private suspend fun premiumPrice(uniId: String): Int? {
        val body = call("detailById.do", "id" to uniId)
        val oil = JSONObject(body).getJSONObject("RESULT").getJSONArray("OIL")
        if (oil.length() == 0) return null
        val prices = oil.getJSONObject(0).optJSONArray("OIL_PRICE") ?: return null
        for (i in 0 until prices.length()) {
            val row = prices.getJSONObject(i)
            if (row.optString("PRODCD") == FuelType.Premium.code) {
                return row.optDouble("PRICE", 0.0).toInt().takeIf { it > 0 }
            }
        }
        return null
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

        /** 저장 구조를 바꿀 때마다 올린다. */
        const val SCHEMA = 2

        /** 유가는 하루 한두 번 바뀐다. 6시간이면 충분하다. */
        const val CACHE_SEC = 6L * 60 * 60

        /** 반경 구간마다 가져올 주유소 수 */
        const val PER_RADIUS = 5
    }
}
