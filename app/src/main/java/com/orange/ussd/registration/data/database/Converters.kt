package com.orange.ussd.registration.data.database

import androidx.room.TypeConverter
import com.orange.ussd.registration.data.model.RegistrationStatus

class Converters {
    
    @TypeConverter
    fun fromRegistrationStatus(value: RegistrationStatus): String {
        return value.name
    }
    
    @TypeConverter
    fun toRegistrationStatus(value: String): RegistrationStatus {
        return try {
            RegistrationStatus.valueOf(value)
        } catch (e: IllegalArgumentException) {
            RegistrationStatus.PENDING
        }
    }
}
