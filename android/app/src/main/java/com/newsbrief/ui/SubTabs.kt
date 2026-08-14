package com.newsbrief.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** 목록 위에 붙는 즐겨찾기 걸러보기 칩. 용어 화면과 같은 방식으로 뉴스에도 쓴다. */
@Composable
fun FavoriteFilterRow(count: Int, active: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 6.dp),
        horizontalArrangement = Arrangement.End,
    ) {
        FilterChip(
            selected = active,
            onClick = onToggle,
            label = { Text("즐겨찾기 $count") },
        )
    }
}

/** 아래 메뉴를 늘리지 않으려고 화면 위쪽에 두는 안쪽 탭. */
@Composable
fun SubTabs(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    TabRow(
        selectedTabIndex = selected,
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        labels.forEachIndexed { index, label ->
            Tab(
                selected = selected == index,
                onClick = { onSelect(index) },
                text = { Text(label) },
            )
        }
    }
}
