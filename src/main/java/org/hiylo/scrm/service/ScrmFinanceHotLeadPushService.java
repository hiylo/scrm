/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmFinanceHotLeadPushService.java
 * Date : 2026/10/23 12:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.config.ScrmFinanceLeadPushProperties;
import org.hiylo.scrm.dto.ScrmNotificationSendDto;
import org.hiylo.scrm.entity.ScrmAiConversationEntity;
import org.hiylo.scrm.entity.ScrmCustomerEntity;
import org.hiylo.scrm.entity.ScrmLeadScoreEntity;
import org.hiylo.scrm.entity.ScrmLeadScoringModelEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.repository.ScrmAiConversationRepository;
import org.hiylo.scrm.repository.ScrmCustomerRepository;
import org.hiylo.scrm.repository.ScrmNotificationRepository;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 财税模块⑤ 高意向线索推送服务。
 * <p>
 * 模块⑤「高意向推送销售」实现: 当 {@link ScrmLeadScoringCalculationService#calculateScore}
 * 产出的评分等级命中 {@code A_PLUS / SUPER_HOT / HOT} 时, 本服务将其经
 * <b>NotificationCenter</b> 推送给销售（站内信, 复用 V13 预置模板 {@code lead-hot-push}）
 * 并附对话摘要 + 意向理由（评分维度明细 JSON）。
 * </p>
 * <p>
 * 实现约定（对齐 D05 {@code ScrmInspectionAlertService}）:
 * </p>
 * <ul>
 *   <li>推送失败不抛出, 只记 warn 日志 —— 高意向推送不阻断评分主流程;</li>
 *   <li>按「通知模板 + 关联评分记录」幂等去重（{@code related_type=LEAD_SCORE + related_id=scoreId}）,
 *       同一评分记录重复计算 / 重算不会重复推送;</li>
 *   <li>评分模型过滤: 仅推送配置的财税模型（{@code scrm.finance-lead-push.model-code}, 默认
 *       {@code FIN_LEAD_SCORE_V1}）产出的热线索, 不干扰其他评分模型;</li>
 *   <li>总开关 {@code scrm.finance-lead-push.enabled=false} 时仅记录日志。</li>
 * </ul>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScrmFinanceHotLeadPushService {

    /** 高意向等级集合: A_PLUS / SUPER_HOT / HOT（对齐 scoring-model.json 的 pushRule） */
    private static final List<String> HOT_GRADES = List.of(
            ScrmLeadScoringCalculationService.GRADE_A_PLUS,
            ScrmLeadScoringCalculationService.GRADE_SUPER_HOT,
            ScrmLeadScoringCalculationService.GRADE_HOT);

    /** 关联类型: 线索评分记录（用于推送幂等去重） */
    private static final String RELATED_TYPE_LEAD_SCORE = "LEAD_SCORE";

    /** 推送发送者 ID（V13 模板 sender_name=AI接待） */
    private static final String SENDER_ID = "ai-assistant";

    /** 推送优先级（越高越优先, 通知中心偏好检查用） */
    private static final int NOTIFY_PRIORITY = 2;

    /** 对话摘要最大长度（字符） */
    private static final int SUMMARY_MAX_LENGTH = 120;

    /** 通知中心门面（站内信推送） */
    private final ScrmNotificationCenterService notificationCenterService;

    /** 高意向推送配置（scrm.finance-lead-push.*） */
    private final ScrmFinanceLeadPushProperties pushProperties;

    /** 财税评分模型管理服务（按编码解析模型 ID） */
    private final ScrmLeadScoringModelService modelService;

    /** AI 对话记录数据访问层（取最近一次客户消息做对话摘要） */
    private final ScrmAiConversationRepository conversationRepository;

    /** 客户数据访问层（取客户名称/备注） */
    private final ScrmCustomerRepository customerRepository;

    /** 通知记录数据访问层（幂等去重查询） */
    private final ScrmNotificationRepository notificationRepository;

    /** JSON 解析器（解析 dimensionScores 意向理由） */
    private final ObjectMapper objectMapper;

    /**
     * 高意向线索推送入口: 等级命中 A_PLUS / SUPER_HOT / HOT 且归属财税模型时,
     * 经 NotificationCenter 推送销售并附对话摘要 + 意向理由。
     * <p>推送失败只记日志不抛出, 不阻断评分主流程。</p>
     *
     * @param score 刚计算/更新的评分记录
     */
    public void pushIfHot(ScrmLeadScoreEntity score) {
        if (score == null || score.getId() == null) {
            return;
        }
        if (!pushProperties.isEnabled()) {
            log.debug("[高意向推送] 总开关已关闭 (scrm.finance-lead-push.enabled=false), 跳过: scoreId={}",
                    score.getId());
            return;
        }
        String grade = score.getGrade();
        if (grade == null || !HOT_GRADES.contains(grade)) {
            return;
        }
        if (!matchesFinanceModel(score.getModelId())) {
            return;
        }
        if (alreadyPushed(score.getId())) {
            log.debug("[高意向推送] 该评分记录已推送过, 跳过去重: scoreId={}", score.getId());
            return;
        }
        try {
            ScrmNotificationSendDto dto = buildSendDto(score);
            notificationCenterService.sendNotification(dto);
            log.info("[高意向推送] 高意向线索已推送销售: scoreId={}, customerId={}, grade={}, reason={}",
                    score.getId(), score.getCustomerId(), grade, buildReason(score));
        } catch (Exception e) {
            log.warn("[高意向推送] NotificationCenter 推送失败 (不影响评分主流程): scoreId={}, grade={}, err={}",
                    score.getId(), grade, e.getMessage());
        }
    }

    /**
     * 判断评分记录是否归属财税模型（modelCode 配置为空时不限模型）。
     *
     * @param modelId 评分记录模型 ID
     * @return true 推送 / false 跳过
     */
    private boolean matchesFinanceModel(Long modelId) {
        String modelCode = pushProperties.getModelCode();
        if (modelCode == null || modelCode.isBlank()) {
            return true;
        }
        if (modelId == null) {
            return false;
        }
        try {
            ScrmLeadScoringModelEntity model = modelService.getModelByCode(modelCode);
            return model != null && modelId.equals(model.getId());
        } catch (ScrmException e) {
            log.warn("[高意向推送] 财税评分模型未找到, 按不匹配处理: modelCode={}, err={}",
                    modelCode, e.getMessage());
            return false;
        }
    }

    /**
     * 幂等去重: 该评分记录是否已推送过（同模板 + 同关联评分记录）。
     *
     * @param scoreId 评分记录 ID
     * @return true 已推送
     */
    private boolean alreadyPushed(Long scoreId) {
        try {
            return notificationRepository.existsByRelatedTypeAndRelatedIdAndTemplateCode(
                    RELATED_TYPE_LEAD_SCORE, scoreId.toString(), pushProperties.getTemplateCode());
        } catch (Exception e) {
            log.warn("[高意向推送] 去重查询失败, 按未推送处理: scoreId={}, err={}", scoreId, e.getMessage());
            return false;
        }
    }

    /**
     * 构建通知发送参数: 模板 + 接收人 + 变量（客户 / 等级 / 得分 / 意向理由 / 对话摘要 / 联系方式）。
     *
     * @param score 评分记录
     * @return 通知发送参数
     */
    private ScrmNotificationSendDto buildSendDto(ScrmLeadScoreEntity score) {
        ScrmNotificationSendDto dto = new ScrmNotificationSendDto();
        dto.setTemplateCode(pushProperties.getTemplateCode());
        dto.setRecipientType("USER");
        dto.setRecipients(List.of(pushProperties.getRecipient()));
        dto.setSenderId(SENDER_ID);
        dto.setSenderName("AI接待");
        dto.setPriority(NOTIFY_PRIORITY);
        dto.setRelatedType(RELATED_TYPE_LEAD_SCORE);
        dto.setRelatedId(score.getId().toString());
        Map<String, String> variables = new LinkedHashMap<>();
        variables.put("customerName", score.getCustomerName() != null ? score.getCustomerName()
                : "客户#" + score.getCustomerId());
        variables.put("grade", score.getGrade());
        variables.put("gradeLabel", score.getGradeLabel() != null ? score.getGradeLabel() : score.getGrade());
        variables.put("totalScore", score.getScorePercent() != null
                ? String.valueOf(score.getScorePercent()) : String.valueOf(score.getTotalScore()));
        variables.put("reason", buildReason(score));
        variables.put("conversationSummary", buildConversationSummary(score.getCustomerId()));
        variables.put("contact", buildContact(score.getCustomerId()));
        dto.setVariables(variables);
        return dto;
    }

    /**
     * 构建意向理由: 解析 dimensionScores JSON 逐维度拼装（意向理由可读）。
     *
     * @param score 评分记录
     * @return 理由文本（解析失败时返回等级说明兜底）
     */
    private String buildReason(ScrmLeadScoreEntity score) {
        String dimensionScores = score.getDimensionScores();
        if (dimensionScores == null || dimensionScores.isBlank()) {
            return "等级 " + score.getGrade() + ", 总分 " + score.getTotalScore();
        }
        try {
            List<Map<String, Object>> dims = objectMapper.readValue(
                    dimensionScores, new TypeReference<List<Map<String, Object>>>() {});
            if (dims == null || dims.isEmpty()) {
                return "等级 " + score.getGrade() + ", 总分 " + score.getTotalScore();
            }
            StringBuilder sb = new StringBuilder(dims.size() * 48);
            for (Map<String, Object> dim : dims) {
                if (sb.length() > 0) {
                    sb.append("; ");
                }
                sb.append(dim.getOrDefault("dimension", "?"))
                        .append(' ')
                        .append(dim.getOrDefault("score", 0))
                        .append('/')
                        .append(dim.getOrDefault("maxScore", "?"));
                Object details = dim.get("details");
                if (details != null && !details.toString().isBlank()) {
                    sb.append(" (").append(details).append(')');
                }
            }
            return sb.toString();
        } catch (Exception e) {
            log.warn("[高意向推送] 意向理由 JSON 解析失败, 使用等级兜底: scoreId={}, err={}",
                    score.getId(), e.getMessage());
            return "等级 " + score.getGrade() + ", 总分 " + score.getTotalScore();
        }
    }

    /**
     * 构建对话摘要: 取该客户最近一次 AI 对话的客户消息（截断）。
     *
     * @param customerId 客户 ID
     * @return 摘要文本（无对话记录返回固定提示）
     */
    private String buildConversationSummary(Long customerId) {
        try {
            ScrmAiConversationEntity latest = conversationRepository
                    .findFirstByCustomerIdOrderByCreatedAtDesc(customerId).orElse(null);
            if (latest == null || latest.getUserMessage() == null || latest.getUserMessage().isBlank()) {
                return "无 AI 接待对话记录";
            }
            String message = latest.getUserMessage().trim();
            if (message.length() > SUMMARY_MAX_LENGTH) {
                message = message.substring(0, SUMMARY_MAX_LENGTH) + "…";
            }
            return message;
        } catch (Exception e) {
            log.warn("[高意向推送] 对话摘要获取失败, 使用兜底文案: customerId={}, err={}",
                    customerId, e.getMessage());
            return "无 AI 接待对话记录";
        }
    }

    /**
     * 构建客户联系方式: 优先取客户备注（通常登记手机/微信）, 无则固定提示。
     * <p>客户实体无独立联系方式字段, 联系方式登记在备注或客户档案中。</p>
     *
     * @param customerId 客户 ID
     * @return 联系方式文本
     */
    private String buildContact(Long customerId) {
        try {
            ScrmCustomerEntity customer = customerRepository.findById(customerId).orElse(null);
            if (customer == null) {
                return "见客户档案";
            }
            if (customer.getRemark() != null && !customer.getRemark().isBlank()) {
                return customer.getRemark().trim();
            }
        } catch (Exception e) {
            log.warn("[高意向推送] 客户联系方式获取失败, 使用兜底文案: customerId={}, err={}",
                    customerId, e.getMessage());
        }
        return "见客户档案";
    }
}