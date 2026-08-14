package com.newsbrief.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.newsbrief.data.Network
import com.newsbrief.data.SettingsStore
import com.newsbrief.data.WeatherRepository
import com.newsbrief.data.WidgetSnapshot
import com.newsbrief.data.WidgetSnapshotStore
import com.newsbrief.data.buildWidgetSnapshot
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

object WidgetUpdater {

    private const val WORK_NAME = "widget-refresh"

    /** 앱이 이미 받아 둔 값으로 위젯을 갱신한다. 네트워크를 다시 쓰지 않는다. */
    suspend fun push(context: Context, snapshot: WidgetSnapshot) {
        WidgetSnapshotStore(context).save(snapshot)
        DashboardWidget().updateAll(context)
    }

    /**
     * 안드로이드는 위젯을 너무 자주 깨우지 못하게 막고 있어 최소 간격이 15분이다.
     * 30분마다 돌리고, 그 사이 최신값이 필요하면 앱을 열면 된다.
     */
    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<WidgetWorker>(30, TimeUnit.MINUTES).build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }
}

class WidgetWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val settings = SettingsStore(applicationContext).load()

        val brief = runCatching { Network.fetchBrief() }.getOrNull()
        val quotes = runCatching { Network.fetchQuotes() }.getOrDefault(emptyList())
        val weather = runCatching {
            WeatherRepository.fetch(applicationContext, settings.useLocation)
        }.getOrNull()

        // 전부 실패했으면 기존 표시를 지우지 않고 그대로 둔다
        if (brief == null && quotes.isEmpty() && weather == null) return Result.retry()

        val snapshot = buildWidgetSnapshot(
            brief = brief,
            quotes = quotes,
            weather = weather,
            chosenTopics = settings.topics,
            now = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")),
        )
        WidgetUpdater.push(applicationContext, snapshot)
        return Result.success()
    }
}
