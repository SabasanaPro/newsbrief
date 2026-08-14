package com.newsbrief.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.newsbrief.data.RateTable
import com.newsbrief.data.currencyOf
import com.newsbrief.data.formatAmount

@Composable
fun CurrencyScreen(
    table: RateTable,
    codes: List<String>,
    loading: Boolean,
    onCodesChange: (List<String>) -> Unit,
    onRefresh: () -> Unit,
) {
    // 편집 중인 줄과 그 줄에 사용자가 친 글자. 나머지 줄은 여기서 계산해 채운다.
    var editingCode by remember { mutableStateOf(codes.firstOrNull() ?: "KRW") }
    var editingText by remember { mutableStateOf("") }
    var showPicker by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        if (!table.isUsable) {
            Box(Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
                if (loading) CircularProgressIndicator()
                else Text("환율을 불러오지 못했습니다", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Column
        }

        val amount = editingText.toDoubleOrNull()

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        ) {
            Column(Modifier.padding(vertical = 4.dp)) {
                codes.forEachIndexed { index, code ->
                    CurrencyRow(
                        code = code,
                        editing = code == editingCode,
                        editingText = editingText,
                        // 어느 줄을 고치든 그 줄이 기준이 되고 나머지가 따라 바뀐다
                        converted = amount?.let { table.convert(it, editingCode, code) },
                        removable = codes.size > 2,
                        onSelect = {
                            if (editingCode != code) {
                                // 줄을 옮기면 보이던 값을 그대로 이어받아 이어서 고칠 수 있게 한다
                                val carried = amount?.let { table.convert(it, editingCode, code) }
                                editingCode = code
                                editingText = carried?.let { trimForEditing(it, code) }.orEmpty()
                            }
                        },
                        onTextChange = { input -> editingText = sanitize(input) },
                        onRemove = { onCodesChange(codes - code) },
                    )
                    if (index != codes.lastIndex) HorizontalDivider(Modifier.padding(horizontal = 14.dp))
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        OutlinedButton(onClick = { showPicker = true }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("화폐 추가")
        }

        Spacer(Modifier.height(12.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "환율 기준 ${formatRateDate(table.updatedAt)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onRefresh, enabled = !loading) { Text("지금 갱신") }
        }
        Text(
            "환율은 하루 한 번 갱신됩니다. 받아온 값을 저장해 두어 인터넷이 없어도 계산됩니다.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (showPicker) {
        CurrencyPickerDialog(
            selected = codes,
            available = table.rates.keys,
            onConfirm = onCodesChange,
            onDismiss = { showPicker = false },
        )
    }
}

/**
 * 고치고 있는 줄만 입력칸이고 나머지는 계산된 값을 보여준다.
 * 줄을 누르면 그 줄이 입력칸으로 바뀐다 — 화면이 조용해지고 기호도 붙여 쓸 수 있다.
 */
@Composable
private fun CurrencyRow(
    code: String,
    editing: Boolean,
    editingText: String,
    converted: Double?,
    removable: Boolean,
    onSelect: () -> Unit,
    onTextChange: (String) -> Unit,
    onRemove: () -> Unit,
) {
    val info = currencyOf(code)
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(editing) {
        if (editing) {
            runCatching { focusRequester.requestFocus() }
            keyboard?.show()
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(info.flag, fontSize = 24.sp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.width(104.dp)) {
            Text(
                info.code,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                info.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
            )
        }

        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = info.symbol,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (editing) {
                BasicTextField(
                    value = editingText,
                    onValueChange = onTextChange,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .widthIn(min = 60.dp)
                        .focusRequester(focusRequester),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    textStyle = LocalTextStyle.current.copy(
                        textAlign = TextAlign.End,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                )
            } else {
                Text(
                    text = converted?.let { formatAmount(it, info.decimals) } ?: "0",
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }

        if (removable) {
            IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "$code 삭제",
                    modifier = Modifier.size(15.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            Spacer(Modifier.width(32.dp))
        }
    }
}

/** 숫자와 소수점만 남기고, 소수점은 하나까지만 허용한다. */
private fun sanitize(input: String): String {
    val filtered = input.filter { it.isDigit() || it == '.' }
    val firstDot = filtered.indexOf('.')
    if (firstDot < 0) return filtered.take(15)
    return (filtered.substring(0, firstDot + 1) +
        filtered.substring(firstDot + 1).filter { it != '.' }).take(15)
}

/** 다른 줄로 옮길 때 넣어줄 값. 쉼표 없이, 그 통화의 소수점 자리수에 맞춘다. */
private fun trimForEditing(value: Double, code: String): String {
    val decimals = currencyOf(code).decimals
    return formatAmount(value, decimals).replace(",", "")
}

/** "Fri, 14 Aug 2026 00:02:32 +0000" → "8월 14일" */
private fun formatRateDate(raw: String): String {
    val months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    val parts = raw.split(" ")
    if (parts.size < 4) return raw.ifBlank { "알 수 없음" }
    val day = parts[1].toIntOrNull() ?: return raw
    val month = months.indexOf(parts[2]).takeIf { it >= 0 }?.plus(1) ?: return raw
    return "${month}월 ${day}일"
}
