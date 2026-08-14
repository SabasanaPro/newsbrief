package com.newsbrief.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.compose.ui.graphics.Color
import com.newsbrief.MainActivity
import com.newsbrief.data.WidgetSnapshot
import com.newsbrief.data.WidgetSnapshotStore

/** 2×2 요약, 4×2 상세 두 가지 크기를 지원한다. */
private val SMALL = DpSize(140.dp, 110.dp)
private val WIDE = DpSize(280.dp, 110.dp)

private val Rise = ColorProvider(Color(0xFFD32F2F))
private val Fall = ColorProvider(Color(0xFF1976D2))

class DashboardWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(setOf(SMALL, WIDE))

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
    val wide = LocalSize.current.width >= WIDE.width

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.widgetBackground)
            .cornerRadius(16.dp)
            .padding(12.dp)
            // 위젯을 누르면 앱이 열린다
            .clickable(actionStartActivity<MainActivity>()),
    ) {
        if (snapshot.isEmpty) {
            Text(
                "앱을 한 번 열면 표시됩니다",
                style = TextStyle(fontSize = 12.sp, color = GlanceTheme.colors.onSurfaceVariant),
            )
            return@Column
        }

        WeatherLine(snapshot)
        Spacer(GlanceModifier.height(6.dp))
        QuoteLine(snapshot, wide)

        if (snapshot.lottoNumbers.isNotBlank()) {
            Spacer(GlanceModifier.height(6.dp))
            Text(
                "🍀 ${snapshot.lottoRound} ${snapshot.lottoNumbers}",
                style = TextStyle(fontSize = 12.sp, color = GlanceTheme.colors.onSurface),
                maxLines = 1,
            )
        }

        // 좁은 위젯에는 기사 제목이 들어갈 자리가 없다
        if (wide && snapshot.headlines.isNotEmpty()) {
            Spacer(GlanceModifier.height(6.dp))
            snapshot.headlines.take(2).forEach { headline ->
                Text(
                    "• $headline",
                    style = TextStyle(fontSize = 12.sp, color = GlanceTheme.colors.onSurface),
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun WeatherLine(snapshot: WidgetSnapshot) {
    if (snapshot.weather.isBlank()) return
    Text(
        "${snapshot.weatherEmoji} ${snapshot.weather}",
        style = TextStyle(
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = GlanceTheme.colors.onSurface,
        ),
        maxLines = 1,
    )
}

@Composable
private fun QuoteLine(snapshot: WidgetSnapshot, wide: Boolean) {
    // 좁을 땐 코스피와 비트코인만
    val shown = if (wide) snapshot.quotes else snapshot.quotes.filter {
        it.name == "코스피" || it.name == "비트코인"
    }
    if (shown.isEmpty()) return

    Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        shown.forEachIndexed { index, quote ->
            if (index > 0) Spacer(GlanceModifier.width(10.dp))
            Text(
                "${quote.name} ${arrow(quote.direction)}${quote.rate}",
                style = TextStyle(
                    fontSize = 12.sp,
                    color = when (quote.direction) {
                        1 -> Rise
                        -1 -> Fall
                        else -> GlanceTheme.colors.onSurfaceVariant
                    },
                ),
                maxLines = 1,
            )
        }
    }
}

private fun arrow(direction: Int) = when (direction) {
    1 -> "▲"
    -1 -> "▼"
    else -> "–"
}
