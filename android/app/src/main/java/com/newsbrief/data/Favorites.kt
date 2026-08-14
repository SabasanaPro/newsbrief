package com.newsbrief.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

const val DEFAULT_FOLDER = "기본"

/**
 * 즐겨찾기한 기사. 뉴스 목록은 하루 두 번 갈리므로 기사 내용을 통째로 담아 둔다.
 * 그래야 시간이 지나 목록에서 사라진 기사도 다시 열어볼 수 있다.
 */
@Serializable
data class Favorite(
    val link: String,
    val title: String,
    val source: String,
    val categoryName: String = "",
    val folder: String = DEFAULT_FOLDER,
    val savedAt: Long = 0L,
)

@Serializable
data class FavoritesData(
    val folders: List<String> = listOf(DEFAULT_FOLDER),
    val items: List<Favorite> = emptyList(),
)

class FavoritesStore(context: Context) {

    private val prefs = context.getSharedPreferences("favorites", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    fun load(): FavoritesData {
        val raw = prefs.getString(KEY, null) ?: return FavoritesData()
        return runCatching { json.decodeFromString(FavoritesData.serializer(), raw) }
            .getOrDefault(FavoritesData())
    }

    fun save(data: FavoritesData) {
        prefs.edit().putString(KEY, json.encodeToString(FavoritesData.serializer(), data)).apply()
    }

    private companion object {
        const val KEY = "data"
    }
}

fun FavoritesData.contains(link: String): Boolean = items.any { it.link == link }

fun FavoritesData.add(favorite: Favorite): FavoritesData {
    val folders = if (favorite.folder in folders) folders else folders + favorite.folder
    return copy(folders = folders, items = items.filterNot { it.link == favorite.link } + favorite)
}

fun FavoritesData.remove(link: String): FavoritesData =
    copy(items = items.filterNot { it.link == link })

fun FavoritesData.addFolder(name: String): FavoritesData =
    if (name.isBlank() || name in folders) this else copy(folders = folders + name)

/** 폴더를 지우면 그 안의 기사는 기본 폴더로 옮긴다. */
fun FavoritesData.removeFolder(name: String): FavoritesData {
    if (name == DEFAULT_FOLDER) return this
    return copy(
        folders = folders.filterNot { it == name },
        items = items.map { if (it.folder == name) it.copy(folder = DEFAULT_FOLDER) else it },
    )
}
