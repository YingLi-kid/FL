@file:Suppress("DEPRECATION") // LocalClipboardManager 在当前 BOM 版本仍为官方兼容 API

package com.example.myapplication.ui

import com.example.myapplication.R
import com.example.myapplication.appText

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Mail
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.myapplication.AppLanguage
import com.example.myapplication.BuildConfig
import com.example.myapplication.service.AppUpdate
import com.example.myapplication.service.AppUpdates
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.CancellationException
import com.example.myapplication.ui.theme.MyApplicationTheme
import com.example.myapplication.ui.theme.TechCyan
import kotlinx.coroutines.launch

private const val OFFICIAL_DOCS_URL = "https://www.fluentic.cn/config/soft/"
private const val DEVELOPER_EMAIL = "administrator@fluentic.cn"

// 各类弹窗文本
private data class InfoDoc(val titleRes: Int, val bodyRes: Int)

private val aboutDoc = InfoDoc(R.string.about_title, R.string.about_body)

private val userAgreement = InfoDoc(R.string.terms_title, R.string.terms_body)

private val privacyPolicy = InfoDoc(R.string.privacy_title, R.string.privacy_body)

private val openSourceLicenses = InfoDoc(R.string.licenses_title, R.string.licenses_body)

/**
 * 设置页：服务商管理、官方使用文档、关于软件、联系开发者、检查更新、清除缓存、用户协议、隐私政策、免责声明、开源许可。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsScreen(
    snackbarHostState: SnackbarHostState,
    onOpenProviderManager: () -> Unit,
    onBack: () -> Unit,
    onOpenLocalModels: (() -> Unit)? = null,
    generation: SpeechGeneration = rememberSpeechGeneration(),
    language: AppLanguage = AppLanguage.CHINESE,
    onLanguageChange: ((AppLanguage) -> Unit)? = null,
    checkForUpdate: suspend () -> AppUpdate? = { AppUpdates.check(BuildConfig.VERSION_NAME) }
) {
    var currentDoc by remember { mutableStateOf<InfoDoc?>(null) }
    var showDisclaimer by remember { mutableStateOf(false) }
    var showLanguages by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val generationState by generation.state.collectAsState()
    var checkingUpdate by remember { mutableStateOf(false) }
    var availableUpdate by remember { mutableStateOf<AppUpdate?>(null) }

    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = appText(R.string.settings),
                        style = MaterialTheme.typography.titleLarge.copy(brush = TechAccentBrush)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = appText(R.string.back),
                            tint = TechCyan
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = TechCyan
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 关于软件头部卡
            GlassCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(58.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(TechCyan.copy(alpha = 0.14f))
                            .border(1.dp, TechCyan.copy(alpha = 0.45f), RoundedCornerShape(18.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.GraphicEq,
                            contentDescription = null,
                            tint = TechCyan,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(appText(R.string.app_name), style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            appText(R.string.app_version, BuildConfig.VERSION_NAME),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            onOpenLocalModels?.let { open ->
                GlassCard(onClick = open) {
                    Text(appText(R.string.local_models), style = MaterialTheme.typography.titleMedium)
                    Text(appText(R.string.local_models_description), style = MaterialTheme.typography.bodySmall)
                }
            }

            // 服务商管理入口
            GlassCard(onClick = onOpenProviderManager) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(TechCyan.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.Tune, contentDescription = null, tint = TechCyan)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(appText(R.string.manage_providers), style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            appText(R.string.providers_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        Icons.Rounded.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 关于与支持
            GlassCard(contentPadding = PaddingValues(0.dp)) {
                if (onLanguageChange != null) {
                    SettingItem(Icons.Rounded.Translate, appText(R.string.app_language), supporting = language.nativeName) {
                        showLanguages = true
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
                SettingItem(
                    icon = Icons.Rounded.Description,
                    title = appText(R.string.official_docs),
                    supporting = appText(R.string.official_docs_description),
                    onClick = {
                        runCatching { uriHandler.openUri(OFFICIAL_DOCS_URL) }.onFailure {
                            scope.launch { snackbarHostState.showSnackbar(appText(R.string.browser_open_docs, OFFICIAL_DOCS_URL)) }
                        }
                    }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingItem(Icons.Rounded.Info, appText(R.string.about_menu)) { currentDoc = aboutDoc }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingItem(
                    icon = Icons.Rounded.Mail,
                    title = appText(R.string.contact_developer),
                    supporting = DEVELOPER_EMAIL,
                    onClick = {
                        clipboardManager.setText(AnnotatedString(DEVELOPER_EMAIL))
                        scope.launch { snackbarHostState.showSnackbar(appText(R.string.contact_copied, DEVELOPER_EMAIL)) }
                    }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingItem(
                    Icons.Rounded.Refresh,
                    appText(R.string.check_updates),
                    supporting = if (checkingUpdate) appText(R.string.update_checking) else null,
                    enabled = !checkingUpdate
                ) {
                    if (!checkingUpdate) {
                        checkingUpdate = true
                        scope.launch {
                            val message = try {
                                availableUpdate = checkForUpdate()
                                if (availableUpdate == null) appText(R.string.update_current, BuildConfig.VERSION_NAME) else null
                            } catch (e: CancellationException) {
                                throw e
                            } catch (_: Exception) {
                                appText(R.string.update_check_error)
                            } finally {
                                checkingUpdate = false
                            }
                            if (message != null) snackbarHostState.showSnackbar(message)
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingItem(
                    Icons.Rounded.Delete,
                    appText(R.string.clear_cache),
                    supporting = if (generationState.progress != null) appText(R.string.clear_cache_wait) else null,
                    enabled = generationState.progress == null && !generationState.clearingCache
                ) {
                    scope.launch {
                        val success = generation.clearCache(context) ?: return@launch
                        snackbarHostState.showSnackbar(if (success) appText(R.string.cache_cleared) else appText(R.string.cache_clear_error))
                    }
                }
            }

            // 协议与声明
            GlassCard(contentPadding = PaddingValues(0.dp)) {
                SettingItem(Icons.Rounded.Description, appText(R.string.terms_title)) { currentDoc = userAgreement }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingItem(Icons.Rounded.Security, appText(R.string.privacy_title)) { currentDoc = privacyPolicy }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingItem(Icons.Rounded.Warning, appText(R.string.disclaimer_title)) { showDisclaimer = true }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingItem(Icons.Rounded.Code, appText(R.string.licenses_title)) { currentDoc = openSourceLicenses }
            }

            // 版权
            Text(
                text = appText(R.string.copyright_notice),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
        }
    }

    if (showLanguages && onLanguageChange != null) {
        AlertDialog(
            onDismissRequest = { showLanguages = false },
            title = { Text(appText(R.string.app_language)) },
            text = {
                Column {
                    AppLanguage.entries.forEach { choice ->
                        Row(
                            modifier = Modifier.fillMaxWidth().selectable(
                                selected = choice == language,
                                role = Role.RadioButton,
                                onClick = {
                                    showLanguages = false
                                    onLanguageChange(choice)
                                }
                            ).padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = choice == language, onClick = null)
                            Spacer(Modifier.width(12.dp))
                            Text(choice.nativeName)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showLanguages = false }) { Text(appText(R.string.cancel)) } }
        )
    }

    availableUpdate?.let { update ->
        AlertDialog(
            onDismissRequest = { availableUpdate = null },
            title = { Text(appText(R.string.update_available, update.version)) },
            text = { Text(appText(R.string.update_description, BuildConfig.VERSION_NAME)) },
            confirmButton = {
                TextButton(onClick = {
                    availableUpdate = null
                    runCatching { uriHandler.openUri(update.downloadUrl) }.onFailure {
                        scope.launch { snackbarHostState.showSnackbar(appText(R.string.browser_open_release)) }
                    }
                }) { Text(appText(R.string.download_apk)) }
            },
            dismissButton = {
                TextButton(onClick = { availableUpdate = null }) { Text(appText(R.string.later)) }
            }
        )
    }

    if (showDisclaimer) DisclaimerDialog(onConfirm = { showDisclaimer = false })

    // 条款/说明弹窗
    currentDoc?.let { doc ->
        AlertDialog(
            onDismissRequest = { currentDoc = null },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurface,
            confirmButton = {
                TextButton(onClick = { currentDoc = null }) {
                    Text(appText(R.string.dialog_ok), color = TechCyan)
                }
            },
            title = { Text(appText(doc.titleRes), style = gradientTitleStyle()) },
            text = { Text(appText(doc.bodyRes), style = MaterialTheme.typography.bodyMedium) }
        )
    }
}

@Composable
private fun SettingItem(
    icon: ImageVector,
    title: String,
    supporting: String? = null,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    ListItem(
        leadingContent = { Icon(icon, contentDescription = null, tint = TechCyan) },
        headlineContent = { Text(title) },
        supportingContent = supporting?.let { text ->
            { Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        },
        trailingContent = {
            Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick)
    )
}

@Preview(showBackground = true)
@Composable
private fun SettingsScreenPreview() {
    MyApplicationTheme {
        Box(modifier = Modifier.background(Color(0xFF080D1C))) {
            SettingsScreen(
                snackbarHostState = SnackbarHostState(),
                onOpenProviderManager = {},
                onBack = {}
            )
        }
    }
}
