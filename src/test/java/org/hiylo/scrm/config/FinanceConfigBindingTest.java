/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm-finance
 * File : FinanceConfigBindingTest.java
 * Date : 2026/10/23 18:30:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.config;

import org.hiylo.scrm.feign.OcrApiConfig;
import org.hiylo.scrm.feign.QixinApiConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 财税定制域配置绑定回归测试。
 * <p>
 * 验证 {@code application.yml} 中新增的 {@code finance:} 段经
 * {@code scrm.<域>} 别名链被各 {@code @ConfigurationProperties} 正常绑定,
 * 用于防护「占位符写错 / 别名断链 / 默认值漂移」导致的启动期配置缺失。
 * 使用 test profile, H2 内存库, 不连接任何外部服务。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("财税定制域配置绑定（finance 段 → scrm 别名 → ConfigurationProperties）")
class FinanceConfigBindingTest {

    /** 证照 OCR 配置 */
    @Autowired
    private OcrApiConfig ocrApiConfig;

    /** 启信宝工商 API 配置 */
    @Autowired
    private QixinApiConfig qixinApiConfig;

    /** 巡检调度配置 */
    @Autowired
    private ScrmInspectionProperties inspectionProperties;

    /** 报税日历配置 */
    @Autowired
    private ScrmTaxCalendarProperties taxCalendarProperties;

    /** 续费流失配置 */
    @Autowired
    private ScrmRenewalProperties renewalProperties;

    /** 政策推送配置 */
    @Autowired
    private ScrmPolicyPushProperties policyPushProperties;

    /** 财税知识库配置 */
    @Autowired
    private ScrmFinanceKnowledgeProperties knowledgeProperties;

    /** 高意向推送配置 */
    @Autowired
    private ScrmFinanceLeadPushProperties leadPushProperties;

    /** 营销批量生成配置 */
    @Autowired
    private ScrmMarketingBatchProperties marketingBatchProperties;

    @Test
    @DisplayName("模块② 证照 OCR: fail-closed 默认 + 地址/超时按 finance.ocr 绑定")
    void ocrConfigBindsFromFinanceSection() {
        assertThat(ocrApiConfig.isEnabled()).as("默认关闭（未部署 ocr-service 不发请求）").isFalse();
        assertThat(ocrApiConfig.getToken()).as("令牌默认空").isEmpty();
        assertThat(ocrApiConfig.getBaseUrl()).isEqualTo("http://192.168.1.180:8097/api/ocr");
        assertThat(ocrApiConfig.getConnectTimeoutSeconds()).isEqualTo(10);
        assertThat(ocrApiConfig.getReadTimeoutSeconds()).isEqualTo(200);
    }

    @Test
    @DisplayName("模块① 巡检: 启信宝默认关闭、cron/接收人按 finance.inspection 绑定")
    void inspectionConfigBindsFromFinanceSection() {
        assertThat(qixinApiConfig.isEnabled()).as("启信宝默认关闭").isFalse();
        assertThat(qixinApiConfig.getToken()).as("启信宝 token 默认空").isEmpty();
        assertThat(qixinApiConfig.getBaseUrl()).isEqualTo("https://api.qixin.com");
        assertThat(qixinApiConfig.getConnectTimeoutSeconds()).isEqualTo(5);
        assertThat(qixinApiConfig.getReadTimeoutSeconds()).isEqualTo(10);
        assertThat(inspectionProperties.getBusinessCron()).isEqualTo("0 30 2 * * ?");
        assertThat(inspectionProperties.getTaxCron()).isEqualTo("0 0 3 * * ?");
        assertThat(inspectionProperties.getNotifyRecipient()).isEqualTo("admin");
        assertThat(inspectionProperties.getNotifyRulePrefix()).isEqualTo("inspection-");
    }

    @Test
    @DisplayName("模块① 公告源开关: 广东/浙江默认开、信用中国默认关（T03 结论）")
    void inspectionNoticeSourcesBindFromFinanceSection() {
        assertThat(inspectionProperties.getNotice()).isNotNull();
        assertThat(inspectionProperties.getNotice().isGuangdongEnabled()).as("广东公告源默认启用").isTrue();
        assertThat(inspectionProperties.getNotice().isZhejiangEnabled()).as("浙江公告源默认启用").isTrue();
        assertThat(inspectionProperties.getNotice().isCreditChinaEnabled())
                .as("信用中国自建默认关闭（412 反爬, 走第三方 API 兜底）").isFalse();
    }

    @Test
    @DisplayName("报税日历: 三个 cron + 预警节奏 + 升级阈值按 finance.tax-calendar 绑定")
    void taxCalendarConfigBindsFromFinanceSection() {
        assertThat(taxCalendarProperties.getGenerateCron()).isEqualTo("0 0 1 * * ?");
        assertThat(taxCalendarProperties.getAdvanceCron()).isEqualTo("0 0 8 * * ?");
        assertThat(taxCalendarProperties.getWarningCron()).isEqualTo("0 0 9 * * ?");
        assertThat(taxCalendarProperties.getRemindDaysBefore()).isEqualTo("7,3,1");
        assertThat(taxCalendarProperties.getEscalateOverdueDays()).isEqualTo(5);
        assertThat(taxCalendarProperties.getRemindTemplateCode()).isEqualTo("tax-deadline-remind");
        assertThat(taxCalendarProperties.isWriteCalendarEvents()).isTrue();
    }

    @Test
    @DisplayName("续费流失: 阈值 75/50 + 30/7/3 节奏按 finance.renewal 绑定")
    void renewalConfigBindsFromFinanceSection() {
        assertThat(renewalProperties.getScanCron()).isEqualTo("0 0 8 * * ?");
        assertThat(renewalProperties.getRemindCron()).isEqualTo("0 0 9 * * ?");
        assertThat(renewalProperties.getRemindDaysBefore()).isEqualTo("30,7,3");
        assertThat(renewalProperties.getHighRiskThreshold()).isEqualTo(75.0);
        assertThat(renewalProperties.getMediumRiskThreshold()).isEqualTo(50.0);
        assertThat(renewalProperties.getLostAfterDays()).isEqualTo(15);
        assertThat(renewalProperties.getRemindTemplateCode()).isEqualTo("renewal-task-remind");
    }

    @Test
    @DisplayName("政策推送: 抽样 10 / 配额 2 按 finance.policy-push 绑定")
    void policyPushConfigBindsFromFinanceSection() {
        assertThat(policyPushProperties.getRefreshCron()).isEqualTo("0 0 3 * * ?");
        assertThat(policyPushProperties.getSamplingAuditCount()).isEqualTo(10);
        assertThat(policyPushProperties.getQuotaPer30Days()).isEqualTo(2);
        assertThat(policyPushProperties.getRemindTemplateCode()).isEqualTo("policy-push-remind");
    }

    @Test
    @DisplayName("模块③ 知识库: 库名/复核周期/导入上限按 finance.knowledge 绑定")
    void knowledgeConfigBindsFromFinanceSection() {
        assertThat(knowledgeProperties.getKbName()).isEqualTo("财税知识库");
        assertThat(knowledgeProperties.getReviewCycleDays()).isEqualTo(180);
        assertThat(knowledgeProperties.getImportMaxFiles()).isEqualTo(100);
    }

    @Test
    @DisplayName("模块⑤ 高意向推送: 模板/接收人/模型编码按 finance.lead-push 绑定")
    void leadPushConfigBindsFromFinanceSection() {
        assertThat(leadPushProperties.isEnabled()).isTrue();
        assertThat(leadPushProperties.getTemplateCode()).isEqualTo("lead-hot-push");
        assertThat(leadPushProperties.getRecipient()).isEqualTo("admin");
        assertThat(leadPushProperties.getModelCode()).isEqualTo("FIN_LEAD_SCORE_V1");
    }

    @Test
    @DisplayName("模块④ 批量生成: 温度/上限/提示词资源按 finance.marketing-batch 绑定")
    void marketingBatchConfigBindsFromFinanceSection() {
        assertThat(marketingBatchProperties.getTemperature()).isEqualTo(0.8);
        assertThat(marketingBatchProperties.getMaxTokens()).isEqualTo(3000);
        assertThat(marketingBatchProperties.getDefaultVariantCount()).isEqualTo(3);
        assertThat(marketingBatchProperties.getMaxVariantCount()).isEqualTo(10);
        assertThat(marketingBatchProperties.getSmsCharLimit()).isEqualTo(67);
        assertThat(marketingBatchProperties.getEmailCharLimit()).isEqualTo(300);
        assertThat(marketingBatchProperties.getPromptResource())
                .isEqualTo("classpath:prompts/marketing-batch.md");
    }
}
