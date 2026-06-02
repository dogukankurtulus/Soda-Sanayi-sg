package com.example.guardwork

import android.media.RingtoneManager
import android.os.Bundle
import android.os.CountDownTimer
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * Alarm ekranı. Hem panik butonu hem man down buraya gelir.
 * Geri sayım dolmadan kullanıcı iptal edebilir. İptal edilmezse SMS gider.
 */
class AlarmActivity : AppCompatActivity() {

    private var timer: CountDownTimer? = null
    private var ringtone: android.media.Ringtone? = null
    private val COUNTDOWN = 15L  // saniye

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        val type = EmergencyManager.Type.valueOf(intent.getStringExtra("type") ?: "SOS")

        val title = TextView(this).apply {
            text = when (type) {
                EmergencyManager.Type.SOS -> "ACİL DURUM"
                EmergencyManager.Type.MANDOWN -> "MAN DOWN — HAREKETSİZLİK"
                EmergencyManager.Type.FALL -> "DÜŞME TESPİT EDİLDİ"
            }
            textSize = 26f
            gravity = Gravity.CENTER
        }
        val counter = TextView(this).apply {
            textSize = 80f
            gravity = Gravity.CENTER
        }
        val info = TextView(this).apply {
            text = "saniye içinde herkese SMS gönderilecek"
            gravity = Gravity.CENTER
        }
        val cancel = Button(this).apply {
            text = "İYİYİM — İPTAL ET"
            textSize = 18f
            setOnClickListener { stopEverything(); finish() }
        }

        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(48, 48, 48, 48)
            addView(title); addView(counter); addView(info); addView(cancel)
        })

        startSiren()
        timer = object : CountDownTimer(COUNTDOWN * 1000, 1000) {
            override fun onTick(ms: Long) { counter.text = (ms / 1000 + 1).toString() }
            override fun onFinish() {
                EmergencyManager.sendAlert(this@AlarmActivity, type)  // <-- SMS burada gider
                title.text = "ALARM GÖNDERİLDİ"
                counter.text = "✓"
                info.text = "Kişilere SMS gönderildi"
                cancel.text = "ALARMI KAPAT"
            }
        }.start()
    }

    private fun startSiren() {
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ringtone = RingtoneManager.getRingtone(this, uri).apply { play() }
        } catch (_: Exception) {}
        (getSystemService(VIBRATOR_SERVICE) as? Vibrator)?.vibrate(
            VibrationEffect.createWaveform(longArrayOf(0, 600, 400), 0)
        )
    }

    private fun stopEverything() {
        timer?.cancel()
        ringtone?.stop()
        (getSystemService(VIBRATOR_SERVICE) as? Vibrator)?.cancel()
    }

    override fun onDestroy() { stopEverything(); super.onDestroy() }
}
