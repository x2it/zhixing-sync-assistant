package com.realtor.geeksales.data.schema

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 模板字段定义（schema）。
 *
 * 这是 App 与线上「知行同步助手」模板对齐的契约：
 * - 内置字段（builtin=true）：key 对应本地 Customer 列，值存主表
 * - 扩展字段（builtin=false）：线上模板新增的自定义字段，值存 customer_fields（EAV）
 *
 * 线上模板加什么字段，App 表单/详情/导入导出就跟随显示什么——"万物可插"。
 */
data class FieldDef(
    /** 稳定标识：内置字段用 Customer 列名；扩展字段用线上 schema key */
    val key: String,
    /** 显示名（跟随线上模板 label，可被覆盖） */
    val label: String,
    /** 类型：text / number / tel / date / select / multiselect / textarea */
    val type: String = "text",
    /** 分组名：基础信息 / 联系信息 / 购房需求 / 意向与跟进 / 其他 */
    val group: String = "其他",
    val required: Boolean = false,
    /** select / multiselect 的可选项 */
    val options: List<String> = emptyList(),
    /** 显示顺序 */
    val order: Int = 0,
    /** true=存 Customer 列；false=存 customer_fields */
    val builtin: Boolean = true,
    /** true=用户手动添加（跨模板保留）；false=线上模板导入（随模板切换替换） */
    val local: Boolean = false
)

/** 模板元数据：分层（tiers）+ 身份标签 + 属性标签（线上模板下发，驱动筛选 chips 与标签选择器） */
data class TemplateMeta(
    /** 分层选项：S/A/B/C/D/V/U（顺序即展示顺序；模板更换时跟随线上 tiers） */
    val tiers: List<String> = listOf("S", "A", "B", "C", "D", "V", "U"),
    /** 身份标签：买房客户 / 卖房业主 / 租客 / 业主 / 中介同行 */
    val identityTags: List<String> = emptyList(),
    /** 属性标签：学区房 / 地铁房 / 改善型 … */
    val attributeTags: List<String> = emptyList(),
    /** 分层中文语义（模板可覆盖）：S→高价值成交…U→未分类；缺省用通用销售分层语义（行业无关） */
    val tierLabels: Map<String, String> = DEFAULT_TIER_LABELS,
    /** 当前生效模板 id（线上 isActive 模板；用于自动跟随切换） */
    val templateId: String? = null,
    /** 当前生效模板名（展示用） */
    val templateName: String? = null
) {
    /** 全部模板标签（身份 + 属性，去重保序），供编辑页标签选择器 */
    val allTags: List<String> get() = (identityTags + attributeTags).distinct()

    /** 分层的展示字母（U 显示为 /，用户明确要求） */
    fun tierBadge(t: String): String = if (t == "U") "/" else t

    /** 分层的完整语义文案：字母 · 语义（U 显示为 / · 未分类） */
    fun tierLabel(t: String): String {
        // 语义归一：去掉“客户”等冗余后缀，避免长文案误导（线上模板 description 权威，仅做展示清洗）。
        // 只返回语义文字，不带字母前缀——字母由 tierBadge() 单独展示，避免“徽标 S + 行内 S·xxx”重复
        return (tierLabels[t] ?: t).removeSuffix("客户")
    }

    /** 分层建议节奏（通用销售节奏，行业无关；驱动 Dashboard/编辑页的层次提示） */
    fun tierCadence(t: String): String = when (t) {
        "S" -> "每季度维护 1 次"
        "A" -> "每周跟进 1 次"
        "B" -> "每两周跟进 1 次"
        "C" -> "每月联系 1 次"
        "D" -> "每两周摸底 1 次"
        "V" -> "成交后定期售后维护"
        else -> "待分层后按需跟进"
    }

    companion object {
        val DEFAULT_TIER_LABELS: Map<String, String> = mapOf(
            "S" to "高价值成交", "A" to "高意向", "B" to "已接触", "C" to "信息完整",
            "D" to "线索", "V" to "已成交", "U" to "未分类"
        )
    }
}

/** 内置字段 key 集合（值存 Customer 主表列） */
object BuiltinKeys {
    const val NAME = "name"
    const val PHONE = "phone"
    const val PHONE2 = "phone2"
    const val GENDER = "gender"
    const val AGE = "age"
    const val WECHAT = "wechat"
    const val SOURCE = "source"
    const val TAGS = "tags"
    const val EMAIL = "email"
    const val IM = "im"
    const val COMPANY = "company"
    const val JOB_TITLE = "jobTitle"
    const val BIRTHDAY = "birthday"
    const val NICKNAME = "nickname"
    const val ADDRESS = "address"
    const val WEBSITE = "website"
    const val AREA_PREF = "areaPref"
    const val BUDGET_MIN = "budgetMin"
    const val BUDGET_MAX = "budgetMax"
    const val HOUSE_TYPE = "houseType"
    const val TARGET_PROJECT = "targetProject"
    const val INTENT_LEVEL = "intentLevel"
    const val NEXT_FOLLOW_AT = "nextFollowAt"
    const val NOTE = "note"

    /** 所有内置 key */
    val ALL: Set<String> = setOf(
        NAME, PHONE, PHONE2, GENDER, AGE, WECHAT, SOURCE, TAGS,
        EMAIL, IM, COMPANY, JOB_TITLE, BIRTHDAY, NICKNAME, ADDRESS, WEBSITE,
        AREA_PREF, BUDGET_MIN, BUDGET_MAX, HOUSE_TYPE, TARGET_PROJECT,
        INTENT_LEVEL, NEXT_FOLLOW_AT, NOTE
    )
}

/**
 * Schema 存储：内置默认模板（离线可用）+ 线上模板拉取合并 + 本地自定义字段。
 * 合并规则：内置字段的 label/order 可被线上覆盖；线上新增的 key 追加为扩展字段；
 * 本地自定义字段（数据页添加）始终保留。
 * 另存模板元数据（分层 tiers / 身份标签 / 属性标签），驱动列表筛选与编辑页标签选择器。
 */
@Singleton
class SchemaStore @Inject constructor(
    @ApplicationContext ctx: Context
) {
    private val prefs = ctx.getSharedPreferences("tma_prefs", Context.MODE_PRIVATE)

    private companion object {
        const val KEY_SCHEMA = "schema_json_v1"
        const val KEY_LOCAL_FIELDS = "schema_local_fields_v1"
        const val KEY_META = "schema_meta_v1"
    }

    /** 默认模板元数据（行业无关：不预设任何行业标签，拉取线上模板后由线上填充） */
    fun defaultMeta(): TemplateMeta = TemplateMeta(
        tiers = listOf("S", "A", "B", "C", "D", "V", "U"),
        identityTags = emptyList(),
        attributeTags = emptyList()
    )

    /** 当前模板元数据（本地缓存或默认） */
    fun meta(): TemplateMeta {
        val raw = prefs.getString(KEY_META, null)
        if (raw.isNullOrBlank()) return defaultMeta()
        val parts = raw.split("~")
        if (parts.size < 3) return defaultMeta()
        fun list(s: String) = s.split(",").map { it.trim() }.filter { it.isNotBlank() }
        // 第 4 段为分层语义（S:成交高价值,A:高意向,…）；旧缓存无此段 → 默认语义
        val labels = if (parts.size >= 4) {
            parts[3].split(",").mapNotNull { seg ->
                val i = seg.indexOf(':')
                if (i <= 0) null else seg.substring(0, i).trim() to seg.substring(i + 1).trim()
            }.filter { it.second.isNotBlank() }.toMap()
        } else emptyMap()
        return TemplateMeta(
            tiers = list(parts[0]).ifEmpty { defaultMeta().tiers },
            identityTags = list(parts[1]),
            attributeTags = list(parts[2]),
            tierLabels = labels.ifEmpty { TemplateMeta.DEFAULT_TIER_LABELS },
            templateId = if (parts.size >= 5) parts[4].takeIf { it.isNotBlank() } else null,
            templateName = if (parts.size >= 6) parts[5].takeIf { it.isNotBlank() } else null
        )
    }

    fun saveMeta(m: TemplateMeta) {
        fun join(l: List<String>) = l.joinToString(",")
        val labels = m.tierLabels.entries.joinToString(",") { "${it.key}:${it.value}" }
        prefs.edit().putString(KEY_META, "${join(m.tiers)}~${join(m.identityTags)}~${join(m.attributeTags)}~$labels~${m.templateId.orEmpty()}~${m.templateName.orEmpty()}").apply()
    }

    /** 内置默认模板：通用列（全行业可用）+ 房产模板字段（默认房产模板，购房需求组） */
    fun defaultSchema(): List<FieldDef> = listOf(
        FieldDef(BuiltinKeys.NAME, "姓名", "text", "基础信息", required = true, order = 1),
        FieldDef(BuiltinKeys.PHONE, "手机号", "tel", "基础信息", required = true, order = 2),
        FieldDef(BuiltinKeys.PHONE2, "备用电话", "tel", "基础信息", order = 3),
        FieldDef(BuiltinKeys.GENDER, "性别", "text", "基础信息", order = 4),
        FieldDef(BuiltinKeys.AGE, "年龄", "number", "基础信息", order = 5),
        FieldDef(BuiltinKeys.WECHAT, "微信", "text", "基础信息", order = 6),
        FieldDef(BuiltinKeys.SOURCE, "来源", "text", "基础信息", order = 7),
        FieldDef(BuiltinKeys.TAGS, "标签", "text", "基础信息", order = 8),
        FieldDef(BuiltinKeys.EMAIL, "邮箱", "text", "联系信息", order = 10),
        FieldDef(BuiltinKeys.IM, "即时消息", "text", "联系信息", order = 11),
        FieldDef(BuiltinKeys.COMPANY, "公司", "text", "联系信息", order = 12),
        FieldDef(BuiltinKeys.JOB_TITLE, "职位", "text", "联系信息", order = 13),
        FieldDef(BuiltinKeys.BIRTHDAY, "生日", "date", "联系信息", order = 14),
        FieldDef(BuiltinKeys.NICKNAME, "昵称", "text", "联系信息", order = 15),
        FieldDef(BuiltinKeys.ADDRESS, "地址", "text", "联系信息", order = 16),
        FieldDef(BuiltinKeys.WEBSITE, "网站", "text", "联系信息", order = 17),
        // 行业写死字段一律不预设：意向区域/预算上下限/房型偏好/意向楼盘等由线上模板提供（拉取后作为扩展字段）
        FieldDef(BuiltinKeys.INTENT_LEVEL, "意向等级", "select", "意向与跟进", options = listOf("S", "A", "B", "C", "D", "V", "U"), order = 20),
        FieldDef(BuiltinKeys.NEXT_FOLLOW_AT, "下次跟进", "date", "意向与跟进", order = 21),
        FieldDef(BuiltinKeys.NOTE, "备注", "textarea", "意向与跟进", order = 22)
    )

    /** 旧版本写死的内置房产字段（v2.6.3 起不再预设；缓存中残留则自动移除，避免跨行业误导） */
    private val LEGACY_INTERNAL_KEYS = setOf("areaPref", "budgetMin", "budgetMax", "houseType", "targetProject")

    /** 当前生效 schema：本地缓存（含线上合并与自定义）或默认模板；自动迁移清除旧版写死的行业内置字段 */
    fun current(): List<FieldDef> {
        val cached = prefs.getString(KEY_SCHEMA, null)
        if (cached.isNullOrBlank()) return defaultSchema()
        val list = runCatching {
            parseArray(cached).mapNotNull { el -> runCatching { FieldDefSerializer.fromJson(el) }.getOrNull() }
        }.getOrElse { emptyList() }
        if (list.isEmpty()) return defaultSchema()
        val cleaned = list.filter { !(it.builtin && it.key in LEGACY_INTERNAL_KEYS) }
        if (cleaned.size != list.size) save(cleaned)  // 迁移：旧房产内置字段移除并落盘
        return cleaned
    }

    /**
     * 将线上模板合并进当前 schema 并缓存：
     * - 线上模板字段（扩展列）以线上为准，切换模板时旧模板扩展字段自动移除（不残留行业字段）；
     * - 仅保留用户手动添加的字段（local=true，跨模板保留）；
     * - 内置通用字段始终保留。
     */
    fun mergeRemote(remote: List<FieldDef>): List<FieldDef> {
        val local = current()
        val merged = mergeFields(local, remote)
        val remoteKeys = remote.map { it.key }.toSet()
        val result = merged.filter { it.builtin || it.local || it.key in remoteKeys }
        save(result)
        return result
    }

    /** 添加本地自定义字段（数据页），标记 local=true（跨模板保留），保存并返回新 schema */
    fun addLocalField(def: FieldDef): List<FieldDef> {
        val cur = current()
        val next = cur + def.copy(builtin = false, local = true)
        save(next)
        return next
    }

    /** 移除本地自定义字段 */
    fun removeLocalField(key: String): List<FieldDef> {
        val cur = current()
        val next = cur.filter { it.key != key || it.builtin }
        save(next)
        return next
    }

    fun save(schema: List<FieldDef>) {
        val json = "[" + schema.joinToString(",") { FieldDefSerializer.toJson(it) } + "]"
        prefs.edit().putString(KEY_SCHEMA, json).apply()
    }

    /** 轻量 JSON 数组解析：提取每个 {…} 对象（options 数组内不含花括号） */
    private fun parseArray(s: String): List<String> {
        val out = ArrayList<String>()
        var depth = 0
        var start = -1
        var i = 0
        while (i < s.length) {
            when (s[i]) {
                '{' -> {
                    if (depth == 0) start = i
                    depth++
                }
                '}' -> {
                    depth--
                    if (depth == 0 && start >= 0) {
                        out.add(s.substring(start, i + 1))
                        start = -1
                    }
                }
            }
            i++
        }
        return out
    }

    /** 合并：内置字段以本地为基础、线上覆盖 label/type/options/order；线上新增 key 追加为扩展 */
    private fun mergeFields(local: List<FieldDef>, remote: List<FieldDef>): List<FieldDef> {
        val byKey = LinkedHashMap<String, FieldDef>()
        local.forEach { byKey[it.key] = it }
        remote.forEach { r ->
            if (r.key.isBlank()) return@forEach
            val exist = byKey[r.key]
            if (exist != null && exist.builtin) {
                // 内置：覆盖展示配置
                byKey[r.key] = exist.copy(
                    label = r.label.ifBlank { exist.label },
                    type = r.type.ifBlank { exist.type },
                    options = r.options.ifEmpty { exist.options },
                    order = r.order
                )
            } else {
                // 新增 key → 扩展字段（builtin=false）
                byKey[r.key] = FieldDef(
                    key = r.key,
                    label = r.label.ifBlank { r.key },
                    type = r.type.ifBlank { "text" },
                    group = r.group.ifBlank { "其他" },
                    required = r.required,
                    options = r.options,
                    order = r.order,
                    builtin = false
                )
            }
        }
        return byKey.values.sortedBy { it.order }
    }
}

/** FieldDef JSON 序列化（轻量，无 kotlinx 依赖） */
object FieldDefSerializer {
    fun toJson(f: FieldDef): String {
        val base = "{\"key\":\"${esc(f.key)}\",\"label\":\"${esc(f.label)}\",\"type\":\"${esc(f.type)}\",\"group\":\"${esc(f.group)}\",\"required\":${f.required},\"order\":${f.order},\"builtin\":${f.builtin},\"local\":${f.local}"
        if (f.options.isEmpty()) return "$base}"
        val opts = f.options.joinToString(",", prefix = "[", postfix = "]") { "\"${esc(it)}\"" }
        return "$base,\"options\":$opts}"
    }

    fun fromJson(s: String): FieldDef {
        fun str(k: String): String {
            val m = Regex("\"$k\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").find(s)
            return m?.groupValues?.get(1)?.replace("\\\"", "\"") ?: ""
        }
        fun bool(k: String, def: Boolean = false): Boolean {
            val m = Regex("\"$k\"\\s*:\\s*(true|false)").find(s)
            return m?.groupValues?.get(1)?.toBoolean() ?: def
        }
        fun int(k: String): Int {
            val m = Regex("\"$k\"\\s*:\\s*(\\d+)").find(s)
            return m?.groupValues?.get(1)?.toIntOrNull() ?: 0
        }
        fun list(k: String): List<String> {
            val m = Regex("\"$k\"\\s*:\\s*\\[([^\\]]*)\\]").find(s)
            if (m == null) return emptyList()
            return Regex("\"((?:[^\"\\\\]|\\\\.)*)\"").findAll(m.groupValues[1])
                .map { it.groupValues[1].replace("\\\"", "\"") }.toList()
        }
        return FieldDef(
            key = str("key"),
            label = str("label"),
            type = str("type").ifBlank { "text" },
            local = bool("local"),
            group = str("group").ifBlank { "其他" },
            required = bool("required"),
            options = list("options"),
            order = int("order"),
            builtin = bool("builtin", def = true)
        )
    }

    private fun esc(s: String): String = s.replace("\\", "\\\\").replace("\"", "\\\"")
}
