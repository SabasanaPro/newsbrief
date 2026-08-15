package com.newsbrief.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.newsbrief.data.FuelPrices
import com.newsbrief.data.FuelType
import com.newsbrief.data.RADIUS_OPTIONS
import com.newsbrief.data.Station
import java.text.DecimalFormat

private val won = DecimalFormat("#,##0")
private val won2 = DecimalFormat("#,##0.00")

@Composable
fun FuelScreen(
    prices: FuelPrices,
    loading: Boolean,
    onRefresh: () -> Unit,
    onOpenMap: (name: String, address: String) -> Unit,
) {
    if (!prices.isUsable) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (loading) CircularProgressIndicator()
            else Text("유가를 불러오지 못했습니다", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    var radius by rememberSaveable { mutableStateOf(RADIUS_OPTIONS.first()) }
    var fuel by rememberSaveable { mutableStateOf(FuelType.Gasoline) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text("평균 가격", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        ) {
            Column(Modifier.padding(vertical = 4.dp)) {
                FuelType.entries.forEachIndexed { index, type ->
                    val national = prices.nationalAverage[type.code]
                    val area = prices.areaAverage[type.code]
                    if (national == null) return@forEachIndexed

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(type.label, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.weight(1f))
                        if (area != null) {
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    "${won2.format(area)}원",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    "전국 ${won2.format(national)}원",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        } else {
                            Text(
                                "${won2.format(national)}원",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                    if (index != FuelType.entries.lastIndex) {
                        HorizontalDivider(Modifier.padding(horizontal = 14.dp))
                    }
                }
            }
        }

        Spacer(Modifier.height(6.dp))
        Text(
            if (prices.areaAverage.isEmpty()) "지역을 알 수 없어 전국 평균만 표시합니다"
            else "${prices.areaName} 평균 / 전국 평균",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(20.dp))
        Text(
            if (prices.locatedNearby) "내 주변 최저가" else "${prices.areaName} 최저가",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))

        // 차가 두 대라 유종을 바꿔가며 본다
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(FuelType.Gasoline, FuelType.Premium).forEach { type ->
                FilterChip(
                    selected = fuel == type,
                    onClick = { fuel = type },
                    label = { Text("${type.label} 기준") },
                )
            }
        }
        Spacer(Modifier.height(8.dp))

        if (!prices.locatedNearby) {
            Text(
                "위치를 얻지 못해 ${prices.areaName} 전체 기준으로 보여줍니다. " +
                    "위치 권한과 휴대폰의 위치 기능이 켜져 있는지 확인해주세요.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.height(8.dp))
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RADIUS_OPTIONS.forEach { meters ->
                    FilterChip(
                        selected = radius == meters,
                        onClick = { radius = meters },
                        label = { Text("${meters / 1000}km") },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        val stations = if (prices.locatedNearby) {
            prices.within(radius, fuel).take(8)
        } else {
            prices.nearby.filter { it.priceOf(fuel) != null }.sortedBy { it.priceOf(fuel) }
        }
        if (stations.isEmpty()) {
            Text(
                if (prices.locatedNearby) "반경 ${radius / 1000}km 안에 주유소가 없습니다. 범위를 넓혀보세요."
                else "표시할 주유소가 없습니다",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            ) {
                Column(Modifier.padding(vertical = 4.dp)) {
                    stations.forEachIndexed { index, station ->
                        StationRow(index + 1, station, fuel) { onOpenMap(station.name, station.address) }
                        if (index != stations.lastIndex) {
                            HorizontalDivider(Modifier.padding(horizontal = 14.dp))
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "한국석유공사 오피넷 제공",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onRefresh, enabled = !loading) { Text("지금 갱신") }
        }
        Text(
            "${fuel.label} 값이 싼 순서입니다. 주유소를 누르면 네이버 지도에서 주소로 찾습니다. " +
                "값은 6시간마다 갱신됩니다.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun StationRow(rank: Int, station: Station, fuel: FuelType, onClick: () -> Unit) {
    // 고른 유종을 크게, 나머지 하나를 작게 보여준다
    val main = station.priceOf(fuel)
    val other = if (fuel == FuelType.Premium) FuelType.Gasoline else FuelType.Premium
    val sub = station.priceOf(other)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "$rank",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(18.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(station.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
            Text(
                listOfNotNull(
                    station.brand.takeIf { it.isNotBlank() },
                    station.distance.takeIf { it > 0 }?.let { formatDistance(it) },
                    station.address.takeIf { it.isNotBlank() },
                ).joinToString(" · "),
                maxLines = 1,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                main?.let { "${won.format(it)}원" } ?: "N/A",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
            )
            // 취급하지 않는 유종이 많아 없으면 그대로 표시한다
            Text(
                text = sub?.let { "${other.label} ${won.format(it)}원" } ?: "${other.label} N/A",
                style = MaterialTheme.typography.labelSmall,
                color = if (sub == null) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.primary,
            )
        }
    }
}

private fun formatDistance(meters: Int): String =
    if (meters >= 1000) "%.1fkm".format(meters / 1000.0) else "${meters}m"
