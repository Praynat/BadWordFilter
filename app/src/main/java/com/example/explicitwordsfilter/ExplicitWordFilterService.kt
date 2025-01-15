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
import java.util.Stack

class ExplicitWordFilterService : AccessibilityService() {

    private val defaultWords: List<String> by lazy { loadDefaultWords() }
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var debounceJob: Job? = null
    private var contentChangeJob: Job? = null
    private lateinit var myPackageName: String
    private val wordFrequencyThreshold = 5
    private var blockedPackageName: String? = null

    companion object {
        private const val TAG = "ExplicitWordFilter"
    }

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
            Log.e(TAG, "Error reading DefaultWords.txt", e)
        }
        return words
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {

        if (isAppBlocked()) {

            if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                val currentPackage = event.packageName?.toString()
                if (currentPackage == blockedPackageName) {
                    enforceBlockIfNeeded()
                    return
                }
            }

        }
        event?.let {
            if (event.packageName != null && event.packageName == myPackageName) {
                return
            }

            when (event.eventType) {
                AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> {
                    handleTextChanged(event)
                }
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
                AccessibilityEvent.TYPE_VIEW_SCROLLED,
                AccessibilityEvent.TYPE_VIEW_FOCUSED,
                AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                    handleContentChanged(event)
                }
                else -> {
                    Log.d(TAG, "Event received: type=${event.eventType}, text=${event.text}")
                }
            }
        }
    }

    private fun handleContentChanged(event: AccessibilityEvent) {
        contentChangeJob?.cancel()

        contentChangeJob = serviceScope.launch {
            delay(1000)
            try {
                val rootNode = rootInActiveWindow
                if (rootNode == null) {
                    Log.d(TAG, "Root node is null")
                    return@launch
                }

                val extractedText = StringBuilder()
                traverseNodeAndExtractText(rootNode, extractedText)
                val textContent = extractedText.toString()

                Log.d(TAG, "Extracted text: $textContent")


                val detectedUrlRegex = Regex("URL:(\\S+)")
                val match = detectedUrlRegex.find(textContent)
                if (match != null) {
                    val urlFound = match.groupValues[1]
                    Log.d(TAG, "URL found: $urlFound")

                    // Vérifier si le site est bloqué
                    if (isSiteBlocked(urlFound)) {
                        Log.d(TAG, "Blocking content because site is in the blacklist")
                        blockContent(event)
                    }
                }


                if (textContent.isNotEmpty()) {
                    val frequencies = getExplicitWordFrequencies(textContent)
                    Log.d(TAG, "Word frequencies: $frequencies")

                    if (shouldBlockDueToFrequency(frequencies)) {
                        Log.d(TAG, "Blocking content due to frequency threshold")
                        // 1) Mark the offending package
                        blockedPackageName = event.packageName?.toString()

                        // 2) Block for 5 minutes
                        blockAppFor5Minutes()  // (no context param or pass applicationContext as needed)

                        // 3) Immediately force the user out of it now
                        blockContent(event)
                    }
                }

                rootNode.recycle()
            } catch (e: Exception) {
                Log.e(TAG, "Error in handleContentChanged", e)
            }
        }
    }


    private fun traverseNodeAndExtractText(
        node: AccessibilityNodeInfo,
        extractedText: StringBuilder,
        depth: Int = 0
    ) {
        if (depth > 30) return // Increase depth limit if necessary

        try {
            // 1) Lire le texte
            node.text?.let { nodeText ->
                val text = nodeText.toString().trim()
                if (text.isNotEmpty()) {
                    extractedText.append(text).append(" ")
                    Log.d(TAG, "Found text at depth $depth: $text")
                }
            }

            // 2) Lire la contentDescription
            node.contentDescription?.let { desc ->
                val description = desc.toString().trim()
                if (description.isNotEmpty()) {
                    extractedText.append(description).append(" ")
                    Log.d(TAG, "Found content description at depth $depth: $description")
                }
            }

            // 3) Vérifier si c’est la barre d’adresse du navigateur
            node.viewIdResourceName?.let { viewId ->
                // On cherche un indice comme "url_bar" ou "address_bar"
                if (viewId.contains("url_bar") || viewId.contains("address_bar")) {
                    val foundUrl = node.text?.toString()?.trim()
                    if (!foundUrl.isNullOrEmpty()) {
                        // On préfixe par "URL:" pour la repérer plus tard
                        extractedText.append("URL:$foundUrl ")
                        Log.d(TAG, "Detected URL at depth $depth: $foundUrl")
                    }
                }
            }

            // 4) Explorer récursivement les enfants
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { childNode ->
                    traverseNodeAndExtractText(childNode, extractedText, depth + 1)
                    childNode.recycle()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in traverseNodeAndExtractText at depth $depth", e)
        }
    }


    private fun getExplicitWordFrequencies(text: String): Map<String, Int> {
        val frequencies = mutableMapOf<String, Int>()
        val userWords = getUserAddedWords()
        val explicitWords = defaultWords + userWords
        val normalizedText = text.lowercase()

        explicitWords.forEach { word ->
            val pattern = "\\b${Regex.escape(word.lowercase())}\\b"
            val regex = Regex(pattern)
            val count = regex.findAll(normalizedText).count()
            if (count > 0) {
                frequencies[word] = count
                Log.d(TAG, "Found word '$word' $count times")
            }
        }
        return frequencies
    }

    private fun shouldBlockDueToFrequency(frequencies: Map<String, Int>): Boolean {
        frequencies.forEach { (word, count) ->
            if (count >= wordFrequencyThreshold) {
                Log.d(TAG, "Threshold exceeded for word '$word' with count $count")
                return true
            }
        }
        return false
    }

    private fun handleTextChanged(event: AccessibilityEvent) {
        debounceJob?.cancel()

        debounceJob = serviceScope.launch {
            delay(500)
            val text = getCurrentInputText(event)
            if (text != null && containsExplicitWordsDirectInput(text)) {
                blockContent(event)
            }
        }
    }

    private fun containsExplicitWordsDirectInput(text: String): Boolean {
        val userWords = getUserAddedWords()
        val explicitWords = defaultWords + userWords
        val normalizedText = text.lowercase()

        return explicitWords.any { word ->
            val pattern = "\\b${Regex.escape(word.lowercase())}\\b"
            val regex = Regex(pattern)
            regex.containsMatchIn(normalizedText)
        }
    }

    private fun getCurrentInputText(event: AccessibilityEvent): String? {
        val source = event.source ?: return null

        var node: AccessibilityNodeInfo? = source
        if (node?.className != "android.widget.EditText") {
            node = findEditTextNode(node)
        }

        return node?.text?.toString()
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
            child?.recycle()
        }
        return null
    }

    private fun blockContent(event: AccessibilityEvent?) {
        try {
            // Step 1: Clear text input
            clearTextInput(event?.source)

            // Step 2: Close all browser tabs
            closeAllBrowserTabs()

            for (i in 1..5) {
                val backSuccess = performGlobalAction(GLOBAL_ACTION_BACK)
                Log.d(TAG, if (backSuccess) "Pressed BACK successfully" else "Failed to press BACK")

                Thread.sleep(300)
            }
            // Step 3: Navigate Home to minimize the browser
            val homeSuccess = performGlobalAction(GLOBAL_ACTION_HOME)
            Log.d(TAG, if (homeSuccess) "Navigated to Home screen" else "Failed to navigate to Home screen")
        } catch (e: Exception) {
            Log.e(TAG, "Error in blockContent", e)
        }
    }

    private fun closeAllBrowserTabs() {
        val rootNode = rootInActiveWindow ?: return
        // Step 1: Open Tab Overview
        if (openTabOverview(rootNode)) {
            // Wait for the tab overview to open
            serviceScope.launch {
                delay(500)
                // Step 2: Close all tabs
                val overviewNode = rootInActiveWindow ?: return@launch
                closeTabsInOverview(overviewNode)
            }
        } else {
            Log.d(TAG, "Failed to open tab overview")
        }
    }

    private fun openTabOverview(node: AccessibilityNodeInfo?): Boolean {
        node?.let {
            for (i in 0 until it.childCount) {
                val child = it.getChild(i)
                val contentDesc = child?.contentDescription?.toString()?.lowercase() ?: ""
                val viewId = child?.viewIdResourceName?.lowercase() ?: ""

                if (contentDesc.contains("tab") || viewId.contains("tab")) {
                    val success = child.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    if (success) {
                        Log.d(TAG, "Opened tab overview")
                        return true
                    }
                }

                if (openTabOverview(child)) return true
            }
        }
        return false
    }

    private fun closeTabsInOverview(node: AccessibilityNodeInfo?) {
        node?.let {
            val stack = Stack<AccessibilityNodeInfo>()
            stack.push(it)

            while (stack.isNotEmpty()) {
                val currentNode = stack.pop()
                val contentDesc = currentNode.contentDescription?.toString()?.lowercase() ?: ""
                val viewId = currentNode.viewIdResourceName?.lowercase() ?: ""

                if (contentDesc.contains("close tab") || contentDesc.contains("dismiss tab") ||
                    viewId.contains("close_button") || viewId.contains("dismiss_button")) {
                    currentNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    Log.d(TAG, "Closed a tab")
                }

                for (i in 0 until currentNode.childCount) {
                    currentNode.getChild(i)?.let { child ->
                        stack.push(child)
                    }
                }
            }
        }
    }

    private fun clearTextInput(nodeInfo: AccessibilityNodeInfo?) {
        if (nodeInfo == null) return

        try {
            if (nodeInfo.className == "android.widget.EditText") {
                val arguments = Bundle()
                arguments.putCharSequence(
                    AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                    ""
                )
                nodeInfo.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
                Log.d(TAG, "Text cleared before closing the app.")
            } else {
                for (i in 0 until nodeInfo.childCount) {
                    val child = nodeInfo.getChild(i)
                    clearTextInput(child)
                    child?.recycle()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in clearTextInput", e)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "Service connected")
        val info = AccessibilityServiceInfo().apply {
            feedbackType = AccessibilityServiceInfo.FEEDBACK_ALL_MASK
            eventTypes = AccessibilityEvent.TYPES_ALL_MASK // Ensure all events are captured
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                    AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
            notificationTimeout = 100
        }
        serviceInfo = info
        Log.d(TAG, "Service connected and configured")
    }

    override fun onCreate() {
        super.onCreate()
        myPackageName = packageName
        Log.d(TAG, "Service created")
    }

    private fun getUserAddedWords(): Set<String> {
        val prefs = getSharedPreferences("ExplicitWordsPrefs", Context.MODE_PRIVATE)
        return prefs.getStringSet("userWords", emptySet()) ?: emptySet()
    }

    private fun loadBlockedSitesFromAssets(): List<String> {
        val sites = mutableListOf<String>()
        try {
            val inputStream = applicationContext.assets.open("AdultWebsitesFilterList.txt")
            inputStream.bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    val site = line.trim()
                    if (site.isNotEmpty()) {
                        sites.add(site)
                    }
                }
            }
        } catch (e: IOException) {
            Log.e(TAG, "Error reading AdultWebsitesFilterList.txt", e)
        }
        return sites
    }

    private fun isSiteBlocked(url: String): Boolean {
        // 1) Charger la liste des sites utilisateur et la liste par défaut
        val userSites = loadUserBlockedSites(applicationContext)  // ex: Set<String>
        val defaultSites = loadBlockedSitesFromAssets()           // ex: List<String>
        val allSites = defaultSites + userSites                   // fusion

        fun getDomainRoot(fullUrl: String): String {
            val cleaned = fullUrl.lowercase()
                .removePrefix("http://")
                .removePrefix("https://")
                .removePrefix("www.")


            val domainPart = cleaned.substringBefore("/")
            return domainPart.substringBeforeLast(".")
        }

        val detectedRoot = getDomainRoot(url)
        if (detectedRoot.isEmpty()) return false

        return allSites.any { blockedSite ->
            val blockedRoot = getDomainRoot(blockedSite)
            detectedRoot == blockedRoot
        }
    }
    private fun blockAppFor5Minutes() {
        val prefs = getSharedPreferences("BlockPrefs", Context.MODE_PRIVATE)
        val editor = prefs.edit()
        val blockEnd = System.currentTimeMillis() + 5 * 60 * 1000 // 5 minutes from now
        editor.putLong("blockEndTime", blockEnd)
        editor.apply()
    }

    private fun isAppBlocked(): Boolean {
        val prefs = getSharedPreferences("BlockPrefs", Context.MODE_PRIVATE)
        val blockEnd = prefs.getLong("blockEndTime", 0)
        return System.currentTimeMillis() < blockEnd
    }
    private fun enforceBlockIfNeeded() {
        // If the app is blocked, forcibly push user out (for example, go Home or do your "blockContent")
        if (isAppBlocked()) {
            Log.d(TAG, "App is currently in blocked state.")
            blockContent(null)  // or do your own block logic, e.g. performGlobalAction(GLOBAL_ACTION_HOME)
        }
    }


    override fun onInterrupt() {
        Log.d(TAG, "Service interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        Log.d(TAG, "Service destroyed")
    }
}
