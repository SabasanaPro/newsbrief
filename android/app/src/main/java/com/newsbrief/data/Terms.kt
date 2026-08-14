package com.newsbrief.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate

@Serializable
data class Term(
    val term: String = "",
    val reading: String = "",
    val category: String = "",
    val summary: String = "",
    /** "내가 알던 그것이 여기선 뭐라 불리는가" — 이 앱의 핵심. */
    val bridge: String = "",
)

@Serializable
data class TermBook(
    val version: Int = 0,
    val terms: List<Term> = emptyList(),
) {
    val isUsable: Boolean get() = terms.isNotEmpty()

    /**
     * 오늘의 용어. 날짜로 정하기 때문에 하루 동안은 몇 번을 열어도 같은 것이 나오고,
     * 자정이 지나면 다음 것으로 넘어간다.
     */
    fun todays(date: LocalDate = LocalDate.now()): Term? {
        if (terms.isEmpty()) return null
        val index = (date.toEpochDay() % terms.size).toInt()
        return terms[if (index < 0) index + terms.size else index]
    }
}

/**
 * 용어집은 서버에 두고 받아온다. 목록을 늘려도 앱을 다시 깔 필요가 없다.
 * 받아온 것은 저장해 두어 인터넷이 없어도 보인다.
 */
class TermsRepository(context: Context) {

    private val prefs = context.getSharedPreferences("terms", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    fun cached(): TermBook {
        val raw = prefs.getString(KEY, null) ?: return TermBook()
        return runCatching { json.decodeFromString(TermBook.serializer(), raw) }
            .getOrDefault(TermBook())
    }

    suspend fun load(force: Boolean = false): TermBook {
        val cached = cached()
        val now = System.currentTimeMillis() / 1000
        val checkedAt = prefs.getLong(KEY_CHECKED, 0)
        // 용어집은 자주 바뀌지 않으므로 하루 한 번만 확인한다
        if (!force && cached.isUsable && now - checkedAt < CACHE_SEC) return cached

        return runCatching {
            val body = Network.getRaw("$URL?t=${now / 3600}")
            json.decodeFromString(TermBook.serializer(), body).also { book ->
                if (book.isUsable) {
                    prefs.edit()
                        .putString(KEY, json.encodeToString(TermBook.serializer(), book))
                        .putLong(KEY_CHECKED, now)
                        .apply()
                }
            }
        }.getOrElse { cached }
    }

    /** 다시 보고 싶은 용어. 용어 이름만 저장하면 되어 폴더까지는 두지 않았다. */
    fun favorites(): Set<String> = prefs.getStringSet(KEY_FAVORITES, emptySet()).orEmpty()

    fun setFavorites(value: Set<String>) {
        prefs.edit().putStringSet(KEY_FAVORITES, value).apply()
    }

    private companion object {
        const val URL = "https://raw.githubusercontent.com/SabasanaPro/newsbrief/main/data/terms.json"
        const val KEY_FAVORITES = "favorites"
        const val KEY = "book"
        const val KEY_CHECKED = "checked"
        const val CACHE_SEC = 24L * 60 * 60
    }
}
