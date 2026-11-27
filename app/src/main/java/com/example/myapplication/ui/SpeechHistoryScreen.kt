package com.example.myapplication.ui

import com.example.myapplication.R
import com.example.myapplication.appText
import com.example.myapplication.appLocale

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.myapplication.data.SpeechHistory
import com.example.myapplication.data.SpeechRecord
import com.example.myapplication.data.languageLabel
import com.example.myapplication.service.AudioPlayer
import com.example.myapplication.service.SpeechFormat
import com.example.myapplication.service.speechBackendLabel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SpeechHistoryScreen(latestResults: Map<String, SpeechRecord>, onBack: () -> Unit) {
    val app = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val history = remember(app) { SpeechHistory(app) }
    val player = remember(app) { AudioPlayer(app) }
    var records by remember { mutableStateOf<List<SpeechRecord>?>(null) }
    var playing by remember { mutableStateOf<String?>(null) }
    var pendingDelete by remember { mutableStateOf<SpeechRecord?>(null) }
    var exportId by rememberSaveable { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf("") }

    LaunchedEffect(history, latestResults) { records = history.list() }
    DisposableEffect(player) { onDispose { player.stop() } }

    fun exportResult(uri: android.net.Uri?) {
        val id = exportId
        exportId = null
        if (uri == null || id == null) return
        scope.launch {
            try {
                val selected = requireNotNull(history.find(id)) { appText(R.string.history_record_deleted) }
                withContext(Dispatchers.IO) {
                    requireNotNull(app.contentResolver.openOutputStream(uri)) { appText(R.string.export_location_error) }.use { out ->
                        selected.audio.inputStream().use { it.copyTo(out) }
                    }
                }
                message = appText(R.string.audio_exported)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                message = appText(R.string.export_error, e.message.orEmpty())
            }
        }
    }

    val exportWav = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(SpeechFormat.WAV.mime), ::exportResult)
    val exportMp3 = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(SpeechFormat.MP3.mime), ::exportResult)

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text(appText(R.string.generation_history)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, appText(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxWidth().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (message.isNotBlank()) item { Text(message, modifier = Modifier.padding(vertical = 8.dp)) }
            if (records.isNullOrEmpty()) item {
                Text(if (records == null) appText(R.string.loading) else appText(R.string.history_empty), modifier = Modifier.padding(8.dp))
            }
            items(records.orEmpty(), key = { it.id }) { record ->
                GlassCard {
                    Text(record.voice, style = MaterialTheme.typography.titleMedium)
                    val date = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, appLocale)
                        .format(Date(record.createdAt))
                    Text(appText(R.string.history_record_details, speechBackendLabel(record.backend), languageLabel(record.language), record.durationMs / 1000, date),
                        style = MaterialTheme.typography.bodySmall)
                    Column(Modifier.padding(top = 8.dp)) {
                        Text(record.text, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        Row {
                            TextButton(onClick = {
                                if (playing == record.id) {
                                    player.stop()
                                    playing = null
                                } else {
                                    playing = null
                                    if (player.playFile(record.audio, onCompletion = { playing = null }, onError = {
                                            playing = null
                                            message = it
                                        }) > 0) playing = record.id
                                }
                            }) { Text(if (playing == record.id) appText(R.string.stop) else appText(R.string.preview_audio)) }
                            TextButton(onClick = {
                                exportId = record.id
                                val name = appText(R.string.history_export_filename, record.createdAt, record.format.extension)
                                if (record.format == SpeechFormat.WAV) exportWav.launch(name) else exportMp3.launch(name)
                            }) { Text(appText(R.string.export)) }
                            TextButton(onClick = { pendingDelete = record }) { Text(appText(R.string.delete)) }
                        }
                    }
                }
            }
        }
    }

    pendingDelete?.let { record ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(appText(R.string.history_delete_title)) },
            text = { Text(appText(R.string.history_delete_description)) },
            confirmButton = {
                TextButton(onClick = {
                    pendingDelete = null
                    if (playing == record.id) {
                        player.stop()
                        playing = null
                    }
                    scope.launch {
                        message = if (history.delete(record.id)) appText(R.string.history_delete_success) else appText(R.string.history_delete_error)
                        records = history.list()
                    }
                }) { Text(appText(R.string.history_delete_action)) }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text(appText(R.string.cancel)) } }
        )
    }
}
