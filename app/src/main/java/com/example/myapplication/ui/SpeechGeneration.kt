package com.example.myapplication.ui

import com.example.myapplication.R
import com.example.myapplication.appText

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.SpeechHistory
import com.example.myapplication.data.SpeechRecord
import com.example.myapplication.data.VoiceOption
import com.example.myapplication.service.LocalSpeechBackend
import com.example.myapplication.service.SpeechBackend
import com.example.myapplication.service.SpeechParameters
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class SpeechProgress(
    val backend: SpeechBackend,
    val segment: Int = 0,
    val total: Int = 0
) {
    val backendName: String get() = backend.name
    val offline: Boolean get() = backend is LocalSpeechBackend
}

internal data class SpeechGenerationState(
    val progress: SpeechProgress? = null,
    val results: Map<String, SpeechRecord> = emptyMap(),
    val error: Pair<String, String>? = null,
    val clearingCache: Boolean = false
)

internal class SpeechGenerationViewModel : ViewModel() {
    val generation = SpeechGeneration(viewModelScope)
}

internal class SpeechGeneration(private val scope: CoroutineScope) {
    private val mutableState = MutableStateFlow(SpeechGenerationState())
    val state = mutableState.asStateFlow()

    // Both entry points are called on the main thread. The running state is set before launching
    // so switches between pages cannot start a second synthesis or delete files still in use.
    fun start(
        context: Context,
        backend: SpeechBackend,
        voice: VoiceOption,
        text: String,
        language: String,
        parameters: SpeechParameters,
        credentials: Map<String, String>
    ) {
        if (state.value.progress != null || state.value.clearingCache) return
        val app = context.applicationContext
        val initialProgress = SpeechProgress(backend)
        mutableState.update { it.copy(progress = initialProgress, error = null) }
        scope.launch {
            try {
                val temporary = backend.synthesize(app, voice, text, language, parameters, credentials) { segment, total ->
                    mutableState.update { it.copy(progress = initialProgress.copy(segment = segment, total = total)) }
                }
                val record = withContext(NonCancellable) {
                    try {
                        SpeechHistory(app).save(temporary, text, voice.name, if (backend is LocalSpeechBackend) language else "auto", backend.id, backend.format)
                    } finally {
                        withContext(Dispatchers.IO) { temporary.delete() }
                    }
                }
                mutableState.update { it.copy(progress = null, results = it.results + (backend.id to record)) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutableState.update { it.copy(error = backend.id to (e.message ?: appText(R.string.unknown_error))) }
            } finally {
                mutableState.update { it.copy(progress = null) }
            }
        }
    }

    suspend fun clearCache(context: Context): Boolean? {
        if (state.value.progress != null || state.value.clearingCache) return null
        mutableState.update { it.copy(clearingCache = true) }
        try {
            return withContext(Dispatchers.IO) {
                context.cacheDir.listFiles()?.map { it.deleteRecursively() }?.all { it } ?: true
            }
        } finally {
            mutableState.update { it.copy(clearingCache = false) }
        }
    }
}

@Composable
internal fun rememberSpeechGeneration(): SpeechGeneration {
    val scope = rememberCoroutineScope()
    return remember(scope) { SpeechGeneration(scope) }
}
