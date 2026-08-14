package com.newsbrief.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 위젯이 그릴 내용만 추려 담아 둔 것.
 *
 * 위젯은 그려질 때마다 네트워크를 쓸 수 없으므로(느리고 배터리를 먹는다),
 * 앱이나 갱신 작업이 미리 만들어 둔 이 값을 읽어서 표시한다.
 */
@Serializable
data class WidgetSnapshot(
    val updatedAt: String = "",
    val weather: String = "",
    val weatherEmoji: String = "",
    val quotes: List<WidgetQuote> = emptyList(),
    val lottoRound: String = "",
    val lottoNumbers: String = "",
    val headlines: List<String> = emptyList(),
) {
    val isEmpty: Boolean get() = weather.isBlank() && quotes.isEmpty() && headlines.isEmpty()
}

@Serializable
data class WidgetQuote(val name: String, val rate: String, val direction: Int)

class WidgetSnapshotStore(context: Context) {

    private val prefs = context.getSharedPreferences("widget", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    fun load(): WidgetSnapshot {
        val raw = prefs.getString(KEY, null) ?: return WidgetSnapshot()
        return runCatching { json.decodeFromString(WidgetSnapshot.serializer(), raw) }
            .getOrDefault(WidgetSnapshot())
    }

    fun save(snapshot: WidgetSnapshot) {
        prefs.edit().putString(KEY, json.encodeToString(WidgetSnapshot.serializer(), snapshot)).apply()
    }

    private companion object {
        const val KEY = "snapshot"
    }
}

/** 홈 화면과 같은 재료로 위젯용 요약을 만든다. */
fun buildWidgetSnapshot(
    brief: Brief?,
    quotes: List<Quote>,
    weather: Weather?,
    now: String,
): WidgetSnapshot {
    val lotto = brief?.lottery?.lotto
    return WidgetSnapshot(
        updatedAt = now,
        weather = weather?.let { "${it.place} ${it.minTemp}~${it.maxTemp}℃ ${it.description}" }.orEmpty(),
        weatherEmoji = weather?.emoji.orEmpty(),
        quotes = quotes.map { WidgetQuote(it.name, it.rate, it.direction) },
        lottoRound = lotto?.let { "${it.round}회" }.orEmpty(),
        lottoNumbers = lotto?.let { "${it.numbers.joinToString(" ")} + ${it.bonus}" }.orEmpty(),
        headlines = brief?.categories.orEmpty()
            .flatMap { it.items }
            .sortedByDescending { it.sourceCount }
            .take(3)
            .map { it.title },
    )
}
