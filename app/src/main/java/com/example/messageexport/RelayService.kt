package com.example.messageexport

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat

class RelayService : Service() {

    private val TAG = "RelayService"
    private val CHANNEL_ID = "RelayServiceChannel"
    private var retryTimer: java.util.Timer? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "RelayService Created")
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "RelayService Started")

        val notification: Notification =
                NotificationCompat.Builder(this, CHANNEL_ID)
                        .setContentTitle("SMS Relay Active")
                        .setContentText("Listening for incoming SMS messages...")
                        .setSmallIcon(android.R.drawable.ic_menu_send)
                        .build()

        try {
            startForeground(1, notification)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start foreground service: ${e.message}")
        }

        // Setup Hourly Retry Timer
        if (retryTimer == null) {
            retryTimer = java.util.Timer()
            retryTimer?.scheduleAtFixedRate(
                    object : java.util.TimerTask() {
                        override fun run() {
                            Log.i(TAG, "Executing hourly buffer retry...")
                            MessageBuffer.retryBufferedMessages(this@RelayService)
                        }
                    },
                    60000,
                    3600000
            ) // Start after 1 min, repeat every hour
        }

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "RelayService Destroyed")
        retryTimer?.cancel()
        retryTimer = null
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel =
                    NotificationChannel(
                            CHANNEL_ID,
                            "SMS Relay Service Channel",
                            NotificationManager.IMPORTANCE_DEFAULT
                    )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }
}
