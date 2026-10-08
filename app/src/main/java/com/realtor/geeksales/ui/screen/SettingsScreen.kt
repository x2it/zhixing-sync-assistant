package com.realtor.geeksales.ui.screen

import android.content.Context
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.realtor.geeksales.ui.components.GeekCard
import com.realtor.geeksales.ui.components.GeekGhostButton
import com.realtor.geeksales.ui.components.GeekTopBar
import com.realtor.geeksales.ui.theme.Accent
import com.realtor.geeksales.ui.theme.Bg
import com.realtor.geeksales.ui.theme.Danger
import com.realtor.geeksales.ui.theme.Success
import com.realtor.geeksales.ui.theme.TextMuted
import com.realtor.geeksales.ui.theme.TextPrimary
import com.realtor.geeksales.ui.theme.Warning

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onNavCompliance: () -> Unit,
    onOpenPermSettings: () -> Unit
) {
    val ctx = LocalContext.current
    val uriHandler = LocalUriHandler.current
    var checks by remember { mutableStateOf(runPermChecks(ctx)) }
    // 每次回到前台（含从系统设置授权返回）都重新检查，避免"开了权限还显示未授权"
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                checks = runPermChecks(ctx)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    Column(Modifier.fillMaxSize().background(Bg)) {
        GeekTopBar(title = "设置", onBack = onBack)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(12.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            GeekCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("权限状态", color = Accent, style = MaterialTheme.typography.labelMedium)
                    Text("未授权的权限会直接影响对应功能；从系统设置返回本页会自动刷新状态。", color = TextMuted, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(4.dp))
                    PermRow("电话拨打权限", checks.callPhone, "用于详情页「直接拨打」；未开启时点「直接拨打」无反应")
                    PermRow("电话状态权限", checks.readPhone, "用于通话结束自动弹出跟进登记卡片")
                    PermRow("通话记录权限", checks.readCallLog, "用于读取本机通话记录做备份与互动档案")
                    PermRow("短信权限", checks.readSms, "用于短信本地备份与云端同步")
                    PermRow("通讯录读写权限", checks.readContacts, "用于导入/导出系统通讯录与标签分组")
                    PermRow("通知推送权限", checks.postNotify, "用于跟进日期到点提醒")
                    Spacer(Modifier.height(8.dp))
                    GeekGhostButton("跳转系统权限设置", color = Accent, onClick = onOpenPermSettings)
                }
            }
            GeekCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("功能说明", color = Accent, style = MaterialTheme.typography.labelMedium)
                    Text(
                        "默认使用 ACTION_DIAL 打开系统拨号盘。直接 ACTION_CALL 仅在已授权 CALL_PHONE 时可用，按钮在详情页「直接拨打」。",
                        color = TextPrimary, style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        "通话结束自动弹出跟进登记卡片，依赖 PhoneStateListener 监听。机型差异导致未弹出时，可从客户详情「登记跟进」手动补录。",
                        color = TextPrimary, style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        "数据存于本地 Room SQLite，可用「导出 XLSX」做备份。",
                        color = TextPrimary, style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            GeekCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("合规声明", color = Accent, style = MaterialTheme.typography.labelMedium)
                    GeekGhostButton("打开合规声明", color = Warning, onClick = onNavCompliance)
                }
            }
            GeekCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("关于 知行同步助手", color = Accent, style = MaterialTheme.typography.labelMedium)
                    Text("应用名称：知行同步助手", color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
                    Text("应用版本：${com.realtor.geeksales.BuildConfig.VERSION_NAME}", color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
                    Text("目标 SDK：${Build.VERSION.SDK_INT}", color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "© 2026 知行工作室",
                        color = Accent,
                        style = MaterialTheme.typography.labelMedium.copy(textDecoration = TextDecoration.Underline),
                        modifier = Modifier.clickable { uriHandler.openUri("https://w3b.pub") }
                    )
                }
            }
            Spacer(Modifier.height(80.dp))
        }
    }
}

private data class PChecks(val callPhone: Boolean, val readPhone: Boolean, val readCallLog: Boolean, val readContacts: Boolean, val readSms: Boolean, val postNotify: Boolean)

private fun runPermChecks(ctx: Context): PChecks = PChecks(
    callPhone = androidx.core.content.ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.CALL_PHONE) == android.content.pm.PackageManager.PERMISSION_GRANTED,
    readPhone = androidx.core.content.ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.READ_PHONE_STATE) == android.content.pm.PackageManager.PERMISSION_GRANTED,
    readCallLog = androidx.core.content.ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.READ_CALL_LOG) == android.content.pm.PackageManager.PERMISSION_GRANTED,
    readContacts = androidx.core.content.ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.READ_CONTACTS) == android.content.pm.PackageManager.PERMISSION_GRANTED,
    readSms = androidx.core.content.ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.READ_SMS) == android.content.pm.PackageManager.PERMISSION_GRANTED,
    postNotify = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) androidx.core.content.ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED else true
)

@Composable
private fun PermRow(name: String, ok: Boolean, hint: String) {
    val c = if (ok) Success else Danger
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(name, color = TextMuted, modifier = Modifier.weight(1f))
            Text(if (ok) "已授权" else "未授权", color = c, style = MaterialTheme.typography.labelMedium)
        }
        if (!ok) {
            Text("未授权：$hint", color = c, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun Mark(ok: Boolean) {
    val c = if (ok) Success else Danger
    Text(if (ok) "已授权" else "未授权", color = c, style = MaterialTheme.typography.labelMedium)
}
