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
import kotlin.random.Random

class InstagramAccessibilityService : AccessibilityService() {
    companion object {
        var shouldPostComment = false
        var isRunning = false
    }

    // 5 verschiedene Kommentar-Varianten – Instagram sieht nicht immer denselben Text
    private val commentVariants = arrayOf(
        "Passt auf euch auf",
        "Passt auf euch auf 🙏",
        "Passt gut auf euch auf",
        "Passt auf euch auf! ❤️",
        "Passt auf euch auf Freunde"
    )

    // Alle bekannten Texte für den Posten-Button (Deutsch + Englisch + Variationen)
    private val postButtonTexts = arrayOf(
        "Posten", "Teilen", "Post", "Share", "Publish",
        "Veröffentlichen", "Senden", "Kommentieren",
        "Comment", "Send", "Sende", "Absenden"
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

        // Rate-Limit prüfen
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

        // Zufällige kleine Pause (menschenähnlich)
        Thread.sleep((200 + Random.nextInt(400)).toLong())

        // 1. Kommentarfeld finden
        val editText = findCommentField(root)
        if (editText == null) {
            showToast("❌ Kommentarfeld nicht gefunden")
            shouldPostComment = false
            return
        }

        // 2. Zufälligen Kommentar wählen
        val selectedComment = commentVariants[Random.nextInt(commentVariants.size)]

        // 3. Text eingeben
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

        showToast("✍️ Text: $selectedComment")

        // 4. Zufällige Pause vor Klick
        Thread.sleep((300 + Random.nextInt(500)).toLong())

        // 5. Posten-Button finden (versucht 6 verschiedene Methoden)
        var postButton = findPostButton(root)

        if (postButton != null) {
            // Standard: Button gefunden und klicken
            val clicked = postButton.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            if (clicked) {
                prefs.edit().putLong("last_post_time", now).apply()
                showToast("✅ Kommentar gepostet!")
                vibrate()
            } else {
                showToast("❌ Klicken fehlgeschlagen")
            }
        } else {
            // FALLBACK: Koordinaten-Tap (wenn Text nicht gefunden wird)
            // Instagram Posten-Button ist meist unten rechts
            showToast("⚠️ Button nicht gefunden, tippe Koordinaten...")
            val metrics = resources.displayMetrics
            val x = (metrics.widthPixels * 0.85).toFloat()  // 85% von links
            val y = (metrics.heightPixels * 0.88).toFloat() // 88% von oben
            tapScreen(x, y)
            Thread.sleep(400)
            prefs.edit().putLong("last_post_time", now).apply()
            showToast("✅ Kommentar gepostet (Fallback)!")
            vibrate()
        }

        shouldPostComment = false
    }

    // ===== HILFSFUNKTIONEN =====

    private fun findCommentField(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        // Methode 1: Bekannte Platzhalter-Texte
        val hints = arrayOf(
            "Kommentar hinzufügen", "Kommentar", "Kommentieren",
            "Add a comment", "Comment", "Schreib einen Kommentar",
            "Write a comment", "Add comment", "Kommentar hinzufügen ..."
        )
        for (hint in hints) {
            val node = findNodeByText(root, hint)
            if (node != null && isEditText(node)) {
                return node
            }
        }

        // Methode 2: Einfach das erste EditText-Element nehmen
        return findFirstEditText(root)
    }

    private fun isEditText(node: AccessibilityNodeInfo): Boolean {
        return node.className?.contains("EditText", true) == true ||
               node.isEditable
    }

    private fun findPostButton(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        // Methode 1: Suche nach bekannten Texten
        for (text in postButtonTexts) {
            val node = findNodeByText(root, text)
            if (node != null && isClickableButton(node)) {
                return node
            }
        }

        // Methode 2: Suche nach ContentDescription
        val descTexts = arrayOf("Posten", "Post", "Share", "Teilen", "Senden", "Comment")
        for (desc in descTexts) {
            val node = findNodeByContentDescription(root, desc)
            if (node != null && isClickableButton(node)) {
                return node
            }
        }

        // Methode 3: Suche alle klickbaren Elemente mit kurzem Text
        return findAnyClickableButtonWithText(root)
    }

    private fun isClickableButton(node: AccessibilityNodeInfo): Boolean {
        return node.isClickable ||
               node.isFocusable ||
               node.className?.contains("Button", true) == true ||
               node.className?.contains("TextView", true) == true
    }

    private fun findNodeByText(node: AccessibilityNodeInfo, text: String): AccessibilityNodeInfo? {
        if (node.text?.toString()?.contains(text, true) == true) {
            return node
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findNodeByText(child, text)
            if (found != null) return found
        }
        return null
    }

    private fun findNodeByContentDescription(node: AccessibilityNodeInfo, text: String): AccessibilityNodeInfo? {
        if (node.contentDescription?.toString()?.contains(text, true) == true) {
            return node
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findNodeByContentDescription(child, text)
            if (found != null) return found
        }
        return null
    }

    private fun findFirstEditText(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (isEditText(node)) {
            return node
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findFirstEditText(child)
            if (found != null) return found
        }
        return null
    }

    private fun findAnyClickableButtonWithText(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (isClickableButton(node) && !node.text.isNullOrBlank()) {
            val txt = node.text.toString()
            // Instagram Buttons sind kurz (2–20 Zeichen)
            if (txt.length in 2..20) {
                return node
            }
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findAnyClickableButtonWithText(child)
            if (found != null) return found
        }
        return null
    }

    private fun tapScreen(x: Float, y: Float) {
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
