/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : MarketingComplianceValidator.java
 * Date : 2026/10/23 16:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service.marketing;

import org.hiylo.scrm.config.ScrmMarketingBatchProperties;
import org.hiylo.scrm.dto.ScrmMarketingVariantDto;
import org.hiylo.scrm.common.util.TemplateRenderUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 财税营销文案合规校验器（程序化拦截, prompts.md §4.1）。
 * <p>
 * AI 生成变体在进入人工审核队列前必须通过: 占位符完整性、合规禁语扫描、
 * 渠道字数校验与结构完整性四道闸门。任一不过即整批打回, 不进入人工审核
 * （对应 review-checklist「占位符不齐 = 不合格」与「程序化禁语扫描兜底」）。
 * 禁语词表与 prompts/marketing-batch.md「合规禁语清单」保持一致, 扩充时两处同步。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Component
public class MarketingComplianceValidator {

    /**
     * 占位符残留检测改用 {@link TemplateRenderUtils#hasUnresolvedPlaceholder}。
     * <p>原正则只覆盖 {@code {{var}}} 与 {@code {var}}，漏掉了 {@code ${var}}——
     * 用美元花括号写模板时，残留占位符能通过合规扫描流出去。</p>
     */

    /** 合规禁语词表 (广告法最高级用语 + 保证收益 + 夸大承诺 + 违规暗示 + 虚假紧迫) */
    private static final String[] FORBIDDEN_WORDS = {
            // 绝对化用语 / 最高级 ("第一时间" 等词组在扫描时按上下文豁免)
            "最好", "第一", "最专业", "唯一", "首选", "国家级", "顶级", "绝对",
            // 保证收益类
            "保证", "包赚", "稳赚", "无风险", "100%", "必过", "稳过", "包过",
            "保证省钱", "包下证", "内部渠道",
            // 夸大承诺 (数字类由正则另行拦截: 省税 X%/节税几十万/少缴一半)
            "节税几十万", "少缴一半",
            // 违规/灰色服务暗示
            "合理避税", "走账", "虚开", "买票", "代开发票避税", "无票处理",
            // 虚假紧迫
            "仅限今日", "错过再等一年", "立即抢购", "名额有限"
    };

    /** 最高级用语豁免上下文: 不作为违规判定 (如「第一时间联系」) */
    private static final String EXEMPT_FIRST_TIME = "第一时间";

    /** 虚假紧迫正则: 最后 N(个)名额 / 最后 N 天 (无法核实的紧迫表述) */
    private static final Pattern FAKE_URGENCY_PATTERN =
            Pattern.compile("最后\\s*[0-9０-９]{1,2}\\s*个?(名额|天|小时)|最后\\s*[一二两三四五六七八九十]{1,3}\\s*个?(名额|天|小时)");

    /** 夸大承诺数字正则: 省税 X% 起 / 节税 XX万 / 少缴 XX% 等 (不得编造数字承诺) */
    private static final Pattern EXAGGERATED_NUMBER_PATTERN =
            Pattern.compile("(省税|节税|少缴|免税额?)\\s*[0-9]+\\s*(%|％|万|元)");

    private final ScrmMarketingBatchProperties properties;

    /**
     * 构造合规校验器。
     *
     * @param properties 批量生成配置 (渠道字数上限)
     */
    public MarketingComplianceValidator(ScrmMarketingBatchProperties properties) {
        this.properties = properties;
    }

    /**
     * 校验单套变体是否合规。
     *
     * @param variant 变体内容
     * @param channel 目标渠道 (WORK_WECHAT/WECHAT/SMS/EMAIL, 决定字数上限)
     * @param charLimit 渠道字数上限 (可空, 空则按渠道取配置默认)
     * @return 违规项列表; 空列表表示通过
     */
    public List<String> validate(ScrmMarketingVariantDto variant, String channel, Integer charLimit) {
        List<String> violations = new ArrayList<>();
        if (variant == null) {
            violations.add("变体为空");
            return violations;
        }
        String content = variant.getContent();
        if (content == null || content.isBlank()) {
            violations.add("正文为空");
            return violations;
        }
        // 1. 占位符完整性: 正文不得残留未替换占位符
        if (TemplateRenderUtils.hasUnresolvedPlaceholder(content)) {
            violations.add("残留未替换占位符");
        }
        // 2. 合规禁语扫描 (带上下文豁免: 「第一时间」不算绝对化「第一」)
        for (String word : FORBIDDEN_WORDS) {
            if (EXEMPT_FIRST_TIME.equals(word)) {
                continue;
            }
            if ("第一".equals(word) && content.contains(EXEMPT_FIRST_TIME)) {
                continue;
            }
            if (content.contains(word)) {
                violations.add("命中禁语: " + word);
            }
        }
        // 2.1 虚假紧迫: 「最后 N 个名额/最后 N 天」等无法核实的紧迫表述
        if (FAKE_URGENCY_PATTERN.matcher(content).find()) {
            violations.add("命中虚假紧迫表述 (最后 N 个名额/最后 N 天)");
        }
        // 3. 夸大承诺数字: 省税/节税 X% 等未经验证的数字承诺
        if (EXAGGERATED_NUMBER_PATTERN.matcher(content).find()) {
            violations.add("命中夸大数字承诺 (省税/节税金额或比例)");
        }
        // 4. 渠道字数校验
        int limit = resolveCharLimit(channel, charLimit);
        if (charCount(content) > limit) {
            violations.add("超过渠道字数上限: " + limit + " 字");
        }
        return violations;
    }

    /**
     * 收集人工重点审核建议（非硬性打回项）。
     * <p>
     * 按 prompts.md §4.1 程序化拦截仅四项硬性闸门; 涉税免责行属 review-checklist
     * B3 人工审核项, 由本方法给出建议并入变体 riskNotes, 供审核人逐项核对。
     * </p>
     *
     * @param variant 变体内容
     * @return 审核建议列表 (空表示无需特别关注)
     */
    public List<String> collectAdvisories(ScrmMarketingVariantDto variant) {
        List<String> advisories = new ArrayList<>();
        if (variant == null || variant.getContent() == null) {
            return advisories;
        }
        String content = variant.getContent();
        // 涉税/政策内容应附免责行 (review-checklist B3; 无「为准」即提示人工确认)
        if (content.contains("税") && !content.contains("为准")) {
            advisories.add("涉税内容请人工确认附免责表述(以官方文件及主管税务机关答复为准)");
        }
        return advisories;
    }

    /**
     * 按渠道解析字数上限: 入参优先, 其次 SMS/EMAIL 配置, 最后默认值。
     *
     * @param channel   目标渠道
     * @param charLimit 入参字数上限 (可空)
     * @return 字数上限
     */
    public int resolveCharLimit(String channel, Integer charLimit) {
        if (charLimit != null && charLimit > 0) {
            return charLimit;
        }
        if (channel == null) {
            return properties.getDefaultCharLimit();
        }
        switch (channel.toUpperCase()) {
            case "SMS":
                return properties.getSmsCharLimit();
            case "EMAIL":
                return properties.getEmailCharLimit();
            default:
                return properties.getDefaultCharLimit();
        }
    }

    /**
     * 统计字数 (不含标点, prompts.md 口径「不含标点计」; 空白/标点/换行不计)。
     *
     * @param content 正文
     * @return 字数
     */
    public int charCount(String content) {
        if (content == null) {
            return 0;
        }
        int count = 0;
        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                count++;
            }
        }
        return count;
    }
}