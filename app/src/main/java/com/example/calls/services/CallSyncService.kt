package com.example.calls.services

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.IBinder
import android.provider.CallLog
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.calls.R
import com.example.calls.activities.WriteActivity
import com.example.calls.data.SyncPreferences
import com.example.calls.sync.CallUploader
import com.example.calls.sync.SyncResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CallSyncService : Service() {

    private lateinit var callUploader: CallUploader
    private lateinit var contentObserver: ContentObserver
    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())

    private var lastNotificationText = "Watching for new calls..."
    private var lastSyncFailed = false // tracks whether to show the "Manual Upload" action
    private var isSyncing = false

    companion object {
        const val CHANNEL_ID = "call_sync_channel"
        const val NOTIFICATION_ID = 1001
        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning
        const val ACTION_NOTIFICATION_DISMISSED = "com.example.calls.NOTIFICATION_DISMISSED"
        const val ACTION_STOP_SERVICE = "com.example.calls.STOP_SERVICE"
        const val ACTION_REFRESH_STATUS = "com.example.calls.REFRESH_STATUS"
    }

    private val dismissReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            startForeground(NOTIFICATION_ID, buildNotification(lastNotificationText, lastSyncFailed))
        }
    }
    private val refreshReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            // A manual upload just happened elsewhere (WriteActivity) — re-check status now.
            triggerSync()
        }
    }

    private val stopReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            stopSelf()
        }
    }

    override fun onCreate() {
        super.onCreate()
        _isRunning.value = true
        callUploader = CallUploader(applicationContext)

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification(lastNotificationText, lastSyncFailed))

        ContextCompat.registerReceiver(
            this, dismissReceiver, IntentFilter(ACTION_NOTIFICATION_DISMISSED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        ContextCompat.registerReceiver(
            this, stopReceiver, IntentFilter(ACTION_STOP_SERVICE),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        ContextCompat.registerReceiver(
            this, refreshReceiver, IntentFilter(ACTION_REFRESH_STATUS),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        contentObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                super.onChange(selfChange)
                triggerSync()
            }
        }

        contentResolver.registerContentObserver(
            CallLog.Calls.CONTENT_URI,
            true,
            contentObserver
        )

        triggerSync()
    }

    private fun triggerSync() {
        if (isSyncing) return // NEW — skip if a sync is already in progress
        isSyncing = true

        serviceScope.launch {
            try {
                when (val result = callUploader.syncNow()) {
                    is SyncResult.Success -> {
                        lastSyncFailed = false
                        updateNotification(
                            "Last sync: ${formatMillis(SyncPreferences.getLastSyncMillis(this@CallSyncService).first())}"
                        )
                    }
                    is SyncResult.PartialFailure -> {
                        lastSyncFailed = true
                        updateNotification("Uploaded ${result.uploaded} calls, then lost connection")
                    }
                    SyncResult.NetworkFailure -> {
                        lastSyncFailed = true
                        updateNotification("Sync failed — no connection")
                    }
                    SyncResult.NoSimSelected -> {
                        lastSyncFailed = false
                        updateNotification("No SIM selected")
                    }
                    SyncResult.NoNewCalls -> { /* nothing changed, leave notification as-is */ }
                }
            } finally {
                isSyncing = false
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        _isRunning.value = false
        contentResolver.unregisterContentObserver(contentObserver)
        unregisterReceiver(dismissReceiver)
        unregisterReceiver(stopReceiver)
        unregisterReceiver(refreshReceiver)
        serviceScope.coroutineContext[Job]?.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Call Sync",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String, showManualUpload: Boolean): Notification {
        val deleteIntent = Intent(ACTION_NOTIFICATION_DISMISSED).setPackage(packageName)
        val pendingDeleteIntent = PendingIntent.getBroadcast(
            this, 0, deleteIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(ACTION_STOP_SERVICE).setPackage(packageName)
        val pendingStopIntent = PendingIntent.getBroadcast(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Call Sync Active")
            .setContentText(text)
            .setSmallIcon(R.mipmap.main_icon)
            .setOngoing(true)
            .setDeleteIntent(pendingDeleteIntent)

        if (showManualUpload) {
            val manualUploadIntent = Intent(this, WriteActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingManualUploadIntent = PendingIntent.getActivity(
                this, 2, manualUploadIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(0, "Manual Upload", pendingManualUploadIntent)
        }

        builder.addAction(0, "Stop", pendingStopIntent)

        return builder.build()
    }

    private fun updateNotification(text: String) {
        lastNotificationText = text
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(text, lastSyncFailed))
    }

    private fun formatMillis(millis: Long): String {
        val sdf = SimpleDateFormat("MM/dd HH:mm:ss", Locale.getDefault())
        return sdf.format(Date(millis))
    }
}