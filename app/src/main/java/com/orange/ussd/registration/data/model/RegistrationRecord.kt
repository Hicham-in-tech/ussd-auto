package com.orange.ussd.registration.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "registration_records")
data class RegistrationRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val phoneNumber: String,
    val pukLastFour: String,
    val fullName: String,
    val cne: String,
    val status: RegistrationStatus = RegistrationStatus.PENDING,
    val errorMessage: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val ussdExecuted: Boolean = false,
    val nameFilled: Boolean = false,
    val cneFilled: Boolean = false,
    val completed: Boolean = false
)

enum class RegistrationStatus {
    PENDING,
    IN_PROGRESS,
    USSD_SENT,
    NAME_FILLED,
    CNE_FILLED,
    COMPLETED,
    ALREADY_REGISTERED,
    FAILED,
    CANCELLED
}
