package com.newsbrief.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

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
