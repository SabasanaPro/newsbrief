package com.newsbrief.data

import android.content.Context

/** 내가 저장한 로또 번호 한 조합. 폰 안에만 저장되고 서버로 보내지 않는다. */
data class MyNumbers(
    val enabled: Boolean = false,
    val numbers: List<Int> = emptyList(),
) {
    val isComplete: Boolean get() = numbers.size == 6 && numbers.all { it in 1..45 }
}

class MyNumbersStore(context: Context) {

    private val prefs = context.getSharedPreferences("my_lotto", Context.MODE_PRIVATE)

    fun load(): MyNumbers {
        val raw = prefs.getString(KEY_NUMBERS, "").orEmpty()
        val numbers = raw.split(",").mapNotNull { it.trim().toIntOrNull() }.filter { it in 1..45 }
        return MyNumbers(enabled = prefs.getBoolean(KEY_ENABLED, false), numbers = numbers)
    }

    fun save(value: MyNumbers) {
        prefs.edit()
            .putBoolean(KEY_ENABLED, value.enabled)
            .putString(KEY_NUMBERS, value.numbers.joinToString(","))
            .apply()
    }

    private companion object {
        const val KEY_ENABLED = "enabled"
        const val KEY_NUMBERS = "numbers"
    }
}

/** 대조 결과. */
data class CheckResult(
    val rank: Int,          // 1~5등, 0 이면 낙첨
    val matched: Int,
    val bonusMatched: Boolean,
    val matchedNumbers: Set<Int>,
) {
    val label: String
        get() = if (rank == 0) "낙첨" else "${rank}등 당첨"
}

/**
 * 로또 등수 규칙: 6개 일치 1등, 5개+보너스 2등, 5개 3등, 4개 4등, 3개 5등.
 */
fun checkLotto(mine: List<Int>, draw: Lotto): CheckResult {
    val winning = draw.numbers.toSet()
    val matchedNumbers = mine.toSet() intersect winning
    val matched = matchedNumbers.size
    val bonusMatched = draw.bonus in mine

    val rank = when {
        matched == 6 -> 1
        matched == 5 && bonusMatched -> 2
        matched == 5 -> 3
        matched == 4 -> 4
        matched == 3 -> 5
        else -> 0
    }
    return CheckResult(rank, matched, bonusMatched, matchedNumbers)
}
