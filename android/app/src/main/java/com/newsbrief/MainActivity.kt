package com.newsbrief

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import com.newsbrief.data.Story
import com.newsbrief.data.contains
import com.newsbrief.data.needsNotificationPermission
import com.newsbrief.ui.FavoritesScreen
import com.newsbrief.ui.FolderPickerSheet
import com.newsbrief.ui.HomeScreen
import com.newsbrief.ui.LotteryScreen
import com.newsbrief.ui.MarketScreen
import com.newsbrief.ui.MyDashboardTheme
import com.newsbrief.ui.NewsScreen
import com.newsbrief.ui.SettingsScreen
import java.net.URLEncoder

private const val UPBIT_PACKAGE = "com.dunamu.exchange"

class MainActivity : ComponentActivity() {

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestStartupPermissions()

        setContent {
            MyDashboardTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    AppScreen(
                        onOpenLink = ::openLink,
                        onOpenUpbit = ::openUpbit,
                        onSearch = ::openNaverSearch,
                    )
                }
            }
        }
    }

    /** 날씨(위치)와 알림 권한을 첫 실행 때 한 번 묻는다. 거부해도 앱은 그대로 동작한다. */
    private fun requestStartupPermissions() {
        val wanted = buildList {
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            if (needsNotificationPermission) add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permissionLauncher.launch(wanted.toTypedArray())
    }

    private fun openLink(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, "열 수 있는 앱이 없습니다", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openNaverSearch(query: String) {
        val encoded = URLEncoder.encode(query, "UTF-8")
        openLink("https://search.naver.com/search.naver?query=$encoded")
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

private enum class Tab(val label: String, val title: String, val icon: ImageVector) {
    Home("홈", "My Dashboard", Icons.Filled.Home),
    News("뉴스", "오늘의 뉴스", Icons.AutoMirrored.Filled.Article),
    Favorites("즐겨찾기", "즐겨찾기", Icons.Filled.Star),
    Lottery("복권", "복권", Icons.Filled.ConfirmationNumber),
    Market("시세", "시세", Icons.AutoMirrored.Filled.ShowChart),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppScreen(
    onOpenLink: (String) -> Unit,
    onOpenUpbit: () -> Unit,
    onSearch: (String) -> Unit,
    viewModel: BriefViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()
    var selected by rememberSaveable { mutableIntStateOf(0) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    // 별을 눌러 폴더를 고르는 중인 기사
    var pendingFavorite by remember { mutableStateOf<Pair<Story, String>?>(null) }

    val tabs = remember { Tab.entries.toList() }
    val current = tabs[selected]

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (showSettings) "설정" else current.title) },
                navigationIcon = {
                    if (showSettings) {
                        IconButton(onClick = { showSettings = false }) {
                            Icon(Icons.Filled.ArrowBack, contentDescription = "뒤로")
                        }
                    }
                },
                actions = {
                    if (!showSettings) {
                        if (current == Tab.Home || current == Tab.News) {
                            IconButton(
                                onClick = {
                                    if (current == Tab.Home) viewModel.refreshAll() else viewModel.refreshBrief()
                                },
                                enabled = !state.briefLoading,
                            ) {
                                Icon(Icons.Filled.Refresh, contentDescription = "새로고침")
                            }
                        }
                        IconButton(onClick = { showSettings = true }) {
                            Icon(Icons.Filled.Settings, contentDescription = "설정")
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (!showSettings) {
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
            }
        },
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding)) {
            if (showSettings) {
                SettingsScreen(settings = state.settings, onChange = viewModel::updateSettings)
                return@Box
            }

            when (current) {
                Tab.Home -> HomeScreen(
                    brief = state.brief,
                    quotes = state.quotes,
                    weather = state.weather,
                    weatherLoading = state.weatherLoading,
                    myNumbers = state.myNumbers,
                    settings = state.settings,
                    loading = state.briefLoading,
                    onOpenLink = onOpenLink,
                    onSearch = onSearch,
                    onOpenStory = { onOpenLink(it.link) },
                )

                Tab.News -> NewsScreen(
                    categories = state.brief?.categories.orEmpty(),
                    generatedAt = state.brief?.generatedAt.orEmpty(),
                    loading = state.briefLoading,
                    error = state.briefError,
                    isFavorite = { link -> state.favorites.contains(link) },
                    onOpenLink = onOpenLink,
                    onToggleFavorite = { story, categoryName ->
                        if (state.favorites.contains(story.link)) {
                            viewModel.removeFavorite(story.link)
                        } else {
                            pendingFavorite = story to categoryName
                        }
                    },
                )

                Tab.Favorites -> FavoritesScreen(
                    favorites = state.favorites,
                    onOpenLink = onOpenLink,
                    onRemove = viewModel::removeFavorite,
                    onDeleteFolder = viewModel::deleteFolder,
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
                    onSearch = onSearch,
                )
            }
        }
    }

    pendingFavorite?.let { (story, categoryName) ->
        FolderPickerSheet(
            folders = state.favorites.folders,
            onPick = { folder ->
                viewModel.addFavorite(story, categoryName, folder)
                pendingFavorite = null
            },
            onDismiss = { pendingFavorite = null },
        )
    }
}
