package com.example.guardwork

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private val permLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)

        val sos = Button(this).apply {
            text = "ACİL DURUM"
            textSize = 28f
            setPadding(0, 80, 0, 80)
            setOnClickListener {
                startActivity(Intent(this@MainActivity, AlarmActivity::class.java)
                    .putExtra("type", EmergencyManager.Type.SOS.name))
            }
        }

        val manDown = Button(this).apply {
            text = "MAN DOWN: KAPALI"
            textSize = 18f
            setPadding(0, 50, 0, 50)
            setOnClickListener {
                if (ManDownService.running) {
                    ManDownService.stop(this@MainActivity)
                    text = "MAN DOWN: KAPALI"
                    Toast.makeText(context, "İzleme durdu", Toast.LENGTH_SHORT).show()
                } else {
                    ManDownService.start(this@MainActivity)
                    text = "MAN DOWN: AKTİF"
                    Toast.makeText(context, "İzleme başladı", Toast.LENGTH_SHORT).show()
                }
            }
        }

        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(48, 48, 48, 48)
            addView(sos); addView(manDown)
        })

        askPermissions()
    }

    private fun askPermissions() {
        val needed = mutableListOf(
            Manifest.permission.SEND_SMS,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= 33) needed.add(Manifest.permission.POST_NOTIFICATIONS)
        val ask = needed.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (ask.isNotEmpty()) permLauncher.launch(ask.toTypedArray())
    }
}
