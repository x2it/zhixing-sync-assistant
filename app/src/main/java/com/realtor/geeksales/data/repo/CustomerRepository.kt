package com.realtor.geeksales.data.repo

import com.realtor.geeksales.data.db.Customer
import com.realtor.geeksales.data.db.CustomerDao
import com.realtor.geeksales.data.db.CustomerField
import com.realtor.geeksales.data.db.CustomerFieldDao
import com.realtor.geeksales.data.db.CustomerTagMap
import com.realtor.geeksales.data.db.FollowUp
import com.realtor.geeksales.data.db.FollowUpDao
import com.realtor.geeksales.data.db.CallDao
import com.realtor.geeksales.data.db.CallRecord
import com.realtor.geeksales.data.db.FollowResult
import com.realtor.geeksales.data.db.IntentCount
import com.realtor.geeksales.data.db.IntentLevel
import com.realtor.geeksales.data.db.SmsDao
import com.realtor.geeksales.data.db.SmsMessage
import com.realtor.geeksales.data.db.Tag
import com.realtor.geeksales.data.db.TagDao
import com.realtor.geeksales.util.Formatter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CustomerRepository @Inject constructor(
    private val customerDao: CustomerDao,
    private val followUpDao: FollowUpDao,
    private val tagDao: TagDao,
    private val smsDao: SmsDao,
    private val customerFieldDao: CustomerFieldDao,
    private val callDao: CallDao
) {
    fun observeFiltered(
        query: String?,
        level: IntentLevel?,
        tagName: String?,
        onlyOverdue: Boolean,
        onlyQueued: Boolean,
        onlyCalledToday: Boolean = false,
        onlyNotCalled: Boolean = false,
        now: Long = System.currentTimeMillis()
    ): Flow<List<Customer>> = customerDao.observeFiltered(
        query = query?.takeIf { it.isNotBlank() },
        level = level,
        tagName = tagName?.takeIf { it.isNotBlank() },
        onlyOverdue = if (onlyOverdue) 1 else 0,
        onlyQueued = if (onlyQueued) 1 else 0,
        onlyCalledToday = if (onlyCalledToday) 1 else 0,
        onlyNotCalled = if (onlyNotCalled) 1 else 0,
        now = now,
        todayStart = Formatter.todayStart()
    )

    fun observeById(id: Long): Flow<Customer?> = customerDao.observeById(id)

    /** 工作台「今日跟进」：今日需跟进客户（nextFollowAt 在今天内） */
    fun observeTodayFollowups(): Flow<List<Customer>> {
        val start = Formatter.todayStart()
        return customerDao.observeTodayFollowups(start, start + 86_400_000L)
    }

    /** 工作台「已过期」：nextFollowAt 已过期客户 */
    fun observeOverdue(now: Long = System.currentTimeMillis()): Flow<List<Customer>> =
        customerDao.observeOverdue(now)

    suspend fun getFilteredForExport(
        query: String?,
        level: IntentLevel?,
        tagName: String?,
        onlyOverdue: Boolean,
        onlyQueued: Boolean,
        onlyCalledToday: Boolean = false,
        onlyNotCalled: Boolean = false,
        now: Long = System.currentTimeMillis(),
        todayStart: Long = Formatter.todayStart()
    ): List<Customer> = customerDao.getFilteredForExport(
        query = query?.takeIf { it.isNotBlank() },
        level = level,
        tagName = tagName?.takeIf { it.isNotBlank() },
        onlyOverdue = if (onlyOverdue) 1 else 0,
        onlyQueued = if (onlyQueued) 1 else 0,
        onlyCalledToday = if (onlyCalledToday) 1 else 0,
        onlyNotCalled = if (onlyNotCalled) 1 else 0,
        now = now,
        todayStart = todayStart
    )

    suspend fun getTodayStats(): TodayStats {
        val now = System.currentTimeMillis()
        val todayStart = Formatter.todayStart()
        val todayEnd = todayStart + 86_400_000L
        return TodayStats(
            totalCustomers = customerDao.countAll(),
            calledToday = customerDao.countCalledToday(todayStart),
            notCalledToday = customerDao.countNotCalledToday(todayStart),
            overdue = customerDao.countOverdue(now),
            followUpsToday = followUpDao.countToday(todayStart, todayEnd),
            intentCounts = customerDao.groupByIntent()
        )
    }

    suspend fun upsert(customer: Customer, tags: List<String>? = null): Long {
        val id = customerDao.upsert(
            customer.copy(updatedAt = System.currentTimeMillis())
        )
        if (tags != null) {
            tagDao.unlinkAllForCustomer(id)
            tags.filter { it.isNotBlank() }.distinct().forEach { name ->
                val tagId = tagDao.createIfAbsent(Tag(name = name))
                // createIfAbsent 在标签已存在时（IGNORE 冲突）返回 -1，
                // 此时必须查回已有 id，否则该客户永远关联不上已存在的标签。
                val resolvedId = if (tagId > 0) tagId else tagDao.getByName(name)?.id ?: 0L
                if (resolvedId > 0) {
                    tagDao.link(CustomerTagMap(id, resolvedId))
                }
            }
        }
        return id
    }

    /** 读取客户现有标签名，用于编辑页回显（避免保存时误删原标签） */
    suspend fun tagsOf(customerId: Long): List<String> =
        tagDao.getForCustomer(customerId).map { it.name }

    /** 某客户的标签（响应式，详情页展示用） */
    fun observeTagsOf(customerId: Long): Flow<List<String>> =
        tagDao.observeForCustomer(customerId).map { list -> list.map { it.name } }

    /** 仅给客户挂标签（不重写客户数据），用于通讯录导入的群组映射等批量场景 */
    suspend fun applyTags(customerId: Long, tags: List<String>) {
        if (customerId <= 0L) return
        val names = tags.filter { it.isNotBlank() }.distinct()
        if (names.isEmpty()) return
        names.forEach { name ->
            val tagId = tagDao.createIfAbsent(Tag(name = name))
            val resolvedId = if (tagId > 0) tagId else tagDao.getByName(name)?.id ?: 0L
            if (resolvedId > 0) {
                tagDao.link(CustomerTagMap(customerId, resolvedId))
            }
        }
    }

    /** 移除客户上的某个标签（云端优先模式下标签以线上为准） */
    suspend fun removeTag(customerId: Long, tagName: String) {
        if (customerId <= 0L) return
        val tag = tagDao.getByName(tagName) ?: return
        tagDao.deleteMap(customerId, tag.id)
    }

    suspend fun deleteById(id: Long) = customerDao.deleteById(id)

    suspend fun countAll(): Int = customerDao.countAll()

    suspend fun deleteAll() = customerDao.deleteAll()

    suspend fun getById(id: Long): Customer? = customerDao.getById(id)

    suspend fun incDial(id: Long, at: Long = System.currentTimeMillis()) =
        customerDao.incDialCount(id, at)

    fun observeQueue(): Flow<List<Customer>> = customerDao.observeQueue()

    suspend fun addToQueue(id: Long) {
        // MAX+1 保证不与残留编号冲突（COUNT+1 在中间移除后会撞号）
        customerDao.setQueue(id, true, customerDao.nextQueueOrder())
    }

    suspend fun addToQueueBatch(ids: List<Long>) {
        if (ids.isEmpty()) return
        // 单事务内逐条赋唯一编号，事务提交后 Room Flow 只发射一次，避免重组风暴
        customerDao.enqueueAllOrdered(ids)
    }

    /** 批量设置分层（名单多选扁平操作） */
    suspend fun setIntentLevels(ids: List<Long>, level: com.realtor.geeksales.data.db.IntentLevel) {
        if (ids.isNotEmpty()) customerDao.updateIntentLevels(ids, level.name)
    }

    /** 批量打标签（覆盖该批客户的同一标签集合，不动其它标签） */
    suspend fun addTagsToMany(ids: List<Long>, tags: List<String>) {
        ids.forEach { id -> applyTags(id, tags) }
    }

    suspend fun removeFromQueue(id: Long) {
        customerDao.setQueue(id, false, null)
        // 出队后重排，保持编号连续（1..N），与队列页位置编号一致
        customerDao.normalizeQueue()
    }

    fun observeCount() = customerDao.observeCount()

    /** 一次性取本地联系人总数（同步汇总用，不建立持续订阅） */
    suspend fun countOnce(): Int = customerDao.countOnce()

    suspend fun getAll() = customerDao.getAll()

    suspend fun getByPhoneNormalized(p: String) = customerDao.getByPhoneNormalized(p)

    suspend fun clearQueue() = customerDao.clearQueue()

    suspend fun normalizeQueueOnLaunch() = customerDao.normalizeQueue()

    suspend fun upsertAll(list: List<Customer>) = customerDao.upsertAll(list)

    /** 单条 upsert 并返回行 id（知行同步助手导入用） */
    suspend fun upsertAndGetId(c: Customer): Long = customerDao.upsert(c)

    suspend fun groupByIntent() = customerDao.groupByIntent()

    // ---- FollowUps ----
    suspend fun recordFollowUp(
        customerId: Long,
        result: FollowResult,
        durationSec: Int,
        note: String?,
        remindAt: Long?,
        fromPostCall: Boolean = false
    ) {
        followUpDao.insert(
            FollowUp(
                customerId = customerId,
                result = result,
                durationSec = durationSec,
                note = note,
                remindAt = remindAt,
                fromPostCall = fromPostCall
            )
        )
        // 同步 Customer 的下次跟进时间（六层语义：A高意向 B已接触 C信息完整；错号/停机/未接通不误标层级）
        val c = customerDao.getById(customerId) ?: return
        val level = when (result) {
            FollowResult.APPOINTMENT, FollowResult.CONNECTED -> IntentLevel.A
            FollowResult.PENDING -> IntentLevel.B
            FollowResult.NOT_INTERESTED -> IntentLevel.C
            // 错号/停机/未接通 → U（未分类），保留用户手动分层
            FollowResult.WRONG_NUMBER, FollowResult.SHUTDOWN, FollowResult.NOT_REACHED -> IntentLevel.U
        }
        customerDao.upsert(
            c.copy(
                nextFollowAt = remindAt ?: c.nextFollowAt,
                intentLevel = if (level == IntentLevel.U) c.intentLevel else level,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    fun observeFollowUpsOf(customerId: Long): Flow<List<FollowUp>> =
        followUpDao.observeByCustomer(customerId)

    fun observeRecentFollowUps(limit: Int = 50) = followUpDao.observeRecent(limit)

    suspend fun countFollowUpsInRange(start: Long, end: Long) =
        followUpDao.countToday(start, end)

    suspend fun followUpsInRange(start: Long, end: Long) =
        followUpDao.getByRange(start, end)

    // ---- 知行同步助手同步辅助 ----
    suspend fun allFollowUps(): List<FollowUp> = followUpDao.getAll()

    /** 按 (customerId, note, createdAt) 查重，避免云端跟进记录重复导入 */
    suspend fun findFollowUpDedup(customerId: Long, note: String?, createdAt: Long): FollowUp? =
        followUpDao.findByDedupKey(customerId, note, createdAt)

    suspend fun insertFollowUps(list: List<FollowUp>) = followUpDao.insertAll(list)

    /** 写回知行同步助手联系人 id（用于跟进历史推送映射） */
    suspend fun updateWbContactId(customerId: Long, wbId: String) {
        val c = customerDao.getById(customerId) ?: return
        customerDao.upsert(c.copy(wbContactId = wbId, updatedAt = System.currentTimeMillis()))
    }

    // ---- 短信同步 ----
    fun observeSmsOf(customerId: Long): Flow<List<SmsMessage>> = smsDao.observeByCustomer(customerId)

    suspend fun smsByCustomer(customerId: Long): List<SmsMessage> = smsDao.getByCustomer(customerId)

    suspend fun allSms(): List<SmsMessage> = smsDao.getAll()

    suspend fun insertSms(messages: List<SmsMessage>) = smsDao.insertAll(messages)

    suspend fun smsByWbId(wbId: String): SmsMessage? = smsDao.findByWbMessageId(wbId)

    // ---- 通话记录（本地通话镜像 + 云端通话备份）----
    fun observeCallsOf(customerId: Long): Flow<List<CallRecord>> = callDao.observeByCustomer(customerId)

    suspend fun callsByCustomer(customerId: Long): List<CallRecord> = callDao.getByCustomer(customerId)

    suspend fun recentCalls(limit: Int): List<CallRecord> = callDao.getRecent(limit)

    /** 尚未上传云端（无 wbCallId）的通话，按时间倒序 */
    suspend fun pendingCallUploads(limit: Int = 500): List<CallRecord> = callDao.getPendingUpload(limit)

    suspend fun insertCalls(calls: List<CallRecord>) = callDao.insertAll(calls)
    suspend fun allCalls(): List<CallRecord> = callDao.getAll()

    suspend fun callByWbId(wbId: String): CallRecord? = callDao.findByWbCallId(wbId)

    suspend fun updateWbCallId(localId: Long, wbId: String) = callDao.updateWbCallId(localId, wbId)
    suspend fun clearWbCallIds() = callDao.clearWbCallIds()

    /** 清空通话表（时光机恢复前） */
    suspend fun clearAllCalls() = callDao.clearAll()

    // ---- 时光机快照/恢复 ----
    suspend fun allTags(): List<Tag> = tagDao.getAll()

    fun observeAllTags(): Flow<List<Tag>> = tagDao.observeAll()

    suspend fun allTagMappings(): List<CustomerTagMap> = tagDao.getAllMappings()

    /** 清空标签与映射（时光机恢复前） */
    suspend fun clearAllTags() {
        tagDao.clearMappings()
        tagDao.clearAll()
    }

    /** 清空短信表（时光机恢复前） */
    suspend fun clearAllSms() = smsDao.clearAll()

    // ---- 扩展字段（线上模板 schema 驱动的 EAV 存储）----
    fun observeExtFieldsOf(customerId: Long): Flow<List<CustomerField>> =
        customerFieldDao.observeFieldsOf(customerId)

    suspend fun extFieldsOf(customerId: Long): Map<String, String> =
        customerFieldDao.fieldsOf(customerId).associate { it.fieldKey to it.fieldValue }

    suspend fun extFieldValue(customerId: Long, key: String): String? =
        customerFieldDao.valueOf(customerId, key)

    /** 写单个扩展字段 */
    suspend fun putExtField(customerId: Long, key: String, value: String) {
        if (customerId <= 0L || key.isBlank()) return
        customerFieldDao.upsert(CustomerField(customerId = customerId, fieldKey = key, fieldValue = value))
    }

    /** 批量写扩展字段（表单保存/云端导入） */
    suspend fun putExtFields(customerId: Long, values: Map<String, String>) {
        if (customerId <= 0L) return
        customerFieldDao.upsertAll(values.map { (k, v) -> CustomerField(customerId = customerId, fieldKey = k, fieldValue = v) })
    }

    suspend fun clearExtFields(customerId: Long) = customerFieldDao.clearForCustomer(customerId)

    suspend fun deleteExtField(customerId: Long, key: String) = customerFieldDao.delete(customerId, key)

    suspend fun allExtFieldKeys(): List<String> = customerFieldDao.allKeys()

    /** 全部扩展字段（时光机快照用） */
    suspend fun allExtFields(): List<com.realtor.geeksales.data.db.CustomerField> = customerFieldDao.getAll()

    /** 清空全部扩展字段（时光机恢复前） */
    suspend fun clearAllExtFields() = customerFieldDao.clearAll()
}

data class TodayStats(
    val totalCustomers: Int,
    val calledToday: Int,
    val notCalledToday: Int,
    val overdue: Int,
    val followUpsToday: Int,
    val intentCounts: List<IntentCount>
)