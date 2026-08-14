package com.newsbrief.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class Alarm(
    val enabled: Boolean = true,
    val hour: Int = 8,
    val minute: Int = 0,
) {
    val timeLabel: String get() = "%02d:%02d".format(hour, minute)
}

@Serializable
data class AppSettings(
    /** 매일 아침 브리핑 */
    val morning: Alarm = Alarm(true, 8, 0),
    /** 로또 추첨(토 20:35) 이후 */
    val lotto: Alarm = Alarm(true, 21, 0),
    /** 연금복권 추첨(목 19:05) 이후 */
    val pension: Alarm = Alarm(true, 20, 0),
    /** 관심 키워드 뉴스 */
    val keyword: Alarm = Alarm(false, 8, 10),
    val keywords: List<String> = emptyList(),
    /** AI 브리핑에 포함할 주제 id */
    val topics: Set<String> = setOf("stock", "semiconductor", "fx", "crypto"),
    val useLocation: Boolean = true,
    /** 환율 계산기에 올려둔 통화. 순서가 화면 순서다. */
    val currencies: List<String> = listOf("KRW", "USD", "JPY", "EUR", "CNY"),
    /** 시세 화면 한 줄 환율의 기준 통화 */
    val rateBase: String = "USD",
)

class SettingsStore(context: Context) {

    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    fun load(): AppSettings {
        val raw = prefs.getString(KEY, null) ?: return AppSettings()
        return runCatching { json.decodeFromString(AppSettings.serializer(), raw) }
            .getOrDefault(AppSettings())
    }

    fun save(settings: AppSettings) {
        prefs.edit().putString(KEY, json.encodeToString(AppSettings.serializer(), settings)).apply()
    }

    private companion object {
        const val KEY = "data"
    }
}
