package com.example.myapplication.ui

import com.example.myapplication.R
import com.example.myapplication.appText

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.example.myapplication.AppLanguage
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val disclaimerPoints = listOf(
    R.string.disclaimer_rights_title to R.string.disclaimer_rights,
    R.string.disclaimer_usage_title to R.string.disclaimer_usage,
    R.string.disclaimer_modes_title to R.string.disclaimer_modes,
    R.string.disclaimer_liability_title to R.string.disclaimer_liability
)

@Composable
internal fun StartupDisclaimer(
    language: AppLanguage,
    onLanguageChange: (AppLanguage) -> Unit,
    onDecline: () -> Unit,
    content: @Composable () -> Unit
) {
    val app = LocalContext.current.applicationContext
    val skipMarker = remember(app) { File(app.noBackupFilesDir, "disclaimer-v1-hidden") }
    val hideOnLaunch by produceState<Boolean?>(null, skipMarker) {
        value = withContext(Dispatchers.IO) { skipMarker.isFile }
    }
    var acceptedThisLaunch by rememberSaveable { mutableStateOf(false) }
    if (acceptedThisLaunch || hideOnLaunch == true) {
        content()
    } else if (hideOnLaunch == false) {
        val scope = rememberCoroutineScope()
        var saving by remember { mutableStateOf(false) }
        var saveError by remember { mutableStateOf(false) }
        DisclaimerDialog(
            onConfirm = { skipNextTime ->
                if (!skipNextTime) {
                    acceptedThisLaunch = true
                } else if (!saving) {
                    saving = true
                    saveError = false
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) {
                                check(skipMarker.createNewFile() || skipMarker.isFile)
                            }
                            acceptedThisLaunch = true
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            saveError = true
                        } finally {
                            saving = false
                        }
                    }
                }
            },
            onDecline = onDecline,
            alternateLanguage = AppLanguage.entries.first { it != language },
            onLanguageChange = onLanguageChange,
            saving = saving,
            saveError = saveError
        )
    }
}

@Composable
internal fun DisclaimerDialog(
    onConfirm: (Boolean) -> Unit,
    onDecline: (() -> Unit)? = null,
    alternateLanguage: AppLanguage? = null,
    onLanguageChange: ((AppLanguage) -> Unit)? = null,
    saving: Boolean = false,
    saveError: Boolean = false
) {
    val isStartup = onDecline != null
    var skipNextTime by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { if (!isStartup) onConfirm(false) },
        properties = DialogProperties(
            dismissOnBackPress = !isStartup,
            dismissOnClickOutside = !isStartup
        ),
        title = { Text(appText(R.string.disclaimer_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (alternateLanguage != null && onLanguageChange != null) {
                    TextButton(enabled = !saving, onClick = { onLanguageChange(alternateLanguage) }) {
                        Text(alternateLanguage.nativeName)
                    }
                }
                disclaimerPoints.forEach { (title, body) ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(appText(title), style = MaterialTheme.typography.titleSmall)
                        Text(appText(body), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                if (isStartup) {
                    Row(
                        modifier = Modifier.fillMaxWidth().sizeIn(minHeight = 48.dp).toggleable(
                            value = skipNextTime,
                            enabled = !saving,
                            role = Role.Checkbox,
                            onValueChange = { skipNextTime = it }
                        ),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Checkbox(checked = skipNextTime, onCheckedChange = null, enabled = !saving)
                        Text(appText(R.string.disclaimer_skip))
                    }
                    if (saveError) {
                        Text(appText(R.string.disclaimer_storage_error), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = !saving, onClick = { onConfirm(skipNextTime) }) {
                Text(if (isStartup) appText(R.string.disclaimer_continue) else appText(R.string.dialog_ok))
            }
        },
        dismissButton = onDecline?.let { decline ->
            { TextButton(enabled = !saving, onClick = decline) { Text(appText(R.string.exit_app)) } }
        }
    )
}
