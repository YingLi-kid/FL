package com.example.myapplication.ui

import com.example.myapplication.R
import com.example.myapplication.appText

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalUriHandler
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
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.myapplication.data.ProviderCapability
import com.example.myapplication.data.ProviderRuntimeConfig
import com.example.myapplication.data.ServiceProvider
import com.example.myapplication.data.serviceProviders
import com.example.myapplication.ui.theme.MyApplicationTheme
import com.example.myapplication.ui.theme.TechCyan
import com.example.myapplication.ui.theme.TechPink

private fun ProviderCapability.label(): String = when (this) {
    ProviderCapability.VOICE_CLONE -> appText(R.string.capability_clone)
    ProviderCapability.TTS -> appText(R.string.tts_tab)
}

/**
 * 服务商管理页：列出内置主流服务商，进入详情填密钥并启用/停用。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProviderManagerScreen(
    configs: Map<String, ProviderRuntimeConfig>,
    onSaveCredential: (String, String, String) -> Unit,
    onBack: () -> Unit,
    initialProviderId: String? = null
) {
    var selectedProviderId by rememberSaveable(initialProviderId) { mutableStateOf(initialProviderId) }
    val selectedProvider = serviceProviders.firstOrNull { it.id == selectedProviderId }

    fun back() {
        if (selectedProvider == null || initialProviderId != null) onBack()
        else selectedProviderId = null
    }

    BackHandler(enabled = selectedProvider != null) { back() }
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (selectedProvider == null) appText(R.string.manage_providers) else selectedProvider.name,
                        style = MaterialTheme.typography.titleLarge.copy(brush = TechAccentBrush)
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            back()
                        }
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = appText(R.string.back), tint = TechCyan)
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
        if (selectedProvider == null) {
            ProviderList(
                configs = configs,
                onOpenProvider = { selectedProviderId = it },
                modifier = Modifier.padding(innerPadding)
            )
        } else {
            val config = configs[selectedProvider.id] ?: ProviderRuntimeConfig()
            ProviderDetail(
                provider = selectedProvider,
                config = config,
                onSaveCredential = { field, value -> onSaveCredential(selectedProvider.id, field, value) },
                modifier = Modifier.padding(innerPadding)
            )
        }
    }
}

@Composable
private fun ProviderList(
    configs: Map<String, ProviderRuntimeConfig>,
    onOpenProvider: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 说明卡
        GlassCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(TechCyan.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.Security, contentDescription = null, tint = TechCyan)
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    appText(R.string.provider_configuration_help),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        serviceProviders.forEach { provider ->
            val config = configs[provider.id]
            val configured = config?.isCredentialComplete(provider) == true
            val statusText = if (configured) appText(R.string.configured) else appText(R.string.not_configured)
            val statusColor = if (configured) TechCyan else MaterialTheme.colorScheme.onSurfaceVariant

            GlassCard(onClick = { onOpenProvider(provider.id) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(provider.name, style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.width(8.dp))
                            RegionTag(provider.region)
                        }
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            provider.capabilities.forEach { cap ->
                                CapabilityTag(cap.label())
                            }
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    Column(horizontalAlignment = Alignment.End) {
                        Text(statusText, style = MaterialTheme.typography.labelMedium, color = statusColor)
                        Spacer(Modifier.height(4.dp))
                        Icon(
                            Icons.Rounded.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun ProviderDetail(
    provider: ServiceProvider,
    config: ProviderRuntimeConfig,
    onSaveCredential: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val uriHandler = LocalUriHandler.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 能力与说明
        GlassCard {
            Text(provider.description, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                provider.capabilities.forEach { CapabilityTag(it.label()) }
            }
        }

        // 密钥字段
        GlassCard {
            SectionTitle(appText(R.string.credentials_settings), icon = Icons.Rounded.Key)
            Spacer(Modifier.height(12.dp))
            provider.credentialFields.forEach { field ->
                // Keep editor state synchronous: a DataStore round trip must not drop keystrokes.
                var draft by androidx.compose.runtime.remember(provider.id, field.key) { mutableStateOf<String?>(null) }
                val storedValue = config.credentials[field.key] ?: ""
                val value = draft ?: storedValue
                OutlinedTextField(
                    value = value,
                    onValueChange = { draft = it; onSaveCredential(field.key, it) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(field.label) },
                    placeholder = { Text(field.placeholder) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    trailingIcon = {
                        if (value.isNotBlank()) {
                            Text(if (value == storedValue) appText(R.string.saved) else appText(R.string.credential_saving), style = MaterialTheme.typography.labelMedium, color = TechCyan)
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = TechCyan,
                        focusedLabelColor = TechCyan,
                        cursorColor = TechCyan,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                    )
                )
                Spacer(Modifier.height(12.dp))
            }
        }

        // 官方文档
        GlassCard(onClick = { uriHandler.openUri(provider.docsUrl) }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.OpenInNew, contentDescription = null, tint = TechCyan, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(appText(R.string.provider_docs), style = MaterialTheme.typography.bodyMedium)
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun CapabilityTag(text: String) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(TechCyan.copy(alpha = 0.12f))
            .border(1.dp, TechCyan.copy(alpha = 0.4f), shape)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = TechCyan)
    }
}

@Composable
private fun RegionTag(text: String) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Preview(showBackground = true)
@Composable
private fun ProviderManagerPreview() {
    MyApplicationTheme {
        Box(modifier = Modifier.background(Color(0xFF080D1C))) {
            ProviderManagerScreen(
                configs = emptyMap(),
                onSaveCredential = { _, _, _ -> },
                onBack = {}
            )
        }
    }
}
