/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm
 * File : ScrmAiVoucherSuggestionDto.java
 * Date : 2026/11/08 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * AI 智能凭证：票据识别的凭证草稿建议。
 * <p>
 * 由票据图片（发票/银行回单）经 AI Provider 多模态抽取生成：含票据摘要信息与
 * 「建议借贷分录」。所有建议科目都经过确定性过滤——只保留存在于企业科目表中的
 * 科目，借贷必须平衡；不可靠项（未识别/科目不存在）以标记字段暴露给前端，
 * 由会计人工确认后落草稿（绝不自动入账，符合代账审核纪律）。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScrmAiVoucherSuggestionDto {

    /** 是否识别出可供落草的完整建议（true 表示至少有一组平衡分录） */
    private boolean valid;

    /** 票据类型（发票 / 银行回单 / 收据 / 其他） */
    private String billType;

    /** 票据号码（如发票代码+号码 / 回单流水号；未识别为空） */
    private String billNo;

    /** 发生日期（YYYY-MM-DD；未识别为空） */
    private String billDate;

    /** 对方/往来方名称（未识别为空） */
    private String counterparty;

    /** 含税总金额（未识别为 0） */
    private BigDecimal amount;

    /** 摘要建议（如「购买办公用品」「收到货款」） */
    private String summary;

    /** AI 未识别或不可靠字段的提示（人工复核用） */
    private List<String> warnings = new ArrayList<>();

    /** 建议借贷分录（已按企业科目表过滤） */
    private List<SuggestionEntry> entries = new ArrayList<>();

    /**
     * 建议分录条目。
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SuggestionEntry {

        /** 科目编码（已校验存在于企业科目表） */
        private String subjectCode;

        /** 科目名称（宽带回写） */
        private String subjectName;

        /** direction: DEBIT / CREDIT */
        private String direction;

        /** 金额 */
        private BigDecimal amount;

        /** 行摘要 */
        private String summary;
    }
}