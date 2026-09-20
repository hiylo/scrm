/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmSpeechRecommendService.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */

package org.hiylo.scrm.service;


import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import org.hiylo.scrm.dto.ScrmSalesSpeechDto;
import org.hiylo.scrm.dto.ScrmSpeechFeedbackDto;
import org.hiylo.scrm.dto.ScrmSpeechRecommendRequestDto;
import org.hiylo.scrm.dto.ScrmSpeechScenarioDto;
import org.hiylo.scrm.entity.ScrmSalesSpeechEntity;
import org.hiylo.scrm.entity.ScrmSpeechRecommendationEntity;
import org.hiylo.scrm.entity.ScrmSpeechScenarioEntity;
import org.hiylo.scrm.exception.ScrmException;

/**
 * SCRM 销售话术推荐引擎服务门面。
 * <p>
 * 门面模式: 按子域拆分后保留全部 {@code public} 方法签名, 方法体委托给兄弟服务:
 * 场景管理 ({@link ScrmSpeechScenarioService})、话术管理 ({@link ScrmSpeechService})、
 * 推荐匹配 ({@link ScrmSpeechRecommendMatchService})、统计 ({@link ScrmSpeechRecommendStatsService})。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */

@Service
@RequiredArgsConstructor
public class ScrmSpeechRecommendService {

    /** 场景管理兄弟服务 */
    private final ScrmSpeechScenarioService scenarioService;

    /** 话术管理兄弟服务 */
    private final ScrmSpeechService speechService;

    /** 推荐匹配兄弟服务 */
    private final ScrmSpeechRecommendMatchService matchService;

    /** 统计与建议兄弟服务 */
    private final ScrmSpeechRecommendStatsService statsService;

    /**
     * 创建话术场景。
     * <p>校验 scenarioCategory 合法性与 scenarioCode 唯一性后写入归属账号 ID 持久化,
     * priority / enabled 缺省时填默认值, 统计字段初始化为 0。</p>
     *
     * @param dto 场景参数
     * @return 创建后的场景
     * @throws ScrmException 参数非法 / 编码重复
     */
    public ScrmSpeechScenarioEntity createScenario(ScrmSpeechScenarioDto dto) throws ScrmException {
        return scenarioService.createScenario(dto);
    }

    /**
     * 更新话术场景（字段非空才覆盖）。
     *
     * @param id  场景 ID
     * @param dto 场景参数
     * @return 更新后的场景
     * @throws ScrmException 场景不存在 / 参数非法 / 编码重复
     */
    public ScrmSpeechScenarioEntity updateScenario(Long id, ScrmSpeechScenarioDto dto) throws ScrmException {
        return scenarioService.updateScenario(id, dto);
    }

    /**
     * 删除话术场景 (同时清理场景下话术与推荐记录)。
     *
     * @param id 场景 ID
     * @throws ScrmException 场景不存在
     */
    public void deleteScenario(Long id) throws ScrmException {
        scenarioService.deleteScenario(id);
    }

    /**
     * 查询场景详情。
     *
     * @param id 场景 ID
     * @return 场景实体
     * @throws ScrmException 场景不存在
     */
    public ScrmSpeechScenarioEntity getScenario(Long id) throws ScrmException {
        return scenarioService.getScenario(id);
    }

    /**
     * 按编码查询场景。
     *
     * @param code 场景编码
     * @return 场景实体
     * @throws ScrmException 场景不存在
     */
    public ScrmSpeechScenarioEntity getScenarioByCode(String code) throws ScrmException {
        return scenarioService.getScenarioByCode(code);
    }

    /**
     * 分页查询场景, 支持按场景类别 / 客户阶段 / 启用状态 / 关键字过滤。
     *
     * @param scenarioCategory 场景类别过滤（可空）
     * @param customerStage    客户阶段过滤（可空）
     * @param enabled          启用状态过滤（可空）
     * @param keyword          场景名称关键字模糊匹配（可空）
     * @param pageable         分页参数
     * @return 场景分页结果 (按 createTime DESC)
     */
    public Page<ScrmSpeechScenarioEntity> listScenarios(String scenarioCategory, String customerStage,
                                                         Boolean enabled, String keyword, Pageable pageable) {
        return scenarioService.listScenarios(scenarioCategory, customerStage, enabled, keyword, pageable);
    }

    /**
     * 启用场景。
     *
     * @param id 场景 ID
     * @return 更新后的场景
     * @throws ScrmException 场景不存在
     */
    public ScrmSpeechScenarioEntity enableScenario(Long id) throws ScrmException {
        return scenarioService.enableScenario(id);
    }

    /**
     * 禁用场景。
     *
     * @param id 场景 ID
     * @return 更新后的场景
     * @throws ScrmException 场景不存在
     */
    public ScrmSpeechScenarioEntity disableScenario(Long id) throws ScrmException {
        return scenarioService.disableScenario(id);
    }

    /**
     * 更新场景统计 (话术数 / 平均评分 / 使用次数 / 成功率)。
     *
     * @param id 场景 ID
     * @return 更新后的场景
     * @throws ScrmException 场景不存在
     */
    public ScrmSpeechScenarioEntity updateScenarioStats(Long id) throws ScrmException {
        return scenarioService.updateScenarioStats(id);
    }

    /**
     * 创建话术。
     * <p>校验场景存在性与字段合法性后写入归属账号 ID 持久化,
     * speechType / difficultyLevel / versionNo / 统计字段 / enabled 缺省时填默认值。</p>
     *
     * @param dto 话术参数
     * @return 创建后的话术
     * @throws ScrmException 参数非法 / 场景不存在
     */
    public ScrmSalesSpeechEntity createSpeech(ScrmSalesSpeechDto dto) throws ScrmException {
        return speechService.createSpeech(dto);
    }

    /**
     * 更新话术（字段非空才覆盖）。
     *
     * @param id  话术 ID
     * @param dto 话术参数
     * @return 更新后的话术
     * @throws ScrmException 话术不存在 / 参数非法
     */
    public ScrmSalesSpeechEntity updateSpeech(Long id, ScrmSalesSpeechDto dto) throws ScrmException {
        return speechService.updateSpeech(id, dto);
    }

    /**
     * 删除话术。
     *
     * @param id 话术 ID
     * @throws ScrmException 话术不存在
     */
    public void deleteSpeech(Long id) throws ScrmException {
        speechService.deleteSpeech(id);
    }

    /**
     * 查询话术详情。
     *
     * @param id 话术 ID
     * @return 话术实体
     * @throws ScrmException 话术不存在
     */
    public ScrmSalesSpeechEntity getSpeech(Long id) throws ScrmException {
        return speechService.getSpeech(id);
    }

    /**
     * 分页查询话术, 支持按场景 / 类型 / 风格 / 难度 / 启用状态 / 关键字过滤。
     *
     * @param scenarioId      场景 ID 过滤（可空）
     * @param speechType      话术类型过滤（可空）
     * @param speechStyle     话术风格过滤（可空）
     * @param difficultyLevel 难度等级过滤（可空）
     * @param enabled         启用状态过滤（可空）
     * @param keyword         话术标题关键字模糊匹配（可空）
     * @param pageable        分页参数
     * @return 话术分页结果 (按 createTime DESC)
     */
    public Page<ScrmSalesSpeechEntity> listSpeeches(Long scenarioId, String speechType, String speechStyle,
                                                    String difficultyLevel, Boolean enabled, String keyword,
                                                    Pageable pageable) {
        return speechService.listSpeeches(scenarioId, speechType, speechStyle, difficultyLevel, enabled, keyword, pageable);
    }

    /**
     * 启用话术。
     *
     * @param id 话术 ID
     * @return 更新后的话术
     * @throws ScrmException 话术不存在
     */
    public ScrmSalesSpeechEntity enableSpeech(Long id) throws ScrmException {
        return speechService.enableSpeech(id);
    }

    /**
     * 禁用话术。
     *
     * @param id 话术 ID
     * @return 更新后的话术
     * @throws ScrmException 话术不存在
     */
    public ScrmSalesSpeechEntity disableSpeech(Long id) throws ScrmException {
        return speechService.disableSpeech(id);
    }

    /**
     * 验证话术 (标记为已验证)。
     *
     * @param id 话术 ID
     * @return 更新后的话术
     * @throws ScrmException 话术不存在
     */
    public ScrmSalesSpeechEntity verifySpeech(Long id) throws ScrmException {
        return speechService.verifySpeech(id);
    }

    /**
     * 渲染话术 (变量替换)。
     * <p>支持话术变量 ({customerName} / {productName} / {price} 等) 与默认变量 ({time})。
     * variables 入参 key 为变量名 (不含大括号), value 为变量值。</p>
     *
     * @param id        话术 ID
     * @param variables 变量 Map (key 为变量名, value 为变量值)
     * @return 渲染后的话术内容
     * @throws ScrmException 话术不存在
     */
    public String renderSpeech(Long id, Map<String, String> variables) throws ScrmException {
        return speechService.renderSpeech(id, variables);
    }

    /**
     * 更新话术使用统计 (使用次数 +1, 成功时成功次数 +1, 重算评分)。
     * <p>评分公式: 评分 = 0.5 * (成功率 * 5) + 0.3 * (反馈好评率 * 5) + 0.2 * (使用次数归一化)。
     * 使用次数归一化: min(usageCount / 100, 1) * 5。直接 SQL 增量更新避免乐观锁冲突。</p>
     *
     * @param id      话术 ID
     * @param success 是否成功
     * @return 更新后的话术
     * @throws ScrmException 话术不存在
     */
    public ScrmSalesSpeechEntity incrementUsage(Long id, boolean success) throws ScrmException {
        return speechService.incrementUsage(id, success);
    }

    /**
     * 复制话术 (生成新话术, 标题使用 newTitle, 统计字段归零)。
     *
     * @param id       话术 ID
     * @param newTitle 新话术标题
     * @return 复制后的话术
     * @throws ScrmException 话术不存在 / 标题为空
     */
    public ScrmSalesSpeechEntity copySpeech(Long id, String newTitle) throws ScrmException {
        return speechService.copySpeech(id, newTitle);
    }

    /**
     * 查询场景下热门话术 (按 usageCount 降序, rating 降序)。
     *
     * @param scenarioId 场景 ID
     * @param limit      返回条数
     * @return 话术列表
     * @throws ScrmException 场景不存在
     */
    public List<ScrmSalesSpeechEntity> getTopSpeeches(Long scenarioId, int limit) throws ScrmException {
        return speechService.getTopSpeeches(scenarioId, limit);
    }

    /**
     * 推荐话术 (匹配场景 → 筛选话术 → 评分排序 → 持久化推荐记录)。
     * <p>完整实现: 按场景编码定位场景, 加载场景下启用话术, 调用 {@link #rankSpeeches}
     * 评分排序取 Top N, 构建匹配上下文与匹配原因, 持久化推荐记录。</p>
     *
     * @param requestDto 推荐请求
     * @return 推荐记录
     * @throws ScrmException 场景不存在 / 无可用话术
     */
    public ScrmSpeechRecommendationEntity recommend(
            ScrmSpeechRecommendRequestDto requestDto) throws ScrmException {
        return matchService.recommend(requestDto);
    }

    /**
     * 批量推荐话术。
     *
     * @param requests 推荐请求列表
     * @return 推荐记录列表
     * @throws ScrmException 单条推荐失败时抛出
     */
    public List<ScrmSpeechRecommendationEntity> batchRecommend(List<ScrmSpeechRecommendRequestDto> requests)
            throws ScrmException {
        return matchService.batchRecommend(requests);
    }

    /**
     * 查询推荐记录详情。
     *
     * @param id 推荐 ID
     * @return 推荐记录
     * @throws ScrmException 推荐记录不存在
     */
    public ScrmSpeechRecommendationEntity getRecommendation(Long id) throws ScrmException {
        return matchService.getRecommendation(id);
    }

    /**
     * 分页查询推荐记录, 支持按客户 / 场景 / 时间范围过滤。
     *
     * @param customerId 客户 ID 过滤（可空）
     * @param scenarioId 场景 ID 过滤（可空）
     * @param startTime  推荐时间起始 (含, 可空)
     * @param endTime    推荐时间截止 (含, 可空)
     * @param pageable   分页参数
     * @return 推荐记录分页结果 (按 recommendedAt DESC)
     */
    public Page<ScrmSpeechRecommendationEntity> listRecommendations(Long customerId, Long scenarioId,
                                                                     LocalDateTime startTime, LocalDateTime endTime,
                                                                     Pageable pageable) {
        return matchService.listRecommendations(customerId, scenarioId, startTime, endTime, pageable);
    }

    /**
     * 提供反馈 (更新推荐记录, 同步话术使用与反馈统计)。
     * <p>更新推荐记录的 selectedSpeechId / feedback / outcome / comment / usedAt;
     * 调用 incrementUsage 累计话术使用次数与成功次数;
     * 调用 incrementFeedback 累计话术反馈统计。</p>
     *
     * @param feedbackDto 反馈参数
     * @return 更新后的推荐记录
     * @throws ScrmException 推荐记录不存在 / 话术不存在 / 参数非法
     */
    public ScrmSpeechRecommendationEntity provideFeedback(ScrmSpeechFeedbackDto feedbackDto) throws ScrmException {
        return matchService.provideFeedback(feedbackDto);
    }

    /**
     * 查询客户推荐历史 (按 recommendedAt 降序)。
     *
     * @param customerId 客户 ID
     * @param limit      返回条数
     * @return 推荐记录列表
     */
    public List<ScrmSpeechRecommendationEntity> getRecommendationHistory(Long customerId, int limit) {
        return matchService.getRecommendationHistory(customerId, limit);
    }

    /**
     * 匹配场景 (基于上下文条件)。
     * <p>若上下文含 scenarioCode, 按编码加载; 否则遍历启用场景, 解析 triggerConditions JSON,
     * 按 customerStage / channel / productCategory / timeOfDay / sentiment 匹配, 返回最高分场景。</p>
     *
     * @param context 匹配上下文 (可含 scenarioCode / customerStage / channel / productCategory / timeOfDay / sentiment)
     * @return 匹配的场景
     * @throws ScrmException 无匹配场景
     */
    public ScrmSpeechScenarioEntity matchScenario(Map<String, Object> context) throws ScrmException {
        return matchService.matchScenario(context);
    }

    /**
     * 话术评分 (关键词匹配 + 风格匹配 + 评分权重 + 成功率权重 + 推荐验证加分)。
     * <p>评分项:
     * <ul>
     *   <li>关键词匹配: 话术关键词与上下文 previousInteraction / productCategory 比对, 每命中一个 +10</li>
     *   <li>风格匹配: 由上下文 sentiment 推导偏好风格, 匹配 +15</li>
     *   <li>评分权重: rating * 2</li>
     *   <li>成功率权重: usageCount>0 时 successCount/usageCount * 20</li>
     *   <li>推荐加分: isRecommended +10</li>
     *   <li>验证加分: isVerified +5</li>
     * </ul>
     * </p>
     *
     * @param speechId 话术 ID
     * @param context   匹配上下文
     * @return 评分 (含明细 Map 可通过 {@link #scoreSpeechInternal} 获取)
     * @throws ScrmException 话术不存在
     */
    public double scoreSpeech(Long speechId, Map<String, Object> context) throws ScrmException {
        return matchService.scoreSpeech(speechId, context);
    }

    /**
     * 排序话术 (按评分降序取 Top limit)。
     *
     * @param speechIds 话术 ID 列表
     * @param context   匹配上下文
     * @param limit     返回条数
     * @return 排序后的话术 ID 列表
     */
    public List<Long> rankSpeeches(List<Long> speechIds, Map<String, Object> context, int limit) {
        return matchService.rankSpeeches(speechIds, context, limit);
    }

    /**
     * 话术统计: 总数 / 各类型 / 使用率 / 成功率。
     *
     * @param startTime 创建时间起始 (可空)
     * @param endTime   创建时间截止 (可空)
     * @return 统计结果 Map
     */
    public Map<String, Object> getSpeechStats(LocalDateTime startTime, LocalDateTime endTime) {
        return statsService.getSpeechStats(startTime, endTime);
    }

    /**
     * 场景统计: 各场景使用 / 成功率。
     *
     * @param startTime 创建时间起始 (可空)
     * @param endTime   创建时间截止 (可空)
     * @return 统计结果 Map
     */
    public Map<String, Object> getScenarioStats(LocalDateTime startTime, LocalDateTime endTime) {
        return statsService.getScenarioStats(startTime, endTime);
    }

    /**
     * 推荐统计: 推荐数 / 采纳率 / 反馈率。
     *
     * @param startTime 推荐时间起始 (可空)
     * @param endTime   推荐时间截止 (可空)
     * @return 统计结果 Map
     */
    public Map<String, Object> getRecommendationStats(LocalDateTime startTime, LocalDateTime endTime) {
        return statsService.getRecommendationStats(startTime, endTime);
    }

    /**
     * 高绩效话术 (按 usageCount * successRate 综合排序)。
     *
     * @param limit 返回条数
     * @return 话术列表
     */
    public List<ScrmSalesSpeechEntity> getTopPerformingSpeeches(int limit) {
        return statsService.getTopPerformingSpeeches(limit);
    }

    /**
     * 改进建议 (低评分话术)。
     * <p>筛选条件: usageCount ≥ 5 且评分 < 3.0 的话术, 按评分升序返回。</p>
     *
     * @return 改进建议 Map 列表
     */
    public List<Map<String, Object>> getImprovementSuggestions() {
        return statsService.getImprovementSuggestions();
    }

}
