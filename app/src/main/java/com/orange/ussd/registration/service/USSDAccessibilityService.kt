package com.orange.ussd.registration.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
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
    private val MAX_RETRY = 3
    private val ACTION_DELAY = 250L // Ultra-fast response for quick processing
    private val TAG = "USSDAccessibility"

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
            
            // Check if asking for name - ONLY if we haven't filled it yet
            if (!hasFilledName && isNamePrompt(dialogText)) {
                Log.d(TAG, "Detected name input prompt")
                isWaitingForName = true
                retryCount++
                
                handler.postDelayed({
                    try {
                        val rootForFill = rootInActiveWindow ?: return@postDelayed
                        
                        // Verify we're still in USSD dialog
                        val pkg = rootForFill.packageName?.toString() ?: ""
                        if (!isUSSDPackage(pkg)) {
                            Log.d(TAG, "Not in USSD dialog anymore, skipping name fill")
                            try { rootForFill.recycle() } catch (e: Exception) {}
                            return@postDelayed
                        }
                        
                        Log.d(TAG, "Attempting to fill name input field with: $expectedName")
                        val filled = fillInputField(rootForFill, expectedName)

                        if (filled) {
                            serviceScope.launch {
                                try {
                                    database.registrationDao().updateStatus(recordId, RegistrationStatus.NAME_FILLED)
                                    database.registrationDao().updateNameFilled(recordId, true)
                                } catch (e: Exception) {
                                    Log.e(TAG, "DB error: ${e.message}")
                                }
                            }

                            hasFilledName = true
                            isWaitingForName = false
                            lastActionTime = System.currentTimeMillis()
                            Log.d(TAG, "Name filled successfully: $expectedName")

                            // Wait then click Envoyer/OK button
                            handler.postDelayed({
                                try {
                                    val rootForClick = rootInActiveWindow ?: return@postDelayed
                                    
                                    // Verify we're still in USSD dialog
                                    val clickPkg = rootForClick.packageName?.toString() ?: ""
                                    if (!isUSSDPackage(clickPkg)) {
                                        Log.d(TAG, "Not in USSD dialog anymore, skipping button click")
                                        try { rootForClick.recycle() } catch (e: Exception) {}
                                        return@postDelayed
                                    }
                                    
                                    Log.d(TAG, "Looking for Envoyer/OK button after name input...")
                                    // Try clicking the positive button
                                    var okClicked = clickUSSDPositiveButton(rootForClick)
                                    
                                    if (!okClicked) {
                                        // Fallback to text-based button search
                                        Log.d(TAG, "Trying fallback button search...")
                                        okClicked = clickButton(rootForClick, listOf("envoyer", "ok", "send", "valider", "submit"))
                                    }
                                    
                                    if (okClicked) {
                                        lastActionTime = System.currentTimeMillis()
                                        Log.d(TAG, "Successfully clicked Envoyer button for name")
                                    } else {
                                        Log.e(TAG, "Could not find Envoyer button for name")
                                    }
                                    try { rootForClick.recycle() } catch (e: Exception) {}
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error clicking button: ${e.message}")
                                }
                            }, 500)
                        } else {
                            Log.e(TAG, "Failed to fill name input field")
                        }
                        try { rootForFill.recycle() } catch (e: Exception) {}
                    } catch (e: Exception) {
                        Log.e(TAG, "Error filling name: ${e.message}")
                    }
                }, 400)
            }
            
            // Check if asking for CNE - IMPROVED detection after name is filled
            else if (hasFilledName && !hasFilledCNE && isCNEPrompt(dialogText)) {
                Log.d(TAG, "Detected CNE input prompt")
                isWaitingForCNE = true
                retryCount++
                
                handler.postDelayed({
                    try {
                        val rootForFill = rootInActiveWindow ?: return@postDelayed
                        
                        // Verify we're still in USSD dialog
                        val pkg = rootForFill.packageName?.toString() ?: ""
                        if (!isUSSDPackage(pkg)) {
                            Log.d(TAG, "Not in USSD dialog anymore, skipping CNE fill")
                            try { rootForFill.recycle() } catch (e: Exception) {}
                            return@postDelayed
                        }
                        
                        // First, focus on the input field by clicking on it
                        Log.d(TAG, "Focusing on CNE input field...")
                        focusInputField(rootForFill)
                        
                        handler.postDelayed({
                            try {
                                val rootForFill2 = rootInActiveWindow ?: return@postDelayed
                                
                                // Verify again we're still in USSD dialog
                                val pkg2 = rootForFill2.packageName?.toString() ?: ""
                                if (!isUSSDPackage(pkg2)) {
                                    Log.d(TAG, "Not in USSD dialog anymore, skipping CNE input")
                                    try { rootForFill2.recycle() } catch (e: Exception) {}
                                    return@postDelayed
                                }
                                
                                Log.d(TAG, "Attempting to fill CNE input field with: $expectedCNE")
                                val filled = fillInputField(rootForFill2, expectedCNE)

                                if (filled) {
                                    serviceScope.launch {
                                        try {
                                            database.registrationDao().updateStatus(recordId, RegistrationStatus.CNE_FILLED)
                                            database.registrationDao().updateCneFilled(recordId, true)
                                        } catch (e: Exception) {
                                            Log.e(TAG, "DB error: ${e.message}")
                                        }
                                    }

                                    hasFilledCNE = true
                                    isWaitingForCNE = false
                                    lastActionTime = System.currentTimeMillis()
                                    Log.d(TAG, "CNE filled successfully: $expectedCNE")

                                    // Wait then click Envoyer/OK button
                                    handler.postDelayed({
                                        try {
                                            val rootForClick = rootInActiveWindow ?: return@postDelayed
                                            
                                            // Verify we're still in USSD dialog
                                            val clickPkg = rootForClick.packageName?.toString() ?: ""
                                            if (!isUSSDPackage(clickPkg)) {
                                                Log.d(TAG, "Not in USSD dialog anymore, skipping button click")
                                                try { rootForClick.recycle() } catch (e: Exception) {}
                                                return@postDelayed
                                            }
                                            
                                            // Try clicking the positive button
                                            var okClicked = clickUSSDPositiveButton(rootForClick)
                                            
                                            if (!okClicked) {
                                                okClicked = clickButton(rootForClick, listOf("envoyer", "ok", "send", "valider"))
                                            }
                                            
                                            if (okClicked) {
                                                lastActionTime = System.currentTimeMillis()
                                                Log.d(TAG, "Successfully clicked Envoyer button for CNE")
                                            } else {
                                                Log.e(TAG, "Could not find Envoyer button for CNE")
                                            }
                                            try { rootForClick.recycle() } catch (e: Exception) {}
                                        } catch (e: Exception) {
                                            Log.e(TAG, "Error clicking button: ${e.message}")
                                        }
                                    }, 600)
                                } else {
                                    Log.e(TAG, "Failed to fill CNE input field")
                                }
                                try { rootForFill2.recycle() } catch (e: Exception) {}
                            } catch (e: Exception) {
                                Log.e(TAG, "Error filling CNE: ${e.message}")
                            }
                        }, 150)
                        
                        try { rootForFill.recycle() } catch (e: Exception) {}
                    } catch (e: Exception) {
                        Log.e(TAG, "Error in CNE flow: ${e.message}")
                    }
                }, 200)
            }
            
            // Check if there's an input field visible but we haven't detected the prompt type
            // This handles cases where the label text doesn't match our patterns
            else if (hasFilledName && !hasFilledCNE && hasInputField(rootNode) && !isNamePrompt(dialogText)) {
                Log.d(TAG, "Found input field after name - assuming CNE prompt")
                isWaitingForCNE = true
                retryCount++
                
                handler.postDelayed({
                    try {
                        val rootForFill = rootInActiveWindow ?: return@postDelayed
                        
                        // Verify we're still in USSD dialog
                        val pkg = rootForFill.packageName?.toString() ?: ""
                        if (!isUSSDPackage(pkg)) {
                            Log.d(TAG, "Not in USSD dialog anymore, skipping fallback CNE")
                            try { rootForFill.recycle() } catch (e: Exception) {}
                            return@postDelayed
                        }
                        
                        // First, focus on the input field by clicking on it
                        focusInputField(rootForFill)
                        
                        handler.postDelayed({
                            try {
                                val rootForFill2 = rootInActiveWindow ?: return@postDelayed
                                
                                val pkg2 = rootForFill2.packageName?.toString() ?: ""
                                if (!isUSSDPackage(pkg2)) {
                                    try { rootForFill2.recycle() } catch (e: Exception) {}
                                    return@postDelayed
                                }
                                
                                val filled = fillInputField(rootForFill2, expectedCNE)

                                if (filled) {
                                    serviceScope.launch {
                                        try {
                                            database.registrationDao().updateStatus(recordId, RegistrationStatus.CNE_FILLED)
                                            database.registrationDao().updateCneFilled(recordId, true)
                                        } catch (e: Exception) {
                                            Log.e(TAG, "DB error: ${e.message}")
                                        }
                                    }

                                    hasFilledCNE = true
                                    isWaitingForCNE = false
                                    lastActionTime = System.currentTimeMillis()
                                    Log.d(TAG, "CNE filled (fallback): $expectedCNE")

                                    handler.postDelayed({
                                        try {
                                            val rootForClick = rootInActiveWindow ?: return@postDelayed
                                            
                                            val clickPkg = rootForClick.packageName?.toString() ?: ""
                                            if (!isUSSDPackage(clickPkg)) {
                                                try { rootForClick.recycle() } catch (e: Exception) {}
                                                return@postDelayed
                                            }
                                            
                                            var okClicked = clickUSSDPositiveButton(rootForClick)
                                            if (!okClicked) {
                                                okClicked = clickButton(rootForClick, listOf("envoyer", "ok", "send", "valider"))
                                            }
                                            lastActionTime = System.currentTimeMillis()
                                            try { rootForClick.recycle() } catch (e: Exception) {}
                                        } catch (e: Exception) {
                                            Log.e(TAG, "Error clicking button: ${e.message}")
                                        }
                                    }, 600)
                                }
                                try { rootForFill2.recycle() } catch (e: Exception) {}
                            } catch (e: Exception) {
                                Log.e(TAG, "Error in CNE fallback fill: ${e.message}")
                            }
                        }, 300)
                        
                        try { rootForFill.recycle() } catch (e: Exception) {}
                    } catch (e: Exception) {
                        Log.e(TAG, "Error in CNE fallback: ${e.message}")
                    }
                }, 200)
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
                }, 250)
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
                            }, 200)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error dismissing final dialog: ${e.message}")
                            resetState()
                        }
                    }, 250)
                }
            }
            
            // Check if this is ANY dialog with OK button after we've completed both fills
            // This handles the extra validation OK dialogs - BUT ONLY IN USSD DIALOG
            else if (hasFilledName && hasFilledCNE && hasOkButton(rootNode)) {
                Log.d(TAG, "Found dialog with OK button after both fills - dismissing (count: $validationOkCount)")
                lastActionTime = currentTime
                
                handler.postDelayed({
                    try {
                        val rootForClick = rootInActiveWindow ?: return@postDelayed
                        
                        // IMPORTANT: Only click if we're still in USSD dialog
                        val clickPkg = rootForClick.packageName?.toString() ?: ""
                        if (!isUSSDPackage(clickPkg)) {
                            Log.d(TAG, "Not in USSD dialog, skipping OK button click")
                            try { rootForClick.recycle() } catch (e: Exception) {}
                            return@postDelayed
                        }
                        
                        val clicked = clickUSSDPositiveButton(rootForClick)
                        if (clicked) {
                            validationOkCount++
                            Log.d(TAG, "Dismissed validation dialog #$validationOkCount")
                        }
                        try { rootForClick.recycle() } catch (e: Exception) {}
                    } catch (e: Exception) {
                        Log.e(TAG, "Error dismissing validation dialog: ${e.message}")
                    }
                }, 150)
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
                }, 250)
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
        // Enhanced CIN/CNE detection with more keywords
        return text.contains("cne") ||
               text.contains("cin") ||
               text.contains("c.n.e") ||
               text.contains("c.i.n") ||
               text.contains("c n e") ||
               text.contains("c i n") ||
               text.contains("carte") ||
               text.contains("identit") ||
               text.contains("identity") ||
               text.contains("numero") ||
               text.contains("numéro") ||
               text.contains("national") ||
               text.contains("id number") ||
               text.contains("identification") ||
               text.contains("saisir c") ||
               text.contains("entrez c") ||
               text.contains("enter c") ||
               text.contains("votre c") ||
               text.contains("your c") ||
               // Also check for prompts asking for numbers after name
               (hasFilledName && !hasFilledCNE && (
                   text.contains("enter") || 
                   text.contains("entrer") || 
                   text.contains("entrez") ||
                   text.contains("saisir") ||
                   text.contains("saisissez")
               ) && (
                   text.contains("number") || 
                   text.contains("numero") || 
                   text.contains("numéro") ||
                   text.contains("id") ||
                   text.contains("code")
               ))
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
            // Try to find EditText or input field - improved detection
            val isInputField = node.isEditable || 
                              node.isFocusable ||
                              node.className?.contains("EditText") == true || 
                              node.className?.contains("edit") == true ||
                              node.className?.contains("input") == true
            
            if (isInputField) {
                try {
                    Log.d(TAG, "Found input field, attempting to fill with: $text")
                    
                    // First, click on the field to focus it
                    node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    Thread.sleep(100)
                    
                    // Then focus the field
                    node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
                    Thread.sleep(100)
                    
                    // Clear existing text
                    val clearArgs = Bundle()
                    clearArgs.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "")
                    node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, clearArgs)
                    
                    Thread.sleep(100)
                    
                    // Set new text - try multiple times
                    val arguments = Bundle()
                    arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
                    var success = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
                    
                    if (!success) {
                        Log.d(TAG, "First attempt failed, retrying...")
                        Thread.sleep(100)
                        success = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
                    }
                    
                    if (success || true) { // Continue even if success is false, as some devices report false incorrectly
                        Log.d(TAG, "Successfully filled input field with: $text")
                        // Wait for the text to be registered
                        Thread.sleep(150)
                        return true
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error filling input field: ${e.message}")
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
                    Log.w(TAG, "Error searching child for input: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in fillInputField: ${e.message}")
        }
        
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
        Log.d(TAG, "State reset complete")
    }

    override fun onInterrupt() {
        // Service interrupted
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
