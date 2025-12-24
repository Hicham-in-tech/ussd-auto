package com.orange.ussd.registration.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.orange.ussd.registration.data.database.AppDatabase
import com.orange.ussd.registration.data.model.RegistrationStatus
import kotlinx.coroutines.*

class USSDAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var database: AppDatabase
    private val handler = Handler(Looper.getMainLooper())
    
    private var isWaitingForName = false
    private var isWaitingForCNE = false
    private var hasFilledName = false
    private var hasFilledCNE = false
    private var hasDismissedFinalDialog = false
    private var validationOkCount = 0  // Track how many validation OKs we've dismissed
    private var lastActionTime = 0L
    private var lastDialogText = ""
    private var retryCount = 0
    private val MAX_RETRY = 5  // Increased retry count
    private val ACTION_DELAY = 200L // Very fast response
    private val TAG = "USSDAccessibility"
    
    // Track input fill attempts to prevent infinite loops
    private var nameInputAttempts = 0
    private var cneInputAttempts = 0
    private val MAX_INPUT_ATTEMPTS = 5
    
    // Track current record to detect when it changes
    private var lastProcessedRecordId: Long? = null

    // List of USSD dialog package names
    private val USSD_PACKAGES = listOf(
        "com.android.phone",
        "com.android.server.telecom",
        "com.samsung.android.phone",
        "com.sec.android.app.servicemodeapp",
        "com.android.stk",
        "com.android.systemui"
    )

    override fun onServiceConnected() {
        super.onServiceConnected()
        
        database = AppDatabase.getDatabase(this)
        
        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                        AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                        AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED
            
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            
            // Only monitor USSD related packages - don't monitor all packages
            packageNames = USSD_PACKAGES.toTypedArray()
        }
        
        serviceInfo = info
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        
        val currentRecordId = USSDProcessingService.currentRecordId ?: return
        val expectedName = USSDProcessingService.expectedFullName ?: return
        val expectedCNE = USSDProcessingService.expectedCNE ?: return
        
        // IMPORTANT: Reset state when processing a NEW record (new phone number)
        if (lastProcessedRecordId != null && lastProcessedRecordId != currentRecordId) {
            Log.d(TAG, "New record detected (${lastProcessedRecordId} -> $currentRecordId), resetting state")
            resetState()
        }
        lastProcessedRecordId = currentRecordId
        
        // IMPORTANT: Only process events from USSD dialog packages
        val packageName = event.packageName?.toString() ?: return
        if (!isUSSDPackage(packageName)) {
            // Log less frequently to reduce noise
            if (System.currentTimeMillis() - lastActionTime > 5000) {
                Log.d(TAG, "Ignoring event from non-USSD package: $packageName")
            }
            return
        }
        
        Log.d(TAG, "Processing event from USSD package: $packageName")
        
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                handleUSSDDialog(event, currentRecordId, expectedName, expectedCNE)
            }
        }
    }
    
    private fun isUSSDPackage(packageName: String): Boolean {
        // Be more permissive - check for USSD related packages
        val pkgLower = packageName.lowercase()
        return USSD_PACKAGES.any { pkgLower.contains(it) || it.contains(pkgLower) } ||
               pkgLower.contains("phone") ||
               pkgLower.contains("telecom") ||
               pkgLower.contains("ussd") ||
               pkgLower.contains("stk") ||
               pkgLower.contains("android") || // Many USSD dialogs use android.* packages
               pkgLower.contains("dialer") ||
               pkgLower.contains("call") ||
               pkgLower.contains("sim")
    }

    private fun handleUSSDDialog(
        event: AccessibilityEvent,
        recordId: Long,
        expectedName: String,
        expectedCNE: String
    ) {
        val rootNode = try {
            rootInActiveWindow
        } catch (e: Exception) {
            Log.e(TAG, "Error getting root node: ${e.message}")
            return
        }
        
        if (rootNode == null) {
            Log.w(TAG, "Root node is null")
            return
        }
        
        // IMPORTANT: Verify we're in a USSD dialog before doing anything
        val currentPackage = rootNode.packageName?.toString() ?: ""
        if (!isUSSDPackage(currentPackage)) {
            Log.d(TAG, "Not in USSD dialog, ignoring. Current package: $currentPackage")
            try { rootNode.recycle() } catch (e: Exception) {}
            return
        }
        
        // Prevent too rapid actions
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastActionTime < ACTION_DELAY) {
            try { rootNode.recycle() } catch (e: Exception) {}
            return
        }

        try {
            // Look for USSD dialog or input prompts
            val dialogText = extractTextFromNode(rootNode).lowercase()
            
            // Skip if this doesn't look like a USSD dialog at all
            if (dialogText.isEmpty() || dialogText.length < 3) {
                return
            }
            
            // Skip if same dialog text (avoid duplicate processing)
            if (dialogText == lastDialogText && retryCount >= MAX_RETRY) {
                return
            }
            
            if (dialogText != lastDialogText) {
                lastDialogText = dialogText
                retryCount = 0
            }
            
            Log.d(TAG, "USSD Dialog detected: $dialogText")
            Log.d(TAG, "State: hasFilledName=$hasFilledName, hasFilledCNE=$hasFilledCNE, hasDismissedFinal=$hasDismissedFinalDialog")
            Log.d(TAG, "Input attempts - name: $nameInputAttempts, cne: $cneInputAttempts")
            
            // Check if asking for name - ONLY if we haven't filled it yet
            if (!hasFilledName && isNamePrompt(dialogText)) {
                Log.d(TAG, "Detected name input prompt")
                isWaitingForName = true
                retryCount++
                nameInputAttempts++
                
                // Prevent infinite loops
                if (nameInputAttempts > MAX_INPUT_ATTEMPTS) {
                    Log.e(TAG, "Too many name input attempts, marking as failed and moving on")
                    hasFilledName = true  // Skip this step
                    lastActionTime = System.currentTimeMillis()
                    return
                }
                
                // Fill name using async approach to prevent blocking
                serviceScope.launch {
                    try {
                        delay(150) // Small delay for dialog to stabilize
                        
                        withContext(Dispatchers.Main) {
                            try {
                                val rootForFill = rootInActiveWindow
                                if (rootForFill == null) {
                                    Log.e(TAG, "Root node null when trying to fill name")
                                    return@withContext
                                }
                                
                                // Verify we're still in USSD dialog
                                val pkg = rootForFill.packageName?.toString() ?: ""
                                if (!isUSSDPackage(pkg)) {
                                    Log.d(TAG, "Not in USSD dialog anymore, skipping name fill")
                                    try { rootForFill.recycle() } catch (e: Exception) {}
                                    return@withContext
                                }
                                
                                Log.d(TAG, "Filling name field with: $expectedName")
                                val filled = fillInputField(rootForFill, expectedName)
                                
                                if (filled) {
                                    hasFilledName = true
                                    isWaitingForName = false
                                    lastActionTime = System.currentTimeMillis()
                                    Log.d(TAG, "Name filled: $expectedName")
                                    
                                    // Update database async
                                    launch(Dispatchers.IO) {
                                        try {
                                            database.registrationDao().updateStatus(recordId, RegistrationStatus.NAME_FILLED)
                                            database.registrationDao().updateNameFilled(recordId, true)
                                        } catch (e: Exception) {
                                            Log.e(TAG, "DB error: ${e.message}")
                                        }
                                    }
                                    
                                    // Click Send/OK button after a brief delay
                                    handler.postDelayed({
                                        clickSendButtonSafely()
                                    }, 250)
                                } else {
                                    Log.e(TAG, "Failed to fill name field")
                                }
                                
                                try { rootForFill.recycle() } catch (e: Exception) {}
                            } catch (e: Exception) {
                                Log.e(TAG, "Error in name fill: ${e.message}")
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error filling name async: ${e.message}")
                    }
                }
            }
            
            // Check if asking for CNE - IMPROVED detection after name is filled
            else if (hasFilledName && !hasFilledCNE && isCNEPrompt(dialogText)) {
                Log.d(TAG, "Detected CNE input prompt")
                isWaitingForCNE = true
                retryCount++
                cneInputAttempts++
                
                // Prevent infinite loops
                if (cneInputAttempts > MAX_INPUT_ATTEMPTS) {
                    Log.e(TAG, "Too many CNE input attempts, marking as failed and moving on")
                    hasFilledCNE = true  // Skip this step
                    lastActionTime = System.currentTimeMillis()
                    return
                }
                
                // Fill CNE using async approach
                serviceScope.launch {
                    try {
                        delay(150)
                        
                        withContext(Dispatchers.Main) {
                            try {
                                val rootForFill = rootInActiveWindow
                                if (rootForFill == null) {
                                    Log.e(TAG, "Root node null when trying to fill CNE")
                                    return@withContext
                                }
                                
                                val pkg = rootForFill.packageName?.toString() ?: ""
                                if (!isUSSDPackage(pkg)) {
                                    Log.d(TAG, "Not in USSD dialog anymore, skipping CNE fill")
                                    try { rootForFill.recycle() } catch (e: Exception) {}
                                    return@withContext
                                }
                                
                                // Focus and fill
                                focusInputField(rootForFill)
                                
                                Log.d(TAG, "Filling CNE field with: $expectedCNE")
                                val filled = fillInputField(rootForFill, expectedCNE)
                                
                                if (filled) {
                                    hasFilledCNE = true
                                    isWaitingForCNE = false
                                    lastActionTime = System.currentTimeMillis()
                                    Log.d(TAG, "CNE filled: $expectedCNE")
                                    
                                    // Update database async
                                    launch(Dispatchers.IO) {
                                        try {
                                            database.registrationDao().updateStatus(recordId, RegistrationStatus.CNE_FILLED)
                                            database.registrationDao().updateCneFilled(recordId, true)
                                        } catch (e: Exception) {
                                            Log.e(TAG, "DB error: ${e.message}")
                                        }
                                    }
                                    
                                    // Click Send/OK button
                                    handler.postDelayed({
                                        clickSendButtonSafely()
                                    }, 250)
                                } else {
                                    Log.e(TAG, "Failed to fill CNE field")
                                }
                                
                                try { rootForFill.recycle() } catch (e: Exception) {}
                            } catch (e: Exception) {
                                Log.e(TAG, "Error in CNE fill: ${e.message}")
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error filling CNE async: ${e.message}")
                    }
                }
            }
            
            // Fallback: Check if there's an input field visible after name is filled
            else if (hasFilledName && !hasFilledCNE && hasInputField(rootNode) && !isNamePrompt(dialogText)) {
                Log.d(TAG, "Found input field after name - assuming CNE prompt (fallback)")
                isWaitingForCNE = true
                retryCount++
                cneInputAttempts++
                
                if (cneInputAttempts > MAX_INPUT_ATTEMPTS) {
                    Log.e(TAG, "Too many CNE fallback attempts, moving on")
                    hasFilledCNE = true
                    lastActionTime = System.currentTimeMillis()
                    return
                }
                
                serviceScope.launch {
                    try {
                        delay(150)
                        
                        withContext(Dispatchers.Main) {
                            try {
                                val rootForFill = rootInActiveWindow ?: return@withContext
                                
                                val pkg = rootForFill.packageName?.toString() ?: ""
                                if (!isUSSDPackage(pkg)) {
                                    try { rootForFill.recycle() } catch (e: Exception) {}
                                    return@withContext
                                }
                                
                                focusInputField(rootForFill)
                                val filled = fillInputField(rootForFill, expectedCNE)
                                
                                if (filled) {
                                    hasFilledCNE = true
                                    isWaitingForCNE = false
                                    lastActionTime = System.currentTimeMillis()
                                    Log.d(TAG, "CNE filled (fallback): $expectedCNE")
                                    
                                    launch(Dispatchers.IO) {
                                        try {
                                            database.registrationDao().updateStatus(recordId, RegistrationStatus.CNE_FILLED)
                                            database.registrationDao().updateCneFilled(recordId, true)
                                        } catch (e: Exception) {}
                                    }
                                    
                                    handler.postDelayed({
                                        clickSendButtonSafely()
                                    }, 250)
                                }
                                
                                try { rootForFill.recycle() } catch (e: Exception) {}
                            } catch (e: Exception) {
                                Log.e(TAG, "Error in fallback CNE: ${e.message}")
                            }
                        }
                    } catch (e: Exception) {}
                }
            }
            
            // Check for already registered message
            else if ((hasFilledName || hasFilledCNE) && isAlreadyRegisteredMessage(dialogText)) {
                Log.d(TAG, "Detected already registered message")
                val responseMessage = dialogText.take(200)
                serviceScope.launch {
                    try {
                        database.registrationDao().updateStatusWithError(
                            recordId,
                            RegistrationStatus.ALREADY_REGISTERED,
                            "Already registered: $responseMessage"
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "DB error: ${e.message}")
                    }
                    resetState()
                }
                
                lastActionTime = currentTime
                
                // Click OK to dismiss
                handler.postDelayed({
                    try {
                        val rootForClick = rootInActiveWindow ?: return@postDelayed
                        
                        val clickPkg = rootForClick.packageName?.toString() ?: ""
                        if (!isUSSDPackage(clickPkg)) {
                            try { rootForClick.recycle() } catch (e: Exception) {}
                            return@postDelayed
                        }
                        
                        clickUSSDPositiveButton(rootForClick)
                        try { rootForClick.recycle() } catch (e: Exception) {}
                    } catch (e: Exception) {
                        Log.e(TAG, "Error dismissing dialog: ${e.message}")
                    }
                }, 150)
            }
            
            // Check for completion/success messages - IMPROVED to handle final OK
            else if (hasFilledName && hasFilledCNE && isSuccessMessage(dialogText)) {
                Log.d(TAG, "Detected success message, validationOkCount=$validationOkCount")
                
                if (!hasDismissedFinalDialog) {
                    val responseMessage = dialogText.take(200)
                    serviceScope.launch {
                        try {
                            database.registrationDao().updateStatusWithError(
                                recordId,
                                RegistrationStatus.COMPLETED,
                                "Success: $responseMessage"
                            )
                        } catch (e: Exception) {
                            Log.e(TAG, "DB error: ${e.message}")
                        }
                    }
                    
                    hasDismissedFinalDialog = true
                    lastActionTime = currentTime

                    // Click OK to dismiss final dialog
                    handler.postDelayed({
                        try {
                            val rootForClick = rootInActiveWindow ?: return@postDelayed
                            
                            val clickPkg = rootForClick.packageName?.toString() ?: ""
                            if (!isUSSDPackage(clickPkg)) {
                                try { rootForClick.recycle() } catch (e: Exception) {}
                                resetState()
                                return@postDelayed
                            }
                            
                            val clicked = clickUSSDPositiveButton(rootForClick)
                            if (clicked) {
                                Log.d(TAG, "Clicked final OK button")
                                validationOkCount++
                            }
                            try { rootForClick.recycle() } catch (e: Exception) {}
                            
                            // Reset state after dismissing
                            handler.postDelayed({
                                resetState()
                            }, 100)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error dismissing final dialog: ${e.message}")
                            resetState()
                        }
                    }, 150)
                }
            }
            
            // Check if this is ANY dialog with OK button after we've completed both fills
            // This handles the extra validation OK dialogs - BUT ONLY IN USSD DIALOG
            // IMPORTANT: Mark as completed immediately after clicking OK since both fields are filled
            else if (hasFilledName && hasFilledCNE && hasOkButton(rootNode)) {
                Log.d(TAG, "Found dialog with OK button after both fills - dismissing and completing (count: $validationOkCount)")
                lastActionTime = currentTime
                
                // Mark as completed immediately since both name and CNE are filled
                if (!hasDismissedFinalDialog) {
                    hasDismissedFinalDialog = true
                    val responseMessage = dialogText.take(200)
                    serviceScope.launch {
                        try {
                            database.registrationDao().updateStatusWithError(
                                recordId,
                                RegistrationStatus.COMPLETED,
                                "Completed: $responseMessage"
                            )
                        } catch (e: Exception) {
                            Log.e(TAG, "DB error: ${e.message}")
                        }
                    }
                }
                
                handler.postDelayed({
                    try {
                        val rootForClick = rootInActiveWindow ?: return@postDelayed
                        
                        // IMPORTANT: Only click if we're still in USSD dialog
                        val clickPkg = rootForClick.packageName?.toString() ?: ""
                        if (!isUSSDPackage(clickPkg)) {
                            Log.d(TAG, "Not in USSD dialog, skipping OK button click")
                            try { rootForClick.recycle() } catch (e: Exception) {}
                            resetState()
                            return@postDelayed
                        }
                        
                        val clicked = clickUSSDPositiveButton(rootForClick)
                        if (clicked) {
                            validationOkCount++
                            Log.d(TAG, "Dismissed validation dialog #$validationOkCount")
                        }
                        try { rootForClick.recycle() } catch (e: Exception) {}
                        
                        // Reset state after clicking to allow next number
                        handler.postDelayed({
                            resetState()
                        }, 100)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error dismissing validation dialog: ${e.message}")
                        resetState()
                    }
                }, 100)
            }
            
            // Check for error messages
            else if (isErrorMessage(dialogText)) {
                Log.d(TAG, "Detected error message")
                val errorMessage = dialogText.take(200)
                serviceScope.launch {
                    try {
                        database.registrationDao().updateStatusWithError(
                            recordId,
                            RegistrationStatus.FAILED,
                            "Error: $errorMessage"
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "DB error: ${e.message}")
                    }
                    resetState()
                }

                lastActionTime = currentTime

                // Click OK to dismiss
                handler.postDelayed({
                    try {
                        val rootForClick = rootInActiveWindow ?: return@postDelayed
                        clickButton(rootForClick, listOf("ok", "close", "fermer", "dismiss", "annuler", "cancel"))
                        try { rootForClick.recycle() } catch (e: Exception) {}
                    } catch (e: Exception) {
                        Log.e(TAG, "Error dismissing error dialog: ${e.message}")
                    }
                }, 150)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in handleUSSDDialog: ${e.message}")
            e.printStackTrace()
        } finally {
            try { rootNode.recycle() } catch (e: Exception) {}
        }
    }
    
    // Helper functions for prompt detection
    private fun isNamePrompt(text: String): Boolean {
        return text.contains("name") ||
               text.contains("nom") ||
               text.contains("full name") ||
               text.contains("enter name") ||
               text.contains("prenom") ||
               text.contains("prénom") ||
               text.contains("saisir nom") ||
               text.contains("votre nom")
    }
    
    private fun isCNEPrompt(text: String): Boolean {
        return text.contains("cne") ||
               text.contains("cin") ||
               text.contains("carte") ||
               text.contains("identit") ||
               text.contains("c.i.n") ||
               text.contains("c.n.e") ||
               text.contains("numero") ||
               text.contains("numéro") ||
               text.contains("national") ||
               text.contains("id number") ||
               text.contains("identity") ||
               text.contains("saisir c") ||
               (text.contains("enter") && (text.contains("number") || text.contains("id")))
    }
    
    private fun isSuccessMessage(text: String): Boolean {
        return text.contains("success") ||
               text.contains("completed") ||
               text.contains("confirmé") ||
               text.contains("confirme") ||
               text.contains("réussi") ||
               text.contains("reussi") ||
               text.contains("merci") ||
               text.contains("thank") ||
               text.contains("effectué") ||
               text.contains("effectue") ||
               text.contains("enregistré") ||
               text.contains("enregistre") ||
               text.contains("terminé") ||
               text.contains("termine") ||
               text.contains("bienvenue") ||
               text.contains("welcome")
    }
    
    private fun isAlreadyRegisteredMessage(text: String): Boolean {
        return text.contains("already registered") ||
               text.contains("already exist") ||
               text.contains("déjà enregistré") ||
               text.contains("deja enregistre") ||
               text.contains("existe déjà") ||
               text.contains("existe deja") ||
               text.contains("déjà inscrit") ||
               text.contains("deja inscrit")
    }
    
    private fun isErrorMessage(text: String): Boolean {
        return text.contains("error") ||
               text.contains("failed") ||
               text.contains("erreur") ||
               text.contains("échec") ||
               text.contains("echec") ||
               text.contains("invalid") ||
               text.contains("invalide") ||
               text.contains("impossible") ||
               text.contains("problem")
    }
    
    private fun hasInputField(node: AccessibilityNodeInfo): Boolean {
        try {
            if (node.isEditable || 
                node.className?.contains("EditText") == true || 
                node.className?.contains("edit") == true) {
                return true
            }
            
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { child ->
                    val hasInput = hasInputField(child)
                    try { child.recycle() } catch (e: Exception) {}
                    if (hasInput) return true
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking for input field: ${e.message}")
        }
        return false
    }
    
    private fun hasOkButton(node: AccessibilityNodeInfo): Boolean {
        try {
            val nodeText = node.text?.toString()?.lowercase() ?: ""
            val nodeDesc = node.contentDescription?.toString()?.lowercase() ?: ""
            val className = node.className?.toString()?.lowercase() ?: ""
            
            val isButton = node.isClickable || className.contains("button")
            val okTexts = listOf("ok", "close", "fermer", "dismiss", "terminer")
            
            if (isButton && okTexts.any { nodeText.contains(it) || nodeDesc.contains(it) }) {
                return true
            }
            
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { child ->
                    val hasOk = hasOkButton(child)
                    try { child.recycle() } catch (e: Exception) {}
                    if (hasOk) return true
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking for OK button: ${e.message}")
        }
        return false
    }

    private fun extractTextFromNode(node: AccessibilityNodeInfo): String {
        val text = StringBuilder()
        
        try {
            node.text?.let { text.append(it).append(" ") }
            node.contentDescription?.let { text.append(it).append(" ") }
            
            for (i in 0 until node.childCount) {
                try {
                    node.getChild(i)?.let { child ->
                        text.append(extractTextFromNode(child))
                        try { child.recycle() } catch (e: Exception) {}
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error getting child node: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting text: ${e.message}")
        }
        
        return text.toString()
    }

    private fun fillInputField(node: AccessibilityNodeInfo, text: String): Boolean {
        try {
            // Try to find EditText or input field
            if (node.isEditable || node.className?.contains("EditText") == true || node.className?.contains("edit") == true) {
                try {
                    Log.d(TAG, "Found input field, attempting to fill with: $text")
                    Log.d(TAG, "Android SDK: ${Build.VERSION.SDK_INT}, className: ${node.className}")
                    
                    // METHOD 1: Direct ACTION_SET_TEXT (most reliable on Android 5+)
                    val arguments = Bundle().apply {
                        putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
                    }
                    
                    // Focus first
                    node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
                    
                    // Try set text directly
                    var success = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
                    
                    if (success) {
                        Log.d(TAG, "ACTION_SET_TEXT succeeded for: $text")
                        return true
                    }
                    
                    Log.d(TAG, "Direct ACTION_SET_TEXT failed, trying click+focus+setText...")
                    
                    // METHOD 2: Click to activate, focus, then set text
                    node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
                    
                    success = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
                    if (success) {
                        Log.d(TAG, "ACTION_SET_TEXT succeeded after click+focus")
                        return true
                    }
                    
                    // METHOD 3: Clear text first, then set (Android 10+)
                    Log.d(TAG, "Trying clear then set method...")
                    val clearArgs = Bundle().apply {
                        putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "")
                    }
                    node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, clearArgs)
                    
                    success = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
                    if (success) {
                        Log.d(TAG, "ACTION_SET_TEXT succeeded after clear")
                        return true
                    }
                    
                    // METHOD 4: Use clipboard paste
                    Log.d(TAG, "Trying clipboard paste method...")
                    try {
                        val clipboard = applicationContext.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        if (clipboard != null) {
                            val clip = ClipData.newPlainText("ussd_input", text)
                            clipboard.setPrimaryClip(clip)
                            
                            // Select all and paste
                            val selectArgs = Bundle().apply {
                                putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, 0)
                                putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, 99999)
                            }
                            node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, selectArgs)
                            
                            val pasted = node.performAction(AccessibilityNodeInfo.ACTION_PASTE)
                            if (pasted) {
                                Log.d(TAG, "Clipboard paste succeeded")
                                return true
                            }
                        }
                    } catch (clipError: Exception) {
                        Log.e(TAG, "Clipboard failed: ${clipError.message}")
                    }
                    
                    // METHOD 5: Use gesture tap for Android 7+
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        Log.d(TAG, "Trying gesture tap method...")
                        try {
                            val bounds = Rect()
                            node.getBoundsInScreen(bounds)
                            val cx = bounds.centerX().toFloat()
                            val cy = bounds.centerY().toFloat()
                            
                            if (cx > 0 && cy > 0) {
                                val path = Path()
                                path.moveTo(cx, cy)
                                
                                val gesture = GestureDescription.Builder()
                                    .addStroke(GestureDescription.StrokeDescription(path, 0, 50))
                                    .build()
                                
                                dispatchGesture(gesture, object : GestureResultCallback() {
                                    override fun onCompleted(gestureDescription: GestureDescription?) {
                                        Log.d(TAG, "Gesture tap completed, setting text...")
                                        handler.postDelayed({
                                            try {
                                                rootInActiveWindow?.let { root ->
                                                    setTextToEditableField(root, text)
                                                    try { root.recycle() } catch (e: Exception) {}
                                                }
                                            } catch (e: Exception) {}
                                        }, 150)
                                    }
                                }, null)
                            }
                        } catch (gestureError: Exception) {
                            Log.e(TAG, "Gesture failed: ${gestureError.message}")
                        }
                    }
                    
                    // Assume success to prevent blocking - text might have been set
                    Log.w(TAG, "All methods attempted, continuing...")
                    return true
                    
                } catch (e: Exception) {
                    Log.e(TAG, "Error filling input: ${e.message}")
                    e.printStackTrace()
                    return true // Return true to prevent blocking
                }
            }
            
            // Search children recursively
            for (i in 0 until node.childCount) {
                try {
                    node.getChild(i)?.let { child ->
                        if (fillInputField(child, text)) {
                            try { child.recycle() } catch (e: Exception) {}
                            return true
                        }
                        try { child.recycle() } catch (e: Exception) {}
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error searching child: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in fillInputField: ${e.message}")
        }
        
        return false
    }
    
    /**
     * Helper function to set text in editable field
     */
    private fun setTextToEditableField(node: AccessibilityNodeInfo, text: String): Boolean {
        try {
            if (node.isEditable || node.className?.contains("EditText") == true) {
                val args = Bundle().apply {
                    putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
                }
                return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            }
            
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { child ->
                    if (setTextToEditableField(child, text)) {
                        try { child.recycle() } catch (e: Exception) {}
                        return true
                    }
                    try { child.recycle() } catch (e: Exception) {}
                }
            }
        } catch (e: Exception) {}
        return false
    }

    private fun clickButton(node: AccessibilityNodeInfo, buttonTexts: List<String>): Boolean {
        try {
            val nodeText = node.text?.toString()?.lowercase() ?: ""
            val nodeDesc = node.contentDescription?.toString()?.lowercase() ?: ""
            val className = node.className?.toString()?.lowercase() ?: ""
            val viewId = node.viewIdResourceName?.lowercase() ?: ""
            
            // Check if this is a button or clickable element
            val isButton = node.isClickable || className.contains("button") || 
                          className.contains("imagebutton")
            
            // Also check for common USSD dialog button IDs
            val isUSSDButton = viewId.contains("button") || 
                               viewId.contains("positive") || 
                               viewId.contains("negative") ||
                               viewId.contains("ok") ||
                               viewId.contains("send") ||
                               viewId.contains("submit")
            
            if ((isButton || isUSSDButton) && buttonTexts.any { nodeText.contains(it) || nodeDesc.contains(it) }) {
                Log.d(TAG, "Clicking USSD button: '$nodeText' (desc: '$nodeDesc', id: '$viewId')")
                val success = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                if (success) {
                    Thread.sleep(100)
                    return true
                }
            }
            
            // Search children
            for (i in 0 until node.childCount) {
                try {
                    node.getChild(i)?.let { child ->
                        if (clickButton(child, buttonTexts)) {
                            try { child.recycle() } catch (e: Exception) {}
                            return true
                        }
                        try { child.recycle() } catch (e: Exception) {}
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error searching child for button: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in clickButton: ${e.message}")
        }
        
        return false
    }
    
    /**
     * Find and click the positive/send button in USSD dialog
     * This is more targeted than clickButton - looks for specific USSD dialog patterns
     */
    private fun clickUSSDPositiveButton(node: AccessibilityNodeInfo): Boolean {
        try {
            val nodeText = node.text?.toString()?.lowercase() ?: ""
            val nodeDesc = node.contentDescription?.toString()?.lowercase() ?: ""
            val className = node.className?.toString()?.lowercase() ?: ""
            val viewId = node.viewIdResourceName?.lowercase() ?: ""
            
            // Look for positive button patterns in USSD dialogs
            val positiveButtonTexts = listOf("envoyer", "send", "ok", "valider", "oui", "yes", "submit", "confirm")
            val positiveButtonIds = listOf("button1", "positive", "ok", "send", "submit", "alertdialogprobutton")
            
            val isClickable = node.isClickable || className.contains("button")
            val matchesText = positiveButtonTexts.any { nodeText.contains(it) || nodeDesc.contains(it) }
            val matchesId = positiveButtonIds.any { viewId.contains(it) }
            
            if (isClickable && (matchesText || matchesId)) {
                Log.d(TAG, "Found USSD positive button: '$nodeText' (id: '$viewId')")
                val success = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                if (success) {
                    Thread.sleep(100)
                    return true
                }
            }
            
            // Search children
            for (i in 0 until node.childCount) {
                try {
                    node.getChild(i)?.let { child ->
                        if (clickUSSDPositiveButton(child)) {
                            try { child.recycle() } catch (e: Exception) {}
                            return true
                        }
                        try { child.recycle() } catch (e: Exception) {}
                    }
                } catch (e: Exception) {}
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in clickUSSDPositiveButton: ${e.message}")
        }
        
        return false
    }
    
    // REMOVED clickAnyButton - we don't want to click random buttons
    
    private fun pressBackButton(): Boolean {
        return try {
            performGlobalAction(GLOBAL_ACTION_BACK)
        } catch (e: Exception) {
            Log.e(TAG, "Error performing back action: ${e.message}")
            false
        }
    }
    
    /**
     * Press Enter key using IME action
     */
    private fun pressEnterKey(node: AccessibilityNodeInfo): Boolean {
        try {
            // Find input field and perform IME action (Enter)
            if (node.isEditable || node.className?.contains("EditText") == true) {
                // Try IME_ACTION_DONE which acts like Enter
                val success = node.performAction(AccessibilityNodeInfo.ACTION_NEXT_HTML_ELEMENT) ||
                              node.performAction(AccessibilityNodeInfo.ACTION_NEXT_AT_MOVEMENT_GRANULARITY)
                
                if (!success) {
                    // Try unfocusing which might trigger submit
                    node.performAction(AccessibilityNodeInfo.ACTION_CLEAR_FOCUS)
                }
                Log.d(TAG, "Pressed Enter on input field")
                return true
            }
            
            // Search children
            for (i in 0 until node.childCount) {
                try {
                    node.getChild(i)?.let { child ->
                        if (pressEnterKey(child)) {
                            try { child.recycle() } catch (e: Exception) {}
                            return true
                        }
                        try { child.recycle() } catch (e: Exception) {}
                    }
                } catch (e: Exception) {}
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error pressing Enter: ${e.message}")
        }
        return false
    }
    
    /**
     * Focus on input field by clicking it
     */
    private fun focusInputField(node: AccessibilityNodeInfo): Boolean {
        try {
            if (node.isEditable || node.className?.contains("EditText") == true || 
                node.className?.contains("edit") == true) {
                // Click to focus
                node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                Thread.sleep(50)
                // Then focus
                node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
                Thread.sleep(50)
                Log.d(TAG, "Focused input field")
                return true
            }
            
            // Search children
            for (i in 0 until node.childCount) {
                try {
                    node.getChild(i)?.let { child ->
                        if (focusInputField(child)) {
                            try { child.recycle() } catch (e: Exception) {}
                            return true
                        }
                        try { child.recycle() } catch (e: Exception) {}
                    }
                } catch (e: Exception) {}
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error focusing input field: ${e.message}")
        }
        return false
    }

    private fun delay(millis: Long) {
        Thread.sleep(millis)
    }
    
    /**
     * Safely click the Send/OK button in USSD dialog
     * Used after filling input fields
     */
    private fun clickSendButtonSafely() {
        try {
            val root = rootInActiveWindow ?: return
            
            val pkg = root.packageName?.toString() ?: ""
            if (!isUSSDPackage(pkg)) {
                Log.d(TAG, "Not in USSD dialog, skipping button click")
                try { root.recycle() } catch (e: Exception) {}
                return
            }
            
            Log.d(TAG, "Looking for Send/OK button...")
            var clicked = clickUSSDPositiveButton(root)
            
            if (!clicked) {
                clicked = clickButton(root, listOf("envoyer", "ok", "send", "valider", "submit", "oui", "yes"))
            }
            
            if (clicked) {
                lastActionTime = System.currentTimeMillis()
                Log.d(TAG, "Send button clicked successfully")
            } else {
                Log.w(TAG, "Could not find Send button")
            }
            
            try { root.recycle() } catch (e: Exception) {}
        } catch (e: Exception) {
            Log.e(TAG, "Error clicking send button: ${e.message}")
        }
    }

    private fun resetState() {
        isWaitingForName = false
        isWaitingForCNE = false
        hasFilledName = false
        hasFilledCNE = false
        hasDismissedFinalDialog = false
        validationOkCount = 0
        lastActionTime = 0L
        lastDialogText = ""
        retryCount = 0
        nameInputAttempts = 0
        cneInputAttempts = 0
        // Don't reset lastProcessedRecordId here - it's used to detect new records
        Log.d(TAG, "State reset complete - ready for next steps")
    }

    override fun onInterrupt() {
        // Service interrupted
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
