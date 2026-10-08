package com.realtor.geeksales.ui.screen

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.realtor.geeksales.ui.components.GeekCard
import com.realtor.geeksales.ui.components.GeekGhostButton
import com.realtor.geeksales.ui.components.GeekTextField
import com.realtor.geeksales.ui.components.GeekPrimaryButton
import com.realtor.geeksales.ui.components.GeekTopBar
import com.realtor.geeksales.ui.components.LoadingState
import com.realtor.geeksales.ui.components.GlobalToast
import com.realtor.geeksales.ui.theme.Accent
import com.realtor.geeksales.ui.theme.Bg
import com.realtor.geeksales.ui.theme.BgElev
import com.realtor.geeksales.ui.theme.BgElev2
import com.realtor.geeksales.ui.theme.Danger
import com.realtor.geeksales.ui.theme.Divider
import com.realtor.geeksales.ui.theme.Success
import com.realtor.geeksales.ui.theme.TextMuted
import com.realtor.geeksales.ui.theme.TextPrimary
import com.realtor.geeksales.ui.theme.TextSecondary
import com.realtor.geeksales.ui.theme.Warning
import com.realtor.geeksales.viewmodel.ImportExportViewModel
import com.realtor.geeksales.viewmodel.SyncMode

@Composable
fun ImportExportScreen(
    onBack: () -> Unit,
    vm: ImportExportViewModel = hiltViewModel()
) {
    val status by vm.status.collectAsStateWithLifecycle()
    var lastNotified by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        // v2.7.3 设备握手：登记设备与环境（幂等轻量，失败静默）
        vm.syncHandshake()
        // 自动跟随线上模板：检测 isActive 模板是否变化，变化则自动应用并提示（静默无变化）
        vm.checkTemplateAuto { name ->
            if (name != null) GlobalToast.showSuccess("线上模板已切换，已自动应用「$name」，旧模板字段已清理")
        }
    }
    LaunchedEffect(status) {
        val m = status.message
        if (m.isEmpty() || m == "idle") return@LaunchedEffect
        if (m == lastNotified) return@LaunchedEffect
        lastNotified = m
        // 进行中：不再弹全局顶部 loading 横幅（下方状态卡已完整展示进度与取消，避免重复）；
        // 结束：轻量 toast 明确成功/失败反馈
        if (!status.running) {
            if (status.isError) GlobalToast.showError(m) else GlobalToast.showSuccess(m)
        }
    }
    var exportPending by remember { mutableStateOf(false) }
    // 危险操作二次确认
    var confirmClear by remember { mutableStateOf(false) }
    var confirmOverwrite by remember { mutableStateOf(false) }
    var confirmOverwriteSms by remember { mutableStateOf(false) }
    var confirmOverwriteCalls by remember { mutableStateOf(false) }
    var confirmClearCloudAll by remember { mutableStateOf(false) }
    var confirmResetSmsSync by remember { mutableStateOf(false) }
    var confirmResetCallSync by remember { mutableStateOf(false) }
    var confirmClearSysSms by remember { mutableStateOf(false) }
    var confirmClearSysCalls by remember { mutableStateOf(false) }
    var confirmClearSysContacts by remember { mutableStateOf(false) }
    var clearSysCode by remember { mutableStateOf("") }
    // API Key 输入状态
    var apiKeyInput by remember { mutableStateOf("") }
    var keyVisible by remember { mutableStateOf(false) }
    val hasKey by remember { mutableStateOf(vm.hasApiKey()) }
    var mode by remember { mutableStateOf(vm.syncMode()) }
    var baseUrlInput by remember { mutableStateOf(vm.baseUrl()) }
    // 自定义字段输入状态
    var newFieldKey by remember { mutableStateOf("") }
    var newFieldLabel by remember { mutableStateOf("") }
    var newFieldType by remember { mutableStateOf("text") }
    var newFieldOptions by remember { mutableStateOf("") }
    var schemaVersion by remember { mutableStateOf(0) }
    val schema = remember(schemaVersion) { vm.currentSchema() }

    val pickCsv = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u: Uri? ->
        if (u != null) vm.importCsv(u)
    }
    val pickXlsx = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u: Uri? ->
        if (u != null) vm.importXlsx(u)
    }
    val createCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { u: Uri? ->
        if (u != null) vm.exportCsv(u)
    }
    val createXlsx = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")) { u: Uri? ->
        if (u != null) vm.exportXlsx(u)
    }
    val createTemplate = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")) { u: Uri? ->
        if (u != null) vm.templateXlsx(u)
    }
    // 时光机恢复文件选择
    val pickRestore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u: Uri? ->
        if (u != null) vm.restoreFrom(u)
    }
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            when {
                exportPending -> vm.exportContacts()
                else -> vm.importContacts()
            }
        }
    }
    // 短信动作标记（备份=1 / 同步云端=2），权限授权后执行对应动作
    val ctx = LocalContext.current
    var smsAction by remember { mutableStateOf(0) }
    // 短信权限（备份 / 云端同步）
    val smsPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            when {
                smsAction == 1 -> vm.backupSms()
                smsAction == 2 -> vm.exportSmsToWorkbuddy()
            }
        } else {
            GlobalToast.showError("未授予「短信」权限：备份与云端同步无法执行。请在 系统设置 → 应用 → 知行同步助手 → 权限 → 短信 中开启后重试")
        }
    }
    // 通话动作标记（同步云端=1 / 仅拉取=2），权限授权后执行（通话同步需要读本机通话记录）
    var callAction by remember { mutableStateOf(0) }
    val callPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            when {
                callAction == 1 -> vm.syncCallsToWorkbuddy()
                callAction == 2 -> vm.pullCallsFromWorkbuddy()
            }
        } else {
            GlobalToast.showError("未授予「通话记录」权限：云端备份与拉取无法执行。请在 系统设置 → 应用 → 知行同步助手 → 权限 → 电话/通话记录 中开启后重试")
        }
    }

    Column(Modifier.fillMaxSize().background(Bg)) {
        GeekTopBar(title = "数据", subtitle = "导入 · 导出 · 知行同步 · 时光机", onBack = onBack)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(12.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 执行状态卡：进行中显示进度；结束后常驻显示结果（成功✓ / 失败✕），不再一闪而过
            if (status.message.isNotEmpty() && status.message != "idle") {
                val err = status.isError
                GeekCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (status.running) {
                            LoadingState(message = status.message)
                            // 确定性进度（同步/导入导出/快照逐步上报 0..1）
                            val p = status.progress
                            if (p != null) {
                                androidx.compose.material3.LinearProgressIndicator(
                                    progress = { p },
                                    modifier = Modifier.fillMaxWidth(),
                                    color = Accent,
                                    trackColor = Divider
                                )
                                Text(
                                    "进度 ${(p * 100).toInt()}%",
                                    color = TextSecondary,
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.align(Alignment.End)
                                )
                            }
                            // 可取消：上传/同步进行中随时可中断，已上传部分保留、未上传部分下次自动补推
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End) {
                                GeekGhostButton("取消", color = Danger, onClick = { vm.cancelRunning() })
                            }
                        } else {
                            // 结束：成功/失败结果常驻（直到下一次操作）
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = if (err) "✕" else "✓",
                                    color = if (err) Danger else Success,
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = status.message,
                                    color = if (err) Danger else TextSecondary,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (err) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Normal
                                )
                            }
                            status.syncSummary?.let {
                                Text(it, color = TextMuted, style = MaterialTheme.typography.labelMedium)
                            }
                            status.report?.let { r ->
                                if (r.error == null) {
                                    Text("总计：${r.total}", color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
                                    Text("成功：${r.success}", color = Success, style = MaterialTheme.typography.bodyMedium)
                                    Text("重复跳过：${r.duplicated}", color = Warning, style = MaterialTheme.typography.bodyMedium)
                                    Text("无效号码：${r.invalid}", color = Danger, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                            status.exportCount?.let { n ->
                                Text("已导出：$n 条", color = Success, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
            val busy = status.running

            // ================= 知行同步助手 · 云端同步 =================
            SectionCard("客户通讯录 · 双向同步", "客户 / 跟进 · 云端双向同步（同步前自动全量备份）") {
                // 服务器地址（可切换，平台迁移/关停时更换）
                Text("服务器地址（平台迁移时可更换）", color = TextMuted, style = MaterialTheme.typography.labelMedium)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    androidx.compose.material3.OutlinedTextField(
                        value = baseUrlInput,
                        onValueChange = { baseUrlInput = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text(com.realtor.geeksales.data.remote.WorkbuddyApi.DEFAULT_BASE_URL, color = TextMuted, style = MaterialTheme.typography.bodyMedium) },
                        singleLine = true,
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(0.dp),
                        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Accent, unfocusedBorderColor = Divider,
                            focusedContainerColor = BgElev2, unfocusedContainerColor = BgElev,
                            cursorColor = Accent,
                            unfocusedTextColor = TextPrimary, focusedTextColor = TextPrimary,
                            unfocusedLabelColor = TextSecondary, focusedLabelColor = Accent
                        ),
                        textStyle = MaterialTheme.typography.bodyMedium
                    )
                    GeekGhostButton("保存", color = if (busy) TextMuted else Accent, onClick = {
                        if (busy || baseUrlInput.isBlank()) return@GeekGhostButton
                        vm.setBaseUrl(baseUrlInput)
                        GlobalToast.showSuccess("服务器地址已更新")
                    })
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("地址应为 域名 + /api（如 https://syn.app.workbuddy.host/api）；保存时自动补 /api 并清理多余字符", color = TextMuted, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                    GeekGhostButton("恢复默认", color = if (busy) TextMuted else Warning, onClick = {
                        if (busy) return@GeekGhostButton
                        vm.resetBaseUrl()
                        GlobalToast.showSuccess("已恢复默认地址 ${com.realtor.geeksales.data.remote.WorkbuddyApi.DEFAULT_BASE_URL}")
                    })
                }
                Spacer(Modifier.height(4.dp))
                // API Key 配置
                Text("API Key（在知行同步助手「API 接入」页生成）", color = TextMuted, style = MaterialTheme.typography.labelMedium)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    androidx.compose.material3.OutlinedTextField(
                        value = apiKeyInput,
                        onValueChange = { apiKeyInput = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text(if (hasKey) "已配置（重新输入可覆盖）" else "zx_ 开头的 35 位密钥", color = TextMuted, style = MaterialTheme.typography.bodyMedium) },
                        singleLine = true,
                        visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(0.dp),
                        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Accent, unfocusedBorderColor = Divider,
                            focusedContainerColor = BgElev2, unfocusedContainerColor = BgElev,
                            cursorColor = Accent,
                            unfocusedTextColor = TextPrimary, focusedTextColor = TextPrimary,
                            unfocusedLabelColor = TextSecondary, focusedLabelColor = Accent
                        ),
                        textStyle = MaterialTheme.typography.bodyMedium
                    )
                    GeekGhostButton(if (keyVisible) "隐藏" else "显示", onClick = { keyVisible = !keyVisible }, color = TextMuted)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GeekGhostButton("验证并保存", color = if (busy) TextMuted else Accent, onClick = {
                        if (busy || apiKeyInput.isBlank()) return@GeekGhostButton
                        vm.saveAndVerifyApiKey(apiKeyInput)
                    })
                    if (hasKey) {
                        GeekGhostButton("清除密钥", color = if (busy) TextMuted else Danger, onClick = { if (!busy) { vm.clearApiKey(); GlobalToast.showSuccess("密钥已清除") } })
                    }
                    GeekGhostButton("检测密钥", color = if (busy || !hasKey) TextMuted else Success, onClick = { if (!busy && hasKey) vm.verifyApiKey() })
                }
                // 同步模式
                Spacer(Modifier.height(4.dp))
                Text("同步冲突策略", color = TextMuted, style = MaterialTheme.typography.labelMedium)
                SyncModeSelector(mode = mode, enabled = !busy, onSelect = { mode = it; vm.setSyncMode(it) })
                Text(
                    text = when (mode) {
                        SyncMode.SMART -> "推荐：两端改动都保留，同一字段冲突时以本地最新为准；绝不删除任何一端数据。"
                        SyncMode.CLOUD_FIRST -> "以知行同步助手为准覆盖本地：适合把网站当主工作台、手机当客户端。"
                        SyncMode.LOCAL_FIRST -> "以本地为准覆盖线上：适合本地深度操作、线上纯备份。"
                    },
                    color = TextSecondary, style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GeekPrimaryButton(if (busy) "处理中…" else "同步到云端", { if (!busy) vm.exportToWorkbuddy() }, Modifier.weight(1f), enabled = !busy)
                    GeekGhostButton(if (busy) "处理中…" else "从云端拉取", color = if (busy) TextMuted else Success, onClick = { if (!busy) vm.importFromWorkbuddy() })
                }
                Text("执行前自动全量备份到「下载/知行同步助手备份」；拉取不删除本地数据。", color = TextMuted, style = MaterialTheme.typography.bodyMedium)
                Text("⚠ API Key 关联知行同步助手账号：一人一账号一密钥，请勿共用。", color = Warning, style = MaterialTheme.typography.bodyMedium)
            }

            // ================= 线上模板 · 万物可插 =================
            SectionCard("线上模板 · 字段可插", "字段 / 分层 / 标签跟随线上模板，换行业即换模板，不写死") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GeekPrimaryButton(if (busy) "处理中…" else "拉取线上模板", { if (!busy) { vm.pullSchema(); schemaVersion++ } }, Modifier.weight(1f), enabled = !busy)
                    Text("当前 ${schema.size} 个字段（内置 ${schema.count { it.builtin }} · 扩展 ${schema.count { !it.builtin }}）", color = TextSecondary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1.2f))
                }
                if (schema.any { !it.builtin }) {
                    Text("扩展字段：", color = TextMuted, style = MaterialTheme.typography.labelMedium)
                    schema.filter { !it.builtin }.forEach { fd ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${fd.label}（${fd.key}·${fd.type}）", color = TextPrimary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            GeekGhostButton("移除", color = Danger, onClick = { if (!busy) { vm.removeCustomField(fd.key); schemaVersion++ } })
                        }
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text("添加自定义字段", color = TextMuted, style = MaterialTheme.typography.labelMedium)
                androidx.compose.material3.OutlinedTextField(
                    value = newFieldKey,
                    onValueChange = { newFieldKey = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("字段 Key（英文小写，如 scriptVersion）", color = TextMuted, style = MaterialTheme.typography.bodyMedium) },
                    singleLine = true,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(0.dp),
                    colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Accent, unfocusedBorderColor = Divider,
                        focusedContainerColor = BgElev2, unfocusedContainerColor = BgElev,
                        cursorColor = Accent,
                        unfocusedTextColor = TextPrimary, focusedTextColor = TextPrimary,
                        unfocusedLabelColor = TextSecondary, focusedLabelColor = Accent
                    ),
                    textStyle = MaterialTheme.typography.bodyMedium
                )
                androidx.compose.material3.OutlinedTextField(
                    value = newFieldLabel,
                    onValueChange = { newFieldLabel = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("显示名（如 话术版本）", color = TextMuted, style = MaterialTheme.typography.bodyMedium) },
                    singleLine = true,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(0.dp),
                    colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Accent, unfocusedBorderColor = Divider,
                        focusedContainerColor = BgElev2, unfocusedContainerColor = BgElev,
                        cursorColor = Accent,
                        unfocusedTextColor = TextPrimary, focusedTextColor = TextPrimary,
                        unfocusedLabelColor = TextSecondary, focusedLabelColor = Accent
                    ),
                    textStyle = MaterialTheme.typography.bodyMedium
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("text" to "文本", "number" to "数字", "date" to "日期", "select" to "单选", "multiselect" to "多选", "textarea" to "长文本").forEach { (t, n) ->
                        val on = newFieldType == t
                        Text(
                            text = n,
                            color = if (on) Accent else TextSecondary,
                            modifier = Modifier
                                .background(if (on) Accent.copy(alpha = 0.16f) else BgElev2)
                                .border(1.dp, if (on) Accent else Divider)
                                .clickable { newFieldType = t }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1
                        )
                    }
                }
                if (newFieldType == "select" || newFieldType == "multiselect") {
                    androidx.compose.material3.OutlinedTextField(
                        value = newFieldOptions,
                        onValueChange = { newFieldOptions = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("选项（逗号分隔，如 新客,老客,转介绍）", color = TextMuted, style = MaterialTheme.typography.bodyMedium) },
                        singleLine = true,
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(0.dp),
                        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Accent, unfocusedBorderColor = Divider,
                            focusedContainerColor = BgElev2, unfocusedContainerColor = BgElev,
                            cursorColor = Accent,
                            unfocusedTextColor = TextPrimary, focusedTextColor = TextPrimary,
                            unfocusedLabelColor = TextSecondary, focusedLabelColor = Accent
                        ),
                        textStyle = MaterialTheme.typography.bodyMedium
                    )
                }
                GeekGhostButton("添加字段", color = if (busy) TextMuted else Accent, onClick = {
                    if (busy) return@GeekGhostButton
                    vm.addCustomField(
                        newFieldKey,
                        newFieldLabel,
                        newFieldType,
                        newFieldOptions.split(',', '，').map { it.trim() }.filter { it.isNotBlank() }
                    )
                    schemaVersion++
                    newFieldKey = ""; newFieldLabel = ""; newFieldOptions = ""
                })

            }

            // ================= 时光机 =================
            SectionCard("时光机 · 数据回溯", "客户 / 跟进 / 标签 / 短信 全量快照与恢复") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GeekPrimaryButton(if (busy) "处理中…" else "立即快照", { if (!busy) vm.snapshotNow() }, Modifier.weight(1f), enabled = !busy)
                    GeekGhostButton(if (busy) "处理中…" else "从备份恢复", color = if (busy) TextMuted else Warning, onClick = { if (!busy) pickRestore.launch(arrayOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "application/vnd.ms-excel", "text/*")) })
                }
                Text("快照为完整 XLSX（客户/跟进/标签/短信）；恢复为覆盖模式，恢复前自动备份当前状态（双保险）。", color = TextMuted, style = MaterialTheme.typography.bodyMedium)
            }

            // ================= 短信备份与同步 =================
            SectionCard("短信 · 双向同步", "本地备份 · 云端双向同步（需短信权限）") {
                val smsGranted = androidx.core.content.ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_SMS) == android.content.pm.PackageManager.PERMISSION_GRANTED
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    GeekPrimaryButton(if (busy) "处理中…" else "备份到本地", { if (!busy) { smsAction = 1; smsPermLauncher.launch(Manifest.permission.READ_SMS) } }, Modifier.weight(1f), enabled = !busy)
                    GeekGhostButton(if (busy) "处理中…" else "同步到云端", color = if (busy) TextMuted else TextSecondary, onClick = { if (!busy) { smsAction = 2; smsPermLauncher.launch(Manifest.permission.READ_SMS) } })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (smsGranted) "短信权限：已授权" else "短信权限：未授权（拒绝后需到系统设置开启）",
                        color = if (smsGranted) Success else Danger, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f)
                    )
                    if (!smsGranted) {
                        GeekGhostButton("去系统设置开启", color = Warning, onClick = {
                            runCatching { ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))) }
                        })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GeekGhostButton(if (busy) "处理中…" else "从云端拉取", color = if (busy) TextMuted else TextSecondary, onClick = { if (!busy) vm.importSmsFromWorkbuddy() })
                    Spacer(Modifier.weight(1f))
                }
                Text("短信属敏感数据：默认仅本地备份（下载/知行同步助手备份）；「同步到云端」仅主动点击时执行。", color = TextMuted, style = MaterialTheme.typography.bodyMedium)
            }

            // ================= 通话记录备份与同步 =================
            SectionCard("通话记录 · 双向同步", "通话记录 · 云端双向同步（需通话记录权限）") {
                val callGranted = androidx.core.content.ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CALL_LOG) == android.content.pm.PackageManager.PERMISSION_GRANTED
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GeekPrimaryButton(if (busy) "处理中…" else "同步到云端", {
                        if (!busy) { callAction = 1; callPermLauncher.launch(Manifest.permission.READ_CALL_LOG) }
                    }, Modifier.weight(1f), enabled = !busy)
                    GeekGhostButton(if (busy) "处理中…" else "从云端拉取", color = if (busy) TextMuted else TextSecondary, onClick = {
                        if (!busy) { callAction = 2; callPermLauncher.launch(Manifest.permission.READ_CALL_LOG) }
                    })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (callGranted) "通话记录权限：已授权" else "通话记录权限：未授权（拒绝后需到系统设置开启）",
                        color = if (callGranted) Success else Danger, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f)
                    )
                    if (!callGranted) {
                        GeekGhostButton("去系统设置开启", color = Warning, onClick = {
                            runCatching { ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))) }
                        })
                    }
                }
                Text("同步到云端 = 读取本机通话（呼入/呼出/未接/时长）入库并上传，客户详情「互动档案」可查看；拉取 = 线上通话回本地。重复同步自动补全历史，首次同步自动开启云端开关。", color = TextMuted, style = MaterialTheme.typography.bodyMedium)
            }

            // ================= 导入数据 =================
            SectionCard("导入数据", "CSV / XLSX / 系统通讯录") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GeekPrimaryButton("导入 CSV", { if (!busy) pickCsv.launch(arrayOf("text/*", "text/csv", "application/csv")) }, Modifier.weight(1f), enabled = !busy)
                    GeekPrimaryButton("导入 XLSX", { if (!busy) pickXlsx.launch(arrayOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "application/vnd.ms-excel")) }, Modifier.weight(1f), enabled = !busy)
                }
                GeekGhostButton(if (busy) "处理中…" else "从通讯录导入", color = if (busy) TextMuted else TextSecondary, onClick = {
                    if (busy) return@GeekGhostButton
                    exportPending = false
                    permLauncher.launch(Manifest.permission.READ_CONTACTS)
                })
            }

            // ================= 导出与模板 =================
            SectionCard("导出与模板", "CSV / XLSX / 系统通讯录 / 模板") {
                // 规范：区内主操作（导出 CSV/XLSX）用主色填充；辅助操作统一中性描边
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GeekPrimaryButton(if (busy) "处理中…" else "导出 CSV", { if (!busy) createCsv.launch("tma_customers.csv") }, Modifier.weight(1f), enabled = !busy)
                    GeekPrimaryButton(if (busy) "处理中…" else "导出 XLSX", { if (!busy) createXlsx.launch("tma_customers.xlsx") }, Modifier.weight(1f), enabled = !busy)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GeekGhostButton(if (busy) "处理中…" else "追加到通讯录", color = if (busy) TextMuted else TextSecondary, onClick = {
                        if (busy) return@GeekGhostButton
                        exportPending = true
                        permLauncher.launch(Manifest.permission.WRITE_CONTACTS)
                    })
                    GeekGhostButton("下载导入模板", color = if (busy) TextMuted else TextSecondary, onClick = { if (!busy) createTemplate.launch("tma_template.xlsx") })
                }
                Text("「追加到通讯录」= 增量：只新增 App 里有而手机通讯录缺失的联系人，绝不删除手机已有联系人；「覆盖通讯录」= 全量重建（见下方危险操作，覆盖时自动重建标签分组），两者用途不同。", color = TextMuted, style = MaterialTheme.typography.bodyMedium)
            }

            // ================= 危险操作（遥控面板式：同类聚合为 2 列网格，说明压缩） =================
            SectionCard("危险操作", "覆盖 = 以一方为准重建另一方，执行前自动备份到「下载/知行同步助手备份」，确认后不可撤销") {
                Text("清空全部客户数据，不可恢复，建议先导出备份。", color = TextMuted, style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    GeekGhostButton(if (busy) "处理中…" else "清空全部数据", color = if (busy) TextMuted else Danger, onClick = { if (!busy) confirmClear = true }, modifier = Modifier.weight(1f))
                    Spacer(Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
                Text("覆盖（以云端/ App 为准重建本地）", color = TextSecondary, style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(4.dp))
                val overwriteBtns = listOf<Pair<String, () -> Unit>>(
                    "覆盖通讯录" to { if (!busy) confirmOverwrite = true },
                    "覆盖短信记录" to { if (!busy) confirmOverwriteSms = true },
                    "覆盖通话记录" to { if (!busy) confirmOverwriteCalls = true }
                )
                overwriteBtns.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        row.forEach { (label, act) -> GeekGhostButton(label, color = Danger, onClick = act, modifier = Modifier.weight(1f)) }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text("云端数据（清空 / 重置，均不可撤销）", color = TextSecondary, style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(4.dp))
                val cloudBtns = listOf<Pair<String, () -> Unit>>(
                    "一键清空云端全部" to { if (!busy) confirmClearCloudAll = true },
                    "重置短信同步状态" to { if (!busy) confirmResetSmsSync = true },
                    "重置通话同步状态" to { if (!busy) confirmResetCallSync = true }
                )
                cloudBtns.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        row.forEach { (label, act) -> GeekGhostButton(label, color = if (busy) TextMuted else Danger, onClick = act, modifier = Modifier.weight(1f)) }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
                Text("一键清空云端 = 联系人+短信+通话+标签+批次全清（本机数据不受影响），清空后点各「同步到云端」即用本机数据重建；重置 = 清本地增量标记，下次全量对账（幂等不重复）。", color = TextMuted, style = MaterialTheme.typography.bodySmall)
            }

            // ================= 清空手机系统数据（删除系统库本身，不可恢复；强确认=输入「清空」） =================
            SectionCard("清空手机系统数据", "直接删除手机系统自带的短信 / 通话 / 通讯录（不是 App 数据、不是云端）。每个操作前自动备份，并需输入「清空」二字确认。") {
                val sysBtns = listOf<Pair<String, () -> Unit>>(
                    "清空系统短信" to { if (!busy) confirmClearSysSms = true },
                    "清空系统通话记录" to { if (!busy) confirmClearSysCalls = true },
                    "清空系统通讯录" to { if (!busy) confirmClearSysContacts = true }
                )
                sysBtns.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        row.forEach { (label, act) -> GeekGhostButton(label, color = if (busy) TextMuted else Danger, onClick = act, modifier = Modifier.weight(1f)) }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
                Text("分别删除手机系统短信库 / 通话记录 / 通讯录全部内容（需对应「修改」权限，已授权时可用）。", color = TextMuted, style = MaterialTheme.typography.bodySmall)
            }

            // ================= 字段说明（通用列 + 模板扩展列，换行业后跟随线上模板更新） =================
            SectionCard("字段说明", "通用列全行业适用；模板扩展列与分层跟随当前模板，换行业拉取新模板即可") {
                val fields = vm.currentSchema().sortedBy { it.order }
                val commonNames = fields.filter { it.builtin }.map { it.label }
                val customNames = fields.filter { !it.builtin }.map { it.label }
                val tierMeta = vm.meta()
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "内置通用列（全行业通用，无行业预设）：${commonNames.joinToString("、")}",
                        color = TextPrimary, style = MaterialTheme.typography.bodyMedium
                    )
                    if (customNames.isNotEmpty()) {
                        Text("扩展列（来自线上模板或本地添加）：${customNames.joinToString("、")}", color = TextMuted, style = MaterialTheme.typography.bodyMedium)
                    } else {
                        Text("扩展列：暂无（点「拉取线上模板」或「添加字段」即可新增，不限于行业）", color = TextMuted, style = MaterialTheme.typography.bodyMedium)
                    }
                    Text(
                        "分层选项：${tierMeta.tiers.joinToString(" ") { "${tierMeta.tierBadge(it)}:${tierMeta.tierLabel(it)}" }}（跟随模板）",
                        color = TextMuted, style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        "第一行为表头（中文）；手机号必填，重复自动去重；扩展列可选。",
                        color = TextMuted, style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            Spacer(Modifier.height(80.dp))
        }
    }

    // 危险操作二次确认（避免误触不可逆操作）
    if (confirmClear) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("清空全部客户数据？", color = TextPrimary, style = MaterialTheme.typography.titleMedium) },
            text = { Text("将删除 App 内全部客户/跟进/标签数据，不可恢复。建议先「导出与模板」备份一份。", color = TextSecondary, style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { confirmClear = false; vm.clearAll() }) {
                    Text("确认清空", color = Danger, style = MaterialTheme.typography.labelLarge)
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { confirmClear = false }) { Text("取消", color = TextSecondary) }
            }
        )
    }
    if (confirmOverwrite) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmOverwrite = false },
            title = { Text("覆盖手机通讯录？", color = TextPrimary, style = MaterialTheme.typography.titleMedium) },
            text = { Text("执行顺序：自动备份到「下载/知行同步助手备份」→ 清空手机通讯录全部联系人（含分组）→ 以 App 内客户全量重写（含备注与标签分组）。\\n仅建议在新手机或专用设备上使用，覆盖后原通讯录不可恢复！", color = TextSecondary, style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { confirmOverwrite = false; vm.overwriteContacts() }) {
                    Text("备份并覆盖", color = Danger, style = MaterialTheme.typography.labelLarge)
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { confirmOverwrite = false }) { Text("取消", color = TextSecondary) }
            }
        )
    }
    if (confirmOverwriteSms) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmOverwriteSms = false },
            title = { Text("覆盖本地短信记录？", color = TextPrimary, style = MaterialTheme.typography.titleMedium) },
            text = { Text("执行顺序：自动备份本地短信到「下载/知行同步助手备份」→ 清空本地短信记录 → 以云端短信全量重建（含客户关联）。\n可重复执行（无次数限制），每次都以当前云端数据重建；云端为空时会中止并保留本地，不会清空。", color = TextSecondary, style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { confirmOverwriteSms = false; vm.overwriteSms() }) {
                    Text("备份并覆盖", color = Danger, style = MaterialTheme.typography.labelLarge)
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { confirmOverwriteSms = false }) { Text("取消", color = TextSecondary) }
            }
        )
    }
    if (confirmOverwriteCalls) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmOverwriteCalls = false },
            title = { Text("覆盖本地通话记录？", color = TextPrimary, style = MaterialTheme.typography.titleMedium) },
            text = { Text("执行顺序：自动备份本地通话到「下载/知行同步助手备份」→ 清空本地通话记录 → 以云端通话全量重建（含客户关联）。\n可重复执行（无次数限制），每次都以当前云端数据重建；云端为空时会中止并保留本地，不会清空。", color = TextSecondary, style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { confirmOverwriteCalls = false; vm.overwriteCalls() }) {
                    Text("备份并覆盖", color = Danger, style = MaterialTheme.typography.labelLarge)
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { confirmOverwriteCalls = false }) { Text("取消", color = TextSecondary) }
            }
        )
    }
    if (confirmClearCloudAll) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmClearCloudAll = false },
            title = { Text("一键清空云端全部？", color = TextPrimary) },
            text = { Text("将删除云端全部数据：联系人、短信、通话、标签、导入批次（本机数据与手机通讯录均不受影响）。清空后点各「同步到云端」即可用本机数据全量重建。此操作不可恢复，请确认！", color = TextSecondary) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { confirmClearCloudAll = false; vm.clearCloudAll() }) {
                    Text("确认清空", color = Danger)
                }
                androidx.compose.material3.TextButton(onClick = { confirmClearCloudAll = false }) { Text("取消", color = TextSecondary) }
            }
        )
    }
    if (confirmResetSmsSync) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmResetSmsSync = false },
            title = { Text("重置短信同步状态？", color = TextPrimary) },
            text = { Text("将清空短信增量游标，下次「同步到云端」全量对账本机系统短信：云端已有的自动跳过（幂等），新短信全部补传。本地短信数据不受影响。", color = TextSecondary) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { confirmResetSmsSync = false; vm.resetSmsSyncState() }) {
                    Text("确认重置", color = Danger)
                }
                androidx.compose.material3.TextButton(onClick = { confirmResetSmsSync = false }) { Text("取消", color = TextSecondary) }
            }
        )
    }
    if (confirmResetCallSync) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmResetCallSync = false },
            title = { Text("重置通话同步状态？", color = TextPrimary) },
            text = { Text("将清空本地通话记录的上传标记（本地记录本身保留），之后「同步到云端」会全量重传。云端已删的会重新上传，云端仍存在的自动跳过（服务端幂等，不会产生重复）。", color = TextSecondary) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { confirmResetCallSync = false; vm.resetCallSyncState() }) {
                    Text("确认重置", color = Danger)
                }
                androidx.compose.material3.TextButton(onClick = { confirmResetCallSync = false }) { Text("取消", color = TextSecondary) }
            }
        )
    }
    // 清空手机系统数据：强确认（输入「清空」二字才可执行），防误操作
    if (confirmClearSysSms || confirmClearSysCalls || confirmClearSysContacts) {
        val (sysTitle, sysDesc) = when {
            confirmClearSysSms -> "清空手机系统短信？" to "将直接删除手机系统短信库中的全部短信（不是 App 数据、不是云端）。执行前自动备份到「下载/知行同步助手备份」。此操作不可恢复，请谨慎。"
            confirmClearSysCalls -> "清空手机系统通话记录？" to "将直接删除手机系统通话记录中的全部条目（不是 App 数据、不是云端）。执行前自动备份到「下载/知行同步助手备份」。此操作不可恢复，请谨慎。"
            else -> "清空手机系统通讯录？" to "将直接删除手机系统通讯录中的全部联系人（不是 App 数据、不是云端）。执行前自动备份客户 XLSX 到「下载/知行同步助手备份」。此操作不可恢复，请谨慎。"
        }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmClearSysSms = false; confirmClearSysCalls = false; confirmClearSysContacts = false; clearSysCode = "" },
            title = { Text(sysTitle, color = Danger) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(sysDesc, color = TextSecondary)
                    Text("输入「清空」以确认：", color = TextMuted, style = MaterialTheme.typography.bodySmall)
                    GeekTextField(clearSysCode, { clearSysCode = it }, placeholder = "清空", label = "确认码")
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    enabled = clearSysCode.trim() == "清空",
                    onClick = {
                        clearSysCode = ""
                        when {
                            confirmClearSysSms -> { confirmClearSysSms = false; vm.clearSystemSms() }
                            confirmClearSysCalls -> { confirmClearSysCalls = false; vm.clearSystemCalls() }
                            confirmClearSysContacts -> { confirmClearSysContacts = false; vm.clearSystemContacts() }
                        }
                    }
                ) {
                    Text(if (clearSysCode.trim() == "清空") "确认清空" else "请输入「清空」", color = if (clearSysCode.trim() == "清空") Danger else TextMuted)
                }
                androidx.compose.material3.TextButton(onClick = { confirmClearSysSms = false; confirmClearSysCalls = false; confirmClearSysContacts = false; clearSysCode = "" }) { Text("取消", color = TextSecondary) }
            }
        )
    }
}

/** 分区卡片：左侧强调条 + 标题 + 副标题，数据页统一视觉 */
@Composable
private fun SectionCard(title: String, subtitle: String?, content: @Composable () -> Unit) {
    GeekCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(width = 3.dp, height = 14.dp).background(Accent))
                Spacer(Modifier.padding(start = 6.dp))
                Text(title, color = TextPrimary, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 8.dp))
            }
            if (subtitle != null) {
                Text(subtitle, color = TextMuted, style = MaterialTheme.typography.labelMedium)
            }
            content()
        }
    }
}

/** 同步模式三选一 */
@Composable
private fun SyncModeSelector(mode: SyncMode, enabled: Boolean, onSelect: (SyncMode) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SyncMode.entries.forEach { m ->
            val selected = mode == m
            val color = when (m) {
                SyncMode.SMART -> Accent
                SyncMode.CLOUD_FIRST -> Warning
                SyncMode.LOCAL_FIRST -> Success
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .then(if (enabled) Modifier.clickable { onSelect(m) } else Modifier)
                    .background(if (selected) color.copy(alpha = 0.14f) else BgElev2)
                    .border(1.dp, if (selected) color else Divider)
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(m.label, color = if (selected) color else TextSecondary, style = MaterialTheme.typography.labelMedium, fontFamily = FontFamily.Monospace)
            }
        }
    }
}
