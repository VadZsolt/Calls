package com.example.calls.services

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.CallLog
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.calls.R
import com.example.calls.sync.CallUploader
import com.example.calls.sync.SyncResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class CallSyncService : Service() {

    private lateinit var callUploader: CallUploader
    private lateinit var contentObserver: ContentObserver
    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())

    private var lastNotificationText = "Watching for new calls..."
    companion object {
        const val CHANNEL_ID = "call_sync_channel"
        const val NOTIFICATION_ID = 1001
        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning
        const val ACTION_NOTIFICATION_DISMISSED = "com.example.calls.NOTIFICATION_DISMISSED"
        const val ACTION_STOP_SERVICE = "com.example.calls.STOP_SERVICE"
    }
    private val dismissReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            // Re-post immediately, so it reappears right away
            startForeground(NOTIFICATION_ID, buildNotification(lastNotificationText))
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
        startForeground(NOTIFICATION_ID, buildNotification("Watching for new calls..."))

        val filter = IntentFilter(ACTION_NOTIFICATION_DISMISSED)
        ContextCompat.registerReceiver(
            this,
            dismissReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        ContextCompat.registerReceiver(
            this, stopReceiver, IntentFilter(ACTION_STOP_SERVICE),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        // Register a listener that fires whenever the call log content changes
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

        // Also run one sync immediately when the service starts
        triggerSync()
    }

    private fun triggerSync() {
        serviceScope.launch {
            when (val result = callUploader.syncNow()) {
                is SyncResult.Success -> updateNotification("Last sync: uploaded ${result.count} call(s)")
                is SyncResult.PartialFailure -> updateNotification("Uploaded ${result.uploaded}, then lost connection")
                SyncResult.NetworkFailure -> updateNotification("Sync failed — no connection")
                SyncResult.NoSimSelected -> updateNotification("No SIM selected")
                SyncResult.NoNewCalls -> { /* nothing changed, leave notification as-is */ }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // START_STICKY: if the OS kills the service, try to restart it
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        _isRunning.value = false
        contentResolver.unregisterContentObserver(contentObserver)
        unregisterReceiver(dismissReceiver)
        unregisterReceiver(stopReceiver)
        serviceScope.coroutineContext[Job]?.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Call Sync",
                NotificationManager.IMPORTANCE_DEFAULT// low = no sound/heads-up, just a quiet icon
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
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

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Call Sync Active")
            .setContentText(text)
            .setSmallIcon(R.mipmap.main_icon)
            .setOngoing(true)
            .setDeleteIntent(pendingDeleteIntent)
            .addAction(0, "Stop", pendingStopIntent)
            .build()
    }

    private fun updateNotification(text: String) {
        lastNotificationText = text
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(text))
    }
}