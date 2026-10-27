/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm-finance
 * File : ScrmConversationSignalService.java
 * Date : 2026/10/24 09:45:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hiylo.scrm.entity.ScrmConversationEntity;
import org.hiylo.scrm.entity.ScrmConversationMessageEntity;
import org.hiylo.scrm.repository.ScrmConversationMessageRepository;
import org.hiylo.scrm.repository.ScrmConversationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 会话意向信号提取服务（为线索评分补充 4 个会话维度键）。
 * <p>
 * <b>背景</b>：财税线索评分模型（{@code FIN_LEAD_SCORE_V1}）的 4 个维度依赖
 * {@code priceInquiry} / {@code companyInfoGiven} / {@code urgencyLevel} /
 * {@code conversationDepth} 四个上下文键，但评分服务的 {@code buildCustomerContext}
 * 从未产出它们——所有规则判定为 null，评分恒为 0、档位恒为 DEAD，高意向推送永不触发。
 * </p>
 * <p>
 * <b>为什么用规则而不是 LLM</b>：评分是数值结果，必须可复现、可单测、可解释。
 * 用关键词与计数规则可以从会话文本里稳定提取这 4 个信号，且零额外成本、无外部依赖。
 * LLM 只适合做后续的语义增强，不作为评分数值的来源（符合「关键数值必须由代码精确计算」）。
 * </p>
 * <ul>
 *   <li>{@code priceInquiry} 0-3：客户主动问价/费用的程度（问价次数分档）</li>
 *   <li>{@code companyInfoGiven} 0-3：客户透露企业信息的程度（企业名/税号/纳税人身份/经营情况）</li>
 *   <li>{@code urgencyLevel} 0-3：紧迫度（时间压力词 / 逾期 / 被罚 / 马上要办）</li>
 *   <li>{@code conversationDepth} 0-N：对话深度（客户有效发言条数）</li>
 * </ul>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScrmConversationSignalService {

    /** 单客户最多分析的会话数（防止历史会话过多拖慢评分） */
    private static final int MAX_CONVERSATIONS = 20;

    /** 单会话最多分析的消息条数（取最近的） */
    private static final int MAX_MESSAGES_PER_CONVERSATION = 200;

    /** 客户侧消息方向（IN = 客户发来） */
    private static final String DIRECTION_IN = "IN";

    /** 问价类关键词（客户主动询价/问费用） */
    private static final Pattern PRICE_PATTERN = Pattern.compile(
            "(多少钱|怎么收费|费用|价格|报价|贵不贵|便宜|优惠|打折|多少钱一年|年费|收费标准|要多少)");

    /** 企业信息类关键词（客户透露主体/经营信息） */
    private static final Pattern COMPANY_INFO_PATTERN = Pattern.compile(
            "(公司|企业|个体户|合伙企业|有限公司|营业执照|统一社会信用代码|纳税人|小规模|一般纳税人"
                    + "|经营范围|注册资本|法人|股东|税号|开票|发票|对公账户)");

    /** 紧迫度类关键词（时间压力 / 风险驱动） */
    private static final Pattern URGENCY_PATTERN = Pattern.compile(
            "(着急|急|尽快|马上|立刻|今天|明天|本周|月底|季度末|逾期|罚款|滞纳金|催|被查|异常"
                    + "|来不及|赶不上|最后一天|截止)");

    /** 会话数据访问层（按客户查会话） */
    private final ScrmConversationRepository conversationRepository;

    /** 会话消息数据访问层（取消息内容） */
    private final ScrmConversationMessageRepository messageRepository;

    /**
     * 提取客户的会话意向信号。
     * <p>
     * 无会话或无客户消息时全部返回 0（不编造）。方法不抛异常——
     * 会话分析失败不应阻断评分主流程，降级为全 0。
     * </p>
     *
     * @param customerId 客户 ID
     * @return 信号键值（priceInquiry / companyInfoGiven / urgencyLevel / conversationDepth）
     */
    @Transactional(readOnly = true)
    public Map<String, Object> extractSignals(Long customerId) {
        Map<String, Object> signals = new LinkedHashMap<>();
        signals.put("priceInquiry", 0);
        signals.put("companyInfoGiven", 0);
        signals.put("urgencyLevel", 0);
        signals.put("conversationDepth", 0);
        if (customerId == null) {
            return signals;
        }
        try {
            List<ScrmConversationEntity> conversations = conversationRepository.findByCustomerId(customerId);
            if (conversations == null || conversations.isEmpty()) {
                return signals;
            }
            List<String> customerMessages = new ArrayList<>();
            int scanned = 0;
            for (ScrmConversationEntity conversation : conversations) {
                if (scanned++ >= MAX_CONVERSATIONS) {
                    break;
                }
                List<ScrmConversationMessageEntity> messages = messageRepository
                        .findByConversationIdOrderBySentAtDesc(conversation.getId());
                if (messages == null) {
                    continue;
                }
                int taken = 0;
                for (ScrmConversationMessageEntity message : messages) {
                    if (taken++ >= MAX_MESSAGES_PER_CONVERSATION) {
                        break;
                    }
                    if (!DIRECTION_IN.equalsIgnoreCase(message.getDirection())) {
                        continue;
                    }
                    String content = message.getContent();
                    if (content != null && !content.isBlank()) {
                        customerMessages.add(content);
                    }
                }
            }
            if (customerMessages.isEmpty()) {
                return signals;
            }
            signals.put("priceInquiry", classifyPrice(customerMessages));
            signals.put("companyInfoGiven", classifyCompanyInfo(customerMessages));
            signals.put("urgencyLevel", classifyUrgency(customerMessages));
            signals.put("conversationDepth", customerMessages.size());
            log.debug("[会话信号] customerId={}, 有效客户消息={}, signals={}",
                    customerId, customerMessages.size(), signals);
        } catch (Exception e) {
            // 会话分析是评分的增强信号，失败降级为全 0，不阻断评分
            log.warn("[会话信号] 提取失败, 降级为 0: customerId={}, err={}", customerId, e.getMessage());
        }
        return signals;
    }

    /**
     * 问价程度分档（0-3）。
     * <p>命中 1 次记 1，2 次记 2，3 次及以上记 3（模型规则按 1/2/≥3 给 35/25/40 分）。</p>
     *
     * @param messages 客户消息
     * @return 0-3
     */
    private int classifyPrice(List<String> messages) {
        long hits = messages.stream().filter(m -> PRICE_PATTERN.matcher(m).find()).count();
        return (int) Math.min(3, hits);
    }

    /**
     * 企业信息透露程度分档（0-3）。
     * <p>
     * 按「不同维度」计分而非按次数：命中企业名/执照 = 2 分基础，
     * 涉及纳税人身份或经营情况再 +1，最多 3 分。
     * </p>
     *
     * @param messages 客户消息
     * @return 0-3
     */
    private int classifyCompanyInfo(List<String> messages) {
        int hintCount = 0;
        boolean hasSubject = false;
        boolean hasTaxProfile = false;
        for (String message : messages) {
            if (!COMPANY_INFO_PATTERN.matcher(message).find()) {
                continue;
            }
            hintCount++;
            if (message.contains("公司") || message.contains("企业") || message.contains("个体")
                    || message.contains("营业执照") || message.contains("信用代码")) {
                hasSubject = true;
            }
            if (message.contains("小规模") || message.contains("一般纳税人") || message.contains("纳税人")
                    || message.contains("开票") || message.contains("发票") || message.contains("税号")) {
                hasTaxProfile = true;
            }
        }
        if (hintCount == 0) {
            return 0;
        }
        int level = hasSubject ? 2 : 1;
        if (hasSubject && hasTaxProfile) {
            level = 3;
        }
        return level;
    }

    /**
     * 紧迫度分档（0-3）。
     *
     * @param messages 客户消息
     * @return 0-3
     */
    private int classifyUrgency(List<String> messages) {
        long hits = messages.stream().filter(m -> URGENCY_PATTERN.matcher(m).find()).count();
        return (int) Math.min(3, hits);
    }
}
