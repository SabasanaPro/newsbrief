package com.newsbrief.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.newsbrief.MainActivity
import com.newsbrief.R
import com.newsbrief.data.Alarm
import com.newsbrief.data.AppSettings
import com.newsbrief.data.Network
import com.newsbrief.data.SettingsStore
import com.newsbrief.data.WeatherRepository
import java.time.DayOfWeek
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters
import java.util.concurrent.TimeUnit

private val KST: ZoneId = ZoneId.of("Asia/Seoul")
private const val CHANNEL_ID = "my_dashboard"

enum class AlarmKind(val key: String, val day: DayOfWeek?) {
    Morning("morning", null),
    Lotto("lotto", DayOfWeek.SATURDAY),
    Pension("pension", DayOfWeek.THURSDAY),
    Keyword("keyword", null);

    companion object {
        fun of(key: String?): AlarmKind? = entries.firstOrNull { it.key == key }
    }
}

object NotificationScheduler {

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "My Dashboard 알림",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = "브리핑, 복권 발표, 관심 뉴스 알림" }

        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    fun rescheduleAll(context: Context, settings: AppSettings) {
        ensureChannel(context)
        schedule(context, AlarmKind.Morning, settings.morning)
        schedule(context, AlarmKind.Lotto, settings.lotto)
        schedule(context, AlarmKind.Pension, settings.pension)
        schedule(context, AlarmKind.Keyword, settings.keyword)
    }

    private fun schedule(context: Context, kind: AlarmKind, alarm: Alarm) {
        val manager = WorkManager.getInstance(context)
        if (!alarm.enabled) {
            manager.cancelUniqueWork(kind.key)
            return
        }

        val request = OneTimeWorkRequestBuilder<AlarmWorker>()
            .setInitialDelay(delayUntilNext(alarm, kind.day), TimeUnit.MILLISECONDS)
            .setInputData(Data.Builder().putString(KEY_KIND, kind.key).build())
            .build()

        manager.enqueueUniqueWork(kind.key, ExistingWorkPolicy.REPLACE, request)
    }

    /** 다음 알림 시각까지 남은 밀리초. 요일이 지정되면 그 요일까지 기다린다. */
    fun delayUntilNext(alarm: Alarm, day: DayOfWeek?): Long {
        val now = ZonedDateTime.now(KST)
        var target = now.withHour(alarm.hour).withMinute(alarm.minute).withSecond(0).withNano(0)
        if (day != null) {
            target = target.with(TemporalAdjusters.nextOrSame(day))
        }
        if (!target.isAfter(now)) {
            target = if (day != null) target.plusWeeks(1) else target.plusDays(1)
        }
        return Duration.between(now, target).toMillis().coerceAtLeast(60_000L)
    }

    const val KEY_KIND = "kind"
}

/**
 * 예약된 시각에 깨어나 알림을 띄우고, 스스로 다음 회차를 다시 예약한다.
 * 서버 푸시가 아니라 폰 안에서 도는 방식이라 별도 비용이 들지 않는다.
 */
class AlarmWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val kind = AlarmKind.of(inputData.getString(NotificationScheduler.KEY_KIND))
            ?: return Result.success()

        val settings = SettingsStore(applicationContext).load()
        val alarm = when (kind) {
            AlarmKind.Morning -> settings.morning
            AlarmKind.Lotto -> settings.lotto
            AlarmKind.Pension -> settings.pension
            AlarmKind.Keyword -> settings.keyword
        }

        if (alarm.enabled) {
            runCatching { buildContent(kind, settings) }
                .getOrNull()
                ?.let { (title, body) -> notify(kind, title, body) }
        }

        // 다음 회차 예약 — 알림이 꺼져 있으면 rescheduleAll 이 정리한다
        NotificationScheduler.rescheduleAll(applicationContext, settings)
        return Result.success()
    }

    private suspend fun buildContent(kind: AlarmKind, settings: AppSettings): Pair<String, String>? =
        when (kind) {
            AlarmKind.Morning -> "☀️ 오늘의 브리핑" to morningBody(settings)

            AlarmKind.Lotto -> "🎉 로또 당첨번호가 나왔습니다" to "당첨결과를 확인해보세요"

            AlarmKind.Pension -> "🎉 연금복권 당첨번호가 나왔습니다" to "당첨결과를 확인해보세요"

            AlarmKind.Keyword -> keywordBody(settings)?.let { "🔥 관심 뉴스" to it }
        }

    private suspend fun morningBody(settings: AppSettings): String {
        val parts = mutableListOf<String>()

        runCatching { WeatherRepository.fetch(applicationContext, settings.useLocation) }
            .getOrNull()?.let { parts += it.summary }

        runCatching { Network.fetchQuotes() }.getOrNull()?.let { quotes ->
            quotes.filter { it.name == "코스피" || it.name == "비트코인" }.forEach { quote ->
                val sign = if (quote.direction >= 0) "+" else "-"
                parts += "${quote.name} $sign${quote.rate}"
            }
        }

        return parts.joinToString(" · ").ifBlank { "오늘의 브리핑을 확인해보세요" }
    }

    /** 관심 키워드가 오늘 기사 제목에 등장하면 알린다. */
    private suspend fun keywordBody(settings: AppSettings): String? {
        if (settings.keywords.isEmpty()) return null
        val brief = runCatching { Network.fetchBrief() }.getOrNull() ?: return null

        val titles = brief.categories.flatMap { it.items }.map { it.title }
        val hits = settings.keywords.filter { keyword ->
            titles.any { it.contains(keyword, ignoreCase = true) }
        }
        if (hits.isEmpty()) return null

        return "${hits.joinToString(", ")} 관련 주요 뉴스가 있습니다."
    }

    private fun notify(kind: AlarmKind, title: String, body: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val intent = Intent(applicationContext, MainActivity::class.java)
            .apply { flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP }
        val pending = PendingIntent.getActivity(
            applicationContext,
            kind.ordinal,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(applicationContext).notify(kind.ordinal + 100, notification)
    }
}
