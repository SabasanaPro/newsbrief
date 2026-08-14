package com.newsbrief.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.newsbrief.data.CURRENCIES
import com.newsbrief.data.CurrencyInfo
import com.newsbrief.data.MAX_CURRENCIES
import com.newsbrief.data.groupKeyOf

/**
 * 화폐 추가 화면. 통화가 70개가 넘어 검색과 가나다 묶음이 없으면 찾기 어렵다.
 *
 * [single] 이면 하나만 고르는 용도(한 줄 환율의 기준 통화)로 동작한다.
 */
@Composable
fun CurrencyPickerDialog(
    selected: List<String>,
    available: Set<String>,
    onConfirm: (List<String>) -> Unit,
    onDismiss: () -> Unit,
    single: Boolean = false,
) {
    var picked by remember { mutableStateOf(selected) }
    var query by remember { mutableStateOf("") }

    // 환율 표에 없는 통화는 계산이 안 되므로 아예 보여주지 않는다
    val all = remember(available) {
        CURRENCIES.filter { it.code in available }.sortedBy { it.name }
    }
    val shown = remember(query, all) {
        if (query.isBlank()) all
        else all.filter {
            it.name.contains(query, true) || it.code.contains(query, true)
        }
    }
    val full = !single && picked.size >= MAX_CURRENCIES

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (single) "기준 통화" else "화폐 추가 (${picked.size}/$MAX_CURRENCIES)")
        },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("통화명 또는 코드 검색") },
                )
                Spacer(Modifier.height(8.dp))

                LazyColumn(Modifier.heightIn(max = 380.dp)) {
                    var lastGroup = ""
                    items(shown, key = { it.code }) { info ->
                        val group = groupKeyOf(info.name)
                        val showGroup = group != lastGroup
                        lastGroup = group

                        CurrencyPickerRow(
                            info = info,
                            group = if (showGroup) group else "",
                            checked = info.code in picked,
                            enabled = single || info.code in picked || !full,
                            onToggle = {
                                picked = when {
                                    single -> listOf(info.code)
                                    info.code in picked -> picked - info.code
                                    else -> picked + info.code
                                }
                            },
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                    }
                }

                if (full) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "최대 ${MAX_CURRENCIES}개까지 고를 수 있습니다",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(picked)
                    onDismiss()
                },
                // 두 개는 남겨야 환산이 의미가 있다
                enabled = single || picked.size >= 2,
            ) { Text("확인") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}

@Composable
private fun CurrencyPickerRow(
    info: CurrencyInfo,
    group: String,
    checked: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onToggle)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 가나다 묶음 머리글자. 같은 글자가 이어지면 빈칸으로 둬 목록이 조용해진다.
        Text(
            text = group,
            modifier = Modifier.width(26.dp),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
        )
        Checkbox(checked = checked, onCheckedChange = { onToggle() }, enabled = enabled)
        Spacer(Modifier.width(4.dp))
        Column(Modifier.weight(1f)) {
            Text(
                info.code,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                info.name,
                style = MaterialTheme.typography.bodyMedium,
                color = if (enabled) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(info.flag, fontSize = 22.sp, modifier = Modifier.padding(end = 4.dp))
    }
}
