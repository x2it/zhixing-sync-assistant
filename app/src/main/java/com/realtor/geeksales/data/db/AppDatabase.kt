package com.realtor.geeksales.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

class Converters {
    @TypeConverter fun intentLevelToStr(v: IntentLevel): String = v.name
    @TypeConverter fun strToIntentLevel(s: String): IntentLevel = runCatching { IntentLevel.valueOf(s) }.getOrDefault(IntentLevel.U)

    @TypeConverter fun followResultToStr(v: FollowResult): String = v.name
    @TypeConverter fun strToFollowResult(s: String): FollowResult = runCatching { FollowResult.valueOf(s) }.getOrDefault(FollowResult.PENDING)
}

@Database(
    entities = [
        Customer::class,
        FollowUp::class,
        Tag::class,
        CustomerTagMap::class,
        SmsMessage::class,
        CustomerField::class,
        CallRecord::class
    ],
    version = 6,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun customerDao(): CustomerDao
    abstract fun followUpDao(): FollowUpDao
    abstract fun tagDao(): TagDao
    abstract fun smsDao(): SmsDao
    abstract fun customerFieldDao(): CustomerFieldDao
    abstract fun callDao(): CallDao

    companion object {
        const val NAME = "geek_sales.db"

        val MIGRATIONS = arrayOf<androidx.room.migration.Migration>(
            // v1 → v2：新增通讯录对齐字段（email/company/jobTitle/address/nickname/website/birthday/im）
            object : androidx.room.migration.Migration(1, 2) {
                override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE customers ADD COLUMN email TEXT")
                    db.execSQL("ALTER TABLE customers ADD COLUMN company TEXT")
                    db.execSQL("ALTER TABLE customers ADD COLUMN jobTitle TEXT")
                    db.execSQL("ALTER TABLE customers ADD COLUMN address TEXT")
                    db.execSQL("ALTER TABLE customers ADD COLUMN nickname TEXT")
                    db.execSQL("ALTER TABLE customers ADD COLUMN website TEXT")
                    db.execSQL("ALTER TABLE customers ADD COLUMN birthday TEXT")
                    db.execSQL("ALTER TABLE customers ADD COLUMN im TEXT")
                }
            },
            // v2 → v3：知行同步助手（线上）联系人 id，双向同步映射用
            object : androidx.room.migration.Migration(2, 3) {
                override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE customers ADD COLUMN wbContactId TEXT")
                }
            },
            // v3 → v4：短信记录表（知行同步助手拉回的短信，供客户时间线展示）
            object : androidx.room.migration.Migration(3, 4) {
                override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `sms_messages` (" +
                            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "`customerId` INTEGER NOT NULL, " +
                            "`phone` TEXT NOT NULL, " +
                            "`body` TEXT NOT NULL, " +
                            "`direction` TEXT NOT NULL, " +
                            "`messageDate` INTEGER NOT NULL, " +
                            "`wbMessageId` TEXT, " +
                            "`createdAt` INTEGER NOT NULL)"
                    )
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_sms_messages_customerId` ON `sms_messages` (`customerId`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_sms_messages_messageDate` ON `sms_messages` (`messageDate`)")
                }
            },
            // v4 → v5：客户扩展字段表（线上模板 schema 驱动的"万物可插"存储）
            object : androidx.room.migration.Migration(4, 5) {
                override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `customer_fields` (" +
                            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "`customerId` INTEGER NOT NULL, " +
                            "`fieldKey` TEXT NOT NULL, " +
                            "`fieldValue` TEXT NOT NULL)"
                    )
                    db.execSQL(
                        "CREATE UNIQUE INDEX IF NOT EXISTS `index_customer_fields_customerId_fieldKey` " +
                            "ON `customer_fields` (`customerId`, `fieldKey`)"
                    )
                }
            },
            // v5 → v6：通话记录表（本地通话镜像 + 知行同步助手通话备份，详情页互动档案）
            object : androidx.room.migration.Migration(5, 6) {
                override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `call_records` (" +
                            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "`customerId` INTEGER NOT NULL, " +
                            "`phone` TEXT NOT NULL, " +
                            "`direction` TEXT NOT NULL, " +
                            "`duration` INTEGER NOT NULL, " +
                            "`callDate` INTEGER NOT NULL, " +
                            "`note` TEXT, " +
                            "`wbCallId` TEXT, " +
                            "`createdAt` INTEGER NOT NULL)"
                    )
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_call_records_customerId` ON `call_records` (`customerId`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_call_records_callDate` ON `call_records` (`callDate`)")
                }
            }
        )
    }
}
