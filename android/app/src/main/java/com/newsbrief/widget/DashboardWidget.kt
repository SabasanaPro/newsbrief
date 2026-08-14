package com.newsbrief.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.newsbrief.MainActivity
import com.newsbrief.data.WidgetSnapshot
import com.newsbrief.data.WidgetSnapshotStore
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val Rise = ColorProvider(Color(0xFFD32F2F))
private val Fall = ColorProvider(Color(0xFF1976D2))

/**
 * 앱을 열었을 때 보이는 홈 화면을 그대로 옮긴 위젯.
 * 세로로 길어질 수 있어 목록으로 만들어 두었다 — 위젯을 작게 줄여도 안에서 넘겨 볼 수 있다.
 */
class DashboardWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = WidgetSnapshotStore(context).load()
        provideContent {
            GlanceTheme {
                WidgetBody(snapshot)
            }
        }
    }
}

class DashboardWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = DashboardWidget()
}

@Composable
private fun WidgetBody(snapshot: WidgetSnapshot) {
    LazyColumn(
        modifier = GlanceModifier
            .fillMaxWidth()
            .background(GlanceTheme.colors.widgetBackground)
            .cornerRadius(20.dp)
            .padding(12.dp)
            .clickable(actionStartActivity<MainActivity>()),
    ) {
        item { DateHeader(snapshot.updatedAt) }

        if (snapshot.isEmpty) {
            item {
                Spacer(GlanceModifier.height(8.dp))
                Text(
                    "앱을 한 번 열면 표시됩니다",
                    style = TextStyle(fontSize = 13.sp, color = GlanceTheme.colors.onSurfaceVariant),
                )
            }
            return@LazyColumn
        }

        if (snapshot.briefing.isNotBlank()) {
            item {
                WidgetCard("🤖 AI 오늘의 브리핑") {
                    Text(
                        snapshot.briefing,
                        style = TextStyle(fontSize = 13.sp, color = GlanceTheme.colors.onSurface),
                    )
                }
            }
        }

        if (snapshot.headlines.isNotEmpty()) {
            item {
                WidgetCard("📰 오늘 주요 뉴스") {
                    snapshot.headlines.take(3).forEach { headline ->
                        BulletLine(headline)
                    }
                }
            }
        }

        if (snapshot.quotes.isNotEmpty()) {
            item {
                WidgetCard("📈 시세") {
                    snapshot.quotes.forEach { quote ->
                        Row(
                            modifier = GlanceModifier.fillMaxWidth().padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "• ${quote.name}",
                                style = TextStyle(fontSize = 13.sp, color = GlanceTheme.colors.onSurface),
                                modifier = GlanceModifier.defaultWeight(),
                            )
                            Text(
                                quote.price,
                                style = TextStyle(
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = GlanceTheme.colors.onSurface,
                                ),
                            )
                            Spacer(GlanceModifier.width(6.dp))
                            Text(
                                "${arrow(quote.direction)}${quote.rate}",
                                style = TextStyle(fontSize = 12.sp, color = directionColor(quote.direction)),
                            )
                        }
                    }
                }
            }
        }

        if (snapshot.lottoNumbers.isNotBlank() || snapshot.pensionLine.isNotBlank()) {
            item {
                WidgetCard("🍀 복권") {
                    if (snapshot.lottoNumbers.isNotBlank()) {
                        BulletLine("${snapshot.lottoRound} ${snapshot.lottoNumbers}")
                        if (snapshot.lottoPrize.isNotBlank()) BulletLine(snapshot.lottoPrize)
                    }
                    if (snapshot.pensionLine.isNotBlank()) {
                        BulletLine("연금복권 ${snapshot.pensionLine}")
                    }
                }
            }
        }

        if (snapshot.weather.isNotBlank()) {
            item {
                WidgetCard("${snapshot.weatherEmoji.ifBlank { "🌤️" }} 오늘 날씨") {
                    BulletLine(snapshot.weather)
                }
            }
        }

        item { Spacer(GlanceModifier.height(4.dp)) }
    }
}

@Composable
private fun DateHeader(updatedAt: String) {
    val today = LocalDate.now().format(DateTimeFormatter.ofPattern("M월 d일 EEEE", Locale.KOREA))
    Row(
        modifier = GlanceModifier.fillMaxWidth().padding(bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "🗓️ $today",
            style = TextStyle(
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = GlanceTheme.colors.onSurface,
            ),
            modifier = GlanceModifier.defaultWeight(),
        )
        if (updatedAt.isNotBlank()) {
            Text(
                updatedAt,
                style = TextStyle(fontSize = 11.sp, color = GlanceTheme.colors.onSurfaceVariant),
            )
        }
    }
}

/** 홈 화면 카드와 같은 모양 — 둥근 모서리에 제목 한 줄, 그 아래 내용. */
@Composable
private fun WidgetCard(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
    ) {
        Column(
            modifier = GlanceModifier
                .fillMaxWidth()
                .background(GlanceTheme.colors.surface)
                .cornerRadius(14.dp)
                .padding(12.dp),
        ) {
            Text(
                title,
                style = TextStyle(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = GlanceTheme.colors.onSurface,
                ),
            )
            Spacer(GlanceModifier.height(6.dp))
            content()
        }
    }
}

@Composable
private fun BulletLine(text: String) {
    Text(
        "• $text",
        style = TextStyle(fontSize = 13.sp, color = GlanceTheme.colors.onSurface),
        modifier = GlanceModifier.padding(vertical = 1.dp),
        maxLines = 2,
    )
}

@Composable
private fun directionColor(direction: Int) = when (direction) {
    1 -> Rise
    -1 -> Fall
    else -> GlanceTheme.colors.onSurfaceVariant
}

private fun arrow(direction: Int) = when (direction) {
    1 -> "▲"
    -1 -> "▼"
    else -> "–"
}
