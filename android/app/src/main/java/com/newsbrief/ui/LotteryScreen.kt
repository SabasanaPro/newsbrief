package com.newsbrief.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.newsbrief.data.Lotto
import com.newsbrief.data.MyNumbers
import com.newsbrief.data.Pension
import com.newsbrief.data.checkLotto
import java.text.DecimalFormat
import java.time.DayOfWeek
import java.time.LocalDate

@Composable
fun LotteryScreen(
    lotto: Lotto?,
    pension: Pension?,
    myNumbers: MyNumbers,
    onMyNumbersChange: (MyNumbers) -> Unit,
    onOpenLink: (String) -> Unit,
) {
    if (lotto == null && pension == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("당첨번호를 불러오지 못했습니다", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // 로또를 항상 먼저 보여준다
        if (lotto != null) LottoCard(lotto, myNumbers, onMyNumbersChange, onOpenLink)
        if (pension != null) PensionCard(pension, onOpenLink)

        Spacer(Modifier.height(8.dp))
    }
}

/* ---------------- 로또 ---------------- */

@Composable
private fun LottoCard(
    lotto: Lotto,
    myNumbers: MyNumbers,
    onMyNumbersChange: (MyNumbers) -> Unit,
    onOpenLink: (String) -> Unit,
) {
    SectionCard {
        CardHeader("로또 6/45", "${lotto.round}회 · ${lotto.drawDate}")
        Spacer(Modifier.height(14.dp))

        val matched = if (myNumbers.enabled && myNumbers.isComplete) {
            checkLotto(myNumbers.numbers, lotto).matchedNumbers
        } else {
            emptySet()
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            lotto.numbers.forEach { number ->
                NumberBall(number, highlighted = number in matched)
                Spacer(Modifier.width(5.dp))
            }
            Text("+", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(5.dp))
            NumberBall(lotto.bonus, highlighted = false)
        }

        Spacer(Modifier.height(14.dp))
        PrizeLine(
            winners = lotto.firstPrizeWinners,
            amountText = lotto.firstPrizeAmount?.let { formatKoreanAmount(it) },
        )

        MyNumbersSection(lotto, myNumbers, onMyNumbersChange)

        Spacer(Modifier.height(4.dp))
        TextButton(onClick = { onOpenLink(lotto.link) }) { Text("동행복권에서 보기") }
    }
}

/* ---------------- 연금복권 ---------------- */

@Composable
private fun PensionCard(pension: Pension, onOpenLink: (String) -> Unit) {
    SectionCard {
        CardHeader("연금복권 720+", "${pension.round}회 · ${pension.drawDate}")
        Spacer(Modifier.height(14.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "${pension.group ?: "-"}조",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(10.dp))
            pension.number.orEmpty().forEach { digit ->
                DigitBox(digit.toString())
                Spacer(Modifier.width(4.dp))
            }
        }

        if (!pension.bonus.isNullOrBlank()) {
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "보너스",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(10.dp))
                pension.bonus.forEach { digit ->
                    DigitBox(digit.toString())
                    Spacer(Modifier.width(4.dp))
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        PrizeLine(
            winners = pension.firstPrizeWinners,
            // 연금복권 1등은 총액으로 오므로 20년(240개월) 기준 월 수령액으로 바꿔 보여준다
            amountText = pension.firstPrizeAmount?.let { "월 ${formatKoreanAmount(it / 240)}(20년)" },
        )

        Spacer(Modifier.height(4.dp))
        TextButton(onClick = { onOpenLink(pension.link) }) { Text("동행복권에서 보기") }
    }
}

/* ---------------- 내 번호 대조 ---------------- */

@Composable
private fun MyNumbersSection(
    lotto: Lotto,
    myNumbers: MyNumbers,
    onChange: (MyNumbers) -> Unit,
) {
    Spacer(Modifier.height(14.dp))
    HorizontalDivider()
    Spacer(Modifier.height(8.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text("내 번호 대조", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Switch(
            checked = myNumbers.enabled,
            onCheckedChange = { onChange(myNumbers.copy(enabled = it)) },
        )
    }

    if (!myNumbers.enabled) return

    Spacer(Modifier.height(8.dp))

    // 화면에서 편집 중인 값은 문자열로 두고, 6칸이 모두 유효할 때만 저장한다.
    // 스위치를 켤 때만 저장된 값으로 초기화한다 — 입력 중 재설정되면 안 되기 때문.
    var values by remember(myNumbers.enabled) {
        mutableStateOf(
            List(6) { index ->
                val text = myNumbers.numbers.getOrNull(index)?.toString() ?: ""
                TextFieldValue(text, TextRange(text.length))
            }
        )
    }
    val focusRequesters = remember { List(6) { FocusRequester() } }
    var inputError by remember { mutableStateOf<String?>(null) }

    /** 커서를 해당 칸의 글자 끝으로 보내면서 포커스를 옮긴다. */
    fun moveTo(index: Int) {
        val text = values[index].text
        values = values.toMutableList().also { it[index] = TextFieldValue(text, TextRange(text.length)) }
        focusRequesters[index].requestFocus()
    }

    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        values.forEachIndexed { index, field ->
            OutlinedTextField(
                value = field,
                onValueChange = onValueChange@{ input ->
                    val digits = input.text.filter { it.isDigit() }.take(2)
                    val previous = values[index].text
                    // '01' 처럼 앞에 0을 붙여 치면 한 자리 수로 정리한다
                    val normalized = if (digits.length == 2 && digits[0] == '0') digits.substring(1) else digits

                    // 46 이상은 아예 받지 않는다. '0' 은 '01' 을 치는 중일 수 있어 통과시킨다.
                    val number = normalized.toIntOrNull()
                    if (normalized.isNotEmpty() && normalized != "0" && (number == null || number !in 1..45)) {
                        inputError = "1~45 사이의 숫자만 입력할 수 있습니다"
                        return@onValueChange
                    }
                    inputError = null

                    values = values.toMutableList().also {
                        it[index] = TextFieldValue(normalized, TextRange(normalized.length))
                    }
                    val numbers = values.mapNotNull { it.text.toIntOrNull() }.filter { it in 1..45 }
                    if (numbers.size == 6 && numbers.distinct().size == 6) {
                        onChange(myNumbers.copy(numbers = numbers))
                    }

                    when {
                        // 칸을 다 지우면 왼쪽 칸 끝으로
                        normalized.isEmpty() && previous.isNotEmpty() && index > 0 -> moveTo(index - 1)
                        // 두 자리를 채웠거나 0을 붙여 한 자리를 확정하면 오른쪽 칸으로
                        digits.length == 2 && normalized != "0" && index < 5 -> moveTo(index + 1)
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequesters[index])
                    .onKeyEvent { event ->
                        // 빈 칸에서 지우기를 누르면 왼쪽 칸 끝으로 이동해 이어서 지울 수 있게 한다
                        val backspaceOnEmpty = event.type == KeyEventType.KeyDown &&
                            event.key == Key.Backspace &&
                            values[index].text.isEmpty() &&
                            index > 0
                        if (backspaceOnEmpty) {
                            moveTo(index - 1)
                            true
                        } else {
                            false
                        }
                    },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = if (index < 5) ImeAction.Next else ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(
                    onNext = { if (index < 5) moveTo(index + 1) },
                ),
                textStyle = TextStyle(textAlign = TextAlign.Center, fontSize = 15.sp),
            )
        }
    }

    Spacer(Modifier.height(10.dp))

    val parsed = values.mapNotNull { it.text.toIntOrNull() }.filter { it in 1..45 }
    when {
        inputError != null -> ResultText(inputError!!, MaterialTheme.colorScheme.error)
        parsed.size < 6 -> ResultText("1~45 사이 숫자 6개를 입력하세요", MaterialTheme.colorScheme.onSurfaceVariant)
        parsed.distinct().size < 6 -> ResultText("중복된 숫자가 있습니다", MaterialTheme.colorScheme.error)
        else -> {
            val result = checkLotto(parsed, lotto)
            ResultText(
                text = "${lotto.round}회 결과 — ${result.label} (${result.matched}개 일치" +
                    (if (result.rank == 2) ", 보너스 포함" else "") + ")",
                color = if (result.rank == 0) MaterialTheme.colorScheme.onSurfaceVariant else RiseColor,
            )
        }
    }
}

@Composable
private fun ResultText(text: String, color: Color) {
    Text(text = text, style = MaterialTheme.typography.bodyMedium, color = color, fontWeight = FontWeight.Medium)
}

/* ---------------- 공용 조각 ---------------- */

@Composable
private fun SectionCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(16.dp)) { content() }
    }
}

@Composable
private fun CardHeader(title: String, subtitle: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            subtitle,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PrizeLine(winners: Int?, amountText: String?) {
    if (winners == null && amountText == null) return
    val parts = listOfNotNull(
        winners?.let { "${it}명" },
        amountText,
    )
    Text(
        text = "1등 : ${parts.joinToString(" / ")}",
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Medium,
    )
}

@Composable
private fun NumberBall(number: Int, highlighted: Boolean) {
    Box(
        modifier = Modifier
            .size(if (highlighted) 38.dp else 34.dp)
            .background(ballColor(number), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = number.toString(),
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = if (highlighted) 15.sp else 13.sp,
        )
    }
}

@Composable
private fun DigitBox(digit: String) {
    Box(
        modifier = Modifier
            .size(width = 26.dp, height = 34.dp)
            .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = digit,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
        )
    }
}

/** 동행복권 공식 볼 색상. */
private fun ballColor(number: Int): Color = when (number) {
    in 1..10 -> Color(0xFFFBC400)
    in 11..20 -> Color(0xFF69C8F2)
    in 21..30 -> Color(0xFFFF7272)
    in 31..40 -> Color(0xFFAAAAAA)
    else -> Color(0xFFB0D840)
}

private val amountFormat = DecimalFormat("#,##0")

/** 2,441,919,375 → "24.4억", 7,000,000 → "700만원" */
private fun formatKoreanAmount(amount: Long): String = when {
    amount >= 100_000_000 -> "%.1f억".format(amount / 100_000_000.0)
    amount >= 10_000 -> "${amountFormat.format(amount / 10_000)}만원"
    else -> "${amountFormat.format(amount)}원"
}
