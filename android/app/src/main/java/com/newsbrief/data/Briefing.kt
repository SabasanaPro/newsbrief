package com.newsbrief.data

/** AI 브리핑에 넣을 수 있는 주제 개수. 너무 많으면 문단이 장황해진다. */
const val MAX_TOPICS = 4

/**
 * 주제별 구를 한 문단으로 엮는다.
 *
 * 기사 문장을 그대로 늘어놓으면 목록처럼 읽혀서, 짧은 구를 문장 틀에 끼워
 * 하루를 요약하는 말투로 만든다. 홈 화면과 위젯이 같은 문장을 쓰도록 여기 둔다.
 */
fun composeBriefing(phrases: List<String>): String {
    // 두 주제가 같은 기사를 대표로 골랐을 수 있어 같은 구는 한 번만 쓴다
    val items = phrases.filter { it.isNotBlank() }.distinct()
    return when (items.size) {
        0 -> ""
        1 -> "오늘은 ${items[0]}${subjectParticle(items[0])} 눈에 띕니다."
        2 -> "오늘은 ${items[0]}${andParticle(items[0])} ${items[1]}${subjectParticle(items[1])} 눈에 띕니다."
        3 -> "오늘은 ${items[0]}${andParticle(items[0])} ${items[1]}${subjectParticle(items[1])} 눈에 띄고, " +
            "${items[2]} 소식도 이어졌습니다."

        else -> "오늘은 ${items[0]}${andParticle(items[0])} ${items[1]}${subjectParticle(items[1])} 눈에 띕니다. " +
            "${items[2]}, ${items[3]} 소식도 함께 전해졌습니다."
    }
}

/** 설정에서 고른 주제를 우선 쓰되, 그날 잡힌 게 없으면 화제가 큰 주제로 대신한다. */
fun pickBriefingTopics(topics: List<Topic>, chosenIds: Set<String>): List<Topic> {
    val chosen = topics.filter { it.id in chosenIds }.take(MAX_TOPICS)
    return chosen.ifEmpty { topics.take(MAX_TOPICS) }
}

/** 받침이 있으면 '과·이', 없으면 '와·가'. 한글이 아니면 받침 없는 쪽으로 둔다. */
private fun hasFinalConsonant(text: String): Boolean {
    val last = text.trimEnd(' ', '…', '.', '"', '\'', '”', '’', ')', ']').lastOrNull() ?: return false
    if (last !in '가'..'힣') return false
    return (last.code - 0xAC00) % 28 != 0
}

private fun andParticle(text: String) = if (hasFinalConsonant(text)) "과" else "와"

private fun subjectParticle(text: String) = if (hasFinalConsonant(text)) "이" else "가"
