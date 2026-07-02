package com.reelcommenter

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat

class FloatingButtonService : Service() {
    private lateinit var windowManager: WindowManager
    private lateinit var floatingView: View
    private var initialX: Int = 0
    private var initialY: Int = 0
    private var initialTouchX: Float = 0f
    private var initialTouchY: Float = 0f
    private var isMoving = false
    private lateinit var countdownText: TextView
    private val handler = Handler(Looper.getMainLooper())
    private var countdownRunnable: Runnable? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForeground()

        floatingView = LayoutInflater.from(this).inflate(R.layout.floating_button, null)

        val prefs = getSharedPreferences("reel_commenter", Context.MODE_PRIVATE)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )

        params.gravity = Gravity.TOP or Gravity.START
        params.x = prefs.getInt("button_x", 100)
        params.y = prefs.getInt("button_y", 200)

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager.addView(floatingView, params)

        val button = floatingView.findViewById<ImageButton>(R.id.floatingActionButton)
        countdownText = floatingView.findViewById(R.id.countdownText)

        button.setOnClickListener {
            if (InstagramAccessibilityService.isRunning) {
                InstagramAccessibilityService.shouldPostComment = true
                Toast.makeText(this, "Kommentar wird gepostet...", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Accessibility-Service nicht aktiv! Öffne die App.", Toast.LENGTH_LONG).show()
                val intent = Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                startActivity(intent)
            }
        }

        button.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isMoving = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (kotlin.math.abs(dx) > 10 || kotlin.math.abs(dy) > 10) {
                        isMoving = true
                    }
                    params.x = initialX + dx
                    params.y = initialY + dy
                    windowManager.updateViewLayout(floatingView, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isMoving) {
                        button.performClick()
                    } else {
                        prefs.edit().putInt("button_x", params.x).putInt("button_y", params.y).apply()
                    }
                    true
                }
                else -> false
            }
        }

        startCountdown()
    }

    private fun startCountdown() {
        val runnable = object : Runnable {
            override fun run() {
                val prefs = getSharedPreferences("reel_commenter", Context.MODE_PRIVATE)
                val delayMs = prefs.getInt("delay_seconds", 12) * 1000
                val lastPost = prefs.getLong("last_post_time", 0)
                val now = System.currentTimeMillis()
                val remaining = ((delayMs - (now - lastPost)) / 1000).toInt()

                if (remaining > 0) {
                    countdownText.text = "$remaining"
                    countdownText.visibility = View.VISIBLE
                } else {
                    countdownText.visibility = View.GONE
                }

                handler.postDelayed(this, 1000)
            }
        }
        countdownRunnable = runnable
        handler.post(runnable)
    }

    private fun startForeground() {
        val channelId = "reel_commenter_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Reel Commenter", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }

        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE)

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Reel Commenter")
            .setContentText("Schwebender Button ist aktiv")
            .setSmallIcon(android.R.drawable.ic_menu_edit)
            .setContentIntent(pendingIntent)
            .build()

        startForeground(1, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        countdownRunnable?.let { handler.removeCallbacks(it) }
        if (::floatingView.isInitialized) {
            windowManager.removeView(floatingView)
        }
    }
}
