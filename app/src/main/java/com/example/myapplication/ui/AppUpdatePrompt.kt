package com.example.myapplication.ui

import android.widget.Toast
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.BuildConfig
import com.example.myapplication.R
import com.example.myapplication.appText
import com.example.myapplication.service.AppUpdate
import com.example.myapplication.service.AppUpdates
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

internal class AppUpdateViewModel(
    private val fetch: suspend () -> AppUpdate? = { AppUpdates.check(BuildConfig.VERSION_NAME) }
) : ViewModel() {
    var available by mutableStateOf<AppUpdate?>(null)
        private set
    var checkingManually by mutableStateOf(false)
        private set
    var message by mutableStateOf<Int?>(null)
        private set
    private var started = false
    private var checking = false

    fun onLaunch() {
        if (started) return
        started = true
        check()
    }

    fun checkManually() {
        checkingManually = true
        check()
    }

    // A manual request during the startup check joins the same call and enables feedback.
    private fun check() {
        if (checking) return
        checking = true
        viewModelScope.launch {
            try {
                available = fetch()
                if (available == null && checkingManually) message = R.string.update_current
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                if (checkingManually) message = R.string.update_check_error
            } finally {
                checking = false
                checkingManually = false
            }
        }
    }

    fun dismiss() { available = null }
    fun clearMessage() { message = null }
}

@Composable
internal fun AppUpdatePrompt(updates: AppUpdateViewModel) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    LaunchedEffect(updates) { updates.onLaunch() }
    val message = updates.message
    LaunchedEffect(message) {
        if (message != null) {
            val text = if (message == R.string.update_current) appText(message, BuildConfig.VERSION_NAME) else appText(message)
            Toast.makeText(context, text, Toast.LENGTH_LONG).show()
            updates.clearMessage()
        }
    }
    updates.available?.let { update ->
        AlertDialog(
            onDismissRequest = updates::dismiss,
            title = { Text(appText(R.string.update_available, update.version)) },
            text = { Text(appText(R.string.update_description, BuildConfig.VERSION_NAME)) },
            confirmButton = {
                TextButton(onClick = {
                    try {
                        uriHandler.openUri(update.downloadUrl)
                        updates.dismiss()
                    } catch (_: RuntimeException) {
                        Toast.makeText(context, appText(R.string.browser_open_release), Toast.LENGTH_LONG).show()
                    }
                }) { Text(appText(R.string.download_apk)) }
            },
            dismissButton = { TextButton(onClick = updates::dismiss) { Text(appText(R.string.later)) } }
        )
    }
}
