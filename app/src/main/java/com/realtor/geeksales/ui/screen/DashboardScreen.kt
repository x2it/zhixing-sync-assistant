package com.realtor.geeksales.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.realtor.geeksales.navigation.Routes
import com.realtor.geeksales.ui.components.GeekCard
import com.realtor.geeksales.ui.components.GeekGhostButton
import com.realtor.geeksales.ui.components.GeekTopBar
import com.realtor.geeksales.ui.components.StatTile
import com.realtor.geeksales.ui.theme.Accent
import com.realtor.geeksales.ui.theme.Bg
import com.realtor.geeksales.ui.theme.Danger
import com.realtor.geeksales.ui.theme.IntentA
import com.realtor.geeksales.ui.theme.IntentB
import com.realtor.geeksales.ui.theme.IntentC
import com.realtor.geeksales.ui.theme.IntentD
import com.realtor.geeksales.ui.theme.IntentS
import com.realtor.geeksales.ui.theme.IntentU
import com.realtor.geeksales.ui.theme.IntentV
import com.realtor.geeksales.ui.theme.Success
import com.realtor.geeksales.ui.theme.TextMuted
import com.realtor.geeksales.ui.theme.TextPrimary
import com.realtor.geeksales.ui.theme.TextSecondary
import com.realtor.geeksales.ui.theme.Warning
import com.realtor.geeksales.viewmodel.DashboardViewModel

@Composable
fun DashboardScreen(
    onNav: (String) -> Unit,
    vm: DashboardViewModel = hiltViewModel()
) {
    // 模板跟随：进入工作台即检测线上生效模板（isActive），变化则自动应用并提示。
    // 与数据页逻辑一致，保证「线上切模板 → 回工作台」分层语义立即更新，无需先进数据页
    val ieVm: com.realtor.geeksales.viewmodel.ImportExportViewModel = hiltViewModel()
    var tierFollowed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        ieVm.checkTemplateAuto { name ->
            if (name != null && !tierFollowed) {
                tierFollowed = true
                com.realtor.geeksales.ui.components.GlobalToast.showSuccess("线上模板已切换，已自动应用「$name」，分层已更新")
            }
        }
    }
    val totalCustomers by vm.totalCustomers.collectAsStateWithLifecycle()
    val calledToday by vm.calledToday.collectAsStateWithLifecycle()
    val notCalledToday by vm.notCalledToday.collectAsStateWithLifecycle()
    val overdue by vm.overdue.collectAsStateWithLifecycle()
    val followUpsToday by vm.followUpsToday.collectAsStateWithLifecycle()
    val intentCounts by vm.intentCounts.collectAsStateWithLifecycle()
    val overdueCustomers by vm.overdueCustomers.collectAsStateWithLifecycle()
    val todayCustomers by vm.todayCustomers.collectAsStateWithLifecycle()

    val intentMap = intentCounts.associate { it.level to it.cnt }
    val maxCount = (intentMap.values.maxOrNull() ?: 0).coerceAtLeast(1)

    Column(Modifier.fillMaxSize().background(Bg)) {
        GeekTopBar(title = "知行同步助手 // 工作台", subtitle = "连接 · 记录 · 同步")
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 统计卡 2×2 对称网格（过期跟进并入下方「今日跟进」卡内显示）
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) {
                    StatTile("总客户", totalCustomers.toString(), accent = Accent)
                }
                Box(Modifier.weight(1f)) {
                    StatTile("今日未拨打", notCalledToday.toString(), accent = Warning)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) {
                    StatTile("今日已拨打", calledToday.toString(), accent = Success)
                }
                Box(Modifier.weight(1f)) {
                    StatTile("今日跟进", followUpsToday.toString(), accent = Accent)
                }
            }

            // 今日跟进（界面①：逾期 + 今日，点击直达客户详情）
            GeekCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("今日跟进 · 逾期${overdueCustomers.size} · 今日${todayCustomers.size}", color = Accent, style = MaterialTheme.typography.labelMedium)
                    if (overdueCustomers.isEmpty() && todayCustomers.isEmpty()) {
                        Text("暂无待跟进客户", color = TextMuted, style = MaterialTheme.typography.bodyMedium)
                    } else {
                        overdueCustomers.take(5).forEach { c ->
                            FollowupRow(c, "逾期", Danger) { onNav("${com.realtor.geeksales.navigation.Routes.CUSTOMER_DETAIL.replace("{id}", c.id.toString())}") }
                        }
                        todayCustomers.take(5).forEach { c ->
                            FollowupRow(c, "今日", Warning) { onNav("${com.realtor.geeksales.navigation.Routes.CUSTOMER_DETAIL.replace("{id}", c.id.toString())}") }
                        }
                    }
                }
            }

            GeekCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val tierMeta = vm.templateMeta.collectAsStateWithLifecycle().value
                    Text("分层分布", color = Accent, style = MaterialTheme.typography.labelMedium)
                    val tierColor = mapOf(
                        "S" to IntentS, "A" to IntentA, "B" to IntentB, "C" to IntentC,
                        "D" to IntentD, "V" to IntentV, "U" to IntentU
                    )
                    tierMeta.tiers.forEach { t ->
                        val count = intentMap[t] ?: 0
                        if (count > 0 || t != "U") {
                            IntentRow(
                                level = tierMeta.tierBadge(t),
                                semantic = tierMeta.tierLabel(t),
                                cadence = tierMeta.tierCadence(t),
                                count = count,
                                color = tierColor[t] ?: Accent,
                                maxCount = maxCount
                            )
                        }
                    }
                    // 未分类（本地状态 U，显示 /）：有未分类客户时单独成行，不再错位挂靠
                    val unclassified = intentMap["U"] ?: 0
                    if (unclassified > 0 && tierMeta.tiers.none { it == "U" }) {
                        IntentRow(
                            level = "/",
                            semantic = "未分类",
                            cadence = "待分层后按需跟进",
                            count = unclassified,
                            color = IntentU,
                            maxCount = maxCount
                        )
                    }
                }
            }
            Spacer(Modifier.height(80.dp))
        }
    }
}

@Composable
private fun FollowupRow(c: com.realtor.geeksales.data.db.Customer, tag: String, color: Color, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(6.dp))
            .clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {        Text(tag, color = color, style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(40.dp))
        Column(Modifier.weight(1f)) {
            Text(c.name, color = TextPrimary, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            if (c.nextFollowAt != null) {
                Text("下次跟进 ${com.realtor.geeksales.util.Formatter.full(c.nextFollowAt)}", color = TextMuted, style = MaterialTheme.typography.labelMedium)
            }
        }
        if (!c.phone.isNullOrBlank()) {
            Text(c.phone, color = TextSecondary, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun IntentRow(level: String, semantic: String, cadence: String, count: Int, color: Color, maxCount: Int) {
    // 行头：徽标 + 语义 + 数量（两端对齐，语义弹性截断自适应）
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            level, color = color, style = MaterialTheme.typography.labelLarge,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            modifier = Modifier.width(24.dp)
        )
        Column(Modifier.weight(1f)) {
            Text(
                semantic, color = TextMuted, style = MaterialTheme.typography.labelMedium,
                maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
            Text(
                "建议：$cadence", color = com.realtor.geeksales.ui.theme.TextMuted,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }
        Text("$count", color = TextPrimary, style = MaterialTheme.typography.titleSmall,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
    }
    // 进度条：占满整行宽度（count/max 比例自适应，不写死像素宽）
    Box(
        Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(3.dp))
            .background(com.realtor.geeksales.ui.theme.Divider)
    ) {
        Box(
            Modifier
                .fillMaxWidth((count.toFloat() / maxCount).coerceIn(0f, 1f))
                .height(6.dp)
                .background(color)
        )
    }
}
