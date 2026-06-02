package com.example.guardwork

import android.app.*
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlin.math.sqrt

/**
 * Arka planda ivmeölçeri dinler.
 *  - Hareketsizlik: STILL_LIMIT_MS boyunca neredeyse hiç hareket yoksa -> şüphe
 *  - Düşme: anlık ivme FALL_G eşiğini aşarsa -> şüphe
 * Şüphe oluşunca AlarmActivity açılır (geri sayım + iptal). İptal edilmezse SMS gider.
 */
class ManDownService : Service(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var accel: Sensor? = null

    private var lastMag = 9.81f
    private var stillSince = 0L
    private var lastMoveTriggered = false

    companion object {
        const val CHANNEL = "mandown_channel"
        const val NOTIF_ID = 42

        // Ayarlanabilir eşikler
        const val STILL_LIMIT_MS = 12_000L  // 12 sn hareketsizlik
        const val FALL_G = 2.6f             // düşme darbe eşiği (g)
        const val MOVE_DELTA = 0.4f         // bu altı "hareketsiz" sayılır

        @Volatile var running = false

        fun start(ctx: Context) {
            val i = Intent(ctx, ManDownService::class.java)
            if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i) else ctx.startService(i)
        }
        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, ManDownService::class.java))
        }
    }

    override fun onCreate() {
        super.onCreate()
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accel = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        createChannel()
        startForeground(NOTIF_ID, buildNotification())
        sensorManager.registerListener(this, accel, SensorManager.SENSOR_DELAY_NORMAL)
        stillSince = System.currentTimeMillis()
        running = true
    }

    override fun onSensorChanged(e: SensorEvent) {
        val mag = sqrt(e.values[0] * e.values[0] + e.values[1] * e.values[1] + e.values[2] * e.values[2])
        val delta = kotlin.math.abs(mag - lastMag)
        lastMag = mag

        // 1) Düşme: ani yüksek-G
        if (mag / 9.81f > FALL_G && !lastMoveTriggered) {
            lastMoveTriggered = true
            trigger(EmergencyManager.Type.FALL)
            return
        }

        // 2) Hareketsizlik
        val now = System.currentTimeMillis()
        if (delta < MOVE_DELTA) {
            if (now - stillSince >= STILL_LIMIT_MS && !lastMoveTriggered) {
                lastMoveTriggered = true
                trigger(EmergencyManager.Type.MANDOWN)
            }
        } else {
            stillSince = now            // hareket var -> sayacı sıfırla
            lastMoveTriggered = false
        }
    }

    private fun trigger(type: EmergencyManager.Type) {
        val i = Intent(this, AlarmActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra("type", type.name)
        }
        startActivity(i)
    }

    override fun onAccuracyChanged(s: Sensor?, a: Int) {}
    override fun onBind(i: Intent?): IBinder? = null

    override fun onDestroy() {
        sensorManager.unregisterListener(this)
        running = false
        super.onDestroy()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val ch = NotificationChannel(CHANNEL, "Man Down İzleme", NotificationManager.IMPORTANCE_LOW)
            (getSystemService(NotificationManager::class.java)).createNotificationChannel(ch)
        }
    }

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL)
            .setContentTitle("GuardWork aktif")
            .setContentText("Man down izleme çalışıyor")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setOngoing(true)
            .build()
}
