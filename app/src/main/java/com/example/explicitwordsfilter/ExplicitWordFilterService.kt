package com.example.explicitwordsfilter

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.*
import java.io.IOException

class ExplicitWordFilterService : AccessibilityService() {

    private val defaultWords: List<String> by lazy { loadDefaultWords() }
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var debounceJob: Job? = null
    private lateinit var myPackageName: String


    private fun loadDefaultWords(): List<String> {
        val words = mutableListOf<String>()
        try {
            val inputStream = applicationContext.assets.open("DefaultWords.txt")
            inputStream.bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    val word = line.trim()
                    if (word.isNotEmpty()) {
                        words.add(word)
                    }
                }
            }
        } catch (e: IOException) {
            Log.e("ExplicitWordFilter", "Error reading DefaultWords.txt", e)
        }
        return words
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event?.let {
            if (event.packageName != null && event.packageName == myPackageName) {
                // Ignore events from your own app
                return
            }

            when (event.eventType) {
                AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> {
                    handleTextChanged(event)
                }
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
                AccessibilityEvent.TYPE_VIEW_SCROLLED,
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                    handleContentChanged(event)
                }
                AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED,
                AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                    handleImmediateDetection(event)
                }
                else -> {
                    Log.d("ExplicitWordFilter", "Event type not handled: ${event.eventType}")
                }
            }
        }
    }
    private var contentChangeJob: Job? = null

    private fun handleContentChanged(event: AccessibilityEvent) {
        // Cancel any existing content change job
        contentChangeJob?.cancel()

        // Start a new debounce job
        contentChangeJob = serviceScope.launch {
            delay(1000) // Wait for 1 second to debounce frequent events
            val rootNode = rootInActiveWindow ?: return@launch
            val extractedText = StringBuilder()
            traverseNodeAndExtractText(rootNode, extractedText)
            val textContent = extractedText.toString()

            if (textContent.isNotEmpty() && containsExplicitWords(textContent)) {
                // Option 1: Block content
                blockContent(event)
                // Option 2: Mask content
                // maskExplicitContent(rootNode)
            }
        }
    }
    private fun traverseNodeAndExtractText(
        node: AccessibilityNodeInfo,
        extractedText: StringBuilder,
        depth: Int = 0
    ) {
        if (depth > 10) return // Limit depth to prevent stack overflow
        if (node.childCount == 0) {
            node.text?.let {
                extractedText.append(it.toString()).append(" ")
            }
        } else {
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let {
                    traverseNodeAndExtractText(it, extractedText, depth + 1)
                }
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        val info = AccessibilityServiceInfo().apply {
            feedbackType = AccessibilityServiceInfo.FEEDBACK_ALL_MASK
            eventTypes = AccessibilityEvent.TYPES_ALL_MASK
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                    AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
        }
        this.serviceInfo = info
    }

    private fun handleTextChanged(event: AccessibilityEvent) {
        // Cancel any existing debounce job
        debounceJob?.cancel()

        // Start a new debounce job
        debounceJob = serviceScope.launch {
            delay(500) // Wait for 500 milliseconds
            val text = getCurrentInputText(event)
            if (text != null && containsExplicitWords(text)) {
                blockContent(event)
            }
        }
    }

    private fun handleImmediateDetection(event: AccessibilityEvent) {
        // Check if the event indicates an input submission
        if (isInputSubmissionEvent(event)) {
            // Cancel any pending debounce job
            debounceJob?.cancel()

            // Get the current text and process it immediately
            val text = getCurrentInputText(event)
            if (text != null && containsExplicitWords(text)) {
                blockContent(event)
            }
        }
    }

    private fun isInputSubmissionEvent(event: AccessibilityEvent): Boolean {
        val source = event.source ?: return false

        // Check for enter key press
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED) {
            if (event.action == AccessibilityNodeInfo.ACTION_NEXT_HTML_ELEMENT) {
                return true
            }
        }

        // Check for button clicks (e.g., Send, Search buttons)
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
            val className = source.className?.toString()
            if (className == "android.widget.Button" || className == "android.widget.ImageButton") {
                // Optionally, check content description or resource ID
                return true
            }
        }

        return false
    }

    private fun getCurrentInputText(event: AccessibilityEvent): String? {
        val source = event.source ?: return null

        // Navigate to the EditText node if necessary
        var node: AccessibilityNodeInfo? = source
        if (node?.className != "android.widget.EditText") {
            node = findEditTextNode(node)
        }

        return node?.text?.toString()
    }
    override fun onCreate() {
        super.onCreate()
        myPackageName = this.packageName
    }

    private fun findEditTextNode(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null

        if (node.className == "android.widget.EditText") {
            return node
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            val result = findEditTextNode(child)
            if (result != null) {
                return result
            }
        }
        return null
    }

    private fun clearTextInput(nodeInfo: AccessibilityNodeInfo?) {
        if (nodeInfo == null) return

        if (nodeInfo.className == "android.widget.EditText") {
            val arguments = Bundle()
            arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "")
            nodeInfo.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
            Log.d("ExplicitWordFilter", "Text cleared before closing the app.")
        } else {
            // Attempt to find EditText in child nodes
            for (i in 0 until nodeInfo.childCount) {
                val child = nodeInfo.getChild(i)
                clearTextInput(child)
            }
        }
    }

    private fun containsExplicitWords(text: String): Boolean {
        val userWords = getUserAddedWords()
        val explicitWords = defaultWords + userWords

        val normalizedText = text.lowercase()

        return explicitWords.any { word ->
            val pattern = "\\b${Regex.escape(word.lowercase())}\\b"
            val regex = Regex(pattern)
            regex.containsMatchIn(normalizedText)
        }
    }


    private fun getUserAddedWords(): Set<String> {
        val prefs = getSharedPreferences("ExplicitWordsPrefs", Context.MODE_PRIVATE)
        return prefs.getStringSet("userWords", emptySet()) ?: emptySet()
    }

    private fun blockContent(event: AccessibilityEvent) {
        val source = event.source ?: return

        // Clear the text input
        clearTextInput(source)

        // Perform a global action to navigate away from the current app
        val success = performGlobalAction(GLOBAL_ACTION_HOME)

        if (success) {
            Log.d("ExplicitWordFilter", "App closed due to explicit content.")
        } else {
            Log.d("ExplicitWordFilter", "Failed to close the app.")
        }
    }

    override fun onInterrupt() {
        // Handle interruption
        Log.d("ExplicitWordFilter", "Service interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel() // Cancel all coroutines when the service is destroyed
    }
}
