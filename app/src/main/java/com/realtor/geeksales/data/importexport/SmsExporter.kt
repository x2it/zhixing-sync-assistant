package com.realtor.geeksales.data.importexport

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import com.realtor.geeksales.data.repo.CustomerRepository
import com.realtor.geeksales.util.Formatter
import com.opencsv.CSVWriterBuilder
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

data class SmsReport(
    val total: Int = 0,
    val exported: Int = 0,
    val matched: Int = 0,
    val error: String? = null
)

/**
 * 短信备份：读取系统短信（需 READ_SMS 权限），增量导出 CSV 到下载目录。
 * 隐私说明：短信为最敏感个人数据，本功能仅做本地备份，不上传任何服务器；
 * 文件落在用户自己的「下载/知行同步助手备份」目录。
 */
@Singleton
class SmsExporter @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val repo: CustomerRepository
) {
    companion object {
        private const val PREFS = "tma_prefs"
        private const val KEY_LAST_ID = "sms_last_backup_id"
        private val SMS_URI: Uri = Uri.parse("content://sms")
        private val HEADERS = arrayOf(
            "时间", "类型", "号码", "客户名", "内容"
        )
    }

    private val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** 上次备份到的短信 _id（增量起点） */
    private fun lastBackupId(): Long = prefs.getLong(KEY_LAST_ID, 0L)

    /**
     * 增量备份短信到 Downloads/知行同步助手备份，返回报告；无新短信返回 exported=0。
     * 需先确保 READ_SMS 权限（UI 层请求）。
     */
    suspend fun backupToDownloads(): SmsReport = withContext(Dispatchers.IO) {
        // 1) 读取增量短信
        val projection = arrayOf("_id", "address", "body", "date", "type")
        val selection = if (lastBackupId() > 0) "_id > ?" else null
        val args = if (selection != null) arrayOf(lastBackupId().toString()) else null
        val rows = mutableListOf<Array<String>>()
        val customers = repo.getAll()
        val nameByPhone = HashMap<String, String>()
        customers.forEach { c ->
            if (c.phoneNormalized.isNotBlank()) nameByPhone[c.phoneNormalized] = c.name
        }
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        var maxId = lastBackupId()
        var matched = 0
        runCatching {
            ctx.contentResolver.query(SMS_URI, projection, selection, args, "_id ASC")?.use { c ->
                val idI = c.getColumnIndexOrThrow("_id")
                val addrI = c.getColumnIndexOrThrow("address")
                val bodyI = c.getColumnIndexOrThrow("body")
                val dateI = c.getColumnIndexOrThrow("date")
                val typeI = c.getColumnIndexOrThrow("type")
                while (c.moveToNext()) {
                    val id = c.getLong(idI)
                    val address = c.getString(addrI).orEmpty().trim()
                    val body = c.getString(bodyI).orEmpty()
                    val dateMs = c.getLong(dateI)
                    val type = c.getInt(typeI)
                    maxId = maxOf(maxId, id)
                    val typeLabel = when (type) {
                        1 -> "收"
                        2 -> "发"
                        3 -> "草稿"
                        else -> "其他"
                    }
                    val customerName = nameByPhone[Formatter.normalizePhone(address)]
                    if (customerName != null) matched++
                    rows.add(
                        arrayOf(
                            sdf.format(Date(dateMs)),
                            typeLabel,
                            address,
                            customerName.orEmpty(),
                            body
                        )
                    )
                    if (rows.size % 1000 == 0) kotlinx.coroutines.yield()
                }
            }
        }.onFailure { t ->
            return@withContext SmsReport(error = "读取短信失败：${t.message ?: t.javaClass.simpleName}")
        }
        if (rows.isEmpty()) return@withContext SmsReport(total = 0, exported = 0, matched = matched)

        // 2) 写入 Downloads/知行同步助手备份
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.getDefault()).format(Date())
        val display = "TMA短信备份-${stamp}.csv"
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, display)
            put(MediaStore.MediaColumns.MIME_TYPE, "text/csv")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/知行同步助手备份")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: return@withContext SmsReport(error = "无法创建备份文件")
        runCatching {
            ctx.contentResolver.openOutputStream(uri)?.use { os ->
                os.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())) // UTF-8 BOM
                val writer = CSVWriterBuilder(OutputStreamWriter(os, Charsets.UTF_8)).build()
                writer.writeNext(HEADERS)
                rows.forEach { writer.writeNext(it) }
                writer.flushQuietly()
            }
        }.onFailure {
            ctx.contentResolver.delete(uri, null, null)
            return@withContext SmsReport(error = "写入备份文件失败：${it.message}")
        }
        values.clear()
        values.put(MediaStore.MediaColumns.IS_PENDING, 0)
        ctx.contentResolver.update(uri, values, null, null)
        // 3) 记录增量游标
        prefs.edit().putLong(KEY_LAST_ID, maxId).apply()
        SmsReport(total = rows.size, exported = rows.size, matched = matched)
    }
}
