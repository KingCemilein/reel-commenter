package com.reelcommenter

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast

class InstagramAccessibilityService : AccessibilityService() {
    companion object {
        var shouldPostComment = false
        var isRunning = false
    }

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
            val remaining = (delayMs - (now - lastPost)) / 1000
            showToast("Warte noch $remaining Sekunden!")
            shouldPostComment = false
            return
        }

        val root = rootInActiveWindow
        if (root == null) {
            showToast("Konnte Instagram-Fenster nicht lesen")
            shouldPostComment = false
            return
        }

        val commentText = prefs.getString("comment_text", "Passt auf euch auf") ?: "Passt auf euch auf"
      val editText = findEditText(root)
        if (editText != null) {
            val args = Bundle()
            args.putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                commentText
            )
            val success = editText.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            if (success) {
                Thread.sleep(300)

                val postButton = findNodeByText(root, "Posten")
                    ?: findNodeByText(root, "Teilen")
                    ?: findNodeByText(root, "Post")
                    ?: findNodeByText(root, "Share")

                if (postButton != null) {
                    val clicked = postButton.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    if (clicked) {
                        prefs.edit().putLong("last_post_time", now).apply()

                        showToast("Kommentar gepostet!")
                    } else {
                        showToast("Klicken fehlgeschlagen")
                    }
                } else {
                    showToast("Posten-Button nicht gefunden")
                }
            } else {
                showToast("Text eingeben fehlgeschlagen")
            }
        } else {
            showToast("Kommentarfeld nicht gefunden")
        }

        shouldPostComment = false
    }

    private fun findNodeByText(node: AccessibilityNodeInfo, text: String): AccessibilityNodeInfo? {
        if (node.text?.toString()?.contains(text, true) == true ||
            node.contentDescription?.toString()?.contains(text, true) == true
        ) {
            return node
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findNodeByText(child, text)
            if (found != null) return found

          }
        return null
    }

    private fun findEditText(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.className == "android.widget.EditText" || node.className == "android.widget.AutoCompleteTextView") {
            if (node.isEditable) return node
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findEditText(child)
            if (found != null) return found
        }
        return null
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
