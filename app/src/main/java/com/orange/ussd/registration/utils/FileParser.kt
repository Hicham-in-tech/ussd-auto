package com.orange.ussd.registration.utils

import android.content.Context
import android.net.Uri
import com.orange.ussd.registration.data.model.RegistrationRecord
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.apache.poi.ss.usermodel.CellType

object FileParser {
    
    data class SkippedRecord(
        val lineNumber: Int,
        val phoneNumber: String,
        val pukLastFour: String,
        val fullName: String,
        val cne: String,
        val reason: String
    )
    
    data class ParseResult(
        val records: List<RegistrationRecord>,
        val errors: List<String>,
        val skippedRecords: List<SkippedRecord> = emptyList()
    )
    
    /**
     * Parse CSV or Excel file and return list of RegistrationRecords
     * Expected columns: PHONE_NUMBER, DDDD (last 4 of PUK), FULL_NAME, CNE
     * Supports both CSV and Excel (.xlsx, .xls) files
     * Records with invalid PUK (not 4 digits) or phone numbers will be skipped
     */
    fun parseFile(context: Context, uri: Uri): ParseResult {
        return try {
            // Get MIME type from content resolver
            val mimeType = context.contentResolver.getType(uri)?.lowercase()

            // Also check file name/path
            val fileName = uri.path?.lowercase() ?: ""
            val displayName = uri.lastPathSegment?.lowercase() ?: ""

            // Determine file type
            val isCSV = mimeType?.contains("csv") == true ||
                       mimeType?.contains("text") == true ||
                       fileName.endsWith(".csv") ||
                       displayName.endsWith(".csv")

            val isExcel = mimeType?.contains("spreadsheet") == true ||
                         mimeType?.contains("excel") == true ||
                         mimeType?.contains("ms-excel") == true ||
                         fileName.endsWith(".xlsx") ||
                         fileName.endsWith(".xls") ||
                         displayName.endsWith(".xlsx") ||
                         displayName.endsWith(".xls")

            when {
                isCSV -> parseCSV(context, uri)
                isExcel -> parseExcel(context, uri)
                else -> {
                    // Try CSV as fallback
                    val csvResult = parseCSV(context, uri)
                    if (csvResult.records.isNotEmpty()) {
                        csvResult
                    } else {
                        ParseResult(
                            emptyList(),
                            listOf("Unsupported file format. Please use CSV or Excel files. MIME type: $mimeType")
                        )
                    }
                }
            }
        } catch (e: Exception) {
            ParseResult(emptyList(), listOf("Error parsing file: ${e.message}"))
        }
    }
    
    private fun parseCSV(context: Context, uri: Uri): ParseResult {
        val records = mutableListOf<RegistrationRecord>()
        val errors = mutableListOf<String>()
        val skippedRecords = mutableListOf<SkippedRecord>()
        
        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                // Read raw content to detect delimiter
                val content = inputStream.bufferedReader().readText()
                val delimiter = if (content.contains(';')) ';' else ','
                
                // Parse the content manually by lines
                val lines = content.lines().filter { it.isNotBlank() }
                
                if (lines.isEmpty()) {
                    return ParseResult(emptyList(), listOf("File is empty"))
                }
                
                // Skip header row if it exists
                val startIndex = if (lines[0].contains("PHONE", ignoreCase = true) || 
                                   lines[0].contains("0665", ignoreCase = true) || 
                                   lines[0].contains("0622", ignoreCase = true)) {
                    if (lines[0].startsWith("06")) 0 else 1
                } else 0
                
                lines.drop(startIndex).forEachIndexed { index, line ->
                    val lineNumber = index + startIndex + 1
                    val row = line.split(delimiter).map { it.trim() }
                    
                    if (row.size < 4) {
                        errors.add("Line $lineNumber: Expected at least 4 columns, found ${row.size}")
                        return@forEachIndexed
                    }
                    
                    try {
                        val phoneNumber = row[0]
                        val pukLastFour = row[1]
                        
                        // Support different column formats
                        val (fullName, cne) = when {
                            row.size >= 5 -> {
                                // Format: phone;puk;cne;firstname;lastname
                                val name = "${row[3]} ${row[4]}"
                                Pair(name, row[2])
                            }
                            else -> {
                                // Format: phone;puk;fullname;cne
                                Pair(row[2], row[3])
                            }
                        }
                        
                        // Validation - track invalid records
                        val validationErrors = mutableListOf<String>()
                        
                        if (!isValidPhoneNumber(phoneNumber)) {
                            validationErrors.add("Invalid phone number")
                        }
                        
                        if (pukLastFour.length != 4 || !pukLastFour.all { it.isDigit() }) {
                            validationErrors.add("PUK must be exactly 4 digits")
                        }
                        
                        if (fullName.isBlank()) {
                            validationErrors.add("Full name is required")
                        }
                        
                        if (cne.isBlank()) {
                            validationErrors.add("CNE is required")
                        }
                        
                        // If there are validation errors, skip this record
                        if (validationErrors.isNotEmpty()) {
                            skippedRecords.add(
                                SkippedRecord(
                                    lineNumber = lineNumber,
                                    phoneNumber = phoneNumber,
                                    pukLastFour = pukLastFour,
                                    fullName = fullName,
                                    cne = cne,
                                    reason = validationErrors.joinToString(", ")
                                )
                            )
                            return@forEachIndexed
                        }
                        
                        records.add(
                            RegistrationRecord(
                                phoneNumber = phoneNumber,
                                pukLastFour = pukLastFour,
                                fullName = fullName,
                                cne = cne
                            )
                        )
                    } catch (e: Exception) {
                        errors.add("Line $lineNumber: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            return ParseResult(emptyList(), listOf("Error reading CSV file: ${e.message}"))
        }
        
        return ParseResult(records, errors, skippedRecords)
    }
    
    private fun parseExcel(context: Context, uri: Uri): ParseResult {
        val records = mutableListOf<RegistrationRecord>()
        val errors = mutableListOf<String>()
        val skippedRecords = mutableListOf<SkippedRecord>()
        
        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val workbook = WorkbookFactory.create(inputStream)
                val sheet = workbook.getSheetAt(0)
                
                if (sheet.physicalNumberOfRows == 0) {
                    return ParseResult(emptyList(), listOf("Excel file is empty"))
                }
                
                // Detect if first row is header
                val firstRow = sheet.getRow(0)
                val startIndex = if (firstRow != null) {
                    val firstCell = firstRow.getCell(0)?.toString() ?: ""
                    if (firstCell.contains("PHONE", ignoreCase = true) || 
                        firstCell.contains("0665", ignoreCase = true) ||
                        firstCell.contains("0622", ignoreCase = true)) {
                        if (firstCell.startsWith("06")) 0 else 1
                    } else 0
                } else 0
                
                // Parse data rows
                for (i in startIndex until sheet.physicalNumberOfRows) {
                    val row = sheet.getRow(i) ?: continue
                    val lineNumber = i + 1
                    
                    if (row.physicalNumberOfCells < 4) {
                        errors.add("Line $lineNumber: Expected at least 4 columns, found ${row.physicalNumberOfCells}")
                        continue
                    }
                    
                    try {
                        fun getCellValue(cellIndex: Int): String {
                            val cell = row.getCell(cellIndex) ?: return ""
                            return when (cell.cellType) {
                                CellType.STRING -> cell.stringCellValue
                                CellType.NUMERIC -> cell.numericCellValue.toLong().toString()
                                else -> cell.toString()
                            }.trim()
                        }
                        
                        val phoneNumber = getCellValue(0)
                        val pukLastFour = getCellValue(1)
                        
                        // Support different column formats
                        val (fullName, cne) = when {
                            row.physicalNumberOfCells >= 5 -> {
                                // Format: phone;puk;cne;firstname;lastname
                                val name = "${getCellValue(3)} ${getCellValue(4)}"
                                Pair(name, getCellValue(2))
                            }
                            else -> {
                                // Format: phone;puk;fullname;cne
                                Pair(getCellValue(2), getCellValue(3))
                            }
                        }
                        
                        // Validation - track invalid records
                        val validationErrors = mutableListOf<String>()
                        
                        if (!isValidPhoneNumber(phoneNumber)) {
                            validationErrors.add("Invalid phone number")
                        }
                        
                        if (pukLastFour.length != 4 || !pukLastFour.all { it.isDigit() }) {
                            validationErrors.add("PUK must be exactly 4 digits")
                        }
                        
                        if (fullName.isBlank()) {
                            validationErrors.add("Full name is required")
                        }
                        
                        if (cne.isBlank()) {
                            validationErrors.add("CNE is required")
                        }
                        
                        // If there are validation errors, skip this record
                        if (validationErrors.isNotEmpty()) {
                            skippedRecords.add(
                                SkippedRecord(
                                    lineNumber = lineNumber,
                                    phoneNumber = phoneNumber,
                                    pukLastFour = pukLastFour,
                                    fullName = fullName,
                                    cne = cne,
                                    reason = validationErrors.joinToString(", ")
                                )
                            )
                            continue
                        }
                        
                        records.add(
                            RegistrationRecord(
                                phoneNumber = phoneNumber,
                                pukLastFour = pukLastFour,
                                fullName = fullName,
                                cne = cne
                            )
                        )
                    } catch (e: Exception) {
                        errors.add("Line $lineNumber: ${e.message}")
                    }
                }
                
                workbook.close()
            }
        } catch (e: Exception) {
            return ParseResult(emptyList(), listOf("Error reading Excel file: ${e.message}"))
        }
        
        return ParseResult(records, errors, skippedRecords)
    }
    
    private fun isValidPhoneNumber(phone: String): Boolean {
        // Moroccan phone numbers: starts with 0, followed by 6 or 7, total 10 digits
        return phone.matches(Regex("^0[67]\\d{8}$"))
    }
}
