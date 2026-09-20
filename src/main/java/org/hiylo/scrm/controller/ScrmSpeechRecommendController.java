/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmSpeechRecommendController.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hiylo.scrm.dto.ScrmSalesSpeechDto;
import org.hiylo.scrm.dto.ScrmSpeechFeedbackDto;
import org.hiylo.scrm.dto.ScrmSpeechRecommendRequestDto;
import org.hiylo.scrm.dto.ScrmSpeechScenarioDto;
import org.hiylo.scrm.entity.ScrmSalesSpeechEntity;
import org.hiylo.scrm.entity.ScrmSpeechRecommendationEntity;
import org.hiylo.scrm.entity.ScrmSpeechScenarioEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.rbac.annotation.RateLimit;
import org.hiylo.scrm.rbac.annotation.RequirePermission;
import org.hiylo.scrm.service.ScrmSpeechRecommendService;
import org.hiylo.scrm.common.OperationResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * SCRM 销售话术推荐引擎控制器。
 * <p>
 * 提供销售话术推荐引擎的全部接口: 场景管理 (CRUD + 启停), 话术库管理 (CRUD + 启停 + 验证 +
 * 变量渲染 + 使用统计 + 复制 + 热门查询), 推荐与反馈 (推荐 + 批量推荐 + 推荐历史 + 反馈回写),
 * 统计与建议 (话术统计 + 场景统计 + 推荐统计 + 高绩效话术 + 改进建议)。
 * 权限由 gateway-server 统一鉴权, {@code @RequirePermission} 作为端点权限元数据声明。
 * </p>
 *
 * @author Hsi Chu
 */
@Slf4j
@RestController
@RequestMapping("/scrm/speech-recommend")
@RequiredArgsConstructor
public class ScrmSpeechRecommendController {

    /** 话术推荐服务 */
    private final ScrmSpeechRecommendService scrmSpeechRecommendService;

    // ============================================================
    // 场景管理
    // ============================================================

    /**
     * 创建话术场景。
     *
     * @param dto 场景参数
     * @return 创建后的场景
     * @throws ScrmException 参数非法 / 编码重复
     */
    @RequirePermission(resource = "scrm_speech_recommend", action = "create")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/scenarios")
    public OperationResponse<ScrmSpeechScenarioEntity> createScenario(@Valid @RequestBody ScrmSpeechScenarioDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmSpeechRecommendService.createScenario(dto));
    }

    /**
     * 更新话术场景。
     *
     * @param id  场景 ID
     * @param dto 场景参数
     * @return 更新后的场景
     * @throws ScrmException 场景不存在 / 参数非法 / 编码重复
     */
    @RequirePermission(resource = "scrm_speech_recommend", action = "update")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PutMapping("/scenarios/{id}")
    public OperationResponse<ScrmSpeechScenarioEntity> updateScenario(@PathVariable Long id,
                                                                       @RequestBody ScrmSpeechScenarioDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmSpeechRecommendService.updateScenario(id, dto));
    }

    /**
     * 删除话术场景 (同时清理场景下话术与推荐记录)。
     *
     * @param id 场景 ID
     * @return 空响应
     * @throws ScrmException 场景不存在
     */
    @RequirePermission(resource = "scrm_speech_recommend", action = "delete")
    @DeleteMapping("/scenarios/{id}")
    public OperationResponse<Void> deleteScenario(@PathVariable Long id) throws ScrmException {
        scrmSpeechRecommendService.deleteScenario(id);
        return OperationResponse.build();
    }

    /**
     * 查询场景详情。
     *
     * @param id 场景 ID
     * @return 场景详情
     * @throws ScrmException 场景不存在
     */
    @RequirePermission(resource = "scrm_speech_recommend", action = "read")
    @GetMapping("/scenarios/{id}")
    public OperationResponse<ScrmSpeechScenarioEntity> getScenario(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmSpeechRecommendService.getScenario(id));
    }

    /**
     * 按编码查询场景。
     *
     * @param code 场景编码
     * @return 场景详情
     * @throws ScrmException 场景不存在
     */
    @RequirePermission(resource = "scrm_speech_recommend", action = "read")
    @GetMapping("/scenarios/code/{code}")
    public OperationResponse<ScrmSpeechScenarioEntity> getScenarioByCode(@PathVariable String code)
            throws ScrmException {
        return OperationResponse.build(scrmSpeechRecommendService.getScenarioByCode(code));
    }

    /**
     * 分页查询场景列表。
     *
     * @param scenarioCategory 场景类别过滤（可空）: GREETING/INQUIRY/PITCH/OBJECTION/CLOSING/FOLLOW_UP/
     *                          CROSS_SELL/UP_SELL/RETENTION/RECOVERY/APPOINTMENT/REFERRAL/THANK_YOU/APOLOGY
     * @param customerStage    客户阶段过滤（可空）: NEW/ACTIVE/AT_RISK/CHURNED/VIP/PROSPECT
     * @param enabled          启用状态过滤（可空）
     * @param keyword          场景名称关键字模糊匹配（可空）
     * @param page             页码（从 0 开始, 默认 0）
     * @param size             每页大小（默认 20）
     * @return 场景分页结果 (按 createTime DESC)
     */
    @RequirePermission(resource = "scrm_speech_recommend", action = "read")
    @GetMapping("/scenarios/list")
    public OperationResponse<Page<ScrmSpeechScenarioEntity>> listScenarios(
            @RequestParam(required = false) String scenarioCategory,
            @RequestParam(required = false) String customerStage,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "createTime"));
        return OperationResponse.build(scrmSpeechRecommendService.listScenarios(
                scenarioCategory, customerStage, enabled, keyword, pageable));
    }

    /**
     * 启用场景。
     *
     * @param id 场景 ID
     * @return 更新后的场景
     * @throws ScrmException 场景不存在
     */
    @RequirePermission(resource = "scrm_speech_recommend", action = "update")
    @PostMapping("/scenarios/{id}/enable")
    public OperationResponse<ScrmSpeechScenarioEntity> enableScenario(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmSpeechRecommendService.enableScenario(id));
    }

    /**
     * 禁用场景。
     *
     * @param id 场景 ID
     * @return 更新后的场景
     * @throws ScrmException 场景不存在
     */
    @RequirePermission(resource = "scrm_speech_recommend", action = "update")
    @PostMapping("/scenarios/{id}/disable")
    public OperationResponse<ScrmSpeechScenarioEntity> disableScenario(
            @PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmSpeechRecommendService.disableScenario(id));
    }

    // ============================================================
    // 话术管理
    // ============================================================

    /**
     * 创建话术。
     *
     * @param dto 话术参数
     * @return 创建后的话术
     * @throws ScrmException 参数非法 / 场景不存在
     */
    @RequirePermission(resource = "scrm_speech_recommend", action = "create")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/speeches")
    public OperationResponse<ScrmSalesSpeechEntity> createSpeech(@Valid @RequestBody ScrmSalesSpeechDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmSpeechRecommendService.createSpeech(dto));
    }

    /**
     * 更新话术。
     *
     * @param id  话术 ID
     * @param dto 话术参数
     * @return 更新后的话术
     * @throws ScrmException 话术不存在 / 参数非法
     */
    @RequirePermission(resource = "scrm_speech_recommend", action = "update")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PutMapping("/speeches/{id}")
    public OperationResponse<ScrmSalesSpeechEntity> updateSpeech(@PathVariable Long id,
                                                                   @RequestBody ScrmSalesSpeechDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmSpeechRecommendService.updateSpeech(id, dto));
    }

    /**
     * 删除话术。
     *
     * @param id 话术 ID
     * @return 空响应
     * @throws ScrmException 话术不存在
     */
    @RequirePermission(resource = "scrm_speech_recommend", action = "delete")
    @DeleteMapping("/speeches/{id}")
    public OperationResponse<Void> deleteSpeech(@PathVariable Long id) throws ScrmException {
        scrmSpeechRecommendService.deleteSpeech(id);
        return OperationResponse.build();
    }

    /**
     * 查询话术详情。
     *
     * @param id 话术 ID
     * @return 话术详情
     * @throws ScrmException 话术不存在
     */
    @RequirePermission(resource = "scrm_speech_recommend", action = "read")
    @GetMapping("/speeches/{id}")
    public OperationResponse<ScrmSalesSpeechEntity> getSpeech(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmSpeechRecommendService.getSpeech(id));
    }

    /**
     * 分页查询话术列表。
     *
     * @param scenarioId      场景 ID 过滤（可空）
     * @param speechType      话术类型过滤（可空）: TEXT/SCRIPT/QA/GUIDE/TEMPLATE
     * @param speechStyle     话术风格过滤（可空）: FORMAL/FRIENDLY/PROFESSIONAL/CASUAL/PERSUASIVE/EMPATHETIC
     * @param difficultyLevel 难度等级过滤（可空）: BEGINNER/INTERMEDIATE/ADVANCED/EXPERT
     * @param enabled         启用状态过滤（可空）
     * @param keyword         话术标题关键字模糊匹配（可空）
     * @param page            页码（从 0 开始, 默认 0）
     * @param size            每页大小（默认 20）
     * @return 话术分页结果 (按 createTime DESC)
     */
    @RequirePermission(resource = "scrm_speech_recommend", action = "read")
    @GetMapping("/speeches/list")
    public OperationResponse<Page<ScrmSalesSpeechEntity>> listSpeeches(
            @RequestParam(required = false) Long scenarioId,
            @RequestParam(required = false) String speechType,
            @RequestParam(required = false) String speechStyle,
            @RequestParam(required = false) String difficultyLevel,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "createTime"));
        return OperationResponse.build(scrmSpeechRecommendService.listSpeeches(
                scenarioId, speechType, speechStyle, difficultyLevel, enabled, keyword, pageable));
    }

    /**
     * 启用话术。
     *
     * @param id 话术 ID
     * @return 更新后的话术
     * @throws ScrmException 话术不存在
     */
    @RequirePermission(resource = "scrm_speech_recommend", action = "update")
    @PostMapping("/speeches/{id}/enable")
    public OperationResponse<ScrmSalesSpeechEntity> enableSpeech(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmSpeechRecommendService.enableSpeech(id));
    }

    /**
     * 禁用话术。
     *
     * @param id 话术 ID
     * @return 更新后的话术
     * @throws ScrmException 话术不存在
     */
    @RequirePermission(resource = "scrm_speech_recommend", action = "update")
    @PostMapping("/speeches/{id}/disable")
    public OperationResponse<ScrmSalesSpeechEntity> disableSpeech(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmSpeechRecommendService.disableSpeech(id));
    }

    /**
     * 验证话术 (标记为已验证)。
     *
     * @param id 话术 ID
     * @return 更新后的话术
     * @throws ScrmException 话术不存在
     */
    @RequirePermission(resource = "scrm_speech_recommend", action = "update")
    @PostMapping("/speeches/{id}/verify")
    public OperationResponse<ScrmSalesSpeechEntity> verifySpeech(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmSpeechRecommendService.verifySpeech(id));
    }

    /**
     * 渲染话术 (变量替换)。
     *
     * @param id        话术 ID
     * @param variables 变量 Map (key 为变量名, value 为变量值)
     * @return 渲染后的话术内容
     * @throws ScrmException 话术不存在
     */
    @RequirePermission(resource = "scrm_speech_recommend", action = "read")
    @PostMapping("/speeches/{id}/render")
    public OperationResponse<String> renderSpeech(@PathVariable Long id,
                                                   @RequestBody(required = false) Map<String, String> variables)
            throws ScrmException {
        return OperationResponse.build(scrmSpeechRecommendService.renderSpeech(id, variables));
    }

    /**
     * 更新话术使用统计 (使用次数 +1, 成功时成功次数 +1)。
     *
     * @param id      话术 ID
     * @param success 是否成功
     * @return 更新后的话术
     * @throws ScrmException 话术不存在
     */
    @RequirePermission(resource = "scrm_speech_recommend", action = "update")
    @RateLimit(capacity = 60, refillTokens = 60, refillPeriodSeconds = 60)
    @PostMapping("/speeches/{id}/usage")
    public OperationResponse<ScrmSalesSpeechEntity> incrementUsage(@PathVariable Long id,
                                                                    @RequestParam(
                                                                            defaultValue = "false") boolean success)
            throws ScrmException {
        return OperationResponse.build(scrmSpeechRecommendService.incrementUsage(id, success));
    }

    /**
     * 复制话术 (生成新话术, 标题使用 newTitle)。
     *
     * @param id       话术 ID
     * @param newTitle 新话术标题
     * @return 复制后的话术
     * @throws ScrmException 话术不存在 / 标题为空
     */
    @RequirePermission(resource = "scrm_speech_recommend", action = "create")
    @PostMapping("/speeches/{id}/copy")
    public OperationResponse<ScrmSalesSpeechEntity> copySpeech(@PathVariable Long id,
                                                                @RequestParam String newTitle)
            throws ScrmException {
        return OperationResponse.build(scrmSpeechRecommendService.copySpeech(id, newTitle));
    }

    /**
     * 查询场景下热门话术 (按 usageCount 降序, rating 降序)。
     *
     * @param scenarioId 场景 ID
     * @param limit      返回条数（默认 5）
     * @return 话术列表
     * @throws ScrmException 场景不存在
     */
    @RequirePermission(resource = "scrm_speech_recommend", action = "read")
    @GetMapping("/speeches/top/{scenarioId}")
    public OperationResponse<List<ScrmSalesSpeechEntity>> getTopSpeeches(@PathVariable Long scenarioId,
                                                                          @RequestParam(defaultValue = "5") int limit)
            throws ScrmException {
        return OperationResponse.build(scrmSpeechRecommendService.getTopSpeeches(scenarioId, limit));
    }

    // ============================================================
    // 推荐与反馈
    // ============================================================

    /**
     * 推荐话术 (匹配场景 → 筛选话术 → 评分排序 → 持久化推荐记录)。
     *
     * @param dto 推荐请求
     * @return 推荐记录
     * @throws ScrmException 场景不存在 / 无可用话术
     */
    @RequirePermission(resource = "scrm_speech_recommend", action = "read")
    @RateLimit(capacity = 60, refillTokens = 60, refillPeriodSeconds = 60)
    @PostMapping("/recommend")
    public OperationResponse<ScrmSpeechRecommendationEntity> recommend(
            @Valid @RequestBody ScrmSpeechRecommendRequestDto dto) throws ScrmException {
        return OperationResponse.build(scrmSpeechRecommendService.recommend(dto));
    }

    /**
     * 批量推荐话术。
     *
     * @param requests 推荐请求列表
     * @return 推荐记录列表
     * @throws ScrmException 单条推荐失败时抛出
     */
    @RequirePermission(resource = "scrm_speech_recommend", action = "read")
    @RateLimit(capacity = 20, refillTokens = 20, refillPeriodSeconds = 60)
    @PostMapping("/recommend/batch")
    public OperationResponse<List<ScrmSpeechRecommendationEntity>> batchRecommend(
            @RequestBody List<ScrmSpeechRecommendRequestDto> requests) throws ScrmException {
        return OperationResponse.build(scrmSpeechRecommendService.batchRecommend(requests));
    }

    /**
     * 查询推荐记录详情。
     *
     * @param id 推荐 ID
     * @return 推荐记录
     * @throws ScrmException 推荐记录不存在
     */
    @RequirePermission(resource = "scrm_speech_recommend", action = "read")
    @GetMapping("/recommend/{id}")
    public OperationResponse<ScrmSpeechRecommendationEntity> getRecommendation(@PathVariable Long id)
            throws ScrmException {
        return OperationResponse.build(scrmSpeechRecommendService.getRecommendation(id));
    }

    /**
     * 分页查询推荐记录列表。
     *
     * @param customerId 客户 ID 过滤（可空）
     * @param scenarioId 场景 ID 过滤（可空）
     * @param startTime  推荐时间起始 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @param endTime    推荐时间截止 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @param page       页码（从 0 开始, 默认 0）
     * @param size       每页大小（默认 20）
     * @return 推荐记录分页结果 (按 recommendedAt DESC)
     */
    @RequirePermission(resource = "scrm_speech_recommend", action = "read")
    @GetMapping("/recommend/list")
    public OperationResponse<Page<ScrmSpeechRecommendationEntity>> listRecommendations(
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) Long scenarioId,
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "recommendedAt"));
        return OperationResponse.build(scrmSpeechRecommendService.listRecommendations(
                customerId, scenarioId, startTime, endTime, pageable));
    }

    /**
     * 提供反馈 (更新推荐记录, 同步话术使用与反馈统计)。
     *
     * @param dto 反馈参数
     * @return 更新后的推荐记录
     * @throws ScrmException 推荐记录不存在 / 话术不存在 / 参数非法
     */
    @RequirePermission(resource = "scrm_speech_recommend", action = "update")
    @RateLimit(capacity = 60, refillTokens = 60, refillPeriodSeconds = 60)
    @PostMapping("/recommend/feedback")
    public OperationResponse<ScrmSpeechRecommendationEntity> provideFeedback(
            @Valid @RequestBody ScrmSpeechFeedbackDto dto) throws ScrmException {
        return OperationResponse.build(scrmSpeechRecommendService.provideFeedback(dto));
    }

    /**
     * 查询客户推荐历史。
     *
     * @param customerId 客户 ID
     * @param limit      返回条数（默认 10）
     * @return 推荐记录列表
     */
    @RequirePermission(resource = "scrm_speech_recommend", action = "read")
    @GetMapping("/recommend/history/{customerId}")
    public OperationResponse<List<ScrmSpeechRecommendationEntity>> getRecommendationHistory(
            @PathVariable Long customerId,
            @RequestParam(defaultValue = "10") int limit) {
        return OperationResponse.build(scrmSpeechRecommendService.getRecommendationHistory(customerId, limit));
    }

    // ============================================================
    // 统计与建议
    // ============================================================

    /**
     * 话术统计概览: 总数 / 各类型 / 使用率 / 成功率。
     *
     * @param startTime 创建时间起始 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @param endTime   创建时间截止 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @return 统计结果 Map
     */
    @RequirePermission(resource = "scrm_speech_recommend", action = "read")
    @GetMapping("/stats/overview")
    public OperationResponse<Map<String, Object>> getSpeechStats(
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime) {
        return OperationResponse.build(scrmSpeechRecommendService.getSpeechStats(startTime, endTime));
    }

    /**
     * 场景统计: 各场景使用 / 成功率。
     *
     * @param startTime 创建时间起始 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @param endTime   创建时间截止 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @return 统计结果 Map
     */
    @RequirePermission(resource = "scrm_speech_recommend", action = "read")
    @GetMapping("/stats/scenarios")
    public OperationResponse<Map<String, Object>> getScenarioStats(
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime) {
        return OperationResponse.build(scrmSpeechRecommendService.getScenarioStats(startTime, endTime));
    }

    /**
     * 推荐统计: 推荐数 / 采纳率 / 反馈率。
     *
     * @param startTime 推荐时间起始 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @param endTime   推荐时间截止 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @return 统计结果 Map
     */
    @RequirePermission(resource = "scrm_speech_recommend", action = "read")
    @GetMapping("/stats/recommendations")
    public OperationResponse<Map<String, Object>> getRecommendationStats(
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime) {
        return OperationResponse.build(scrmSpeechRecommendService.getRecommendationStats(startTime, endTime));
    }

    /**
     * 高绩效话术 (按 usageCount * successRate 综合排序)。
     *
     * @param limit 返回条数（默认 5）
     * @return 话术列表
     */
    @RequirePermission(resource = "scrm_speech_recommend", action = "read")
    @GetMapping("/stats/top-performing")
    public OperationResponse<List<ScrmSalesSpeechEntity>> getTopPerformingSpeeches(
            @RequestParam(defaultValue = "5") int limit) {
        return OperationResponse.build(scrmSpeechRecommendService.getTopPerformingSpeeches(limit));
    }

    /**
     * 改进建议 (低评分话术)。
     *
     * @return 改进建议 Map 列表
     */
    @RequirePermission(resource = "scrm_speech_recommend", action = "read")
    @GetMapping("/stats/suggestions")
    public OperationResponse<List<Map<String, Object>>> getImprovementSuggestions() {
        return OperationResponse.build(scrmSpeechRecommendService.getImprovementSuggestions());
    }
}
