package com.reelcommenter

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.NumberPicker
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val editText = findViewById<EditText>(R.id.commentText)
        val numberPicker = findViewById<NumberPicker>(R.id.delayPicker)
        val startButton = findViewById<Button>(R.id.startButton)
        val stopButton = findViewById<Button>(R.id.stopButton)
        val accessibilityButton = findViewById<Button>(R.id.accessibilityButton)

        numberPicker.minValue = 5
        numberPicker.maxValue = 60

        val prefs = getSharedPreferences("reel_commenter", Context.MODE_PRIVATE)
        editText.setText(prefs.getString("comment_text", "Passt auf euch auf"))
        numberPicker.value = prefs.getInt("delay_seconds", 12)

        accessibilityButton.setOnClickListener {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
            Toast.makeText(this, "Aktiviere 'Reel Commenter' in der Liste und kehre zurück", Toast.LENGTH_LONG).show()
        }

        startButton.setOnClickListener {
            prefs.edit()
                .putString("comment_text", editText.text.toString())
                .putInt("delay_seconds", numberPicker.value)
                .apply()

                if (!Settings.canDrawOverlays(this)) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
                Toast.makeText(this, "Erlaube 'Über anderen Apps einblenden'", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            val serviceIntent = Intent(this, FloatingButtonService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

              startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
            Toast.makeText(this, "Schwebender Button aktiviert", Toast.LENGTH_SHORT).show()
        }

        stopButton.setOnClickListener {
            stopService(Intent(this, FloatingButtonService::class.java))
            Toast.makeText(this, "Button deaktiviert", Toast.LENGTH_SHORT).show()
        }
    }
}
