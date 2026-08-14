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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.newsbrief.data.Quote
import com.newsbrief.data.RateTable
import com.newsbrief.data.currencyOf
import com.newsbrief.data.formatAmount

@Composable
fun MarketScreen(
    quotes: List<Quote>,
    loading: Boolean,
    error: String?,
    table: RateTable,
    rateBases: List<String>,
    onRateBasesChange: (List<String>) -> Unit,
    onRefresh: () -> Unit,
    onOpenUpbit: () -> Unit,
    onSearch: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("시장", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            IconButton(onClick = onRefresh, enabled = !loading) {
                if (loading) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Filled.Refresh, contentDescription = "새로고침")
                }
            }
        }

        Spacer(Modifier.height(4.dp))

        if (quotes.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
                if (loading) {
                    CircularProgressIndicator()
                } else {
                    Text(
                        error ?: "시세를 불러오지 못했습니다",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            ) {
                Column(Modifier.padding(vertical = 4.dp)) {
                    quotes.forEachIndexed { index, quote ->
                        QuoteRow(quote, onClick = { onSearch(quote.name) })
                        if (index != quotes.lastIndex) {
                            HorizontalDivider(Modifier.padding(horizontal = 14.dp))
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        OutlinedButton(onClick = onOpenUpbit, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(8.dp))
            Text("업비트 열기")
        }

        Spacer(Modifier.height(10.dp))
        Text(
            "코스피·코스닥은 평일 09:00~15:30 에만 값이 움직입니다.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(20.dp))
        Text("환율", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        RateCard(table, rateBases, onRateBasesChange)

        Spacer(Modifier.height(16.dp))
    }
}

/** 원화 기준 환율 네 줄. 각 줄의 통화를 눌러 다른 통화로 바꿀 수 있다. */
@Composable
private fun RateCard(table: RateTable, bases: List<String>, onBasesChange: (List<String>) -> Unit) {
    if (!table.isUsable) return
    // 통화를 바꾸는 중인 줄 번호
    var editingSlot by remember { mutableStateOf<Int?>(null) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(vertical = 4.dp)) {
            bases.forEachIndexed { index, code ->
                RateRow(table, code) { editingSlot = index }
                if (index != bases.lastIndex) HorizontalDivider(Modifier.padding(horizontal = 14.dp))
            }
        }
    }

    editingSlot?.let { slot ->
        CurrencyPickerDialog(
            selected = listOf(bases[slot]),
            available = table.rates.keys,
            single = true,
            onConfirm = { picked ->
                picked.firstOrNull()?.let { code ->
                    onBasesChange(bases.toMutableList().also { it[slot] = code })
                }
            },
            onDismiss = { editingSlot = null },
        )
    }
}

@Composable
private fun RateRow(table: RateTable, code: String, onPick: () -> Unit) {
    val info = currencyOf(code)
    val krw = currencyOf("KRW")

    // 엔화처럼 한 단위가 몇 원밖에 안 되는 통화는 100 단위로 보는 게 익숙하다
    val perUnit = table.convert(1.0, code, "KRW")
    val unit = if (perUnit != null && perUnit < 100) 100 else 1
    val value = perUnit?.times(unit)

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 통화 부분만 눌리는 영역이다
        Row(
            modifier = Modifier.clickable(onClick = onPick),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("$unit ${info.flag} ${info.code}", style = MaterialTheme.typography.bodyMedium)
            Icon(
                Icons.Filled.ArrowDropDown,
                contentDescription = "통화 바꾸기",
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.width(4.dp))
        Text("=", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.weight(1f))
        Text(
            text = value?.let { "${krw.symbol}${formatAmount(it, 2)}" } ?: "-",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun QuoteRow(quote: Quote, onClick: () -> Unit) {
    val color = when (quote.direction) {
        1 -> RiseColor
        -1 -> FallColor
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val sign = when (quote.direction) {
        1 -> "▲"
        -1 -> "▼"
        else -> "–"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text(quote.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            quote.status?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(quote.price, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text(
                "$sign ${quote.change} (${quote.rate})",
                style = MaterialTheme.typography.labelMedium,
                color = color,
            )
        }
    }
}
