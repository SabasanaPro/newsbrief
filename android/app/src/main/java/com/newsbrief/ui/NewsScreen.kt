package com.newsbrief.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.newsbrief.data.Category
import com.newsbrief.data.Story

@Composable
fun NewsScreen(
    categories: List<Category>,
    generatedAt: String,
    loading: Boolean,
    error: String?,
    isFavorite: (String) -> Boolean,
    onOpenLink: (String) -> Unit,
    onToggleFavorite: (Story, String) -> Unit,
) {
    if (loading && categories.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    if (categories.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                error ?: "표시할 뉴스가 없습니다",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item {
            Text(
                text = "기준 ${formatGeneratedAt(generatedAt)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        categories.forEach { category ->
            item(key = "header-${category.id}") {
                Text(
                    text = category.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 14.dp, bottom = 2.dp),
                )
            }
            items(category.items, key = { it.link }) { story ->
                StoryRow(
                    story = story,
                    favorite = isFavorite(story.link),
                    onOpen = { onOpenLink(story.link) },
                    onToggleFavorite = { onToggleFavorite(story, category.name) },
                )
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

/** 목록을 짧게 유지하려고 제목만 보여준다. 요약은 원문에서 확인. */
@Composable
private fun StoryRow(
    story: Story,
    favorite: Boolean,
    onOpen: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = story.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = story.source,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    if (story.sourceCount > 1) {
                        Spacer(Modifier.width(4.dp))
                        HeatBadge(story.sourceCount)
                    }
                }
            }
            FavoriteStar(favorite = favorite, onClick = onToggleFavorite)
        }
    }
}

/** 빈 별은 노란 테두리, 등록되면 노랗게 채워진다. */
@Composable
fun FavoriteStar(favorite: Boolean, onClick: () -> Unit, size: Int = 22) {
    IconButton(onClick = onClick) {
        Icon(
            imageVector = if (favorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
            contentDescription = if (favorite) "즐겨찾기 해제" else "즐겨찾기",
            tint = StarColor,
            modifier = Modifier.size(size.dp),
        )
    }
}

/** 몇 개 매체가 함께 다뤘는지 — 화제성 표시. */
@Composable
private fun HeatBadge(sourceCount: Int) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Text(
            text = "${sourceCount}개 매체",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
        )
    }
}

/** "2026-08-14T15:29:57+09:00" → "08/14 15:29" */
fun formatGeneratedAt(raw: String): String {
    if (raw.length < 16) return raw
    return "${raw.substring(5, 7)}/${raw.substring(8, 10)} ${raw.substring(11, 16)}"
}
