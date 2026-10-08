package com.realtor.geeksales.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.realtor.geeksales.data.db.FollowUp
import com.realtor.geeksales.navigation.Routes
import com.realtor.geeksales.ui.components.AsciiDivider
import com.realtor.geeksales.ui.components.EmptyState
import com.realtor.geeksales.ui.components.GeekCard
import com.realtor.geeksales.ui.components.GeekGhostButton
import com.realtor.geeksales.ui.components.GeekPrimaryButton
import com.realtor.geeksales.ui.components.GeekTopBar
import com.realtor.geeksales.ui.components.IntentLevelChip
import com.realtor.geeksales.ui.components.resultColor
import com.realtor.geeksales.ui.theme.Accent
import com.realtor.geeksales.ui.theme.Bg
import com.realtor.geeksales.ui.theme.BgElev2
import com.realtor.geeksales.ui.theme.Danger
import com.realtor.geeksales.ui.theme.Divider
import com.realtor.geeksales.ui.theme.Success
import com.realtor.geeksales.ui.theme.TextMuted
import com.realtor.geeksales.ui.theme.TextPrimary
import com.realtor.geeksales.ui.theme.TextSecondary
import com.realtor.geeksales.util.Formatter
import com.realtor.geeksales.viewmodel.CustomerDetailViewModel

@Composable
fun CustomerDetailScreen(
    id: Long,
    onNav: (String) -> Unit,
    onBack: () -> Unit,
    openFollowUp: (Long) -> Unit,
    vm: CustomerDetailViewModel = hiltViewModel()
) {
    val c by vm.customer.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val list by vm.followUps.collectAsStateWithLifecycle()
    val smsList by vm.smsMessages.collectAsStateWithLifecycle()
    val callList by vm.callRecords.collectAsStateWithLifecycle()
    val extFields by vm.extFields.collectAsStateWithLifecycle()
    val schema by vm.schema.collectAsStateWithLifecycle()
    val tags by vm.tags.collectAsStateWithLifecycle()

    // 「直接拨打」需要 CALL_PHONE 权限：未授权时先请求，拒绝后引导去系统设置
    val callPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) vm.dial(true)
        else com.realtor.geeksales.ui.components.GlobalToast.showError("未授予「拨打电话」权限：请在 系统设置 → 应用 → 知行同步助手 → 权限 → 拨打电话 中开启（仅影响「直接拨打」，普通拨号不受影响）")
    }
    androidx.compose.runtime.LaunchedEffect(id) { vm.setCustomerId(id) }
    // 超时降级：3 秒仍未加载出客户，显示明确错误态而非无限转圈（历史"看似假死"来源之一）
    var loadTimeout by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(id, c) {
        if (c == null) {
            loadTimeout = false
            kotlinx.coroutines.delay(3000)
            if (c == null) loadTimeout = true
        } else loadTimeout = false
    }
    Column(Modifier.fillMaxSize().background(Bg)) {
        GeekTopBar(
            title = "客户详情",
            subtitle = c?.name ?: "--",
            onBack = onBack,
            actions = {
                if (c != null) {
                    GeekGhostButton("编辑", onClick = { onNav(Routes.customerEdit(c!!.id)) }, color = Accent)
                }
            }
        )
        if (c == null) {
            if (loadTimeout) {
                // 明确的错误界面：可返回、可重试，绝不无限转圈
                Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text("未找到该客户", color = Danger, style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(8.dp))
                    Text("可能已被删除，或数据加载异常。", color = TextMuted, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(20.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        GeekGhostButton("返回", color = Accent, onClick = onBack)
                        GeekGhostButton("重试", color = Success, onClick = { loadTimeout = false; vm.setCustomerId(id) })
                    }
                }
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        androidx.compose.material3.CircularProgressIndicator(color = Accent, modifier = Modifier.width(32.dp).height(32.dp))
                        Spacer(Modifier.height(12.dp))
                        Text("加载中...", color = TextMuted, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            return@Column
        }
        val customer = c!!
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    GeekCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(customer.name, color = TextPrimary, style = MaterialTheme.typography.headlineMedium, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                                Spacer(Modifier.width(10.dp))
                                IntentLevelChip(customer.intentLevel)
                            }
                            // 标签展示（模板标签选择器录入 / 云端同步回来）
                            if (tags.isNotEmpty()) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                                    tags.forEach { t ->
                                        Text(
                                            text = t,
                                            color = TextSecondary,
                                            modifier = Modifier
                                                .background(BgElev2)
                                                .border(1.dp, Divider)
                                                .padding(horizontal = 8.dp, vertical = 3.dp),
                                            style = MaterialTheme.typography.labelMedium,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("电话  ", color = TextMuted, style = MaterialTheme.typography.bodyMedium)
                                Text(customer.phone, color = Accent, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.headlineMedium, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            }
                            if (!customer.phone2.isNullOrBlank()) Row { Text("备用  ", color = TextMuted, style = MaterialTheme.typography.bodyMedium); Spacer(Modifier.width(4.dp)); Text(customer.phone2!!, color = TextSecondary, style = MaterialTheme.typography.bodyMedium) }
                            // schema 驱动字段区：线上模板定义什么就显示什么（内置列 + 扩展字段）
                            val schemaFields = remember(schema, extFields) {
                                schema
                                    .filter { it.key != com.realtor.geeksales.data.schema.BuiltinKeys.NAME &&
                                        it.key != com.realtor.geeksales.data.schema.BuiltinKeys.PHONE &&
                                        it.key != com.realtor.geeksales.data.schema.BuiltinKeys.PHONE2 &&
                                        it.key != com.realtor.geeksales.data.schema.BuiltinKeys.INTENT_LEVEL &&
                                        it.key != com.realtor.geeksales.data.schema.BuiltinKeys.NOTE }
                                    .sortedBy { it.order }
                            }
                            schemaFields.forEach { f ->
                                val v = if (f.builtin) builtinDetailValue(f.key, customer) else extFields[f.key]
                                if (!v.isNullOrBlank()) KV(f.label, v)
                            }
                            KV("拨打次数", "${customer.dialCount}  |  最近 ${Formatter.full(customer.lastDialAt)}")
                            if (!customer.note.isNullOrBlank()) {
                                Spacer(Modifier.height(4.dp))
                                Column(Modifier.fillMaxWidth().background(BgElev2).padding(10.dp).border(1.dp, Divider)) {
                                    Text("备注", color = TextMuted, style = MaterialTheme.typography.labelMedium)
                                    Text(customer.note!!, color = TextPrimary, style = MaterialTheme.typography.bodyLarge)
                                }
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GeekPrimaryButton("拨号", { vm.dial(false) }, Modifier.weight(1f))
                        GeekGhostButton("直接拨打", color = Danger, onClick = {
                            if (androidx.core.content.ContextCompat.checkSelfPermission(
                                    ctx, android.Manifest.permission.CALL_PHONE
                                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                            ) {
                                vm.dial(true)
                            } else {
                                callPermLauncher.launch(android.Manifest.permission.CALL_PHONE)
                            }
                        })
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GeekPrimaryButton("登记跟进", { openFollowUp(customer.id) }, Modifier.weight(1f))
                        GeekGhostButton(if (customer.queued) "从队列移除" else "加入拨号队列", color = Success, onClick = { vm.toggleQueue() })
                    }
                }
            }
            item { AsciiDivider(Modifier.padding(horizontal = 12.dp)) }
            item {
                Column(Modifier.padding(12.dp)) {
                    Text("跟进记录", color = Accent, style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.height(6.dp))
                }
            }
            if (list.isEmpty()) {
                item { EmptyState("还没有跟进记录", "拨打一次电话后，挂断会自动弹出登记卡片。") }
            } else {
                items(list) { f -> FollowItem(f) }
            }
            item { AsciiDivider(Modifier.padding(horizontal = 12.dp)) }
            item {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("短信记录", color = Accent, style = MaterialTheme.typography.labelMedium)
                        Spacer(Modifier.width(8.dp))
                        Text("（知行同步助手同步）", color = TextMuted, style = MaterialTheme.typography.labelMedium)
                    }
                    Spacer(Modifier.height(6.dp))
                }
            }
            if (smsList.isEmpty()) {
                item { EmptyState("暂无云端短信", "在「数据」页从知行同步助手拉取短信后，这里会显示与该客户相关的短信。") }
            } else {
                items(smsList) { s -> SmsItem(s) }
            }
            item { AsciiDivider(Modifier.padding(horizontal = 12.dp)) }
            item {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("通话记录", color = Accent, style = MaterialTheme.typography.labelMedium)
                        Spacer(Modifier.width(8.dp))
                        Text("（本机镜像 + 云端备份）", color = TextMuted, style = MaterialTheme.typography.labelMedium)
                    }
                    Spacer(Modifier.height(6.dp))
                }
            }
            if (callList.isEmpty()) {
                item { EmptyState("暂无通话记录", "在「数据」页执行「同步到云端」后，这里会显示该客户的通话时间线。") }
            } else {
                items(callList) { c -> CallItem(c) }
            }
            item { Spacer(Modifier.height(100.dp)) }
        }
    }
}

/** 内置字段值映射（详情展示用）：key → Customer 列值 */
private fun builtinDetailValue(key: String, c: com.realtor.geeksales.data.db.Customer): String? = when (key) {
    com.realtor.geeksales.data.schema.BuiltinKeys.GENDER -> c.gender
    com.realtor.geeksales.data.schema.BuiltinKeys.AGE -> c.age?.toString()
    com.realtor.geeksales.data.schema.BuiltinKeys.WECHAT -> c.wechat
    com.realtor.geeksales.data.schema.BuiltinKeys.SOURCE -> c.source
    com.realtor.geeksales.data.schema.BuiltinKeys.TAGS -> null // 标签另行展示
    com.realtor.geeksales.data.schema.BuiltinKeys.EMAIL -> c.email
    com.realtor.geeksales.data.schema.BuiltinKeys.IM -> c.im
    com.realtor.geeksales.data.schema.BuiltinKeys.COMPANY -> c.company
    com.realtor.geeksales.data.schema.BuiltinKeys.JOB_TITLE -> c.jobTitle
    com.realtor.geeksales.data.schema.BuiltinKeys.BIRTHDAY -> c.birthday
    com.realtor.geeksales.data.schema.BuiltinKeys.NICKNAME -> c.nickname
    com.realtor.geeksales.data.schema.BuiltinKeys.ADDRESS -> c.address
    com.realtor.geeksales.data.schema.BuiltinKeys.WEBSITE -> c.website
    com.realtor.geeksales.data.schema.BuiltinKeys.AREA_PREF -> c.areaPref
    com.realtor.geeksales.data.schema.BuiltinKeys.BUDGET_MIN -> c.budgetMinWan?.let { "$it 万" }
    com.realtor.geeksales.data.schema.BuiltinKeys.BUDGET_MAX -> c.budgetMaxWan?.let { "$it 万" }
    com.realtor.geeksales.data.schema.BuiltinKeys.HOUSE_TYPE -> c.houseType
    com.realtor.geeksales.data.schema.BuiltinKeys.TARGET_PROJECT -> c.targetProject
    com.realtor.geeksales.data.schema.BuiltinKeys.NEXT_FOLLOW_AT -> c.nextFollowAt?.let { Formatter.full(it) }
    else -> null
}

@Composable
private fun SmsItem(s: com.realtor.geeksales.data.db.SmsMessage) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp)) {
        Column(Modifier.background(BgElev2).padding(12.dp).border(1.dp, Divider)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (s.direction == "in") "收" else "发", color = if (s.direction == "in") Success else Accent, style = MaterialTheme.typography.labelLarge, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                Spacer(Modifier.width(10.dp))
                Text(s.phone, color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.weight(1f))
                Text(Formatter.full(s.messageDate), color = TextMuted, style = MaterialTheme.typography.labelMedium)
            }
            Spacer(Modifier.height(6.dp))
            Text(s.body, color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** 通话记录条目（互动档案）：呼入/呼出/未接 + 时长 + 时间 */
@Composable
private fun CallItem(c: com.realtor.geeksales.data.db.CallRecord) {
    val (label, color) = when (c.direction) {
        "in" -> "呼入" to Success
        "out" -> "呼出" to Accent
        else -> "未接" to com.realtor.geeksales.ui.theme.Danger
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp)) {
        Column(Modifier.background(BgElev2).padding(12.dp).border(1.dp, Divider)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, color = color, style = MaterialTheme.typography.labelLarge, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                Spacer(Modifier.width(10.dp))
                Text(if (c.duration > 0) "时长 ${Formatter.humanDuration(c.duration.toInt())}" else "未接通", color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.weight(1f))
                Text(Formatter.full(c.callDate), color = TextMuted, style = MaterialTheme.typography.labelMedium)
            }
            if (!c.note.isNullOrBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(c.note!!, color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun FollowItem(f: FollowUp) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp)) {
        Column(Modifier.background(BgElev2).padding(12.dp).border(1.dp, Divider)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(followResultLabel(f.result), color = resultColor(f.result), style = MaterialTheme.typography.labelLarge, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                Spacer(Modifier.width(10.dp))
                Text("时长 ${Formatter.humanDuration(f.durationSec)}", color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.weight(1f))
                Text(Formatter.full(f.createdAt), color = TextMuted, style = MaterialTheme.typography.labelMedium)
            }
            if (!f.note.isNullOrBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(f.note!!, color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
            }
            if (f.remindAt != null) {
                Spacer(Modifier.height(4.dp))
                Text("下次跟进  ${Formatter.full(f.remindAt)}", color = com.realtor.geeksales.ui.theme.Warning, style = MaterialTheme.typography.labelMedium)
            }
            if (f.fromPostCall) {
                Spacer(Modifier.height(4.dp))
                Text("（挂断自动登记）", color = TextMuted, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun KV(k: String, v: String?) {
    if (v.isNullOrBlank()) return
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        Text(k, color = TextMuted, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(64.dp))
        // 值占满剩余宽度并自然换行，长备注/地址不再横向溢出
        Text(
            v, color = TextPrimary, style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
    }
}

/** 跟进结果的中文标签（替代英文枚举名） */
private fun followResultLabel(r: com.realtor.geeksales.data.db.FollowResult): String = when (r) {
    com.realtor.geeksales.data.db.FollowResult.CONNECTED -> "已接通"
    com.realtor.geeksales.data.db.FollowResult.APPOINTMENT -> "已约看"
    com.realtor.geeksales.data.db.FollowResult.PENDING -> "待跟进"
    com.realtor.geeksales.data.db.FollowResult.NOT_REACHED -> "未接通"
    com.realtor.geeksales.data.db.FollowResult.NOT_INTERESTED -> "已拒绝"
    com.realtor.geeksales.data.db.FollowResult.WRONG_NUMBER -> "空错号"
    com.realtor.geeksales.data.db.FollowResult.SHUTDOWN -> "关停机"
}
