package com.realtor.geeksales.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * 短信记录：知行同步助手拉回的短信 + 可选的本地系统短信镜像。
 * 注意：本表数据只来自云端同步，不反向写回系统短信箱。
 */
@Entity(
    tableName = "sms_messages",
    indices = [Index(value = ["customerId"]), Index(value = ["messageDate"])]
)
data class SmsMessage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** 关联本地客户；0 = 未关联 */
    val customerId: Long = 0,
    /** 对方号码（原始格式） */
    val phone: String,
    val body: String,
    /** in=收到, out=发出 */
    val direction: String,
    /** 短信时间 EpochMillis */
    val messageDate: Long,
    /** 线上消息 id（去重） */
    val wbMessageId: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Dao
interface SmsDao {

    @Insert
    suspend fun insert(message: SmsMessage): Long

    @Insert
    suspend fun insertAll(messages: List<SmsMessage>)

    @Query("SELECT * FROM sms_messages WHERE wbMessageId = :wbId LIMIT 1")
    suspend fun findByWbMessageId(wbId: String): SmsMessage?

    @Query("SELECT * FROM sms_messages WHERE customerId = :customerId ORDER BY messageDate ASC")
    fun observeByCustomer(customerId: Long): Flow<List<SmsMessage>>

    @Query("SELECT * FROM sms_messages WHERE customerId = :customerId ORDER BY messageDate ASC")
    suspend fun getByCustomer(customerId: Long): List<SmsMessage>

    @Query("SELECT * FROM sms_messages ORDER BY messageDate ASC")
    suspend fun getAll(): List<SmsMessage>

    @Query("DELETE FROM sms_messages")
    suspend fun clearAll()
}
