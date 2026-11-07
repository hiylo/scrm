/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm
 * File : TaxNoticeRecord.java
 * Date : 2026/10/23 09:30:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.dto;

import lombok.Data;

/**
 * 税务公告抓取统一记录模型（各省级抓取适配器的公共产出）。
 * <p>
 * 按 T03 落地建议: 每省一个适配器实现类, 产出统一 {@code TaxNoticeRecord}
 * （企业标识 / 公告类型 / 所属期 / 金额 / 公告日期 / 来源 URL / 唯一键）。
 * 唯一键 = 纳税人识别号 + 公告类型 + 所属期起（欠税叠加税种, 见 T03 §3.2）,
 * 用于同一企业同一所属期重复出现判「无变化」。
 * </p>
 * <p><b>合规</b>: 只抓公开栏目; 只查已授权客户企业; 证件号码等敏感字段在
 * 落库/日志前脱敏（由写入方执行）。</p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class TaxNoticeRecord {

    /** 公告类型: 欠税 / 非正常户 / 重大税收违法 / 行政处罚 / 通知公告 */
    private String noticeType;

    /** 纳税人名称（即企业名称） */
    private String enterpriseName;

    /** 纳税人识别号 / 统一社会信用代码 */
    private String creditCode;

    /** 法定代表人 */
    private String legalPerson;

    /** 欠税所属期起（yyyy-MM 或 yyyy-MM-dd, 可作为增量游标） */
    private String taxPeriodStart;

    /** 欠税所属期止（yyyy-MM 或 yyyy-MM-dd, 可空） */
    private String taxPeriodEnd;

    /** 欠缴税种（可空） */
    private String taxCategory;

    /** 欠税金额（元, 字符串原样保留, 数值计算由服务层精确处理） */
    private String taxAmount;

    /** 公告日期（yyyy-MM-dd） */
    private String noticeDate;

    /** 公告机关（可空） */
    private String noticeAuthority;

    /** 来源省份代码: GD(广东) / ZJ(浙江) */
    private String province;

    /** 来源 URL（可空） */
    private String sourceUrl;

    /** 明细唯一键（纳税人识别号 + 公告类型 + 所属期起 [+ 税种]） */
    private String uniqueKey;
}