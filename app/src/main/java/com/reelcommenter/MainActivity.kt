package com.reelcommenter

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.NumberPicker
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.util.Calendar

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val prefs = getSharedPreferences("reel_commenter", Context.MODE_PRIVATE)

        val statsText = findViewById<TextView>(R.id.statsText)
        val autoModeCheck = findViewById<CheckBox>(R.id.autoModeCheck)
        val minDelayPicker = findViewById<NumberPicker>(R.id.minDelayPicker)
        val maxDelayPicker = findViewById<NumberPicker>(R.id.maxDelayPicker)
        val pool1Input = findViewById<EditText>(R.id.pool1Input)
        val pool2Input = findViewById<EditText>(R.id.pool2Input)
        val pool3Input = findViewById<EditText>(R.id.pool3Input)
        val poolSpinner = findViewById<Spinner>(R.id.poolSpinner)
        val soundCheck = findViewById<CheckBox>(R.id.soundCheck)
        val vibrationCheck = findViewById<CheckBox>(R.id.vibrationCheck)
        val fallbackCheck = findViewById<CheckBox>(R.id.fallbackCheck)
        val accessibilityButton = findViewById<Button>(R.id.accessibilityButton)
        val startButton = findViewById<Button>(R.id.startButton)
        val stopButton = findViewById<Button>(R.id.stopButton)
        val resetStatsButton = findViewById<Button>(R.id.resetStatsButton)

        minDelayPicker.minValue = 5
        minDelayPicker.maxValue = 120
        maxDelayPicker.minValue = 5
        maxDelayPicker.maxValue = 120

        autoModeCheck.isChecked = prefs.getBoolean("auto_mode", false)
        minDelayPicker.value = prefs.getInt("delay_min", 12)
        maxDelayPicker.value = prefs.getInt("delay_max", 20)
        pool1Input.setText(prefs.getString("pool1", "Passt auf euch auf,Passt auf euch auf 🙏"))
        pool2Input.setText(prefs.getString("pool2", "Passt gut auf euch auf,Passt auf euch auf! ❤️"))
        pool3Input.setText(prefs.getString("pool3", "Passt auf euch auf Freunde,Bleibt gesund!"))
        soundCheck.isChecked = prefs.getBoolean("sound_enabled", true)
        vibrationCheck.isChecked = prefs.getBoolean("vibration_enabled", true)
        fallbackCheck.isChecked = prefs.getBoolean("use_fallback_coords", false)

        val poolAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, arrayOf("Pool 1", "Pool 2", "Pool 3"))
        poolAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        poolSpinner.adapter = poolAdapter
        poolSpinner.setSelection(prefs.getInt("active_pool", 0))

        updateStats(statsText, prefs)

        val saveListener = CompoundButton.OnCheckedChangeListener { _, _ ->
            saveAll(prefs, autoModeCheck, minDelayPicker, maxDelayPicker, pool1Input, pool2Input, pool3Input, poolSpinner, soundCheck, vibrationCheck, fallbackCheck)
        }

        autoModeCheck.setOnCheckedChangeListener(saveListener)
        soundCheck.setOnCheckedChangeListener(saveListener)
        vibrationCheck.setOnCheckedChangeListener(saveListener)
        fallbackCheck.setOnCheckedChangeListener(saveListener)

        poolSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                prefs.edit().putInt("active_pool", position).apply()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        accessibilityButton.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            Toast.makeText(this, "Aktiviere 'Reel Commenter' in Bedienungshilfen", Toast.LENGTH_LONG).show()
        }

        startButton.setOnClickListener {
            saveAll(prefs, autoModeCheck, minDelayPicker, maxDelayPicker, pool1Input, pool2Input, pool3Input, poolSpinner, soundCheck, vibrationCheck, fallbackCheck)
            if (!Settings.canDrawOverlays(this)) {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                Toast.makeText(this, "Erlaube 'Über anderen Apps einblenden'", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            val intent = Intent(this, FloatingButtonService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
            Toast.makeText(this, "Button aktiviert", Toast.LENGTH_SHORT).show()
        }

        stopButton.setOnClickListener {
            stopService(Intent(this, FloatingButtonService::class.java))
            Toast.makeText(this, "Button deaktiviert", Toast.LENGTH_SHORT).show()
        }

        resetStatsButton.setOnClickListener {
            prefs.edit().putInt("comment_count_today", 0).putInt("comment_count_total", 0).apply()
            updateStats(statsText, prefs)
            Toast.makeText(this, "Statistik zurückgesetzt", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveAll(prefs: android.content.SharedPreferences, autoMode: CheckBox, minDelay: NumberPicker, maxDelay: NumberPicker, p1: EditText, p2: EditText, p3: EditText, poolSpinner: Spinner, sound: CheckBox, vibration: CheckBox, fallback: CheckBox) {
        val minD = minDelay.value
        val maxD = maxDelay.value
        val finalMin = if (minD <= maxD) minD else maxD
        val finalMax = if (maxD >= minD) maxD else minD

        prefs.edit()
            .putBoolean("auto_mode", autoMode.isChecked)
            .putInt("delay_min", finalMin)
            .putInt("delay_max", finalMax)
            .putString("pool1", p1.text.toString())
            .putString("pool2", p2.text.toString())
            .putString("pool3", p3.text.toString())
            .putInt("active_pool", poolSpinner.selectedItemPosition)
            .putBoolean("sound_enabled", sound.isChecked)
            .putBoolean("vibration_enabled", vibration.isChecked)
            .putBoolean("use_fallback_coords", fallback.isChecked)
            .apply()
    }

    private fun updateStats(tv: TextView, prefs: android.content.SharedPreferences) {
        val cal = Calendar.getInstance()
        val todayKey = "${cal.get(Calendar.YEAR)}-${cal.get(Calendar.MONTH)}-${cal.get(Calendar.DAY_OF_MONTH)}"
        val lastReset = prefs.getString("last_count_reset", "")
        if (lastReset != todayKey) {
            prefs.edit().putInt("comment_count_today", 0).putString("last_count_reset", todayKey).apply()
        }
        val today = prefs.getInt("comment_count_today", 0)
        val total = prefs.getInt("comment_count_total", 0)
        tv.text = "📊 Heute: $today | Total: $total"
    }
}
