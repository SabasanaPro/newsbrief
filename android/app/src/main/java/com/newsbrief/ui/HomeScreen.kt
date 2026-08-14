package com.newsbrief.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.newsbrief.data.AppSettings
import com.newsbrief.data.Brief
import com.newsbrief.data.Lotto
import com.newsbrief.data.MAX_TOPICS
import com.newsbrief.data.MyNumbers
import com.newsbrief.data.Pension
import com.newsbrief.data.Quote
import com.newsbrief.data.composeBriefing
import com.newsbrief.data.pickBriefingTopics
import com.newsbrief.data.Story
import com.newsbrief.data.Weather
import com.newsbrief.data.checkLotto
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

@Composable
fun HomeScreen(
    brief: Brief?,
    quotes: List<Quote>,
    weather: Weather?,
    weatherLoading: Boolean,
    myNumbers: MyNumbers,
    settings: AppSettings,
    loading: Boolean,
    onOpenLink: (String) -> Unit,
    onSearch: (String) -> Unit,
    onOpenStory: (Story) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        DateHeader()

        if (brief == null && loading) {
            Spacer(Modifier.height(40.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                CircularProgressIndicator()
            }
            return@Column
        }

        AiBriefingCard(brief, settings)
        TopNewsCard(brief, onOpenStory)
        MarketCard(quotes, onSearch)
        LottoCard(brief?.lottery?.lotto, myNumbers, onOpenLink)
        PensionCard(brief?.lottery?.pension, onOpenLink)
        WeatherCard(weather, weatherLoading, onSearch)

        Spacer(Modifier.height(16.dp))
    }
}

/* ---------------- 날짜 ---------------- */

@Composable
private fun DateHeader() {
    val today = LocalDate.now()
    val text = today.format(DateTimeFormatter.ofPattern("M월 d일 EEEE", Locale.KOREA))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("🗓️", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}

/* ---------------- AI 브리핑 ---------------- */

@Composable
private fun AiBriefingCard(brief: Brief?, settings: AppSettings) {
    val topics = brief?.topics.orEmpty()
    if (topics.isEmpty()) return

    val selected = pickBriefingTopics(topics, settings.topics)
    // 고른 주제가 오늘 하나도 안 잡혀 대신 채운 경우인지
    val fallback = topics.none { it.id in settings.topics }

    HomeCard(title = "🤖 AI 오늘의 브리핑") {
        Text(
            text = composeBriefing(selected.map { it.phrase.ifBlank { it.name } }),
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = selected.joinToString(" · ") { it.name } +
                if (fallback) "  (고른 주제에 오늘 뉴스가 없어 대신 표시)" else "",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}


/* ---------------- 오늘 주요 뉴스 ---------------- */

@Composable
private fun TopNewsCard(brief: Brief?, onOpenStory: (Story) -> Unit) {
    // 여러 매체가 함께 다룬 순으로 전체에서 3건
    val top = brief?.categories.orEmpty()
        .flatMap { it.items }
        .sortedByDescending { it.sourceCount }
        .take(3)
    if (top.isEmpty()) return

    HomeCard(title = "📰 오늘 주요 뉴스") {
        top.forEach { story ->
            BulletRow(text = story.title, onClick = { onOpenStory(story) })
        }
    }
}

/* ---------------- 시장 ---------------- */

@Composable
private fun MarketCard(quotes: List<Quote>, onSearch: (String) -> Unit) {
    if (quotes.isEmpty()) return

    HomeCard(title = "📈 시장") {
        quotes.forEach { quote ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSearch(quote.name) }
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("• ${quote.name}", style = MaterialTheme.typography.bodyMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = quote.price,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "${arrow(quote.direction)} ${quote.rate}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = directionColor(quote.direction),
                    )
                }
            }
        }
    }
}

/* ---------------- 로또 ---------------- */

@Composable
private fun LottoCard(lotto: Lotto?, myNumbers: MyNumbers, onOpenLink: (String) -> Unit) {
    if (lotto == null) return

    HomeCard(title = "🍀 로또", onClick = { onOpenLink(lotto.link) }) {
        BulletRow("${lotto.round}회 ${lotto.numbers.joinToString(", ")} + ${lotto.bonus}")

        val winners = lotto.firstPrizeWinners
        val amount = lotto.firstPrizeAmount
        if (winners != null && amount != null) {
            BulletRow("1등 ${winners}명 / 1인당 ${formatAmount(amount)}")
        }

        BulletRow("다음 추첨까지 ${daysUntil(DayOfWeek.SATURDAY)}일")

        if (myNumbers.enabled && myNumbers.isComplete) {
            val result = checkLotto(myNumbers.numbers, lotto)
            Spacer(Modifier.height(6.dp))
            Text(
                text = "내 번호 ${myNumbers.numbers.joinToString(", ")} → ${result.matched}개 일치" +
                    if (result.rank > 0) " (${result.rank}등)" else "",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = if (result.rank > 0) RiseColor else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/* ---------------- 연금복권 ---------------- */

@Composable
private fun PensionCard(pension: Pension?, onOpenLink: (String) -> Unit) {
    if (pension == null) return

    HomeCard(title = "💰 연금복권 720+", onClick = { onOpenLink(pension.link) }) {
        BulletRow("${pension.round}회 ${pension.group ?: "-"}조 ${pension.number.orEmpty()}")

        pension.bonus?.takeIf { it.isNotBlank() }?.let { BulletRow("보너스 $it") }

        val winners = pension.firstPrizeWinners
        val amount = pension.firstPrizeAmount
        if (winners != null && amount != null) {
            // 총액으로 오므로 20년(240개월) 기준 월 수령액으로 바꿔 보여준다
            BulletRow("1등 ${winners}명 / 월 ${formatAmount(amount / 240)}(20년)")
        }

        BulletRow("다음 추첨까지 ${daysUntil(DayOfWeek.THURSDAY)}일")
    }
}

/* ---------------- 날씨 ---------------- */

@Composable
private fun WeatherCard(weather: Weather?, loading: Boolean, onSearch: (String) -> Unit) {
    HomeCard(
        title = "${weather?.emoji ?: "🌤️"} 오늘 날씨",
        onClick = { onSearch("${weather?.place ?: "오늘"} 날씨") },
    ) {
        when {
            weather != null -> {
                BulletRow("${weather.place} ${weather.minTemp}~${weather.maxTemp}℃ (현재 ${weather.currentTemp}℃)")
                BulletRow("${weather.description} · 강수확률 ${weather.precipitationChance}%")
            }

            loading -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)

            else -> Text(
                "날씨를 불러오지 못했습니다. 위치 권한을 확인해보세요.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/* ---------------- 공용 ---------------- */

@Composable
private fun HomeCard(
    title: String,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun BulletRow(text: String, onClick: (() -> Unit)? = null) {
    Text(
        text = "• $text",
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 3.dp),
    )
}

@Composable
private fun directionColor(direction: Int) = when (direction) {
    1 -> RiseColor
    -1 -> FallColor
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

private fun arrow(direction: Int) = when (direction) {
    1 -> "▲"
    -1 -> "▼"
    else -> "–"
}

private fun daysUntil(day: DayOfWeek): Long {
    val today = LocalDate.now()
    val next = today.with(TemporalAdjusters.next(day))
    return java.time.temporal.ChronoUnit.DAYS.between(today, next)
}

/** 2,441,919,375 → "24.4억원" */
private fun formatAmount(amount: Long): String = when {
    amount >= 100_000_000 -> "%.1f억원".format(amount / 100_000_000.0)
    amount >= 10_000 -> "%,d만원".format(amount / 10_000)
    else -> "%,d원".format(amount)
}
