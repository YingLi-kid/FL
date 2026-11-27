package com.example.myapplication.ui

import com.example.myapplication.R
import com.example.myapplication.appText

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
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
import com.example.myapplication.AppLanguage
import com.example.myapplication.data.SpeechMode
import com.example.myapplication.data.VoiceProfile
import com.example.myapplication.service.SpeechBackend
import com.example.myapplication.service.LocalSpeechBackend
import com.example.myapplication.service.CloudSpeechBackend
import com.example.myapplication.service.localSpeechBackends
import com.example.myapplication.service.cloudSpeechBackends
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import com.example.myapplication.data.VoiceRepository
import com.example.myapplication.data.rememberProviderRepository
import com.example.myapplication.ui.theme.MyApplicationTheme
import com.example.myapplication.ui.theme.TechCyan
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// 顶层页面：主页（两Tab）/ 设置 / 服务商管理
private enum class Screen { HOME, SETTINGS, PROVIDERS, MODELS, HISTORY }

// 底部两个 Tab：0 克隆音色（默认）、1 文字转语音
private data class BottomTab(val icon: ImageVector, val description: String)

private val bottomTabs get() = listOf(
    BottomTab(Icons.Rounded.Mic, appText(R.string.clone_tab)),
    BottomTab(Icons.Rounded.RecordVoiceOver, appText(R.string.tts_tab))
)

@Composable
internal fun HomeScreen(
    generation: SpeechGeneration = rememberSpeechGeneration(),
    language: AppLanguage = AppLanguage.CHINESE,
    onLanguageChange: ((AppLanguage) -> Unit)? = null
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val showMessage: (String) -> Unit = { msg ->
        scope.launch { snackbarHostState.showSnackbar(msg) }
    }

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var ttsPreselectedVoiceId by rememberSaveable { mutableStateOf<String?>(null) }
    val screenState = rememberSaveableStateHolder()
    var currentScreen by rememberSaveable { mutableStateOf(Screen.HOME) }
    var providerReturn by rememberSaveable { mutableStateOf(Screen.SETTINGS) }
    var initialProviderId by rememberSaveable { mutableStateOf<String?>(null) }
    var initialModelId by rememberSaveable { mutableStateOf<String?>(null) }

    fun openProviders(from: Screen, providerId: String? = null) {
        providerReturn = from
        initialProviderId = providerId
        currentScreen = Screen.PROVIDERS
    }

    fun openModels(from: Screen, modelId: String? = null) {
        providerReturn = from
        initialModelId = modelId
        currentScreen = Screen.MODELS
    }

    // 已克隆音色的持久化存储（重启不丢失，支持多次克隆累积）
    val context = LocalContext.current
    val generationState by generation.state.collectAsState()
    val voiceRepository = remember(context) { VoiceRepository(context.applicationContext) }
    val voiceProfiles by voiceRepository.profiles.collectAsState(initial = emptyList())

    // 服务商配置状态（本地持久化）
    val providerRepository = rememberProviderRepository()
    val providerConfigs by providerRepository.configs.collectAsState(initial = emptyMap())

    val storedMode by providerRepository.speechMode.collectAsState(initial = null)
    val mode = if (localSpeechBackends.isNotEmpty()) storedMode ?: SpeechMode.CLOUD else SpeechMode.CLOUD
    val selections by providerRepository.backendSelections.collectAsState(initial = emptyMap())
    val backends: List<SpeechBackend> = if (mode == SpeechMode.LOCAL) localSpeechBackends else cloudSpeechBackends
    val backend = backends.firstOrNull { it.id == selections[mode] } ?: backends.first()
    val localBackend = backend as? LocalSpeechBackend
    val cloudBackend = backend as? CloudSpeechBackend
    val modelInstalled = remember(context, currentScreen, backend) { localBackend?.isInstalled(context) ?: true }
    val localSupported = localBackend?.isSupported() ?: true
    val activeVoices = voiceProfiles.filter(backend::acceptsVoice)
    val credentials = providerConfigs[backend.id]?.credentials.orEmpty()
    val pendingIds = activeVoices.filter {
        it.status == "DEPLOYING" && !it.speakerId.isNullOrBlank()
    }.map { it.id }
    LaunchedEffect(backend, storedMode, credentials, pendingIds) {
        val service = cloudBackend ?: return@LaunchedEffect
        if ((localSpeechBackends.isNotEmpty() && storedMode == null) || !service.credentialsReady(credentials)) {
            return@LaunchedEffect
        }
        while (pendingIds.isNotEmpty()) {
            for (id in pendingIds) {
                val profile = voiceProfiles.firstOrNull { it.id == id } ?: continue
                val status = service.refreshStatus(credentials, requireNotNull(profile.speakerId))
                if (status == "OK" || status == "UNDEPLOYED") {
                    voiceRepository.updateStatus(id, status)
                    showMessage(if (status == "OK") appText(R.string.cloud_voice_ready, profile.name) else appText(R.string.cloud_voice_review_failed, profile.name))
                }
            }
            delay(10000)
        }
    }

    // 子页面打开时拦截返回键/手势，逐级返回
    BackHandler(enabled = currentScreen != Screen.HOME) {
        currentScreen = when (currentScreen) {
            Screen.PROVIDERS, Screen.MODELS -> providerReturn
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
            val screenKey = when (screen) {
                Screen.PROVIDERS -> "providers:$initialProviderId"
                Screen.MODELS -> "models:$initialModelId"
                else -> screen.name
            }
            screenState.SaveableStateProvider(screenKey) {
                when (screen) {
                    Screen.HOME -> MainScaffold(
                        generation = generation,
                        isHomeVisible = currentScreen == Screen.HOME,
                        snackbarHostState = snackbarHostState,
                        showMessage = showMessage,
                        credentials = credentials,
                        selectedTab = selectedTab,
                        onSelectTab = { selectedTab = it },
                        voices = activeVoices,
                        mode = mode,
                        backend = backend,
                        backends = backends,
                        onSelectBackend = { id ->
                            ttsPreselectedVoiceId = null
                            scope.launch { providerRepository.setSelectedBackend(mode, id) }
                        },
                        modelInstalled = modelInstalled,
                        localSupported = localSupported,
                        onSelectMode = { next ->
                            if (next != mode) {
                                ttsPreselectedVoiceId = null
                                scope.launch { providerRepository.setSpeechMode(next) }
                            }
                        },
                        onOpenModel = { openModels(Screen.HOME, localBackend?.id) },
                        onConfigureCloud = { openProviders(Screen.HOME, cloudBackend?.id) },
                        onManageCloud = { openProviders(Screen.HOME) },
                        onOpenHistory = { currentScreen = Screen.HISTORY },
                        onAddVoice = { v ->
                            voiceRepository.upsert(v)
                        },
                        onRemoveVoice = { id ->
                            val profile = voiceProfiles.firstOrNull { it.id == id }
                            scope.launch {
                                voiceRepository.remove(id)
                                val model = localSpeechBackends.firstOrNull { it.id == profile?.backendId }
                                if (model != null) withContext(Dispatchers.IO) {
                                    runCatching { model.deleteReference(context, id) }
                                }
                            }
                        },
                        onRenameVoice = { id, name -> scope.launch { voiceRepository.rename(id, name) } },
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
                        onOpenProviderManager = { openProviders(Screen.SETTINGS) },
                        onOpenLocalModels = if (localSpeechBackends.isNotEmpty()) ({ openModels(Screen.SETTINGS) }) else null,
                        generation = generation,
                        language = language,
                        onLanguageChange = onLanguageChange,
                        onBack = { currentScreen = Screen.HOME }
                    )
                    Screen.PROVIDERS -> ProviderManagerScreen(
                        configs = providerConfigs,
                        onSaveCredential = { id, field, value ->
                            scope.launch { providerRepository.setCredential(id, field, value) }
                        },
                        onBack = { currentScreen = providerReturn },
                        initialProviderId = initialProviderId
                    )
                    Screen.MODELS -> LocalModelsScreen(initialModelId) { currentScreen = providerReturn }
                    Screen.HISTORY -> SpeechHistoryScreen(generationState.results) { currentScreen = Screen.HOME }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScaffold(
    generation: SpeechGeneration,
    isHomeVisible: Boolean,
    snackbarHostState: SnackbarHostState,
    showMessage: (String) -> Unit,
    credentials: Map<String, String>,
    selectedTab: Int,
    onSelectTab: (Int) -> Unit,
    voices: List<VoiceProfile>,
    mode: SpeechMode,
    backend: SpeechBackend,
    backends: List<SpeechBackend>,
    onSelectBackend: (String) -> Unit,
    modelInstalled: Boolean,
    localSupported: Boolean,
    onSelectMode: (SpeechMode) -> Unit,
    onOpenModel: () -> Unit,
    onConfigureCloud: () -> Unit,
    onManageCloud: () -> Unit,
    onOpenHistory: () -> Unit,
    onAddVoice: suspend (VoiceProfile) -> Unit,
    onRemoveVoice: (String) -> Unit,
    onUseVoice: (String) -> Unit,
    onRenameVoice: (String, String) -> Unit,
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
                        text = appText(R.string.app_name),
                        style = MaterialTheme.typography.titleLarge.copy(brush = TechAccentBrush)
                    )
                },
                actions = {
                    IconButton(onClick = onOpenHistory) {
                        Icon(Icons.Rounded.History, contentDescription = appText(R.string.generation_history))
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            imageVector = Icons.Rounded.Settings,
                            contentDescription = appText(R.string.settings),
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (localSpeechBackends.isNotEmpty()) {
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                ) {
                    val modes = listOf(SpeechMode.LOCAL to appText(R.string.local_offline), SpeechMode.CLOUD to appText(R.string.online))
                    modes.forEachIndexed { index, (option, label) ->
                        SegmentedButton(
                            selected = mode == option,
                            onClick = { onSelectMode(option) },
                            shape = SegmentedButtonDefaults.itemShape(index, modes.size),
                            label = { Text(label) }
                        )
                    }
                }
            }
            BackendSelector(
                label = if (mode == SpeechMode.LOCAL) appText(R.string.local_models) else appText(R.string.cloud_providers),
                backend = backend, choices = backends, onSelect = onSelectBackend,
                manageLabel = if (mode == SpeechMode.LOCAL) appText(R.string.manage_local_models) else appText(R.string.manage_cloud_providers),
                onManage = if (mode == SpeechMode.LOCAL) onOpenModel else onManageCloud
            )
            Crossfade(
                targetState = selectedTab,
                animationSpec = tween(220),
                label = "tabSwitch"
            ) { tab ->
                tabState.SaveableStateProvider("${backend.id}:$tab") {
                    when {
                        mode == SpeechMode.LOCAL && (!modelInstalled || !localSupported) -> {
                            GlassCard(modifier = Modifier.padding(16.dp)) {
                                Text(if (!localSupported) appText(R.string.offline_android_requirement) else appText(R.string.selected_model_missing))
                                OutlinedButton(onClick = onOpenModel) {
                                    Text(if (modelInstalled) appText(R.string.manage_model) else appText(R.string.download_or_import_model))
                                }
                            }
                        }
                        tab == 0 && !backend.supportsClone -> {
                            GlassCard(modifier = Modifier.padding(16.dp)) { Text(appText(R.string.provider_clone_unsupported)) }
                        }
                        tab == 0 -> CloneScreen(
                            voices = voices,
                            onAddVoice = onAddVoice,
                            onRemoveVoice = onRemoveVoice,
                            onUseVoice = onUseVoice,
                            onShowMessage = showMessage,
                            providerApiKey = null,
                            backend = backend,
                            credentials = credentials,
                            onConfigureCloud = onConfigureCloud,
                            onRenameVoice = onRenameVoice
                        )
                        else -> TtsScreen(
                            generation = generation,
                            isVisible = isHomeVisible && selectedTab == 1,
                            voices = voices,
                            preselectedVoiceId = ttsPreselectedVoiceId,
                            onPreselectedConsumed = onPreselectedConsumed,
                            onShowMessage = showMessage,
                            providerApiKey = null,
                            backend = backend,
                            credentials = credentials,
                            onConfigureCloud = onConfigureCloud,
                            onCreateVoice = { onSelectTab(0) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BackendSelector(
    label: String, backend: SpeechBackend, choices: List<SpeechBackend>,
    onSelect: (String) -> Unit, manageLabel: String, onManage: () -> Unit
) {
    var expanded by remember(backend is LocalSpeechBackend) { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text("$label · ${backend.name} ▾")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            choices.forEach { choice ->
                DropdownMenuItem(
                    text = { Text(choice.name) },
                    onClick = { expanded = false; onSelect(choice.id) }
                )
            }
            DropdownMenuItem(text = { Text(manageLabel) }, onClick = { expanded = false; onManage() })
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
