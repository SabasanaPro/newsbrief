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
    rateBase: String,
    onRateBaseChange: (String) -> Unit,
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
            Text("시세", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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

        Spacer(Modifier.height(12.dp))
        RateLine(table, rateBase, onRateBaseChange)

        Spacer(Modifier.height(12.dp))
        Text(
            "코스피·코스닥은 평일 09:00~15:30 에만 값이 움직입니다.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 한 줄 환율. 기준 통화를 누르면 엔·유로 등으로 바꿔 볼 수 있다. */
@Composable
private fun RateLine(table: RateTable, base: String, onBaseChange: (String) -> Unit) {
    if (!table.isUsable) return
    var showPicker by remember { mutableStateOf(false) }

    val info = currencyOf(base)
    val krw = currencyOf("KRW")
    val value = table.convert(1.0, base, "KRW")

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 기준 통화 부분만 눌리는 영역이다
            Row(
                modifier = Modifier.clickable { showPicker = true },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("1 ${info.flag} ${info.code}", style = MaterialTheme.typography.bodyMedium)
                Icon(
                    Icons.Filled.ArrowDropDown,
                    contentDescription = "기준 통화 바꾸기",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.width(6.dp))
            Text("=", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.weight(1f))
            Text(
                text = value?.let { "${krw.symbol}${formatAmount(it, 2)}" } ?: "-",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }

    if (showPicker) {
        CurrencyPickerDialog(
            selected = listOf(base),
            available = table.rates.keys,
            single = true,
            onConfirm = { it.firstOrNull()?.let(onBaseChange) },
            onDismiss = { showPicker = false },
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
