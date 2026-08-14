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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
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

/** 설정에서 고를 수 있는 브리핑 주제. 백엔드 topics.py 의 id 와 맞춘다. */
private val TOPIC_OPTIONS = listOf(
    "stock" to "증시",
    "semiconductor" to "반도체",
    "fx" to "환율",
    "rate" to "금리",
    "ai" to "AI·인공지능",
    "crypto" to "가상자산",
    "realestate" to "부동산",
    "oil" to "유가·에너지",
    "trade" to "수출·관세",
    "price" to "물가·고용",
    "politics" to "정치",
    "world" to "국제정세",
    "industry" to "산업·기업",
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(settings: AppSettings, onChange: (AppSettings) -> Unit) {
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

        SettingsCard("🤖 AI 오늘의 브리핑") {
            Text(
                "홈 화면 브리핑에 넣을 주제를 고르세요.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TOPIC_OPTIONS.forEach { (id, name) ->
                    val checked = id in settings.topics
                    FilterChip(
                        selected = checked,
                        onClick = {
                            val next = if (checked) settings.topics - id else settings.topics + id
                            onChange(settings.copy(topics = next))
                        },
                        label = { Text(name) },
                    )
                }
            }
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
private fun SettingsCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}
