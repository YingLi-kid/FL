package com.example.myapplication.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import com.example.myapplication.service.AliyunVoice
import kotlinx.coroutines.delay
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.myapplication.data.VoiceProfile
import com.example.myapplication.data.VoiceRepository
import com.example.myapplication.data.rememberProviderRepository
import com.example.myapplication.ui.theme.MyApplicationTheme
import com.example.myapplication.ui.theme.TechCyan
import kotlinx.coroutines.launch

// 顶层页面：主页（两Tab）/ 设置 / 服务商管理
private enum class Screen { HOME, SETTINGS, PROVIDERS }

// 底部两个 Tab：0 克隆音色（默认）、1 文字转语音
private data class BottomTab(val icon: ImageVector, val description: String)

private val bottomTabs = listOf(
    BottomTab(Icons.Rounded.Mic, "克隆音色"),
    BottomTab(Icons.Rounded.RecordVoiceOver, "文字转语音")
)

@Composable
fun HomeScreen() {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val showMessage: (String) -> Unit = { msg ->
        scope.launch { snackbarHostState.showSnackbar(msg) }
    }

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var ttsPreselectedVoiceId by rememberSaveable { mutableStateOf<String?>(null) }
    val screenState = rememberSaveableStateHolder()
    var currentScreen by rememberSaveable { mutableStateOf(Screen.HOME) }

    // 已克隆音色的持久化存储（重启不丢失，支持多次克隆累积）
    val context = LocalContext.current
    val voiceRepository = remember(context) { VoiceRepository(context.applicationContext) }
    val voiceProfiles by voiceRepository.profiles.collectAsState(initial = emptyList())

    // 服务商配置状态（本地持久化）
    val providerRepository = rememberProviderRepository()
    val providerConfigs by providerRepository.configs.collectAsState(initial = emptyMap())

    // 阿里云百炼启用时的 API Key（真实 TTS/声音复刻用）
    val aliyunApiKey = providerConfigs["aliyun"]
        ?.takeIf { it.enabled }
        ?.credentials?.get("apiKey")

    val pendingIds = voiceProfiles.filter { it.status == "DEPLOYING" && !it.speakerId.isNullOrBlank() }.map { it.id }
    LaunchedEffect(aliyunApiKey, pendingIds) {
        val key = aliyunApiKey?.takeIf { it.isNotBlank() } ?: return@LaunchedEffect
        val service = AliyunVoice()
        while (pendingIds.isNotEmpty()) {
            for (id in pendingIds) {
                val profile = voiceProfiles.firstOrNull { it.id == id } ?: continue
                val response = service.getVoiceStatus(key, profile.speakerId!!)
                if (response is AliyunVoice.Result.Success && response.status in listOf("OK", "UNDEPLOYED")) {
                    voiceRepository.updateStatus(id, response.status)
                    showMessage(if (response.status == "OK") "音色「${profile.name}」已可使用" else "音色「${profile.name}」未通过审核，请更换样本")
                }
            }
            delay(10000)
        }
    }

    // 子页面打开时拦截返回键/手势，逐级返回
    BackHandler(enabled = currentScreen != Screen.HOME) {
        currentScreen = when (currentScreen) {
            Screen.PROVIDERS -> Screen.SETTINGS
            else -> Screen.HOME
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        TechBackground()

        AnimatedContent(
            targetState = currentScreen,
            modifier = Modifier.fillMaxSize(),
            transitionSpec = {
                slideInHorizontally(animationSpec = tween(220)) { fullWidth -> fullWidth } togetherWith
                    slideOutHorizontally(animationSpec = tween(220)) { fullWidth -> -fullWidth / 4 }
            },
            label = "screenNavigation"
        ) { screen ->
            screenState.SaveableStateProvider(screen) {
                when (screen) {
                    Screen.HOME -> MainScaffold(
                        snackbarHostState = snackbarHostState,
                        showMessage = showMessage,
                        providerApiKey = aliyunApiKey,
                        selectedTab = selectedTab,
                        onSelectTab = { selectedTab = it },
                        voices = voiceProfiles,
                        onAddVoice = { v ->
                            voiceRepository.upsert(v)
                        },
                        onRemoveVoice = { id ->
                            scope.launch { voiceRepository.remove(id) }
                        },
                        onUseVoice = { id ->
                            ttsPreselectedVoiceId = id
                            selectedTab = 1
                        },
                        ttsPreselectedVoiceId = ttsPreselectedVoiceId,
                        onPreselectedConsumed = { ttsPreselectedVoiceId = null },
                        onOpenSettings = { currentScreen = Screen.SETTINGS }
                    )
                    Screen.SETTINGS -> SettingsScreen(
                        snackbarHostState = snackbarHostState,
                        onOpenProviderManager = { currentScreen = Screen.PROVIDERS },
                        onBack = { currentScreen = Screen.HOME }
                    )
                    Screen.PROVIDERS -> ProviderManagerScreen(
                        configs = providerConfigs,
                        onToggleEnabled = { id, enabled ->
                            scope.launch { providerRepository.setEnabled(id, enabled) }
                        },
                        onSaveCredential = { id, field, value ->
                            scope.launch { providerRepository.setCredential(id, field, value) }
                        },
                        onBack = { currentScreen = Screen.SETTINGS }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScaffold(
    snackbarHostState: SnackbarHostState,
    showMessage: (String) -> Unit,
    providerApiKey: String?,
    selectedTab: Int,
    onSelectTab: (Int) -> Unit,
    voices: List<VoiceProfile>,
    onAddVoice: suspend (VoiceProfile) -> Unit,
    onRemoveVoice: (String) -> Unit,
    onUseVoice: (String) -> Unit,
    ttsPreselectedVoiceId: String?,
    onPreselectedConsumed: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val tabState = rememberSaveableStateHolder()
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "音色工坊",
                        style = MaterialTheme.typography.titleLarge.copy(brush = TechAccentBrush)
                    )
                },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            imageVector = Icons.Rounded.Settings,
                            contentDescription = "设置",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.55f),
                tonalElevation = 0.dp
            ) {
                bottomTabs.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { onSelectTab(index) },
                        icon = { Icon(tab.icon, contentDescription = tab.description) },
                        label = { Text(tab.description) },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = TechCyan.copy(alpha = 0.18f),
                            selectedIconColor = TechCyan,
                            selectedTextColor = TechCyan,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Crossfade(
                targetState = selectedTab,
                animationSpec = tween(220),
                label = "tabSwitch"
            ) { tab ->
                tabState.SaveableStateProvider(tab) {
                    when (tab) {
                        0 -> CloneScreen(
                            voices = voices,
                            onAddVoice = onAddVoice,
                            onRemoveVoice = onRemoveVoice,
                            onUseVoice = onUseVoice,
                            onShowMessage = showMessage,
                            providerApiKey = providerApiKey
                        )
                        1 -> TtsScreen(
                            voices = voices,
                            preselectedVoiceId = ttsPreselectedVoiceId,
                            onPreselectedConsumed = onPreselectedConsumed,
                            onShowMessage = showMessage,
                            providerApiKey = providerApiKey
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 412, heightDp = 892)
@Composable
private fun HomeScreenPreview() {
    MyApplicationTheme {
        HomeScreen()
    }
}
