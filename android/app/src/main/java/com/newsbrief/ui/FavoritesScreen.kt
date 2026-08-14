package com.newsbrief.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.newsbrief.data.DEFAULT_FOLDER
import com.newsbrief.data.Favorite
import com.newsbrief.data.FavoritesData
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FavoritesScreen(
    favorites: FavoritesData,
    onOpenLink: (String) -> Unit,
    onRemove: (String) -> Unit,
    onDeleteFolder: (String) -> Unit,
) {
    if (favorites.items.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Text(
                "아직 즐겨찾기한 기사가 없습니다.\n뉴스 목록에서 별표를 누르면 여기에 모입니다.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    var confirmFolderDelete by remember { mutableStateOf<String?>(null) }

    // 폴더 안에서는 최근 저장한 것이 위로
    val grouped = favorites.folders
        .associateWith { folder -> favorites.items.filter { it.folder == folder }.sortedByDescending { it.savedAt } }
        .filterValues { it.isNotEmpty() }

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        grouped.forEach { (folder, items) ->
            item(key = "folder-$folder") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "$folder  ${items.size}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    if (folder != DEFAULT_FOLDER) {
                        IconButton(onClick = { confirmFolderDelete = folder }) {
                            Icon(
                                Icons.Filled.DeleteOutline,
                                contentDescription = "폴더 삭제",
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            items(items, key = { it.link }) { favorite ->
                FavoriteRow(
                    favorite = favorite,
                    onOpen = { onOpenLink(favorite.link) },
                    onRemove = { onRemove(favorite.link) },
                )
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }

    confirmFolderDelete?.let { folder ->
        AlertDialog(
            onDismissRequest = { confirmFolderDelete = null },
            title = { Text("'$folder' 폴더를 지울까요?") },
            text = { Text("안에 있는 기사는 지워지지 않고 '$DEFAULT_FOLDER' 폴더로 옮겨집니다.") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteFolder(folder)
                    confirmFolderDelete = null
                }) { Text("삭제") }
            },
            dismissButton = {
                TextButton(onClick = { confirmFolderDelete = null }) { Text("취소") }
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FavoriteRow(favorite: Favorite, onOpen: () -> Unit, onRemove: () -> Unit) {
    var confirmRemove by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth().combinedClickable(
            onClick = onOpen,
            // 길게 누르면 즐겨찾기 해제
            onLongClick = { confirmRemove = true },
        ),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = favorite.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = listOfNotNull(
                        favorite.source.ifBlank { null },
                        favorite.categoryName.ifBlank { null },
                        formatSavedAt(favorite.savedAt),
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FavoriteStar(favorite = true, onClick = onRemove)
        }
    }

    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text("즐겨찾기에서 뺄까요?") },
            text = { Text(favorite.title) },
            confirmButton = {
                TextButton(onClick = {
                    onRemove()
                    confirmRemove = false
                }) { Text("해제") }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemove = false }) { Text("취소") }
            },
        )
    }
}

/**
 * 별을 누를 때 올라오는 폴더 선택 시트.
 * 화면을 통째로 넘기지 않고 고른 뒤 바로 원래 자리로 돌아오게 했다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderPickerSheet(
    folders: List<String>,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    var newFolder by remember { mutableStateOf("") }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            Text(
                "어느 폴더에 담을까요?",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 6.dp),
            )

            folders.forEach { folder ->
                ListItem(
                    headlineContent = { Text(folder) },
                    modifier = Modifier.clickable { onPick(folder) },
                )
            }

            Spacer(Modifier.height(10.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = newFolder,
                    onValueChange = { newFolder = it },
                    label = { Text("새 폴더 이름") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = { if (newFolder.isNotBlank()) onPick(newFolder.trim()) },
                    enabled = newFolder.isNotBlank(),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "폴더 추가")
                }
            }
        }
    }
}

private fun formatSavedAt(millis: Long): String =
    if (millis <= 0) "" else SimpleDateFormat("MM/dd", Locale.KOREA).format(Date(millis))
