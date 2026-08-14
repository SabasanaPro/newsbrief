package com.newsbrief

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import com.newsbrief.ui.LotteryScreen
import com.newsbrief.ui.MarketScreen
import com.newsbrief.ui.NewsBriefTheme
import com.newsbrief.ui.NewsScreen

private const val UPBIT_PACKAGE = "com.dunamu.exchange"

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NewsBriefTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    AppScreen(
                        onOpenLink = ::openLink,
                        onOpenUpbit = ::openUpbit,
                    )
                }
            }
        }
    }

    private fun openLink(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, "열 수 있는 앱이 없습니다", Toast.LENGTH_SHORT).show()
        }
    }

    /** 업비트 앱이 있으면 실행하고, 없으면 스토어로 보낸다. */
    private fun openUpbit() {
        packageManager.getLaunchIntentForPackage(UPBIT_PACKAGE)?.let {
            startActivity(it)
            return
        }
        openLink("https://play.google.com/store/apps/details?id=$UPBIT_PACKAGE")
    }
}

private enum class Tab(val label: String, val icon: ImageVector) {
    News("뉴스", Icons.AutoMirrored.Filled.Article),
    Lottery("복권", Icons.Filled.ConfirmationNumber),
    Market("시세", Icons.AutoMirrored.Filled.ShowChart),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppScreen(
    onOpenLink: (String) -> Unit,
    onOpenUpbit: () -> Unit,
    viewModel: BriefViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()
    var selected by rememberSaveable { mutableIntStateOf(0) }
    val tabs = remember { Tab.entries.toList() }
    val current = tabs[selected]

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (current == Tab.News) "오늘의 브리핑" else current.label) },
                actions = {
                    if (current == Tab.News) {
                        IconButton(
                            onClick = viewModel::refreshBrief,
                            enabled = !state.briefLoading,
                        ) {
                            Icon(Icons.Filled.Refresh, contentDescription = "새로고침")
                        }
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = selected == index,
                        onClick = { selected = index },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding)) {
            when (current) {
                Tab.News -> NewsScreen(
                    categories = state.brief?.categories.orEmpty(),
                    generatedAt = state.brief?.generatedAt.orEmpty(),
                    loading = state.briefLoading,
                    error = state.briefError,
                    onOpenLink = onOpenLink,
                )

                Tab.Lottery -> LotteryScreen(
                    lotto = state.brief?.lottery?.lotto,
                    pension = state.brief?.lottery?.pension,
                    myNumbers = state.myNumbers,
                    onMyNumbersChange = viewModel::setMyNumbers,
                    onOpenLink = onOpenLink,
                )

                Tab.Market -> MarketScreen(
                    quotes = state.quotes,
                    loading = state.quotesLoading,
                    error = state.quotesError,
                    onRefresh = viewModel::refreshQuotes,
                    onOpenUpbit = onOpenUpbit,
                )
            }
        }
    }
}
