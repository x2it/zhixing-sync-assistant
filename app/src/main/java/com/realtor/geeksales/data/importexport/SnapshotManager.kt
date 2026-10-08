package com.realtor.geeksales.data.importexport

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import com.realtor.geeksales.data.db.Customer
import com.realtor.geeksales.data.db.FollowResult
import com.realtor.geeksales.data.db.FollowUp
import com.realtor.geeksales.data.db.IntentLevel
import com.realtor.geeksales.data.db.SmsMessage
import com.realtor.geeksales.data.repo.CustomerRepository
import com.realtor.geeksales.util.Formatter
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.DateUtil
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import java.io.FileInputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 时光机：全量快照 + 覆盖恢复。
 * 快照 = 多表 XLSX（客户 / 跟进 / 标签 / 短信），存「下载/知行同步助手备份」。
 * 恢复 = 清空本地后按快照重建（调用方须先自动快照当前状态，双保险）。
 */
@Singleton
class SnapshotManager @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val repo: CustomerRepository,
    private val excel: ExcelManager
) {
    companion object {
        private const val SHEET_CUSTOMERS = "客户"
        private const val SHEET_FOLLOWUPS = "跟进"
        private const val SHEET_TAGS = "标签"
        private const val SHEET_SMS = "短信"
        private const val SHEET_EXTFIELDS = "扩展字段"
        private val MIN_FMT = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        private val DAY_FMT = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    }

    /** 全量快照到 Downloads/知行同步助手备份，返回文件名；失败返回 null */
    suspend fun snapshotToDownloads(): String? = withContext(Dispatchers.IO) {
        val all = repo.getAll()
        if (all.isEmpty()) return@withContext null
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.getDefault()).format(Date())
        val display = "TMA时光机-${stamp}.xlsx"
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, display)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/知行同步助手备份")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: return@withContext null
        runCatching {
            ctx.contentResolver.openOutputStream(uri)?.use { os ->
                XlsxWriter.writeMulti(os, buildSheets(all))
            }
        }.onFailure {
            ctx.contentResolver.delete(uri, null, null)
            return@withContext null
        }
        values.clear()
        values.put(MediaStore.MediaColumns.IS_PENDING, 0)
        ctx.contentResolver.update(uri, values, null, null)
        display
    }

    private suspend fun buildSheets(all: List<Customer>): List<Pair<String, List<List<String>>>> {
        val nameByPhone = HashMap<String, String>()
        all.forEach { c -> if (c.phoneNormalized.isNotBlank()) nameByPhone[c.phoneNormalized] = c.name }
        // 1) 客户
        val customers = excel.rowsForSnapshot(all)
        // 2) 跟进
        val fuRows = ArrayList<List<String>>()
        fuRows.add(listOf("客户手机号", "客户名", "结果", "时长秒", "备注", "提醒", "时间"))
        val idByPhone = HashMap<Long, String>()
        all.forEach { c -> if (c.phoneNormalized.isNotBlank()) idByPhone[c.id] = c.phoneNormalized }
        repo.allFollowUps().forEach { fu ->
            val phone = idByPhone[fu.customerId] ?: ""
            fuRows.add(
                listOf(
                    phone,
                    nameByPhone[phone].orEmpty(),
                    fu.result.name,
                    fu.durationSec.toString(),
                    fu.note.orEmpty(),
                    fu.remindAt?.let { MIN_FMT.format(Date(it)) }.orEmpty(),
                    MIN_FMT.format(Date(fu.createdAt))
                )
            )
        }
        // 3) 标签
        val tagRows = ArrayList<List<String>>()
        tagRows.add(listOf("标签名", "客户手机号"))
        val tagIdToName = HashMap<Long, String>()
        repo.allTags().forEach { t -> tagIdToName[t.id] = t.name }
        repo.allTagMappings().forEach { m ->
            val phone = idByPhone[m.customerId] ?: ""
            val name = tagIdToName[m.tagId] ?: ""
            if (phone.isNotBlank() && name.isNotBlank()) tagRows.add(listOf(name, phone))
        }
        // 4) 短信
        val smsRows = ArrayList<List<String>>()
        smsRows.add(listOf("客户手机号", "号码", "内容", "方向", "时间"))
        repo.allSms().forEach { s ->
            val phone = if (s.customerId > 0) idByPhone[s.customerId] ?: "" else ""
            smsRows.add(listOf(phone, s.phone, s.body, s.direction, MIN_FMT.format(Date(s.messageDate))))
        }
        // 5) 扩展字段（线上模板自定义字段值）
        val extRows = ArrayList<List<String>>()
        extRows.add(listOf("客户手机号", "字段Key", "字段值"))
        repo.allExtFields().forEach { f ->
            val phone = idByPhone[f.customerId] ?: ""
            if (phone.isNotBlank() && f.fieldKey.isNotBlank()) extRows.add(listOf(phone, f.fieldKey, f.fieldValue))
        }
        return listOf(
            SHEET_CUSTOMERS to customers,
            SHEET_FOLLOWUPS to fuRows,
            SHEET_TAGS to tagRows,
            SHEET_SMS to smsRows,
            SHEET_EXTFIELDS to extRows
        )
    }

    /**
     * 从快照文件恢复（覆盖模式）：清空本地后重建。
     * 返回 null = 成功；否则返回错误信息。
     */
    suspend fun restoreFrom(uri: Uri, onProgress: (Float) -> Unit = {}): String? = withContext(Dispatchers.IO) {
        val tmp = File(ctx.cacheDir, "restore_${System.currentTimeMillis()}.xlsx")
        runCatching {
            ctx.contentResolver.openInputStream(uri)?.use { ins ->
                tmp.outputStream().use { os -> ins.copyTo(os) }
            }
            if (!tmp.exists() || tmp.length() == 0L) return@withContext "备份文件为空或无法读取"
            onProgress(0.1f)
            FileInputStream(tmp).use { fis ->
                val wb = XSSFWorkbook(fis)
                // 1) 解析客户
                val customersSheet = wb.getSheet(SHEET_CUSTOMERS) ?: wb.getSheetAt(0)
                    ?: return@use "快照中缺少「客户」表"
                val customers = parseCustomers(customersSheet)
                if (customers.isEmpty()) return@use "快照中没有客户数据"
                onProgress(0.25f)
                // 2) 解析跟进 / 标签 / 短信 / 扩展字段（均以客户手机号为关联键）
                val fuRows = parseSheetRows(wb.getSheet(SHEET_FOLLOWUPS))
                val tagRows = parseSheetRows(wb.getSheet(SHEET_TAGS))
                val smsRows = parseSheetRows(wb.getSheet(SHEET_SMS))
                val extRows = parseSheetRows(wb.getSheet(SHEET_EXTFIELDS))
                runCatching { wb.close() }
                // 3) 清空重建
                repo.deleteAll()
                repo.clearAllTags()
                repo.clearAllSms()
                repo.clearAllExtFields()
                val newIdByPhone = HashMap<String, Long>()
                val custChunks = customers.chunked(500)
                custChunks.forEachIndexed { ci, batch ->
                    onProgress(0.3f + 0.4f * ci / custChunks.size.coerceAtLeast(1))
                    val ids = repo.upsertAll(batch)
                    batch.forEachIndexed { i, c ->
                        if (c.phoneNormalized.isNotBlank()) newIdByPhone[c.phoneNormalized] = ids[i]
                    }
                    kotlinx.coroutines.yield()
                }
                // 跟进
                onProgress(0.72f)
                var fuCount = 0
                fuRows.forEach { r ->
                    val phone = r.getOrNull(0).orEmpty()
                    val result = runCatching { FollowResult.valueOf(r.getOrNull(2).orEmpty()) }.getOrDefault(FollowResult.PENDING)
                    val dur = r.getOrNull(3)?.toIntOrNull() ?: 0
                    val note = r.getOrNull(4)?.takeIf { it.isNotBlank() }
                    val remind = r.getOrNull(5)?.let { parseMinute(it) }
                    val createdAt = r.getOrNull(6)?.let { parseMinute(it) } ?: System.currentTimeMillis()
                    val cid = newIdByPhone[phone] ?: return@forEach
                    repo.insertFollowUps(
                        listOf(FollowUp(customerId = cid, result = result, durationSec = dur, note = note, remindAt = remind, fromPostCall = false, createdAt = createdAt))
                    )
                    fuCount++
                    if (fuCount % 500 == 0) { onProgress(0.72f + 0.1f * fuCount / (fuRows.size.coerceAtLeast(1))); kotlinx.coroutines.yield() }
                }
                // 标签
                onProgress(0.85f)
                var tagCount = 0
                tagRows.forEach { r ->
                    val name = r.getOrNull(0)?.takeIf { it.isNotBlank() } ?: return@forEach
                    val phone = r.getOrNull(1).orEmpty()
                    val cid = newIdByPhone[phone] ?: return@forEach
                    repo.applyTags(cid, listOf(name))
                    tagCount++
                }
                // 短信
                var smsCount = 0
                val smsToInsert = mutableListOf<SmsMessage>()
                smsRows.forEach { r ->
                    val phone = r.getOrNull(0).orEmpty()
                    val addr = r.getOrNull(1).orEmpty()
                    val body = r.getOrNull(2).orEmpty()
                    val dir = r.getOrNull(3).orEmpty().ifBlank { "in" }
                    val dateMs = r.getOrNull(4)?.let { parseMinute(it) } ?: System.currentTimeMillis()
                    if (addr.isBlank() && body.isBlank()) return@forEach
                    smsToInsert.add(
                        SmsMessage(
                            customerId = newIdByPhone[phone] ?: 0L,
                            phone = addr,
                            body = body,
                            direction = dir,
                            messageDate = dateMs
                        )
                    )
                    smsCount++
                    if (smsCount % 500 == 0) kotlinx.coroutines.yield()
                }
                if (smsToInsert.isNotEmpty()) repo.insertSms(smsToInsert)
                // 扩展字段
                onProgress(0.93f)
                var extCount = 0
                val extByPhone = HashMap<String, MutableList<Pair<String, String>>>()
                extRows.forEach { r ->
                    val phone = r.getOrNull(0).orEmpty()
                    val key = r.getOrNull(1)?.takeIf { it.isNotBlank() } ?: return@forEach
                    val value = r.getOrNull(2).orEmpty()
                    extByPhone.getOrPut(phone) { mutableListOf() }.add(key to value)
                    extCount++
                }
                extByPhone.forEach { (phone, pairs) ->
                    val cid = newIdByPhone[phone] ?: return@forEach
                    repo.putExtFields(cid, pairs.toMap())
                }
                onProgress(1f)
                null
            } ?: "快照解析失败"
        }.getOrElse { t ->
            tmp.delete()
            "恢复失败：${t.message ?: t.javaClass.simpleName}"
        }
    }

    /** 解析「客户」表 → Customer 列表（字段映射与 Excel 导入一致） */
    private fun parseCustomers(sheet: org.apache.poi.ss.usermodel.Sheet): List<Customer> {
        val rows = sheet.iterator()
        if (rows.hasNext()) rows.next() // header
        val parsed = mutableListOf<Customer>()
        val seen = HashSet<String>()
        while (rows.hasNext()) {
            val row = rows.next()
            val name = row.getCell(0)?.str()?.trim().orEmpty()
            val phone = row.getCell(1)?.str()?.trim().orEmpty()
            if (name.isBlank() || !Formatter.isValidCnPhone(phone)) continue
            val norm = Formatter.normalizePhone(phone)
            if (!seen.add(norm)) continue
            val level = when (row.getCell(12)?.str()?.trim()?.uppercase()) {
                "S" -> IntentLevel.S; "A" -> IntentLevel.A; "B" -> IntentLevel.B; "C" -> IntentLevel.C
                "D" -> IntentLevel.D; "V" -> IntentLevel.V; else -> IntentLevel.U
            }
            parsed += Customer(
                name = name, phone = phone, phoneNormalized = norm,
                phone2 = row.getCell(2)?.str()?.takeIf { it.isNotBlank() },
                gender = row.getCell(3)?.str()?.takeIf { it.isNotBlank() },
                age = row.getCell(4)?.numInt(),
                wechat = row.getCell(5)?.str()?.takeIf { it.isNotBlank() },
                source = row.getCell(6)?.str()?.takeIf { it.isNotBlank() },
                areaPref = row.getCell(7)?.str()?.takeIf { it.isNotBlank() },
                budgetMinWan = row.getCell(8)?.numInt(),
                budgetMaxWan = row.getCell(9)?.numInt(),
                houseType = row.getCell(10)?.str()?.takeIf { it.isNotBlank() },
                targetProject = row.getCell(11)?.str()?.takeIf { it.isNotBlank() },
                intentLevel = level,
                note = row.getCell(13)?.str()?.takeIf { it.isNotBlank() },
                nextFollowAt = row.getCell(14)?.day()?.time,
                email = row.getCell(15)?.str()?.takeIf { it.isNotBlank() },
                company = row.getCell(16)?.str()?.takeIf { it.isNotBlank() },
                jobTitle = row.getCell(17)?.str()?.takeIf { it.isNotBlank() },
                address = row.getCell(18)?.str()?.takeIf { it.isNotBlank() },
                nickname = row.getCell(19)?.str()?.takeIf { it.isNotBlank() },
                website = row.getCell(20)?.str()?.takeIf { it.isNotBlank() },
                birthday = row.getCell(21)?.str()?.takeIf { it.isNotBlank() },
                im = row.getCell(22)?.str()?.takeIf { it.isNotBlank() }
            )
            if (parsed.size % 500 == 0) Thread.yield()
        }
        return parsed
    }

    private fun parseSheetRows(sheet: org.apache.poi.ss.usermodel.Sheet?): List<List<String>> {
        if (sheet == null) return emptyList()
        val out = mutableListOf<List<String>>()
        val rows = sheet.iterator()
        if (rows.hasNext()) rows.next() // header
        while (rows.hasNext()) {
            val row = rows.next()
            out.add((0 until row.lastCellNum.toInt()).map { i -> row.getCell(i)?.str().orEmpty() })
        }
        return out
    }

    private fun parseMinute(s: String): Long? = runCatching { MIN_FMT.parse(s)?.time }.getOrNull()

    private fun org.apache.poi.ss.usermodel.Cell?.str(): String = when {
        this == null -> ""
        cellType == CellType.STRING -> stringCellValue
        cellType == CellType.NUMERIC -> {
            val v = numericCellValue
            if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString()
        }
        cellType == CellType.FORMULA -> runCatching { stringCellValue }.getOrElse { numericCellValue.toString() }
        else -> ""
    }

    private fun org.apache.poi.ss.usermodel.Cell?.numInt(): Int? = when {
        this == null -> null
        cellType == CellType.NUMERIC -> numericCellValue.toInt()
        cellType == CellType.STRING -> stringCellValue.trim().toIntOrNull()
        else -> null
    }

    private fun org.apache.poi.ss.usermodel.Cell?.day(): java.util.Date? = runCatching {
        when {
            this == null -> null
            cellType == CellType.NUMERIC && DateUtil.isCellDateFormatted(this) -> dateCellValue
            else -> {
                val s = str().trim()
                if (s.isBlank()) return@runCatching null
                DAY_FMT.parse(s)
            }
        }
    }.getOrNull()
}
