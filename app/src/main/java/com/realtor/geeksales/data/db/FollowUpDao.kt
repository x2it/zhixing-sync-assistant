package com.realtor.geeksales.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FollowUpDao {

    @Insert
    suspend fun insert(followUp: FollowUp): Long

    @Query("SELECT * FROM follow_ups WHERE customerId = :customerId ORDER BY createdAt DESC")
    fun observeByCustomer(customerId: Long): Flow<List<FollowUp>>

    @Query("SELECT * FROM follow_ups ORDER BY createdAt DESC LIMIT :limit")
    fun observeRecent(limit: Int = 50): Flow<List<FollowUp>>

    @Query("SELECT * FROM follow_ups WHERE createdAt >= :sinceMs AND createdAt <= :untilMs ORDER BY createdAt ASC")
    suspend fun getByRange(sinceMs: Long, untilMs: Long): List<FollowUp>

    @Query("SELECT COUNT(*) FROM follow_ups WHERE createdAt >= :dayStart AND createdAt < :dayEnd")
    suspend fun countToday(dayStart: Long, dayEnd: Long): Int

    @Query("SELECT * FROM follow_ups ORDER BY createdAt ASC")
    suspend fun getAll(): List<FollowUp>

    /** 按客户 + 备注 + 日期查重（用于知行同步助手跟进记录去重） */
    @Query("SELECT * FROM follow_ups WHERE customerId = :customerId AND note = :note AND createdAt = :createdAt LIMIT 1")
    suspend fun findByDedupKey(customerId: Long, note: String?, createdAt: Long): FollowUp?

    @Insert
    suspend fun insertAll(followUps: List<FollowUp>)

    /** 下一个未到期的跟进提醒（含客户名），用于排定 Alarm */
    @Query("""
        SELECT f.remindAt AS remindAt, c.name AS customerName
        FROM follow_ups f JOIN customers c ON c.id = f.customerId
        WHERE f.remindAt IS NOT NULL AND f.remindAt > :now
        ORDER BY f.remindAt ASC LIMIT 1
    """)
    suspend fun nextReminder(now: Long): ReminderRow?
}

data class ReminderRow(
    val remindAt: Long,
    val customerName: String
)
