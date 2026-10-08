package com.realtor.geeksales.ui.screen

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.realtor.geeksales.ui.components.GeekCard
import com.realtor.geeksales.ui.components.GeekPrimaryButton
import com.realtor.geeksales.ui.components.GeekTopBar
import com.realtor.geeksales.ui.theme.Accent
import com.realtor.geeksales.ui.theme.Bg
import com.realtor.geeksales.ui.theme.Danger
import com.realtor.geeksales.ui.theme.Success
import com.realtor.geeksales.ui.theme.TextMuted
import com.realtor.geeksales.ui.theme.TextPrimary

private const val PREFS = "tma_prefs"
private const val KEY_AGREE = "compliance_agreed_v1"

fun complianceAgreed(ctx: Context): Boolean =
    ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_AGREE, false)

fun setComplianceAgreed(ctx: Context, v: Boolean) =
    ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_AGREE, v).apply()

@Composable
fun ComplianceScreen(
    onBack: () -> Unit,
    onAgree: () -> Unit
) {
    val ctx = LocalContext.current
    val uriHandler = LocalUriHandler.current
    Column(Modifier.fillMaxSize().background(Bg)) {
        GeekTopBar(title = "合规声明", subtitle = "首次使用请确认", onBack = onBack)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .navigationBarsPadding()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            GeekCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("使用条款 v1", color = Accent, style = MaterialTheme.typography.labelMedium)
                    Text(
                        "本软件（知行同步助手）为人脉运营与客户管理个人自用工具，仅提供拨号、跟进登记与数据备份功能，不提供任何自动外呼、批量骚扰电话等违反工信部与运营商规定的能力。",
                        color = TextPrimary, style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        "使用人应当遵守《中华人民共和国个人信息保护法》《电信和互联网用户个人信息保护规定》等相关法律。对于从任何渠道导入的客户电话，使用人应保证已获得客户同意或具备合法的信息来源与使用依据。",
                        color = TextPrimary, style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        "禁止使用本软件进行骚扰、诈骗、信贷催收、营销轰炸等违反公序良俗或法律禁止的行为。如有违规使用，全部法律责任由使用者本人承担。",
                        color = Danger, style = MaterialTheme.typography.bodyLarge
                    )
                    Text("关于权限：", color = Accent, style = MaterialTheme.typography.labelMedium)
                    Text("CALL_PHONE / READ_PHONE_STATE / READ_CALL_LOG：用于一键拨号、监听通话结束并弹出跟进卡片；不进行通话录音，不上传号码。", color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
                    Text("POST_NOTIFICATIONS：用于下次跟进提醒通知。", color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
                    Text("READ_CONTACTS：支持从通讯录导入客户。", color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    Text("数据与隐私：", color = Accent, style = MaterialTheme.typography.labelMedium)
                    Text("全部客户数据仅保存在本机 SQLite。不上传服务器；如要转移数据请用导出 XLSX/CSV 功能自行备份。卸载 APP 会一并删除数据。", color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
                }
            }
            Spacer(Modifier.height(20.dp))
            GeekPrimaryButton("我已阅读并同意以上条款", {
                setComplianceAgreed(ctx, true)
                onAgree()
            }, Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            Text(
                if (complianceAgreed(ctx)) "已同意" else "未同意",
                color = if (complianceAgreed(ctx)) Success else TextMuted,
                fontFamily = FontFamily.Monospace, textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(20.dp))
            Text(
                "© 2026 知行工作室",
                color = Accent,
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.clickable { uriHandler.openUri("https://w3b.pub") }
            )
            Spacer(Modifier.height(40.dp))
        }
    }
}
