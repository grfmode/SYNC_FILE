package com.sync.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.sync.app.R
import com.sync.app.domain.knowledge.KnowledgeRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Indeks knowledge berjalan di foreground service.
 *
 * Proses yang diangkat ke prioritas foreground tidak dibunuh saat pengguna
 * berpindah aplikasi. Status tiap berkas tetap tersimpan di database, jadi
 * indeks yang terputus dilanjutkan pada pemanggilan berikutnya.
 */
@AndroidEntryPoint
class KnowledgeIndexingService : Service() {

    @Inject
    lateinit var repository: KnowledgeRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildNotification("Menyiapkan indeks…")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        val projectId = intent?.getLongExtra(EXTRA_PROJECT_ID, -1L) ?: -1L
        if (projectId <= 0) {
            stopSelf()
            return START_NOT_STICKY
        }

        if (job?.isActive == true) return START_NOT_STICKY

        job = scope.launch {
            try {
                val report = repository.indexPending(projectId)
                notify(
                    buildNotification(
                        if (report.filesFailed == 0) {
                            "${report.filesIndexed} berkas diindeks, ${report.chunks} potongan."
                        } else {
                            "${report.filesIndexed} berkas diindeks, ${report.filesFailed} gagal."
                        }
                    )
                )
            } finally {
                stopForeground(STOP_FOREGROUND_DETACH)
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTimeout(startId: Int, foregroundServiceType: Int) {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf(startId)
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Indeks knowledge",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Menampilkan progres pengindeksan berkas proyek"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("SYNC mengindeks proyek")
            .setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

    private fun notify(notification: Notification) {
        runCatching {
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        private const val CHANNEL_ID = "sync_knowledge_index"
        private const val NOTIFICATION_ID = 4108
        private const val EXTRA_PROJECT_ID = "projectId"

        fun start(context: Context, projectId: Long) {
            val intent = Intent(context, KnowledgeIndexingService::class.java)
                .putExtra(EXTRA_PROJECT_ID, projectId)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, KnowledgeIndexingService::class.java))
        }
    }
}