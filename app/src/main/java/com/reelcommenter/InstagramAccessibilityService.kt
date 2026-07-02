package com.reelcommenter

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.Path
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import java.util.Calendar
import kotlin.random.Random

class InstagramAccessibilityService : AccessibilityService() {
    companion object {
        var shouldPostComment = false
        var isRunning = false
        var autoModeActive = false
    }

    private val postTexts = arrayOf("Posten","Teilen","Post","Share","Publish","Veröffentlichen","Senden","Kommentieren","Comment","Send","Sende","Absenden","Hochladen")
    private val commentHints = arrayOf("Kommentar hinzufügen","Kommentar","Kommentieren","Add a comment","Comment","Schreib einen Kommentar","Write a comment","Add comment","Kommentar hinzufügen ...")

    override fun onServiceConnected() {
        super.onServiceConnected()
        isRunning = true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.packageName?.toString() != "com.instagram.android") return

        val prefs = getSharedPreferences("reel_commenter", Context.MODE_PRIVATE)
        autoModeActive = prefs.getBoolean("auto_mode", false)

        if (autoModeActive && !shouldPostComment) {
            val now = System.currentTimeMillis()
            val lastPost = prefs.getLong("last_post_time", 0)
            val minDelay = prefs.getInt("delay_min", 12) * 1000
            val maxDelay = prefs.getInt("delay_max", 20) * 1000
            val range = (maxDelay - minDelay + 1).coerceAtLeast(1)
            val needed = minDelay + Random.nextInt(range)
            if (now - lastPost >= needed) {
                shouldPostComment = true
            }
        }

        if (!shouldPostComment) return

        val now = System.currentTimeMillis()
        val lastPost = prefs.getLong("last_post_time", 0)
        val minDelay = prefs.getInt("delay_min", 12) * 1000
        val maxDelay = prefs.getInt("delay_max", 20) * 1000
        val range = (maxDelay - minDelay + 1).coerceAtLeast(1)
        val needed = minDelay + Random.nextInt(range)

        if (now - lastPost < needed) {
            val remaining = (needed - (now - lastPost)) / 1000 + 1
            showToast("⏳ Warte noch $remaining Sek.")
            shouldPostComment = false
            return
        }

        val root = rootInActiveWindow
        if (root == null) {
            showToast("❌ Fenster nicht lesbar")
            shouldPostComment = false
            return
        }

        Thread.sleep((200 + Random.nextInt(600)).toLong())

        val editText = findCommentField(root)
        if (editText == null) {
            showToast("❌ Kommentarfeld nicht gefunden")
            shouldPostComment = false
            return
        }

        val comment = pickRandomComment(prefs)
        val args = Bundle()
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, comment)
        val success = editText.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)

        if (!success) {
            showToast("❌ Text eingeben fehlgeschlagen")
            shouldPostComment = false
            return
        }

        Thread.sleep((300 + Random.nextInt(500)).toLong())

        var postBtn = findPostButton(root)
        if (postBtn != null) {
            if (postBtn.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                onSuccess(prefs, now)
            } else {
                tryFallbackOrFail(prefs, now)
            }
        } else {
            tryFallbackOrFail(prefs, now)
        }

        shouldPostComment = false
    }

    private fun tryFallbackOrFail(prefs: android.content.SharedPreferences, now: Long) {
        if (prefs.getBoolean("use_fallback_coords", false)) {
            val x = prefs.getInt("fallback_x", 85)
            val y = prefs.getInt("fallback_y", 88)
            tapScreenPercent(x, y)
            Thread.sleep(400)
            onSuccess(prefs, now)
        } else {
            showToast("❌ Posten-Button nicht gefunden. Aktiviere Fallback!")
        }
    }

    private fun pickRandomComment(prefs: android.content.SharedPreferences): String {
        val poolKey = when (prefs.getInt("active_pool", 0)) {
            1 -> "pool2"
            2 -> "pool3"
            else -> "pool1"
        }
        val raw = prefs.getString(poolKey, "")?.trim() ?: ""
        val items = raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        return if (items.isNotEmpty()) items[Random.nextInt(items.size)] else "Passt auf euch auf"
    }

    private fun onSuccess(prefs: android.content.SharedPreferences, now: Long) {
        val cal = Calendar.getInstance()
        val todayKey = "${cal.get(Calendar.YEAR)}-${cal.get(Calendar.MONTH)}-${cal.get(Calendar.DAY_OF_MONTH)}"
        val lastReset = prefs.getString("last_count_reset", "")
        if (lastReset != todayKey) {
            prefs.edit().putInt("comment_count_today", 0).putString("last_count_reset", todayKey).apply()
        }
        val today = prefs.getInt("comment_count_today", 0)
        val total = prefs.getInt("comment_count_total", 0)
        prefs.edit()
            .putLong("last_post_time", now)
            .putInt("comment_count_today", today + 1)
            .putInt("comment_count_total", total + 1)
            .apply()

        showToast("✅ Gepostet! (Heute: ${today + 1})")

        if (prefs.getBoolean("sound_enabled", true)) {
            try {
                ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100).startTone(ToneGenerator.TONE_PROP_BEEP, 150)
            } catch (_: Exception) {}
        }
        if (prefs.getBoolean("vibration_enabled", true)) {
            vibrate()
        }
    }

    private fun findCommentField(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        for (h in commentHints) {
            val n = findNodeByText(root, h)
            if (n != null && isEditText(n)) return n
        }
        return findFirstEditText(root)
    }

    private fun isEditText(n: AccessibilityNodeInfo): Boolean {
        return n.className?.contains("EditText", true) == true || n.isEditable
    }

    private fun findPostButton(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        for (t in postTexts) {
            val n = findNodeByText(root, t)
            if (n != null && isClickableButton(n)) return n
        }
        for (t in postTexts) {
            val n = findNodeByDesc(root, t)
            if (n != null && isClickableButton(n)) return n
        }
        return findAnyClickableWithShortText(root)
    }

    private fun isClickableButton(n: AccessibilityNodeInfo): Boolean {
        return n.isClickable || n.isFocusable || n.className?.contains("Button", true) == true || n.className?.contains("TextView", true) == true
    }

    private fun findNodeByText(node: AccessibilityNodeInfo, text: String): AccessibilityNodeInfo? {
        if (node.text?.toString()?.contains(text, true) == true) return node
        for (i in 0 until node.childCount) {
            val c = node.getChild(i) ?: continue
            val f = findNodeByText(c, text)
            if (f != null) return f
        }
        return null
    }

    private fun findNodeByDesc(node: AccessibilityNodeInfo, text: String): AccessibilityNodeInfo? {
        if (node.contentDescription?.toString()?.contains(text, true) == true) return node
        for (i in 0 until node.childCount) {
            val c = node.getChild(i) ?: continue
            val f = findNodeByDesc(c, text)
            if (f != null) return f
        }
        return null
    }

    private fun findFirstEditText(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (isEditText(node)) return node
        for (i in 0 until node.childCount) {
            val c = node.getChild(i) ?: continue
            val f = findFirstEditText(c)
            if (f != null) return f
        }
        return null
    }

    private fun findAnyClickableWithShortText(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (isClickableButton(node) && !node.text.isNullOrBlank()) {
            val t = node.text.toString()
            if (t.length in 2..25) return node
        }
        for (i in 0 until node.childCount) {
            val c = node.getChild(i) ?: continue
            val f = findAnyClickableWithShortText(c)
            if (f != null) return f
        }
        return null
    }

    private fun tapScreenPercent(xPct: Int, yPct: Int) {
        val m = resources.displayMetrics
        val x = (m.widthPixels * xPct / 100f)
        val y = (m.heightPixels * yPct / 100f)
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 100))
            .build()
        dispatchGesture(gesture, null, null)
    }

    private fun vibrate() {
        val v = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(200)
        }
    }

    private fun showToast(msg: String) {
        Toast.makeText(applicationContext, msg, Toast.LENGTH_SHORT).show()
    }

    override fun onInterrupt() {}
    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
    }
}                              
