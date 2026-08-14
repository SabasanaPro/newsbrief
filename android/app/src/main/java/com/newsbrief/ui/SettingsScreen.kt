package com.newsbrief.ui

import android.app.TimePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import com.newsbrief.data.Alarm
import com.newsbrief.data.AppSettings
import com.newsbrief.data.MAX_TOPICS
import com.newsbrief.data.TopicOption

/**
 * 서버가 주제 목록을 내려주기 전에 쓸 기본값.
 * 평소에는 news.json 의 topicCatalog 를 쓰므로, 주제가 늘어도 앱을 다시 깔 필요가 없다.
 */
private val FALLBACK_TOPICS = listOf(
    TopicOption("stock", "증시"),
    TopicOption("semiconductor", "반도체"),
    TopicOption("fx", "환율"),
    TopicOption("crypto", "가상자산"),
)

@Composable
fun SettingsScreen(
    settings: AppSettings,
    topicCatalog: List<TopicOption>,
    onChange: (AppSettings) -> Unit,
) {
    var showTopicPicker by remember { mutableStateOf(false) }
    val catalog = topicCatalog.ifEmpty { FALLBACK_TOPICS }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SettingsCard("🔔 알림") {
            AlarmRow(
                label = "매일 아침 브리핑",
                hint = "날씨와 시세를 요약해 보냅니다",
                alarm = settings.morning,
                onChange = { onChange(settings.copy(morning = it)) },
            )
            HorizontalDivider(Modifier.padding(vertical = 6.dp))
            AlarmRow(
                label = "로또 당첨번호",
                hint = "토요일 · 추첨은 20:35",
                alarm = settings.lotto,
                onChange = { onChange(settings.copy(lotto = it)) },
            )
            HorizontalDivider(Modifier.padding(vertical = 6.dp))
            AlarmRow(
                label = "연금복권 당첨번호",
                hint = "목요일 · 추첨은 19:05",
                alarm = settings.pension,
                onChange = { onChange(settings.copy(pension = it)) },
            )
            HorizontalDivider(Modifier.padding(vertical = 6.dp))
            AlarmRow(
                label = "관심 키워드 뉴스",
                hint = "등록한 단어가 오늘 기사에 나오면 알립니다",
                alarm = settings.keyword,
                onChange = { onChange(settings.copy(keyword = it)) },
            )

            if (settings.keyword.enabled) {
                Spacer(Modifier.height(8.dp))
                KeywordEditor(
                    keywords = settings.keywords,
                    onChange = { onChange(settings.copy(keywords = it)) },
                )
            }
        }

        SettingsCard(
            title = "🤖 AI 오늘의 브리핑",
            action = {
                TextButton(onClick = { showTopicPicker = true }) {
                    Text("주제 선택")
                }
            },
        ) {
            val chosen = catalog.filter { it.id in settings.topics }
            Text(
                text = if (chosen.isEmpty()) "선택한 주제가 없습니다"
                else chosen.joinToString(" · ") { it.name },
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "최대 ${MAX_TOPICS}개까지 고를 수 있습니다 (${chosen.size}/$MAX_TOPICS)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SettingsCard("📍 날씨") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("현재 위치 사용", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        if (settings.useLocation) "위치 권한이 있으면 현재 동네 기준으로 보여줍니다"
                        else "서울 기준으로 보여줍니다",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = settings.useLocation,
                    onCheckedChange = { onChange(settings.copy(useLocation = it)) },
                )
            }
        }

        Text(
            "알림이 오지 않으면 휴대폰 설정 → 배터리에서 이 앱을 '제한 없음'으로 바꿔주세요.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
    }

    if (showTopicPicker) {
        TopicPickerDialog(
            catalog = catalog,
            selected = settings.topics,
            onConfirm = { onChange(settings.copy(topics = it)) },
            onDismiss = { showTopicPicker = false },
        )
    }
}

/** 주제가 20개가 넘어 설정 화면에 다 펼치면 길어지므로 팝업으로 뺀다. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TopicPickerDialog(
    catalog: List<TopicOption>,
    selected: Set<String>,
    onConfirm: (Set<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var picked by remember { mutableStateOf(selected) }
    val full = picked.size >= MAX_TOPICS

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("브리핑 주제 (${picked.size}/$MAX_TOPICS)") },
        text = {
            // 한 줄에 하나씩 놓으면 23개가 너무 길게 늘어져 칩으로 채운다
            FlowRow(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                catalog.forEach { option ->
                    val checked = option.id in picked
                    // 4개를 채우면 나머지는 고를 수 없게 막는다
                    FilterChip(
                        selected = checked,
                        enabled = checked || !full,
                        onClick = {
                            picked = if (checked) picked - option.id else picked + option.id
                        },
                        label = { Text(option.name) },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(picked)
                onDismiss()
            }) { Text("확인") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}

@Composable
private fun AlarmRow(label: String, hint: String, alarm: Alarm, onChange: (Alarm) -> Unit) {
    val context = LocalContext.current

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(
                hint,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (alarm.enabled) {
            AssistChip(
                onClick = {
                    TimePickerDialog(
                        context,
                        { _, hour, minute -> onChange(alarm.copy(hour = hour, minute = minute)) },
                        alarm.hour,
                        alarm.minute,
                        true,
                    ).show()
                },
                label = { Text(alarm.timeLabel) },
            )
            Spacer(Modifier.size(8.dp))
        }

        Switch(checked = alarm.enabled, onCheckedChange = { onChange(alarm.copy(enabled = it)) })
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun KeywordEditor(keywords: List<String>, onChange: (List<String>) -> Unit) {
    var input by remember { mutableStateOf("") }

    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            label = { Text("키워드 (예: 삼성전자)") },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        IconButton(
            onClick = {
                val word = input.trim()
                if (word.isNotEmpty() && word !in keywords) onChange(keywords + word)
                input = ""
            },
            enabled = input.isNotBlank(),
        ) {
            Icon(Icons.Filled.Add, contentDescription = "키워드 추가")
        }
    }

    if (keywords.isNotEmpty()) {
        Spacer(Modifier.height(6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            keywords.forEach { keyword ->
                AssistChip(
                    onClick = { onChange(keywords - keyword) },
                    label = { Text(keyword) },
                    trailingIcon = {
                        Icon(Icons.Filled.Close, contentDescription = "삭제", modifier = Modifier.size(16.dp))
                    },
                )
            }
        }
    }
}

@Composable
private fun SettingsCard(
    title: String,
    action: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                action?.invoke()
            }
            Spacer(Modifier.height(6.dp))
            content()
        }
    }
}
