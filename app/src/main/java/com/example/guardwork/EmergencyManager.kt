package com.example.guardwork

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Handler
import android.os.Looper
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

/**
 * Acil durum mantığı: TAZE konum al (FusedLocation) + tüm kişilere SMS gönder.
 * Hem panik butonu hem man down bu sınıfı çağırır.
 *
 * Gerekli bağımlılık (app/build.gradle):
 *   implementation("com.google.android.gms:play-services-location:21.3.0")
 */
object EmergencyManager {

    // Kişileri istersen SharedPreferences'tan oku; burada sabit örnek:
    var contacts: List<String> = listOf("05320000000", "05330000000")

    enum class Type { SOS, MANDOWN, FALL }

    private const val LOC_TIMEOUT_MS = 8000L  // konum bu sürede gelmezse beklemeyi bırak

    /**
     * Taze konum almaya çalışır; gelmezse son bilinen konuma düşer.
     * Konum elde edilince (veya zaman aşımında) [onReady] ile döner.
     */
    @SuppressLint("MissingPermission")
    private fun resolveLocation(ctx: Context, onReady: (Location?) -> Unit) {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) { onReady(null); return }

        val fused = LocationServices.getFusedLocationProviderClient(ctx)
        var done = false
        val handler = Handler(Looper.getMainLooper())

        // Zaman aşımı koruması: konum gelmese bile SMS yine gitsin
        val timeout = Runnable {
            if (!done) { done = true; onReady(lastKnown(ctx)) }
        }
        handler.postDelayed(timeout, LOC_TIMEOUT_MS)

        val req = CurrentLocationRequest.Builder()
            .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
            .setMaxUpdateAgeMillis(0)   // bayat değil, taze fix iste
            .build()

        fused.getCurrentLocation(req, null)
            .addOnSuccessListener { loc ->
                if (!done) {
                    done = true
                    handler.removeCallbacks(timeout)
                    onReady(loc ?: lastKnown(ctx))
                }
            }
            .addOnFailureListener {
                if (!done) {
                    done = true
                    handler.removeCallbacks(timeout)
                    onReady(lastKnown(ctx))
                }
            }
    }

    /** Yedek: en son bilinen konum (Fused başarısız olursa). */
    @SuppressLint("MissingPermission")
    private fun lastKnown(ctx: Context): Location? {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) return null
        val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        var best: Location? = null
        for (p in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
            try {
                val loc = lm.getLastKnownLocation(p) ?: continue
                if (best == null || loc.accuracy < best!!.accuracy) best = loc
            } catch (_: Exception) {}
        }
        return best
    }

    private fun buildMessage(type: Type, loc: Location?): String {
        val header = when (type) {
            Type.SOS -> "ACIL DURUM! Yardim cagiriyorum."
            Type.MANDOWN -> "MAN DOWN: Hareketsizlik tespit edildi, yanit yok."
            Type.FALL -> "DUSME tespit edildi, yanit yok."
        }
        val link = if (loc != null)
            "Konum: https://maps.google.com/?q=${loc.latitude},${loc.longitude}"
        else
            "Konum alinamadi."
        return "$header $link"
    }

    /** Taze konumu alır ve tüm kişilere SMS atar. (SEND_SMS + konum izni gerekli) */
    fun sendAlert(ctx: Context, type: Type) {
        resolveLocation(ctx) { loc ->
            if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.SEND_SMS)
                != PackageManager.PERMISSION_GRANTED
            ) return@resolveLocation

            val msg = buildMessage(type, loc)
            @Suppress("DEPRECATION")
            val sms = if (android.os.Build.VERSION.SDK_INT >= 31)
                ctx.getSystemService(SmsManager::class.java)
            else
                SmsManager.getDefault()

            contacts.filter { it.isNotBlank() }.forEach { number ->
                try {
                    val parts = sms.divideMessage(msg)
                    sms.sendMultipartTextMessage(number, null, parts, null, null)
                } catch (_: Exception) {}
            }
        }
    }
}
