package com.realtor.geeksales.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 客户分层（与线上知行同步助手六层语义对齐）：
 * S=成交高价值（每季度维护） A=高意向（每周） B=已接触（每2周） C=信息完整（每月） D=线索（每2周联系） V=已成交（售后定期） U=未分类（纯本地状态，不同步）
 * 注意：B/C/D 是接触深度递进漏斗（线索→信息完整→已接触），不是意向从高到低。
 */
enum class IntentLevel { S, A, B, C, D, V, U }

@Entity(
    tableName = "customers",
    indices = [Index(value = ["phoneNormalized"], unique = true)]
)
data class Customer(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** 原始号码字符串 */
    val phone: String,
    /** 归一化后用于去重与查询的号码（仅数字） */
    val phoneNormalized: String,
    /** 备用电话，逗号分隔 */
    val phone2: String? = null,
    val gender: String? = null,
    val age: Int? = null,
    val wechat: String? = null,
    val source: String? = null,   // 来源：端口/到访/转介绍/网络…
    /** 意向区域：如 "朝阳国贸｜通州副中心" */
    val areaPref: String? = null,
    /** 预算下限，单位 万元 */
    val budgetMinWan: Int? = null,
    /** 预算上限，单位 万元 */
    val budgetMaxWan: Int? = null,
    /** 房型偏好：如 两居/三居/叠拼 */
    val houseType: String? = null,
    /** 意向楼盘 */
    val targetProject: String? = null,
    val intentLevel: IntentLevel = IntentLevel.U,
    val note: String? = null,
    /** 下次跟进时间 (EpochMillis UTC) */
    val nextFollowAt: Long? = null,
    // ---- 通讯录对齐字段（v2）：与系统联系人可双向映射 ----
    /** 邮箱 */
    val email: String? = null,
    /** 公司 */
    val company: String? = null,
    /** 职位 */
    val jobTitle: String? = null,
    /** 地址（常用地址，单行文本） */
    val address: String? = null,
    /** 昵称 */
    val nickname: String? = null,
    /** 网站 */
    val website: String? = null,
    /** 生日（YYYY-MM-DD 字符串，系统 Event.BIRTHDAY 对应） */
    val birthday: String? = null,
    /** 即时消息（微信/QQ 等，对应系统 IM 字段） */
    val im: String? = null,
    /** 知行同步助手（线上）联系人 id，双向同步映射用 */
    val wbContactId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    /** 是否已导入拨号队列 */
    val queued: Boolean = false,
    /** 队列中的顺序号，null = 不在队列 */
    val queueOrder: Int? = null,
    /** 已拨打次数 */
    val dialCount: Int = 0,
    /** 最近一次拨打时间 */
    val lastDialAt: Long? = null,
)
