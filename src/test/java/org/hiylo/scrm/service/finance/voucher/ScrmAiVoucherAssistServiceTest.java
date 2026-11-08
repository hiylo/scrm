/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm
 * File : ScrmAiVoucherAssistServiceTest.java
 * Date : 2026/11/08 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service.finance.voucher;

import org.hiylo.scrm.entity.ScrmAccountSubjectEntity;
import org.hiylo.scrm.entity.ScrmAiProviderEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ScrmAiVoucherAssistService#buildSuggestion} 纯逻辑单测：
 * 校验 AI 返回 JSON → 凭证草稿建议的确定性过滤（科目越权剔除 / 借贷平衡 / 金额合法 / 解析失败兜底）。
 * <p>不依赖真实 AI（buildSuggestion 为包私有纯函数，直接注入科目表断言）。</p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@DisplayName("ScrmAiVoucherAssistService：票据识别 → 凭证草稿建议（确定性校验）")
class ScrmAiVoucherAssistServiceTest {

    /** 构造测试用科目 */
    private ScrmAccountSubjectEntity subject(String code, String name) {
        ScrmAccountSubjectEntity s = new ScrmAccountSubjectEntity();
        s.setSubjectCode(code);
        s.setSubjectName(name);
        s.setStatus(ScrmAccountSubjectEntity.STATUS_ACTIVE);
        return s;
    }

    /** 构造 Provider（仅作为参数占位） */
    private ScrmAiProviderEntity provider(String model) {
        ScrmAiProviderEntity p = new ScrmAiProviderEntity();
        p.setProviderName("测试网关");
        p.setDefaultModel(model);
        return p;
    }

    @Test
    @DisplayName("有效发票：借贷平衡分录全部采纳，valid=true")
    void validBalanceEntries() {
        var subjects = List.of(
                subject("1001", "库存现金"),
                subject("6602", "管理费用"),
                subject("1002", "银行存款"));
        String raw = """
                {"bill_type":"增值税普通发票","bill_no":"12345678","bill_date":"2026-11-02",
                "counterparty":"文具店","amount":"520.00","summary":"购买办公用品",
                "entries":[
                  {"subject_code":"6602","direction":"DEBIT","amount":"520.00","summary":"办公用品"},
                  {"subject_code":"1002","direction":"CREDIT","amount":"520.00","summary":"付款"}
                ]}
                """;
        ScrmAiVoucherAssistService service =
                new ScrmAiVoucherAssistService(null, null, null, null);

        var dto = service.buildSuggestion(raw, subjects, provider("group/sensenova-pool"));

        assertThat(dto.isValid()).isTrue();
        assertThat(dto.getBillNo()).isEqualTo("12345678");
        assertThat(dto.getBillDate()).isEqualTo("2026-11-02");
        assertThat(dto.getCounterparty()).isEqualTo("文具店");
        assertThat(dto.getAmount()).isEqualByComparingTo("520.00");
        assertThat(dto.getEntries()).hasSize(2);
        assertThat(dto.getEntries().get(0).getSubjectName()).isEqualTo("管理费用");
        assertThat(dto.getEntries().get(0).getDirection()).isEqualTo("DEBIT");
        assertThat(dto.getEntries().get(1).getSubjectName()).isEqualTo("银行存款");
        assertThat(dto.getWarnings()).isEmpty();
    }

    @Test
    @DisplayName("AI 建议科目不在企业科目表 → 剔除 + warning（防跨企业科目入侵）")
    void unknownSubjectDropped() {
        var subjects = List.of(subject("1002", "银行存款"));
        String raw = """
                {"bill_type":"回单","amount":"100.00","summary":"转账",
                "entries":[
                  {"subject_code":"9999","direction":"DEBIT","amount":"100.00"},
                  {"subject_code":"1002","direction":"CREDIT","amount":"100.00"}
                ]}
                """;
        ScrmAiVoucherAssistService service =
                new ScrmAiVoucherAssistService(null, null, null, null);

        var dto = service.buildSuggestion(raw, subjects, provider("group/x"));

        // 9999 被剔除 → 只余 1 条, 借方总额 0 ≠ 贷方 100 → 不平衡 → invalid
        assertThat(dto.getEntries()).hasSize(1);
        assertThat(dto.getEntries().get(0).getSubjectCode()).isEqualTo("1002");
        assertThat(dto.isValid()).isFalse();
        assertThat(dto.getWarnings()).anyMatch(w -> w.contains("9999"));
    }

    @Test
    @DisplayName("AI 金额非法（负数/文字）→ 剔除该分录")
    void invalidAmountDropped() {
        var subjects = List.of(subject("1002", "银行存款"), subject("6602", "管理费用"));
        String raw = """
                {"bill_type":"发票","amount":"x元","summary":"报销",
                "entries":[
                  {"subject_code":"6602","direction":"DEBIT","amount":"x元"},
                  {"subject_code":"1002","direction":"CREDIT","amount":"abc"}
                ]}
                """;
        ScrmAiVoucherAssistService service =
                new ScrmAiVoucherAssistService(null, null, null, null);

        var dto = service.buildSuggestion(raw, subjects, provider("g"));

        assertThat(dto.getEntries()).isEmpty();
        assertThat(dto.isValid()).isFalse();
        assertThat(dto.getWarnings()).anyMatch(w -> w.contains("金额非法"));
    }

    @Test
    @DisplayName("借贷不平衡（值相等才 valid）→ warning 提示人工核对")
    void unbalancedEntries() {
        var subjects = List.of(subject("1002", "银行存款"), subject("6602", "管理费用"));
        String raw = """
                {"bill_type":"发票","amount":"500.00",
                "entries":[
                  {"subject_code":"6602","direction":"DEBIT","amount":"300.00"},
                  {"subject_code":"1002","direction":"CREDIT","amount":"500.00"}
                ]}
                """;
        ScrmAiVoucherAssistService service =
                new ScrmAiVoucherAssistService(null, null, null, null);

        var dto = service.buildSuggestion(raw, subjects, provider("g"));

        assertThat(dto.isValid()).isFalse();
        assertThat(dto.getWarnings()).anyMatch(w -> w.contains("借贷不平衡"));
    }

    @Test
    @DisplayName("模型返回不可解析 → valid=false + 解析失败 warning（不抛异常）")
    void unparsableContent() {
        var subjects = List.of(subject("1002", "银行存款"));
        ScrmAiVoucherAssistService service =
                new ScrmAiVoucherAssistService(null, null, null, null);

        var dto = service.buildSuggestion("这不是 JSON", subjects, provider("g"));

        assertThat(dto.isValid()).isFalse();
        assertThat(dto.getWarnings()).anyMatch(w -> w.contains("解析"));
    }

    @Test
    @DisplayName("纯视觉/无 text 能力或能力不足的 provider：entries 为空时不放行")
    void emptyEntriesRejected() {
        var subjects = List.of(subject("1002", "银行存款"));
        String raw = """
                {"bill_type":"收据","amount":"0","summary":"无分录",
                "entries":[]}
                """;
        ScrmAiVoucherAssistService service =
                new ScrmAiVoucherAssistService(null, null, null, null);

        var dto = service.buildSuggestion(raw, subjects, provider("g"));

        assertThat(dto.isValid()).isFalse();
        assertThat(dto.getWarnings()).anyMatch(w -> w.contains("未能生成有效分录"));
    }
}