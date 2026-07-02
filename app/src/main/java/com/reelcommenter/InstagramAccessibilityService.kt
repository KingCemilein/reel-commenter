                  package com.reelcommenter

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.Path
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
    }

    private val postButtonTexts = arrayOf(
        "Posten", "Teilen", "Post", "Share", "Publish",
        "Veröffentlichen", "Senden", "Kommentieren",
        "Comment", "Send", "Sende", "Absenden", "Hochladen"
    )

    private val commentHints = arrayOf(
        "Kommentar hinzufügen", "Kommentar", "Kommentieren",
        "Add a comment", "Comment", "Schreib einen Kommentar",
        "Write a comment", "Add comment", "Kommentar hinzufügen ..."
    )

    override fun onServiceConnected() {
        super.onServiceConnected()
        isRunning = true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (!shouldPostComment) return

        if (event.packageName?.toString() != "com.instagram.android") {
            shouldPostComment = false
            return
        }

        val prefs = getSharedPreferences("reel_commenter", Context.MODE_PRIVATE)
        val delayMs = prefs.getInt("delay_seconds", 12) * 1000
        val lastPost = prefs.getLong("last_post_time", 0)
        val now = System.currentTimeMillis()

        if (now - lastPost < delayMs) {
            val remaining = (delayMs - (now - lastPost)) / 1000 + 1
            showToast("⏳ Warte noch $remaining Sekunden!")
            shouldPostComment = false
            return
        }

        val root = rootInActiveWindow
        if (root == null) {
            showToast("❌ Konnte Instagram-Fenster nicht lesen")
            shouldPostComment = false
            return
        }

        Thread.sleep((200 + Random.nextInt(400)).toLong())

        val editText = findCommentField(root)
        if (editText == null) {
            showToast("❌ Kommentarfeld nicht gefunden")
            shouldPostComment = false
            return
        }

        val selectedComment = pickRandomComment(prefs)

        val args = Bundle()
        args.putCharSequence(
            AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
            selectedComment
        )
        val textSuccess = editText.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)

        if (!textSuccess) {
            showToast("❌ Text eingeben fehlgeschlagen")
            shouldPostComment = false
            return
        }

        showToast("✍️ Text eingefügt")

        Thread.sleep((300 + Random.nextInt(500)).toLong())

        var postButton = findPostButton(root)

        if (postButton != null) {
            val clicked = postButton.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            if (clicked) {
                onPostSuccess(prefs, now)
            } else {
                showToast("❌ Klicken fehlgeschlagen")
            }
        } else {
            val useFallback = prefs.getBoolean("use_fallback_coords", false)
            if (useFallback) {
                val xPct = prefs.getInt("fallback_x", 85)
                val yPct = prefs.getInt("fallback_y", 88)
                showToast("⚠️ Button nicht gefunden, tippe $xPct%/$yPct%...")
                tapScreenPercent(xPct, yPct)
                Thread.sleep(400)
                onPostSuccess(prefs, now)
            } else {
                showToast("❌ Posten-Button nicht gefunden. Aktiviere Koordinaten-Fallback in der App!")
            }
        }

        shouldPostComment = false
    }

    private fun pickRandomComment(prefs: android.content.SharedPreferences): String {
        val customList = prefs.getString("comment_list", "")?.trim() ?: ""
        return if (customList.isNotEmpty()) {
            val items = customList.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            if (items.isNotEmpty()) items[Random.nextInt(items.size)] else "Passt auf euch auf"
        } else {
            val defaults = arrayOf(
                "Passt auf euch auf",
                "Passt auf euch auf 🙏",
                "Passt gut auf euch auf",
                "Passt auf euch auf! ❤️",
                "Passt auf euch auf Freunde"
            )
            defaults[Random.nextInt(defaults.size)]
        }
    }

    private fun onPostSuccess(prefs: android.content.SharedPreferences, now: Long) {
        val cal = Calendar.getInstance()
        val todayKey = "${cal.get(Calendar.YEAR)}-${cal.get(Calendar.MONTH)}-${cal.get(Calendar.DAY_OF_MONTH)}"
        val lastReset = prefs.getString("last_count_reset", "")

        if (lastReset != todayKey) {
            prefs.edit().putInt("comment_count_today", 0).putString("last_count_reset", todayKey).apply()
        }

        val todayCount = prefs.getInt("comment_count_today", 0)
        val totalCount = prefs.getInt("comment_count_total", 0)

        prefs.edit()
            .putLong("last_post_time", now)
            .putInt("comment_count_today", todayCount + 1)
            .putInt("comment_count_total", totalCount + 1)
            .apply()

        showToast("✅ Kommentar gepostet! (Heute: ${todayCount + 1})")
        vibrate()
    }

    private fun findCommentField(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        for (hint in commentHints) {
            val node = findNodeByText(root, hint)
            if (node != null && isEditText(node)) return node
        }
        return findFirstEditText(root)
    }

    private fun isEditText(node: AccessibilityNodeInfo): Boolean {
        return node.className?.contains("EditText", true) == true || node.isEditable
    }

    private fun findPostButton(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        for (text in postButtonTexts) {
            val node = findNodeByText(root, text)
            if (node != null && isClickableButton(node)) return node
        }
        for (text in postButtonTexts) {
            val node = findNodeByDesc(root, text)
            if (node != null && isClickableButton(node)) return node
        }
        return findAnyClickableWithShortText(root)
    }

    private fun isClickableButton(node: AccessibilityNodeInfo): Boolean {
        return node.isClickable || node.isFocusable ||
            node.className?.contains("Button", true) == true ||
            node.className?.contains("TextView", true) == true
    }

    private fun findNodeByText(node: AccessibilityNodeInfo, text: String): AccessibilityNodeInfo? {
        if (node.text?.toString()?.contains(text, true) == true) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findNodeByText(child, text)
            if (found != null) return found
        }
        return null
    }

    private fun findNodeByDesc(node: AccessibilityNodeInfo, text: String): AccessibilityNodeInfo? {
        if (node.contentDescription?.toString()?.contains(text, true) == true) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findNodeByDesc(child, text)
            if (found != null) return found
        }
        return null
    }

    private fun findFirstEditText(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (isEditText(node)) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findFirstEditText(child)
            if (found != null) return found
        }
        return null
    }

    private fun findAnyClickableWithShortText(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (isClickableButton(node) && !node.text.isNullOrBlank()) {
            val txt = node.text.toString()
            if (txt.length in 2..25) return node
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findAnyClickableWithShortText(child)
            if (found != null) return found
        }
        return null
    }

    private fun tapScreenPercent(xPercent: Int, yPercent: Int) {
        val metrics = resources.displayMetrics
        val x = (metrics.widthPixels * xPercent / 100f)
        val y = (metrics.heightPixels * yPercent / 100f)
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 100))
            .build()
        dispatchGesture(gesture, null, null)
    }

    private fun vibrate() {
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(200)
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
    }

    override fun onInterrupt() {}
    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
    }
}                          
