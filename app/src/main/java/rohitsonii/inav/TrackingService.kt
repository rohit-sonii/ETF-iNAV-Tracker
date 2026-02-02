package rohitsonii.inav

import android.app.*
import android.content.Intent
import android.os.Build
import android.os.IBinder
import kotlinx.coroutines.*
import org.json.JSONObject
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

class TrackingService : Service() {

    private var trackingJob: Job? = null
    private var symbol: String = ""
    private var frequency: Long = 30L
    private val notificationId = 1001
    private val channelId = "inav_tracking"

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        symbol = intent?.getStringExtra("SYMBOL") ?: ""
        frequency = intent?.getLongExtra("FREQUENCY", 30L) ?: 30L

        createNotificationChannel()
        startForeground(notificationId, createNotification("Fetching...", "--:--"))

        startTracking()

        return START_STICKY
    }

    private fun startTracking() {
        trackingJob?.cancel()
        trackingJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                try {
                    val inav = fetchINAV(symbol)
                    val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())

                    withContext(Dispatchers.Main) {
                        val notification = createNotification(inav, time)
                        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                        notificationManager.notify(notificationId, notification)
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        val notification = createNotification("Error", "--:--")
                        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                        notificationManager.notify(notificationId, notification)
                    }
                }
                delay(frequency * 1000)
            }
        }
    }

    private fun fetchINAV(symbol: String): String {
        val url = "https://www.nseindia.com/api/NextApi/apiClient/GetQuoteApi?" +
                "functionName=getSymbolData&marketType=N&series=EQ&symbol=$symbol"

        val connection = URL(url).openConnection()
        connection.setRequestProperty("Host", "www.nseindia.com")
        connection.setRequestProperty("User-Agent", "")
        connection.setRequestProperty("Cookie", "ext_name=aa")
        connection.connectTimeout = 10000
        connection.readTimeout = 10000

        val response = connection.getInputStream().bufferedReader().readText()
        return JSONObject(response)
            .getJSONArray("equityResponse")
            .getJSONObject(0)
            .getJSONObject("priceInfo")
            .getString("inav")
    }

    private fun createNotification(inav: String, time: String): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, channelId)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        return builder
            .setContentTitle("$symbol - iNAV: $inav")
            .setContentText("Last updated: $time")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "iNAV Tracking",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        trackingJob?.cancel()
        super.onDestroy()
    }
}