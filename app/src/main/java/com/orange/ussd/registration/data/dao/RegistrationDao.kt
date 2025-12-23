package com.orange.ussd.registration.data.dao

import androidx.room.*
import com.orange.ussd.registration.data.model.RegistrationRecord
import com.orange.ussd.registration.data.model.RegistrationStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface RegistrationDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: RegistrationRecord): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(records: List<RegistrationRecord>)
    
    @Update
    suspend fun update(record: RegistrationRecord)
    
    @Delete
    suspend fun delete(record: RegistrationRecord)
    
    @Query("SELECT * FROM registration_records ORDER BY timestamp DESC")
    fun getAllRecords(): Flow<List<RegistrationRecord>>
    
    @Query("SELECT * FROM registration_records WHERE id = :id")
    suspend fun getRecordById(id: Long): RegistrationRecord?
    
    @Query("SELECT * FROM registration_records WHERE phoneNumber = :phoneNumber")
    suspend fun getRecordByPhoneNumber(phoneNumber: String): RegistrationRecord?
    
    @Query("SELECT * FROM registration_records WHERE status = :status ORDER BY timestamp ASC")
    fun getRecordsByStatus(status: RegistrationStatus): Flow<List<RegistrationRecord>>
    
    @Query("SELECT * FROM registration_records WHERE status = :status ORDER BY id ASC LIMIT 1")
    suspend fun getNextPendingRecord(status: RegistrationStatus = RegistrationStatus.PENDING): RegistrationRecord?
    
    @Query("UPDATE registration_records SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: RegistrationStatus)
    
    @Query("UPDATE registration_records SET status = :status, errorMessage = :errorMessage WHERE id = :id")
    suspend fun updateStatusWithError(id: Long, status: RegistrationStatus, errorMessage: String)
    
    @Query("UPDATE registration_records SET ussdExecuted = :executed WHERE id = :id")
    suspend fun updateUssdExecuted(id: Long, executed: Boolean)
    
    @Query("UPDATE registration_records SET nameFilled = :filled WHERE id = :id")
    suspend fun updateNameFilled(id: Long, filled: Boolean)
    
    @Query("UPDATE registration_records SET cneFilled = :filled WHERE id = :id")
    suspend fun updateCneFilled(id: Long, filled: Boolean)
    
    @Query("SELECT COUNT(*) FROM registration_records WHERE status = :status")
    suspend fun getCountByStatus(status: RegistrationStatus): Int
    
    @Query("DELETE FROM registration_records")
    suspend fun deleteAll()
    
    @Query("SELECT COUNT(*) FROM registration_records")
    suspend fun getTotalCount(): Int
}
