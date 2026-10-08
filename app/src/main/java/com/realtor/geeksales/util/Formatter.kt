package com.realtor.geeksales.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Formatter {
    // SimpleDateFormat 非线程安全，改用 ThreadLocal 避免主/IO 线程并发调用时异常
    private val sdfDay = ThreadLocal.withInitial { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }
    private val sdfFull = ThreadLocal.withInitial { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }

    fun day(ms: Long?): String = if (ms == null || ms <= 0) "--" else sdfDay.get().format(Date(ms))
    fun full(ms: Long?): String = if (ms == null || ms <= 0) "--" else sdfFull.get().format(Date(ms))
    fun humanDuration(sec: Int): String {
        if (sec < 60) return "${sec}s"
        val m = sec / 60
        val s = sec % 60
        return if (m < 60) "${m}m${s}s" else "${m / 60}h${m % 60}m"
    }

    /** 归一化手机号：仅数字，并对国内号 86 前缀做兼容处理 */
    fun normalizePhone(raw: String): String {
        val d = raw.filter { it.isDigit() }
        return when {
            d.startsWith("86") && d.length == 13 -> d.drop(2)
            d.startsWith("0086") && d.length == 15 -> d.drop(4)
            else -> d
        }
    }

    fun isValidCnPhone(p: String): Boolean {
        val n = normalizePhone(p)
        // 大陆手机号 11 位；其它号码 ≥ 7 位也允许（如座机）
        return (n.length == 11 && n.startsWith("1")) || n.length in 7..20
    }

    /** 今天 00:00 epoch */
    fun todayStart(): Long {
        val cal = java.util.Calendar.getInstance()
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    fun addDays(anchor: Long, days: Int): Long = anchor + days * 86400_000L
    fun addHours(anchor: Long, hours: Int): Long = anchor + hours * 3600_000L

    /**
     * 兼容解析云端时间 → EpochMillis：
     * 13 位毫秒时间戳 / 10 位秒级时间戳 / "yyyy-MM-dd HH:mm" / ISO8601；失败返回 null。
     * v2.7.2：云端按毫秒时间戳存取，App 上传与回写统一用真实毫秒，不再用分钟精度字符串。
     */
    fun parseEpoch(s: String?): Long? {
        if (s.isNullOrBlank()) return null
        val t = s.trim()
        if (t.all { it.isDigit() }) {
            return runCatching { val n = t.toLong(); if (t.length >= 13) n else n * 1000L }.getOrNull()
        }
        return runCatching { sdfFull.get().parse(t)?.time }.getOrNull()
            ?: runCatching { java.time.OffsetDateTime.parse(t).toInstant().toEpochMilli() }.getOrNull()
    }

    /** EpochMillis → "yyyy-MM-dd HH:mm"（本地去重 key 用，兼容新旧数据）；失败 null */
    fun epochToMinute(ms: Long?): String? =
        ms?.let { if (it <= 0) null else runCatching { sdfFull.get().format(Date(it)) }.getOrNull() }

    /** "yyyy-MM-dd" → EpochMillis（知行同步助手日期格式）；解析失败返回 null */
    fun dayToEpoch(s: String?): Long? {
        if (s.isNullOrBlank()) return null
        return runCatching { sdfDay.get().parse(s)?.time }.getOrNull()
    }

    /** EpochMillis → "yyyy-MM-dd"；null/非法返回 null */
    fun epochToDay(ms: Long?): String? {
        if (ms == null || ms <= 0) return null
        return runCatching { sdfDay.get().format(Date(ms)) }.getOrNull()
    }
}
