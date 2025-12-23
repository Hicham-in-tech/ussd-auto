package com.orange.ussd.registration.ui

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.orange.ussd.registration.R
import com.orange.ussd.registration.data.database.AppDatabase
import com.orange.ussd.registration.data.model.RegistrationStatus
import com.orange.ussd.registration.service.USSDProcessingService
import com.orange.ussd.registration.service.USSDAccessibilityService
import com.orange.ussd.registration.utils.FileParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.poi.ss.usermodel.*
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var database: AppDatabase
    private lateinit var adapter: RegistrationAdapter
    
    private lateinit var btnSelectFile: Button
    private lateinit var btnStartProcessing: Button
    private lateinit var btnStopProcessing: Button
    private lateinit var btnClearAll: Button
    private lateinit var tvStatus: TextView
    private lateinit var tvStats: TextView
    private lateinit var tvUssdMessage: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var recyclerView: RecyclerView
    
    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { handleSelectedFile(it) }
    }

    private val filePickerMultipleTypes = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { handleSelectedFile(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        database = AppDatabase.getDatabase(this)
        
        initViews()
        setupRecyclerView()
        checkPermissions()
        observeData()
    }

    private fun initViews() {
        btnSelectFile = findViewById(R.id.btnSelectFile)
        btnStartProcessing = findViewById(R.id.btnStartProcessing)
        btnStopProcessing = findViewById(R.id.btnStopProcessing)
        btnClearAll = findViewById(R.id.btnClearAll)
        tvStatus = findViewById(R.id.tvStatus)
        tvStats = findViewById(R.id.tvStats)
        tvUssdMessage = findViewById(R.id.tvUssdMessage)
        progressBar = findViewById(R.id.progressBar)
        recyclerView = findViewById(R.id.recyclerView)
        
        btnSelectFile.setOnClickListener { selectFile() }
        btnStartProcessing.setOnClickListener { startProcessing() }
        btnStopProcessing.setOnClickListener { stopProcessing() }
        btnClearAll.setOnClickListener { clearAllRecords() }
        
        btnStopProcessing.isEnabled = false
    }

    private fun setupRecyclerView() {
        adapter = RegistrationAdapter()
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }

    private fun checkPermissions() {
        val permissions = mutableListOf<String>()
        
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) 
            != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.CALL_PHONE)
        }
        
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) 
            != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.READ_PHONE_STATE)
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) 
                != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        
        if (permissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissions.toTypedArray(), 100)
        }
        
        // For Android 12+, check SCHEDULE_EXACT_ALARM permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (!alarmManager.canScheduleExactAlarms()) {
                showExactAlarmPermissionDialog()
            }
        }
        
        // Check if accessibility service is enabled
        if (!isAccessibilityServiceEnabled()) {
            showAccessibilityServiceDialog()
        }
    }
    
    private fun showExactAlarmPermissionDialog() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            AlertDialog.Builder(this)
                .setTitle("Schedule Exact Alarms")
                .setMessage("This app needs permission to schedule exact alarms for reliable USSD automation.")
                .setPositiveButton("Open Settings") { _, _ ->
                    startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
                }
                .setNegativeButton("Skip", null)
                .show()
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val service = "${packageName}/${USSDAccessibilityService::class.java.canonicalName}"
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )
        return enabledServices?.contains(service) == true
    }

    private fun showAccessibilityServiceDialog() {
        AlertDialog.Builder(this)
            .setTitle("Enable Accessibility Service")
            .setMessage("This app requires accessibility service to automatically fill USSD prompts. Please enable it in the next screen.")
            .setPositiveButton("Open Settings") { _, _ ->
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun selectFile() {
        // Use OpenDocument to allow specific file types
        filePickerMultipleTypes.launch(arrayOf(
            "text/csv",
            "text/comma-separated-values",
            "application/csv",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        ))
    }

    private fun handleSelectedFile(uri: Uri) {
        progressBar.visibility = View.VISIBLE
        tvStatus.text = "Parsing file..."
        
        lifecycleScope.launch {
            try {
                val result = FileParser.parseFile(this@MainActivity, uri)
                
                if (result.errors.isNotEmpty()) {
                    showErrorDialog("Parsing Errors", result.errors.joinToString("\n"))
                }
                
                if (result.records.isNotEmpty()) {
                    database.registrationDao().insertAll(result.records)
                    tvStatus.text = "Loaded ${result.records.size} records"
                    Toast.makeText(
                        this@MainActivity,
                        "${result.records.size} records loaded successfully",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    tvStatus.text = "No valid records found"
                }
                
                // Show skipped records notification if any
                if (result.skippedRecords.isNotEmpty()) {
                    showSkippedRecordsDialog(result.skippedRecords)
                }
                
            } catch (e: Exception) {
                tvStatus.text = "Error: ${e.message}"
                showErrorDialog("Error", e.message ?: "Unknown error")
            } finally {
                progressBar.visibility = View.GONE
            }
        }
    }

    private fun startProcessing() {
        if (!isAccessibilityServiceEnabled()) {
            showAccessibilityServiceDialog()
            return
        }
        
        val intent = Intent(this, USSDProcessingService::class.java).apply {
            action = USSDProcessingService.ACTION_START_PROCESSING
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        
        // Disable all buttons except Stop during processing
        btnStartProcessing.isEnabled = false
        btnSelectFile.isEnabled = false
        btnClearAll.isEnabled = false
        btnStopProcessing.isEnabled = true
        tvStatus.text = "Processing started..."
    }

    private fun stopProcessing() {
        val intent = Intent(this, USSDProcessingService::class.java).apply {
            action = USSDProcessingService.ACTION_STOP_PROCESSING
        }
        startService(intent)
        
        // Re-enable all buttons when processing stops
        btnStartProcessing.isEnabled = true
        btnSelectFile.isEnabled = true
        btnClearAll.isEnabled = true
        btnStopProcessing.isEnabled = false
        tvStatus.text = "Processing stopped"
    }

    private fun clearAllRecords() {
        AlertDialog.Builder(this)
            .setTitle("Clear All Records")
            .setMessage("Are you sure you want to delete all records from the database?")
            .setPositiveButton("Yes") { _, _ ->
                lifecycleScope.launch {
                    database.registrationDao().deleteAll()
                    Toast.makeText(this@MainActivity, "All records cleared", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("No", null)
            .show()
    }

    private fun observeData() {
        lifecycleScope.launch {
            database.registrationDao().getAllRecords().collect { records ->
                adapter.submitList(records)
                updateStats(records)
            }
        }
    }

    private fun updateStats(records: List<com.orange.ussd.registration.data.model.RegistrationRecord>) {
        val total = records.size
        val passed = records.count { it.status == RegistrationStatus.COMPLETED }
        val skipped = records.count { it.status == RegistrationStatus.ALREADY_REGISTERED }
        val failed = records.count { it.status == RegistrationStatus.FAILED }
        
        tvStats.text = "Total: $total | Passed: $passed | Skipped: $skipped | Failed: $failed"
        
        // Show USSD message from the most recent processed record
        val latestProcessed = records.filter { 
            it.status == RegistrationStatus.COMPLETED || 
            it.status == RegistrationStatus.ALREADY_REGISTERED ||
            it.status == RegistrationStatus.FAILED 
        }.maxByOrNull { it.timestamp }
        
        if (latestProcessed != null && !latestProcessed.errorMessage.isNullOrEmpty()) {
            tvUssdMessage.text = "Last Response: ${latestProcessed.errorMessage}"
            tvUssdMessage.visibility = View.VISIBLE
        } else {
            tvUssdMessage.visibility = View.GONE
        }
    }

    private fun showManualInputDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_manual_input, null)
        
        val etPhoneNumber = dialogView.findViewById<EditText>(R.id.etPhoneNumber)
        val etPukLastFour = dialogView.findViewById<EditText>(R.id.etPukLastFour)
        val etFullName = dialogView.findViewById<EditText>(R.id.etFullName)
        val etCNE = dialogView.findViewById<EditText>(R.id.etCNE)
        
        AlertDialog.Builder(this)
            .setTitle("Add Registration Manually")
            .setView(dialogView)
            .setPositiveButton("Add") { _, _ ->
                val phoneNumber = etPhoneNumber.text.toString().trim()
                val pukLastFour = etPukLastFour.text.toString().trim()
                val fullName = etFullName.text.toString().trim()
                val cne = etCNE.text.toString().trim()
                
                // Validation
                if (!phoneNumber.matches(Regex("^0[67]\\d{8}$"))) {
                    Toast.makeText(this, "Invalid phone number format", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                
                if (pukLastFour.length != 4 || !pukLastFour.all { it.isDigit() }) {
                    Toast.makeText(this, "PUK must be 4 digits", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                
                if (fullName.isBlank()) {
                    Toast.makeText(this, "Full name is required", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                
                if (cne.isBlank()) {
                    Toast.makeText(this, "CNE is required", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                
                // Add to database
                lifecycleScope.launch {
                    val record = com.orange.ussd.registration.data.model.RegistrationRecord(
                        phoneNumber = phoneNumber,
                        pukLastFour = pukLastFour,
                        fullName = fullName,
                        cne = cne
                    )
                    database.registrationDao().insert(record)
                    Toast.makeText(this@MainActivity, "Record added successfully", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showErrorDialog(title: String, message: String) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }
    
    private fun showSkippedRecordsDialog(skippedRecords: List<FileParser.SkippedRecord>) {
        val message = buildString {
            appendLine("${skippedRecords.size} record(s) were skipped due to validation errors:\n")
            skippedRecords.forEachIndexed { index, record ->
                appendLine("${index + 1}. Line ${record.lineNumber}:")
                appendLine("   Phone: ${record.phoneNumber}")
                appendLine("   PUK: ${record.pukLastFour}")
                appendLine("   Name: ${record.fullName}")
                appendLine("   CNE: ${record.cne}")
                appendLine("   Reason: ${record.reason}")
                appendLine()
            }
        }
        
        AlertDialog.Builder(this)
            .setTitle("⚠️ Skipped Records")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .setNegativeButton("Export List") { _, _ ->
                exportSkippedRecords(skippedRecords)
            }
            .show()
    }
    
    private fun exportSkippedRecords(skippedRecords: List<FileParser.SkippedRecord>) {
        lifecycleScope.launch {
            try {
                val fileName = "skipped_records_${System.currentTimeMillis()}.txt"
                val file = File(getExternalFilesDir(null), fileName)
                
                file.bufferedWriter().use { writer ->
                    writer.write("Skipped Records Report\n")
                    writer.write("Generated: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}\n")
                    writer.write("Total Skipped: ${skippedRecords.size}\n\n")
                    writer.write("=".repeat(60) + "\n\n")
                    
                    skippedRecords.forEachIndexed { index, record ->
                        writer.write("Record ${index + 1}:\n")
                        writer.write("Line Number: ${record.lineNumber}\n")
                        writer.write("Phone Number: ${record.phoneNumber}\n")
                        writer.write("PUK Last 4: ${record.pukLastFour}\n")
                        writer.write("Full Name: ${record.fullName}\n")
                        writer.write("CNE: ${record.cne}\n")
                        writer.write("Reason: ${record.reason}\n")
                        writer.write("-".repeat(60) + "\n\n")
                    }
                }
                
                Toast.makeText(
                    this@MainActivity,
                    "Skipped records exported to: ${file.absolutePath}",
                    Toast.LENGTH_LONG
                ).show()
            } catch (e: Exception) {
                Toast.makeText(
                    this@MainActivity,
                    "Error exporting: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
    

}
