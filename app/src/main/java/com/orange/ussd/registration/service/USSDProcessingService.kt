package com.orange.ussd.registration.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.orange.ussd.registration.R
import com.orange.ussd.registration.data.database.AppDatabase
import com.orange.ussd.registration.data.model.RegistrationStatus
import com.orange.ussd.registration.ui.MainActivity
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class USSDProcessingService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var database: AppDatabase
    private var isProcessing = false
    private var processingJob: Job? = null
    private val processingMutex = Mutex()
    
    companion object {
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "ussd_processing_channel"
        const val ACTION_START_PROCESSING = "START_PROCESSING"
        const val ACTION_STOP_PROCESSING = "STOP_PROCESSING"
        
        // Shared state for accessibility service
        @Volatile var currentRecordId: Long? = null
        @Volatile var expectedFullName: String? = null
        @Volatile var expectedCNE: String? = null
        @Volatile var isCurrentlyProcessing = false
    }

    override fun onCreate() {
        super.onCreate()
        database = AppDatabase.getDatabase(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_PROCESSING -> {
                if (!isProcessing) {
                    startForeground(NOTIFICATION_ID, createNotification("Starting registration process..."))
                    startProcessing()
                }
            }
            ACTION_STOP_PROCESSING -> {
                stopProcessing()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startProcessing() {
        // Cancel any existing processing job
        processingJob?.cancel()
        
        isProcessing = true
        processingJob = serviceScope.launch {
            processNextRecord()
        }
    }

    private fun stopProcessing() {
        isProcessing = false
        
        // Mark current record as cancelled if exists
        currentRecordId?.let { recordId ->
            serviceScope.launch {
                try {
                    database.registrationDao().updateStatusWithError(
                        recordId,
                        RegistrationStatus.CANCELLED,
                        "Stopped by user"
                    )
                } catch (e: Exception) {
                    // Ignore errors during cancellation
                }
            }
        }
        
        // Clear shared state
        currentRecordId = null
        expectedFullName = null
        expectedCNE = null
        
        // Cancel all running coroutines
        serviceScope.coroutineContext.cancelChildren()
        
        // Stop the service
        updateNotification("Processing stopped by user")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    private suspend fun processNextRecord() {
        while (isProcessing) {
            // Ensure only one record is processed at a time using Mutex
            processingMutex.withLock {
                if (isCurrentlyProcessing) {
                    delay(1000)
                    return@withLock
                }
                isCurrentlyProcessing = true
            }
            
            try {
                val record = database.registrationDao().getNextPendingRecord()
                
                if (record == null) {
                    // No more records to process
                    updateNotification("All registrations completed!")
                    delay(3000)
                    stopProcessing()
                    return
                }
                
                // Update status to IN_PROGRESS
                database.registrationDao().updateStatus(record.id, RegistrationStatus.IN_PROGRESS)
                currentRecordId = record.id
                expectedFullName = record.fullName
                expectedCNE = record.cne
                
                updateNotification("Processing: ${record.phoneNumber}")
                
                // Execute USSD code immediately
                delay(500)
                
                // Execute USSD code
                val ussdCode = buildUSSDCode(record.phoneNumber, record.pukLastFour)
                val success = executeUSSD(ussdCode)
                
                if (success) {
                    database.registrationDao().updateStatus(record.id, RegistrationStatus.USSD_SENT)
                    database.registrationDao().updateUssdExecuted(record.id, true)
                    
                    // Wait for accessibility service to handle the prompts
                    // The accessibility service will update the status
                    waitForCompletion(record.id)
                } else {
                    database.registrationDao().updateStatusWithError(
                        record.id,
                        RegistrationStatus.FAILED,
                        "Failed to execute USSD code"
                    )
                }
                
                // Clear shared state after processing
                currentRecordId = null
                expectedFullName = null
                expectedCNE = null

                // Minimal delay - waitForCompletion already waits 1 second after OK is clicked
                delay(500)

            } catch (e: Exception) {
                updateNotification("Error: ${e.message}")
                // Clear state on error
                currentRecordId = null
                expectedFullName = null
                expectedCNE = null
                // Wait briefly on error to ensure system recovers
                delay(1000)
            } finally {
                // Always release the processing lock
                isCurrentlyProcessing = false
            }
        }
    }

    private suspend fun waitForCompletion(recordId: Long, maxWaitTime: Long = 20000) {
        val startTime = System.currentTimeMillis()
        var lastStatus: RegistrationStatus? = null
        var statusUnchangedCount = 0
        
        while (System.currentTimeMillis() - startTime < maxWaitTime) {
            // Check if processing was stopped
            if (!isProcessing) {
                return
            }
            
            val record = database.registrationDao().getRecordById(recordId)
            
            // If completed, already registered, failed, or cancelled - we're done
            if (record?.status == RegistrationStatus.COMPLETED || 
                record?.status == RegistrationStatus.ALREADY_REGISTERED ||
                record?.status == RegistrationStatus.FAILED ||
                record?.status == RegistrationStatus.CANCELLED) {
                // Brief wait to ensure USSD dialog is fully closed
                delay(300)
                return
            }
            
            // If both name AND CNE are filled, consider it done after a short wait
            // This handles cases where success message is not detected
            if (record?.nameFilled == true && record.cneFilled == true) {
                // Wait a bit for potential response dialog
                delay(3000)
                // Check status again
                val updatedRecord = database.registrationDao().getRecordById(recordId)
                if (updatedRecord?.status != RegistrationStatus.COMPLETED && 
                    updatedRecord?.status != RegistrationStatus.ALREADY_REGISTERED &&
                    updatedRecord?.status != RegistrationStatus.FAILED) {
                    // Mark as completed since both inputs were filled
                    database.registrationDao().updateStatusWithError(
                        recordId,
                        RegistrationStatus.COMPLETED,
                        "Completed (name and CNE filled)"
                    )
                }
                delay(300)
                return
            }
            
            // Track if status is stuck (unchanged for too long)
            if (record?.status == lastStatus) {
                statusUnchangedCount++
                // If stuck for more than 10 checks (5 seconds), force continue
                if (statusUnchangedCount > 10 && record?.nameFilled == true) {
                    database.registrationDao().updateStatusWithError(
                        recordId,
                        RegistrationStatus.COMPLETED,
                        "Completed (forced after name filled, CNE may have failed)"
                    )
                    delay(300)
                    return
                }
            } else {
                lastStatus = record?.status
                statusUnchangedCount = 0
            }
            
            delay(500)
        }
        
        // Timeout - check what we accomplished
        val finalRecord = database.registrationDao().getRecordById(recordId)
        if (finalRecord?.nameFilled == true) {
            // At least name was filled, mark as completed
            database.registrationDao().updateStatusWithError(
                recordId,
                RegistrationStatus.COMPLETED,
                "Completed (timeout but name was filled)"
            )
        } else {
            database.registrationDao().updateStatusWithError(
                recordId,
                RegistrationStatus.FAILED,
                "Timeout - registration may be incomplete"
            )
        }
    }

    /**
     * Build USSD code in format: #555*1*{phoneNumber}*1*{pukLastFour}#
     */
    private fun buildUSSDCode(phoneNumber: String, pukLastFour: String): String {
        return "#555*1*$phoneNumber*1*$pukLastFour#"
    }

    /**
     * Execute USSD code using hidden Android API
     */
    private fun executeUSSD(ussdCode: String): Boolean {
        return try {
            // Method 1: Try using intent to dial USSD
            val encodedHash = Uri.encode("#")
            val ussd = ussdCode.replace("#", encodedHash)
            val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$ussd"))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "USSD Processing",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notification for USSD registration processing"
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(message: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, flags)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("USSD Registration")
            .setContentText(message)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(message: String) {
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, createNotification(message))
    }

    override fun onDestroy() {
        super.onDestroy()
        isProcessing = false
        isCurrentlyProcessing = false
        processingJob?.cancel()
        serviceScope.cancel()
        currentRecordId = null
        expectedFullName = null
        expectedCNE = null
    }
}
