package com.realtor.geeksales.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * 通话记录：本地系统通话记录镜像 + 知行同步助手拉回的云端通话。
 * 用于客户详情「互动档案」时间线与通话备份。
 * direction: in=呼入 / out=呼出 / missed=未接（与线上 /api/calls 一致）
 */
@Entity(
    tableName = "call_records",
    indices = [Index(value = ["customerId"]), Index(value = ["callDate"])]
)
data class CallRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** 关联本地客户；0 = 未关联 */
    val customerId: Long = 0,
    /** 对方号码（原始格式） */
    val phone: String,
    /** in / out / missed */
    val direction: String,
    /** 通话时长（秒）；未接为 0 */
    val duration: Long,
    /** 通话时间 EpochMillis */
    val callDate: Long,
    val note: String? = null,
    /** 线上通话记录 id（去重） */
    val wbCallId: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Dao
interface CallDao {

    @Insert
    suspend fun insertAll(calls: List<CallRecord>)

    @Query("SELECT * FROM call_records WHERE wbCallId = :wbId LIMIT 1")
    suspend fun findByWbCallId(wbId: String): CallRecord?

    @Query("SELECT * FROM call_records WHERE customerId = :customerId ORDER BY callDate DESC")
    fun observeByCustomer(customerId: Long): Flow<List<CallRecord>>

    @Query("SELECT * FROM call_records WHERE customerId = :customerId ORDER BY callDate DESC")
    suspend fun getByCustomer(customerId: Long): List<CallRecord>

    @Query("SELECT * FROM call_records ORDER BY callDate DESC LIMIT :limit")
    suspend fun getRecent(limit: Int): List<CallRecord>

    @Query("SELECT * FROM call_records WHERE wbCallId IS NULL ORDER BY callDate DESC LIMIT :limit")
    suspend fun getPendingUpload(limit: Int): List<CallRecord>

    @Query("UPDATE call_records SET wbCallId = :wbId WHERE id = :localId")
    suspend fun updateWbCallId(localId: Long, wbId: String)

    /** 重置全部通话的线上标记（云端已删/想全量重传时用；本地记录保留，服务端幂等重推安全） */
    @Query("UPDATE call_records SET wbCallId = NULL")
    suspend fun clearWbCallIds()

    @Query("SELECT * FROM call_records ORDER BY callDate DESC")
    suspend fun getAll(): List<CallRecord>

    @Query("DELETE FROM call_records")
    suspend fun clearAll()
}
