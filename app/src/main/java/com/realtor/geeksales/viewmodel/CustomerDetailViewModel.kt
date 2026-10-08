package com.realtor.geeksales.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.realtor.geeksales.data.db.Customer
import com.realtor.geeksales.data.db.FollowResult
import com.realtor.geeksales.data.repo.CustomerRepository
import com.realtor.geeksales.data.schema.FieldDef
import com.realtor.geeksales.data.schema.SchemaStore
import com.realtor.geeksales.telephony.DialerHelper
import com.realtor.geeksales.telephony.ReminderScheduler
import com.realtor.geeksales.util.Formatter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CustomerDetailViewModel @Inject constructor(
    private val repo: CustomerRepository,
    private val dialerHelper: DialerHelper,
    private val reminderScheduler: ReminderScheduler,
    private val schemaStore: SchemaStore
) : ViewModel() {

    private val idFlow = MutableStateFlow(0L)

    /** 当前生效模板字段（详情页按 schema 动态展示） */
    private val _schema = MutableStateFlow(schemaStore.current())
    val schema: StateFlow<List<FieldDef>> = _schema

    /** 客户标签（详情页展示，与线上 tagIds 对应） */
    val tags = idFlow.flatMapLatest { id ->
        if (id <= 0) flowOf<List<String>>(emptyList()) else repo.observeTagsOf(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setCustomerId(id: Long) {
        if (id != idFlow.value) idFlow.value = id
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val customer = idFlow.flatMapLatest { id ->
        if (id == 0L) flowOf(null) else repo.observeById(id)
    }.conflate().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** 客户扩展字段（线上模板自定义字段值） */
    @OptIn(ExperimentalCoroutinesApi::class)
    val extFields = idFlow.flatMapLatest { id ->
        if (id == 0L) flowOf(emptyMap()) else repo.observeExtFieldsOf(id).map { list ->
            list.associate { it.fieldKey to it.fieldValue }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    @OptIn(ExperimentalCoroutinesApi::class)
    val followUps = idFlow.flatMapLatest { id ->
        if (id == 0L) flowOf(emptyList()) else repo.observeFollowUpsOf(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 客户短信记录（知行同步助手同步来的云端短信） */
    @OptIn(ExperimentalCoroutinesApi::class)
    val smsMessages = idFlow.flatMapLatest { id ->
        if (id == 0L) flowOf(emptyList()) else repo.observeSmsOf(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 客户通话记录（本地镜像 + 云端通话备份，互动档案的一部分） */
    @OptIn(ExperimentalCoroutinesApi::class)
    val callRecords = idFlow.flatMapLatest { id ->
        if (id == 0L) flowOf(emptyList()) else repo.observeCallsOf(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun dial(direct: Boolean = false) = viewModelScope.launch {
        runCatching {
            val c = customer.value ?: return@launch
            repo.incDial(c.id)
            if (direct) dialerHelper.directCall(c.phone) else dialerHelper.openDialer(c.phone)
        }.onFailure { it.printStackTrace() }
    }

    fun recordFollowUp(result: FollowResult, note: String?, remindAt: Long?, durationSec: Int = 0) =
        viewModelScope.launch {
            runCatching {
                val id = customer.value?.id ?: return@launch
                repo.recordFollowUp(id, result, durationSec, note, remindAt)
                // 登记了下次跟进 → 重排闹钟
                if (remindAt != null) reminderScheduler.scheduleNext()
            }.onFailure { it.printStackTrace() }
        }

    fun toggleQueue() = viewModelScope.launch {
        runCatching {
            val c = customer.value ?: return@launch
            if (c.queued) repo.removeFromQueue(c.id) else repo.addToQueue(c.id)
        }.onFailure { it.printStackTrace() }
    }
}
