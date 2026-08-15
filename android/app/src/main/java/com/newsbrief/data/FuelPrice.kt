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
    /** 도로명 주소. 지도에서 정확히 찾으려면 이름만으로는 부족하다. */
    val address: String = "",
    val gasoline: Int? = null,
    /** 고급휘발유. 취급하지 않는 주유소가 많아 없을 수 있다. */
    val premium: Int? = null,
    val brand: String = "",
    /** 현재 위치에서의 직선거리(m) */
    val distance: Int = 0,
) {
    fun priceOf(type: FuelType): Int? = when (type) {
        FuelType.Premium -> premium
        else -> gasoline
    }
}

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

    /** 고른 유종을 파는 곳만, 그 유종 값이 싼 순으로. */
    fun within(radius: Int, type: FuelType): List<Station> =
        nearby.filter { it.distance <= radius && it.priceOf(type) != null }
            .sortedBy { it.priceOf(type) }
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
        // 지역이나 주변 주유소를 못 얻은 결과는 오래 붙들지 않는다.
        // 위치가 나중에 잡히는 일이 잦아서, 그때 다시 시도해야 한다.
        val window = if (cached.areaAverage.isEmpty() || cached.nearby.isEmpty()) RETRY_SEC else CACHE_SEC
        if (!force && cached.isUsable && now - cached.fetchedEpochSec < window) return cached

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

        val location = if (useLocation) WeatherRepository.currentLatLon(context) else null
        val nearby = if (location != null) {
            aroundStations(location.first, location.second)
        } else {
            emptyList()
        }

        val (adminArea, _) =
            if (useLocation) WeatherRepository.administrativeNames(context) else null to null

        // 주소 검색(Geocoder)은 기기에 따라 빈손으로 돌아온다.
        // 그럴 때는 근처 주유소 주소('경기 안양시 …')의 앞머리로 시·도를 알아낸다.
        val areaHint = adminArea ?: nearby.firstOrNull { it.address.isNotBlank() }?.address

        var areaCode: String? = null
        var areaName = "전국"
        if (areaHint != null) {
            for (i in 0 until sidoRows.length()) {
                val row = sidoRows.getJSONObject(i)
                val name = row.optString("SIDONM")
                if (name.isNotBlank() && name != "전국" && areaHint.contains(name)) {
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

        // 위치를 못 얻었으면 화면이 비지 않게 시·도 최저가라도 보여준다
        val stations = nearby.ifEmpty { areaCode?.let { regionStations(it) }.orEmpty() }

        return FuelPrices(
            schema = SCHEMA,
            areaName = areaName,
            nationalAverage = national,
            areaAverage = areaAverage,
            nearby = stations,
            locatedNearby = nearby.isNotEmpty(),
            fetchedEpochSec = now,
        )
    }

    /**
     * 반경 10km 를 한 번만 불러 놓고, 화면에서 3·5·10km 로 걸러 쓴다.
     * 반경을 바꿀 때마다 다시 부르면 호출 수만 늘고 값은 같기 때문이다.
     */
    private suspend fun aroundStations(latitude: Double, longitude: Double): List<Station> {
        val (x, y) = Katec.fromWgs84(latitude, longitude)

        // 휘발유와 고급휘발유는 파는 곳이 달라 각각 조회한 뒤 합친다.
        // 고급휘발유만 보면 취급점이 적어 목록이 금방 비어 버린다.
        val picked = LinkedHashMap<String, Station>()
        for (type in listOf(FuelType.Gasoline, FuelType.Premium)) {
            val body = call(
                "aroundAll.do",
                "x" to "%.1f".format(x),
                "y" to "%.1f".format(y),
                "radius" to RADIUS_OPTIONS.max().toString(),
                "prodcd" to type.code,
                "sort" to "1",
            )
            val rows = JSONObject(body).getJSONObject("RESULT").getJSONArray("OIL")
            val parsed = (0 until rows.length()).map { i ->
                val row = rows.getJSONObject(i)
                val distance = row.optDouble("DISTANCE", 0.0).toInt()
                Triple(
                    row.optString("UNI_ID"),
                    Station(
                        name = row.optString("OS_NM"),
                        brand = BRANDS[row.optString("POLL_DIV_CD")] ?: "",
                        distance = distance,
                    ),
                    distance,
                )
            }
            // 반경마다 싼 곳 위주로 뽑는다. 3km 안에 아무것도 안 남는 일을 막는다.
            for (radius in RADIUS_OPTIONS) {
                parsed.filter { it.third <= radius }.take(PER_RADIUS).forEach { (id, station, _) ->
                    picked.putIfAbsent(id, station)
                }
            }
        }

        // 유종별 가격과 주소는 목록 API 에 없어 주유소마다 상세를 한 번씩 더 봐야 한다
        return picked.entries.mapNotNull { (id, station) ->
            runCatching { detail(id, station) }.getOrNull()
        }
    }

    /** 상세 조회로 유종별 가격과 도로명 주소를 채운다. */
    private suspend fun detail(uniId: String, base: Station): Station {
        val oil = JSONObject(call("detailById.do", "id" to uniId))
            .getJSONObject("RESULT").getJSONArray("OIL")
        if (oil.length() == 0) return base

        val row = oil.getJSONObject(0)
        val prices = row.optJSONArray("OIL_PRICE")
        var gasoline: Int? = null
        var premium: Int? = null
        if (prices != null) {
            for (i in 0 until prices.length()) {
                val price = prices.getJSONObject(i)
                val value = price.optDouble("PRICE", 0.0).toInt().takeIf { it > 0 }
                when (price.optString("PRODCD")) {
                    FuelType.Gasoline.code -> gasoline = value
                    FuelType.Premium.code -> premium = value
                }
            }
        }
        return base.copy(
            address = row.optString("NEW_ADR").ifBlank { row.optString("VAN_ADR") },
            gasoline = gasoline,
            premium = premium,
        )
    }

    /** 위치를 못 얻었을 때 쓰는 시·도 단위 최저가. 거리는 알 수 없다. */
    private suspend fun regionStations(areaCode: String): List<Station> {
        val picked = LinkedHashMap<String, Station>()
        for (type in listOf(FuelType.Gasoline, FuelType.Premium)) {
            val body = call("lowTop10.do", "area" to areaCode, "prodcd" to type.code, "cnt" to "5")
            val rows = JSONObject(body).getJSONObject("RESULT").getJSONArray("OIL")
            for (i in 0 until rows.length()) {
                val row = rows.getJSONObject(i)
                picked.putIfAbsent(
                    row.optString("UNI_ID"),
                    Station(
                        name = row.optString("OS_NM"),
                        brand = BRANDS[row.optString("POLL_DIV_CD")] ?: "",
                    ),
                )
            }
        }
        return picked.entries.mapNotNull { (id, station) ->
            runCatching { detail(id, station) }.getOrNull()
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

        /** 저장 구조를 바꿀 때마다 올린다. */
        const val SCHEMA = 4

        /** 유가는 하루 한두 번 바뀐다. 6시간이면 충분하다. */
        const val CACHE_SEC = 6L * 60 * 60

        /** 반쪽짜리 결과를 붙들고 있지 않도록 짧게 다시 시도한다. */
        const val RETRY_SEC = 10L * 60

        /** 반경 구간마다 가져올 주유소 수 */
        const val PER_RADIUS = 5
    }
}
