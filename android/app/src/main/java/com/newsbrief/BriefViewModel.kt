package com.newsbrief

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.newsbrief.data.AppSettings
import com.newsbrief.data.Brief
import com.newsbrief.data.Favorite
import com.newsbrief.data.FavoritesData
import com.newsbrief.data.FavoritesStore
import com.newsbrief.data.ExchangeRateRepository
import com.newsbrief.data.MyNumbers
import com.newsbrief.data.MyNumbersStore
import com.newsbrief.data.RateTable
import com.newsbrief.data.Network
import com.newsbrief.data.Quote
import com.newsbrief.data.SettingsStore
import com.newsbrief.data.Story
import com.newsbrief.data.Weather
import com.newsbrief.data.WeatherRepository
import com.newsbrief.data.add
import com.newsbrief.data.addFolder
import com.newsbrief.data.buildWidgetSnapshot
import com.newsbrief.data.remove
import com.newsbrief.data.removeFolder
import com.newsbrief.notify.NotificationScheduler
import com.newsbrief.widget.WidgetUpdater
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UiState(
    val brief: Brief? = null,
    val briefLoading: Boolean = false,
    val briefError: String? = null,
    val quotes: List<Quote> = emptyList(),
    val quotesLoading: Boolean = false,
    val quotesError: String? = null,
    val weather: Weather? = null,
    val weatherLoading: Boolean = false,
    val myNumbers: MyNumbers = MyNumbers(),
    val favorites: FavoritesData = FavoritesData(),
    val settings: AppSettings = AppSettings(),
    val rates: RateTable = RateTable(),
    val ratesLoading: Boolean = false,
)

class BriefViewModel(app: Application) : AndroidViewModel(app) {

    private val numbersStore = MyNumbersStore(app)
    private val favoritesStore = FavoritesStore(app)
    private val settingsStore = SettingsStore(app)
    private val rateRepository = ExchangeRateRepository(app)

    private val _state = MutableStateFlow(
        UiState(
            myNumbers = numbersStore.load(),
            favorites = favoritesStore.load(),
            settings = settingsStore.load(),
        )
    )
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        refreshBrief()
        refreshQuotes()
        refreshWeather()
        refreshRates()
        NotificationScheduler.rescheduleAll(app, _state.value.settings)
        WidgetUpdater.schedule(app)
    }

    /**
     * 환율은 하루 한 번만 바뀌므로 저장해 둔 표를 먼저 보여주고,
     * 유효 기간이 지났을 때만 새로 받아온다.
     */
    fun refreshRates(force: Boolean = false) {
        if (_state.value.ratesLoading) return
        _state.update { it.copy(ratesLoading = true, rates = rateRepository.cached()) }
        viewModelScope.launch {
            val table = rateRepository.load(force)
            _state.update { it.copy(rates = table, ratesLoading = false) }
        }
    }

    /** 앱이 새로 받은 값을 위젯에도 그대로 넘겨 준다. */
    private fun syncWidget() {
        val current = _state.value
        if (current.brief == null && current.quotes.isEmpty() && current.weather == null) return
        viewModelScope.launch {
            runCatching {
                WidgetUpdater.push(
                    getApplication(),
                    buildWidgetSnapshot(
                        brief = current.brief,
                        quotes = current.quotes,
                        weather = current.weather,
                        chosenTopics = current.settings.topics,
                        now = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")),
                    ),
                )
            }
        }
    }

    /* ---------- 불러오기 ---------- */

    fun refreshBrief() {
        if (_state.value.briefLoading) return
        _state.update { it.copy(briefLoading = true, briefError = null) }
        viewModelScope.launch {
            runCatching { Network.fetchBrief() }
                .onSuccess { brief ->
                    _state.update { it.copy(brief = brief, briefLoading = false) }
                    syncWidget()
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(briefLoading = false, briefError = error.message ?: "불러오지 못했습니다")
                    }
                }
        }
    }

    fun refreshQuotes() {
        if (_state.value.quotesLoading) return
        _state.update { it.copy(quotesLoading = true, quotesError = null) }
        viewModelScope.launch {
            runCatching { Network.fetchQuotes() }
                .onSuccess { quotes ->
                    _state.update {
                        it.copy(
                            quotes = quotes,
                            quotesLoading = false,
                            quotesError = if (quotes.isEmpty()) "시세를 불러오지 못했습니다" else null,
                        )
                    }
                    syncWidget()
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(quotesLoading = false, quotesError = error.message ?: "불러오지 못했습니다")
                    }
                }
        }
    }

    fun refreshWeather() {
        if (_state.value.weatherLoading) return
        _state.update { it.copy(weatherLoading = true) }
        viewModelScope.launch {
            val weather = runCatching {
                WeatherRepository.fetch(getApplication(), _state.value.settings.useLocation)
            }.getOrNull()
            _state.update { it.copy(weather = weather, weatherLoading = false) }
            syncWidget()
        }
    }

    fun refreshAll() {
        refreshBrief()
        refreshQuotes()
        refreshWeather()
    }

    /* ---------- 내 로또 번호 ---------- */

    fun setMyNumbers(value: MyNumbers) {
        numbersStore.save(value)
        _state.update { it.copy(myNumbers = value) }
    }

    /* ---------- 즐겨찾기 ---------- */

    fun addFavorite(story: Story, categoryName: String, folder: String) {
        update(
            _state.value.favorites.add(
                Favorite(
                    link = story.link,
                    title = story.title,
                    source = story.source,
                    categoryName = categoryName,
                    folder = folder,
                    savedAt = System.currentTimeMillis(),
                )
            )
        )
    }

    fun removeFavorite(link: String) = update(_state.value.favorites.remove(link))

    fun createFolder(name: String) = update(_state.value.favorites.addFolder(name.trim()))

    fun deleteFolder(name: String) = update(_state.value.favorites.removeFolder(name))

    private fun update(data: FavoritesData) {
        favoritesStore.save(data)
        _state.update { it.copy(favorites = data) }
    }

    /* ---------- 설정 ---------- */

    fun updateSettings(settings: AppSettings) {
        settingsStore.save(settings)
        _state.update { it.copy(settings = settings) }
        NotificationScheduler.rescheduleAll(getApplication(), settings)
    }
}
