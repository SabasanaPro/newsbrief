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

/** 2×2 요약, 4×2 상세, 4×4 전체 세 가지 크기를 지원한다. */
private val SMALL = DpSize(140.dp, 110.dp)
private val WIDE = DpSize(280.dp, 110.dp)
private val FULL = DpSize(280.dp, 240.dp)

private val Rise = ColorProvider(Color(0xFFD32F2F))
private val Fall = ColorProvider(Color(0xFF1976D2))

abstract class BaseDashboardWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(setOf(SMALL, WIDE, FULL))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = WidgetSnapshotStore(context).load()
        provideContent {
            GlanceTheme {
                WidgetBody(snapshot)
            }
        }
    }
}

/**
 * 내용은 같지만 위젯 목록에 크기별로 따로 뜨게 하려고 둘로 나눴다.
 * 런처가 목록에서 보여주는 크기는 등록된 항목마다 하나뿐이기 때문이다.
 */
class DashboardWidget : BaseDashboardWidget()

class DashboardWideWidget : BaseDashboardWidget()

class DashboardFullWidget : BaseDashboardWidget()

class DashboardWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = DashboardWidget()
}

class DashboardWideWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = DashboardWideWidget()
}

class DashboardFullWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = DashboardFullWidget()
}

@Composable
private fun WidgetBody(snapshot: WidgetSnapshot) {
    val size = LocalSize.current
    val wide = size.width >= WIDE.width
    val full = size.height >= FULL.height

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

        // 큰 위젯에만 브리핑 문단이 들어간다
        if (full && snapshot.briefing.isNotBlank()) {
            Spacer(GlanceModifier.height(6.dp))
            Text(
                snapshot.briefing,
                style = TextStyle(fontSize = 12.sp, color = GlanceTheme.colors.onSurface),
                maxLines = 3,
            )
        }

        Spacer(GlanceModifier.height(6.dp))
        if (full) QuoteRows(snapshot) else QuoteLine(snapshot, wide)

        if (snapshot.lottoNumbers.isNotBlank()) {
            Spacer(GlanceModifier.height(6.dp))
            Text(
                "🍀 ${snapshot.lottoRound} ${snapshot.lottoNumbers}",
                style = TextStyle(fontSize = 12.sp, color = GlanceTheme.colors.onSurface),
                maxLines = 1,
            )
            if (full && snapshot.lottoPrize.isNotBlank()) {
                Text(
                    snapshot.lottoPrize,
                    style = TextStyle(fontSize = 11.sp, color = GlanceTheme.colors.onSurfaceVariant),
                    maxLines = 1,
                )
            }
        }

        if (full && snapshot.pensionLine.isNotBlank()) {
            Spacer(GlanceModifier.height(4.dp))
            Text(
                "💰 ${snapshot.pensionLine}",
                style = TextStyle(fontSize = 12.sp, color = GlanceTheme.colors.onSurface),
                maxLines = 1,
            )
        }

        // 좁은 위젯에는 기사 제목이 들어갈 자리가 없다
        if (wide && snapshot.headlines.isNotEmpty()) {
            Spacer(GlanceModifier.height(6.dp))
            snapshot.headlines.take(if (full) 4 else 2).forEach { headline ->
                Text(
                    "• $headline",
                    style = TextStyle(fontSize = 12.sp, color = GlanceTheme.colors.onSurface),
                    maxLines = 1,
                )
            }
        }
    }
}

/** 큰 위젯에서는 시세를 가격까지 한 줄씩 보여준다. */
@Composable
private fun QuoteRows(snapshot: WidgetSnapshot) {
    snapshot.quotes.forEach { quote ->
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                quote.name,
                style = TextStyle(fontSize = 12.sp, color = GlanceTheme.colors.onSurface),
                modifier = GlanceModifier.defaultWeight(),
                maxLines = 1,
            )
            Text(
                "${quote.price}  ${arrow(quote.direction)}${quote.rate}",
                style = TextStyle(fontSize = 12.sp, color = directionColor(quote.direction)),
                maxLines = 1,
            )
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
                style = TextStyle(fontSize = 12.sp, color = directionColor(quote.direction)),
                maxLines = 1,
            )
        }
    }
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
