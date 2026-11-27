package com.example.myapplication.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.myapplication.MainActivity
import com.example.myapplication.R
import com.example.myapplication.appText
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.IOException

internal data class ImportStatus(val model: String = "", val active: Boolean = false,
    val bytes: Long = 0, val stage: ModelImportStage = ModelImportStage.OPENING, val message: String = "")

class ModelImportService : Service() {
    companion object {
        internal val status = MutableStateFlow(ImportStatus())
        private const val channel = "model-import"
        private const val notificationId = 38
        internal fun start(context: Context, model: String, uri: Uri) {
            if (status.value.active) return
            val intent = Intent(context, ModelImportService::class.java).setData(uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION).putExtra("model", model)
            ContextCompat.startForegroundService(context, intent)
            status.value = ImportStatus(model, true)
        }
        internal fun cancel(context: Context) {
            context.startService(Intent(context, ModelImportService::class.java).setAction("cancel"))
        }
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var task: Job? = null
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "cancel") {
            task?.cancel() ?: stopSelf()
            return START_NOT_STICKY
        }
        if (task?.isActive == true) return START_NOT_STICKY
        val model = localSpeechBackends.firstOrNull { it.id == intent?.getStringExtra("model") }
        val uri = intent?.data
        if (model == null || uri == null) { stopSelf(); return START_NOT_STICKY }
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(NotificationChannel(channel, appText(R.string.import_zip), NotificationManager.IMPORTANCE_LOW))
        val pendingFlags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val notification = NotificationCompat.Builder(this, channel)
            .setSmallIcon(android.R.drawable.stat_sys_download).setContentTitle(model.name)
            .setContentText(appText(R.string.import_zip)).setOnlyAlertOnce(true).setOngoing(true)
            .setContentIntent(PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), pendingFlags))
            .addAction(0, appText(R.string.cancel_import), PendingIntent.getService(this, 0, Intent(this, ModelImportService::class.java).setAction("cancel"), pendingFlags))
        startForeground(notificationId, notification.setProgress(100, 0, true).build())
        val store = model.modelPackage(applicationContext)
        task = scope.launch {
            var message = appText(R.string.model_import_cancelled)
            try {
                var lastPercent = -1
                store.importZip({ contentResolver.openInputStream(uri) ?: throw IOException(appText(R.string.model_zip_open_error)) }) { bytes, stage ->
                    val percent = (100 * bytes / store.downloadBytes).toInt()
                    if (percent != lastPercent || status.value.stage != stage) {
                        lastPercent = percent
                        status.value = ImportStatus(model.id, true, bytes, stage)
                        manager.notify(notificationId, notification.setProgress(100, percent, false).build())
                    }
                }
                message = appText(R.string.model_import_success)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { message = e.localizedMessage ?: appText(R.string.file_operation_error) }
            finally {
                status.value = ImportStatus(model.id, message = message)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }
    override fun onTimeout(startId: Int, fgsType: Int) { task?.cancel(); stopSelf() }
    override fun onDestroy() { scope.cancel(); super.onDestroy() }
}
