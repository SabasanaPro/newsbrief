package com.newsbrief

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.newsbrief.data.Brief
import com.newsbrief.data.MyNumbers
import com.newsbrief.data.MyNumbersStore
import com.newsbrief.data.Network
import com.newsbrief.data.Quote
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
    val myNumbers: MyNumbers = MyNumbers(),
)

class BriefViewModel(app: Application) : AndroidViewModel(app) {

    private val store = MyNumbersStore(app)
    private val _state = MutableStateFlow(UiState(myNumbers = store.load()))
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        refreshBrief()
        refreshQuotes()
    }

    fun refreshBrief() {
        if (_state.value.briefLoading) return
        _state.update { it.copy(briefLoading = true, briefError = null) }
        viewModelScope.launch {
            runCatching { Network.fetchBrief() }
                .onSuccess { brief -> _state.update { it.copy(brief = brief, briefLoading = false) } }
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
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(quotesLoading = false, quotesError = error.message ?: "불러오지 못했습니다")
                    }
                }
        }
    }

    fun setMyNumbers(value: MyNumbers) {
        store.save(value)
        _state.update { it.copy(myNumbers = value) }
    }
}
