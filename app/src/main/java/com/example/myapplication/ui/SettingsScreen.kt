@file:Suppress("DEPRECATION") // LocalClipboardManager 在当前 BOM 版本仍为官方兼容 API

package com.example.myapplication.ui

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
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.myapplication.BuildConfig
import com.example.myapplication.ui.theme.MyApplicationTheme
import com.example.myapplication.ui.theme.TechCyan
import kotlinx.coroutines.launch

private const val OFFICIAL_DOCS_URL = "https://www.fluentic.cn/config/soft/"
private const val DEVELOPER_EMAIL = "administrator@fluentic.cn"

// 各类弹窗文本
private data class InfoDoc(val title: String, val body: String)

private val aboutDoc = InfoDoc(
    title = "关于FL音坊",
    body = "FL音坊的名字源于个人网站域名 fluentic，寓意「流利的音色处理工坊」。\n\n" +
        "FL音坊是一款 AI 音频处理工具，提供两大核心功能：\n\n" +
        "1. 克隆音色：用一段本人语音样本，复刻出专属音色，支持多国语言；\n" +
        "2. 文字转语音：把文字合成为自然语音，可使用预设音色或你克隆出的音色。\n\n" +
        "联网模式已接入阿里云百炼 CosyVoice。保存 API Key 后，在主页选择该服务商即可使用。"
)

private val userAgreement = InfoDoc(
    title = "用户协议",
    body = "欢迎使用FL音坊。使用本应用即表示你同意：\n\n" +
        "1. 不得利用本应用从事任何违法违规活动；\n" +
        "2. 本应用源码按 GNU GPL v3.0 授权，使用、修改和分发源码时请遵守该许可证；\n" +
        "3. 音色克隆功能仅可用于本人声音，或已获得明确授权的他人声音；\n" +
        "4. 应用内功能与内容可能随版本更新调整，以最新版本为准。\n\n" +
        "继续使用即视为你已阅读并接受本协议。"
)

private val privacyPolicy = InfoDoc(
    title = "隐私政策",
    body = "本应用高度重视你的隐私：\n\n" +
        "1. 联网模式会将录音或合成文字发送至所选服务商。本地模式的录音、文字和语音生成留在设备上；\n" +
        "2. 不要求注册账号，不收集姓名、手机号等个人身份信息；\n" +
        "3. 你自行填写的服务商密钥仅保存在本机，仅用于向对应服务商发起请求；\n" +
        "4. 麦克风权限用于录音，音频选择和导出通过系统文件选择器完成。生成的语音和台词保存在本机，可在生成记录中删除。"
)

private val disclaimer = InfoDoc(
    title = "免责声明",
    body = "1. 音色克隆需获得声音本人授权，未授权克隆他人音色可能侵犯其声音权益，由此产生的后果由使用者自行承担；\n\n" +
        "2. 各服务商接口、价格与可用性以对应服务商官方为准，本应用仅作为客户端壳提供接入；\n\n" +
        "3. 因使用本应用产生的任何后果，由使用者自行承担。"
)

private val openSourceLicenses = InfoDoc(
    title = "第三方开源许可",
    body = "本应用使用了以下开源技术：\n\n" +
        "· Jetpack Compose（Apache 2.0）\n" +
        "· AndroidX（Apache 2.0）\n" +
        "· Kotlin 协程（Apache 2.0）\n" +
        "· Gradle（Apache 2.0）\n\n" +
        "感谢开源社区的支持。"
)

/**
 * 设置页：服务商管理、官方使用文档、关于软件、联系开发者、检查更新、清除缓存、用户协议、隐私政策、免责声明、开源许可。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    snackbarHostState: SnackbarHostState,
    onOpenProviderManager: () -> Unit,
    onBack: () -> Unit,
    onOpenLocalModels: (() -> Unit)? = null
) {
    var currentDoc by remember { mutableStateOf<InfoDoc?>(null) }
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "设置",
                        style = MaterialTheme.typography.titleLarge.copy(brush = TechAccentBrush)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "返回",
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
                        Text("FL音坊", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "版本 ${BuildConfig.VERSION_NAME}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            onOpenLocalModels?.let { open ->
                GlassCard(onClick = open) {
                    Text("本地模型", style = MaterialTheme.typography.titleMedium)
                    Text("查看模型列表、下载或导入 ZIP", style = MaterialTheme.typography.bodySmall)
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
                        Text("服务商管理", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "配置云服务商与 API 密钥",
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
                SettingItem(
                    icon = Icons.Rounded.Description,
                    title = "官方使用文档",
                    supporting = "安装、服务商配置与使用教程",
                    onClick = {
                        runCatching { uriHandler.openUri(OFFICIAL_DOCS_URL) }.onFailure {
                            scope.launch { snackbarHostState.showSnackbar("无法打开浏览器，请访问 $OFFICIAL_DOCS_URL") }
                        }
                    }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingItem(Icons.Rounded.Info, "关于软件") { currentDoc = aboutDoc }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingItem(
                    icon = Icons.Rounded.Mail,
                    title = "联系开发者",
                    supporting = DEVELOPER_EMAIL,
                    onClick = {
                        clipboardManager.setText(AnnotatedString(DEVELOPER_EMAIL))
                        scope.launch { snackbarHostState.showSnackbar("开发者邮箱已复制：$DEVELOPER_EMAIL") }
                    }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingItem(Icons.Rounded.Refresh, "检查更新") {
                    scope.launch { snackbarHostState.showSnackbar("暂未接入在线更新服务，请通过原安装渠道检查更新") }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingItem(Icons.Rounded.Delete, "清除缓存") {
                    scope.launch {
                        val success = withContext(Dispatchers.IO) {
                            // Settings is shown after audio/recording pages have released their files.
                            context.cacheDir.listFiles()?.map { it.deleteRecursively() }?.all { it } ?: true
                        }
                        snackbarHostState.showSnackbar(if (success) "缓存已清理" else "部分缓存未能清理，请稍后重试")
                    }
                }
            }

            // 协议与声明
            GlassCard(contentPadding = PaddingValues(0.dp)) {
                SettingItem(Icons.Rounded.Description, "用户协议") { currentDoc = userAgreement }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingItem(Icons.Rounded.Security, "隐私政策") { currentDoc = privacyPolicy }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingItem(Icons.Rounded.Warning, "免责声明") { currentDoc = disclaimer }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingItem(Icons.Rounded.Code, "第三方开源许可") { currentDoc = openSourceLicenses }
            }

            // 版权
            Text(
                text = "© 2026 影篱 YingLi · 仅供个人学习交流",
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

    // 条款/说明弹窗
    currentDoc?.let { doc ->
        AlertDialog(
            onDismissRequest = { currentDoc = null },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurface,
            confirmButton = {
                TextButton(onClick = { currentDoc = null }) {
                    Text("我知道了", color = TechCyan)
                }
            },
            title = { Text(doc.title, style = gradientTitleStyle()) },
            text = { Text(doc.body, style = MaterialTheme.typography.bodyMedium) }
        )
    }
}

@Composable
private fun SettingItem(
    icon: ImageVector,
    title: String,
    supporting: String? = null,
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
        modifier = Modifier.clickable(onClick = onClick)
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
