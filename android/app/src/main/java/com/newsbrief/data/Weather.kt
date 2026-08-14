package com.newsbrief.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

data class Weather(
    val place: String,
    val currentTemp: Int,
    val minTemp: Int,
    val maxTemp: Int,
    val description: String,
    val emoji: String,
    val precipitationChance: Int,
) {
    /** "서울 22~28℃ / 구름 조금" */
    val summary: String get() = "$place $minTemp~$maxTemp℃ / $description"
}

/** 서울시청. 위치 권한이 없거나 마지막 위치를 못 얻었을 때 쓴다. */
private const val FALLBACK_LAT = 37.5665
private const val FALLBACK_LON = 126.9780
private const val FALLBACK_PLACE = "서울"

object WeatherRepository {

    suspend fun fetch(context: Context, useLocation: Boolean): Weather {
        val located = if (useLocation) lastKnownLocation(context) else null
        val (latitude, longitude) = located ?: (FALLBACK_LAT to FALLBACK_LON)

        val place = if (located == null) FALLBACK_PLACE else placeName(context, latitude, longitude)
        return fetchForecast(latitude, longitude, place)
    }

    private fun hasLocationPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * 마지막으로 알려진 위치만 읽는다. 실시간 측위는 배터리를 쓰고 실내에서 오래 걸리는데,
     * 날씨는 동네 단위면 충분해서 굳이 필요하지 않다.
     */
    private fun lastKnownLocation(context: Context): Pair<Double, Double>? {
        if (!hasLocationPermission(context)) return null
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null

        val providers = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER, LocationManager.GPS_PROVIDER)
        for (provider in providers) {
            val location = runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
            if (location != null) return location.latitude to location.longitude
        }
        return null
    }

    @Suppress("DEPRECATION")
    private suspend fun placeName(context: Context, latitude: Double, longitude: Double): String =
        withContext(Dispatchers.IO) {
            if (!Geocoder.isPresent()) return@withContext FALLBACK_PLACE
            runCatching {
                val geocoder = Geocoder(context, Locale.KOREA)
                val address = geocoder.getFromLocation(latitude, longitude, 1)?.firstOrNull()
                // 예: '서울특별시 강남구' → '강남구'
                address?.subLocality ?: address?.locality ?: address?.adminArea ?: FALLBACK_PLACE
            }.getOrDefault(FALLBACK_PLACE)
        }

    private suspend fun fetchForecast(latitude: Double, longitude: Double, place: String): Weather {
        val url = buildString {
            append("https://api.open-meteo.com/v1/forecast")
            append("?latitude=").append(latitude)
            append("&longitude=").append(longitude)
            append("&current=temperature_2m,weather_code")
            append("&daily=temperature_2m_max,temperature_2m_min,weather_code,precipitation_probability_max")
            append("&timezone=Asia%2FSeoul&forecast_days=1")
        }
        val body = Network.getRaw(url)
        val root = org.json.JSONObject(body)

        val current = root.getJSONObject("current")
        val daily = root.getJSONObject("daily")

        fun firstInt(key: String): Int =
            daily.getJSONArray(key).optDouble(0, 0.0).let { Math.round(it).toInt() }

        val code = current.optInt("weather_code", 0)
        return Weather(
            place = place,
            currentTemp = Math.round(current.optDouble("temperature_2m", 0.0)).toInt(),
            minTemp = firstInt("temperature_2m_min"),
            maxTemp = firstInt("temperature_2m_max"),
            description = describe(code),
            emoji = emoji(code),
            precipitationChance = firstInt("precipitation_probability_max"),
        )
    }

    /** WMO 날씨 코드 → 한국어 설명. */
    private fun describe(code: Int): String = when (code) {
        0 -> "맑음"
        1 -> "대체로 맑음"
        2 -> "구름 조금"
        3 -> "흐림"
        45, 48 -> "안개"
        51, 53, 55 -> "이슬비"
        56, 57 -> "어는 이슬비"
        61, 63 -> "비"
        65 -> "강한 비"
        66, 67 -> "어는 비"
        71, 73 -> "눈"
        75 -> "많은 눈"
        77 -> "싸락눈"
        80, 81 -> "소나기"
        82 -> "강한 소나기"
        85, 86 -> "소낙눈"
        95 -> "뇌우"
        96, 99 -> "우박 동반 뇌우"
        else -> "-"
    }

    private fun emoji(code: Int): String = when (code) {
        0 -> "☀️"
        1, 2 -> "🌤️"
        3 -> "☁️"
        45, 48 -> "🌫️"
        in 51..57 -> "🌦️"
        in 61..67 -> "🌧️"
        in 71..77 -> "❄️"
        in 80..82 -> "🌦️"
        85, 86 -> "🌨️"
        in 95..99 -> "⛈️"
        else -> "🌡️"
    }

    fun searchQuery(weather: Weather?): String =
        if (weather == null) "날씨" else "${weather.place} 날씨"
}

/** Android 13 부터 알림을 띄우려면 권한을 따로 받아야 한다. */
val needsNotificationPermission: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
