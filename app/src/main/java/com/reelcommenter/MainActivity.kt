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

        val editText = findViewById<EditText>(R.id.commentText)
        val delayPicker = findViewById<NumberPicker>(R.id.delayPicker)
        val startButton = findViewById<Button>(R.id.startButton)
        val stopButton = findViewById<Button>(R.id.stopButton)
        val accessibilityButton = findViewById<Button>(R.id.accessibilityButton)
        val commentListInput = findViewById<EditText>(R.id.commentListInput)
        val languageSpinner = findViewById<Spinner>(R.id.languageSpinner)
        val fallbackXPicker = findViewById<NumberPicker>(R.id.fallbackXPicker)
        val fallbackYPicker = findViewById<NumberPicker>(R.id.fallbackYPicker)
        val fallbackToggle = findViewById<CompoundButton>(R.id.fallbackToggle)
        val resetStatsButton = findViewById<Button>(R.id.resetStatsButton)
        val statsText = findViewById<TextView>(R.id.statsText)

        delayPicker.minValue = 5
        delayPicker.maxValue = 60

        fallbackXPicker.minValue = 0
        fallbackXPicker.maxValue = 100
        fallbackYPicker.minValue = 0
        fallbackYPicker.maxValue = 100

        val prefs = getSharedPreferences("reel_commenter", Context.MODE_PRIVATE)

        editText.setText(prefs.getString("comment_text", "Passt auf euch auf"))
        delayPicker.value = prefs.getInt("delay_seconds", 12)
        commentListInput.setText(prefs.getString("comment_list", ""))
        fallbackXPicker.value = prefs.getInt("fallback_x", 85)
        fallbackYPicker.value = prefs.getInt("fallback_y", 88)
        fallbackToggle.isChecked = prefs.getBoolean("use_fallback_coords", false)

        val languages = arrayOf("Deutsch", "English")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, languages)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        languageSpinner.adapter = adapter
        languageSpinner.setSelection(prefs.getInt("language_index", 0))

        updateStats(statsText, prefs)

        languageSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                prefs.edit().putInt("language_index", position).apply()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        resetStatsButton.setOnClickListener {
            prefs.edit().putInt("comment_count_today", 0).putInt("comment_count_total", 0).apply()
            updateStats(statsText, prefs)
            Toast.makeText(this, "Statistik zurückgesetzt", Toast.LENGTH_SHORT).show()
        }

        accessibilityButton.setOnClickListener {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
            Toast.makeText(this, "Aktiviere 'Reel Commenter' in der Liste und kehre zurück", Toast.LENGTH_LONG).show()
        }

        startButton.setOnClickListener {
            saveSettings(prefs, editText, delayPicker, commentListInput, fallbackXPicker, fallbackYPicker, fallbackToggle)

            if (!Settings.canDrawOverlays(this)) {
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
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

    private fun saveSettings(
        prefs: android.content.SharedPreferences,
        editText: EditText,
        delayPicker: NumberPicker,
        commentList: EditText,
        xPicker: NumberPicker,
        yPicker: NumberPicker,
        fallbackToggle: CompoundButton
    ) {
        prefs.edit()
            .putString("comment_text", editText.text.toString())
            .putInt("delay_seconds", delayPicker.value)
            .putString("comment_list", commentList.text.toString())
            .putInt("fallback_x", xPicker.value)
            .putInt("fallback_y", yPicker.value)
            .putBoolean("use_fallback_coords", fallbackToggle.isChecked)
            .apply()
    }

    private fun updateStats(textView: TextView, prefs: android.content.SharedPreferences) {
        val cal = Calendar.getInstance()
        val todayKey = "${cal.get(Calendar.YEAR)}-${cal.get(Calendar.MONTH)}-${cal.get(Calendar.DAY_OF_MONTH)}"
        val lastReset = prefs.getString("last_count_reset", "")

        val todayCount = if (lastReset == todayKey) prefs.getInt("comment_count_today", 0) else 0
        val totalCount = prefs.getInt("comment_count_total", 0)

        textView.text = "📊 Heute: $todayCount  |  Total: $totalCount"
    }
}
