/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmVocService.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.service;

import lombok.RequiredArgsConstructor;

import org.hiylo.scrm.dto.ScrmVocAnalysisDto;
import org.hiylo.scrm.dto.ScrmVocInsightDto;
import org.hiylo.scrm.dto.ScrmVocResponseDto;
import org.hiylo.scrm.dto.ScrmVocTopicDto;
import org.hiylo.scrm.dto.ScrmVocVoiceDto;
import org.hiylo.scrm.entity.ScrmVocInsightEntity;
import org.hiylo.scrm.entity.ScrmVocTopicEntity;
import org.hiylo.scrm.entity.ScrmVocVoiceEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * SCRM 客户之声 (VoC) 门面服务。
 * <p>
 * 对外保留原有全部 public 方法签名不变, 内部按子域委托给
 * {@link ScrmVocVoiceService} / {@link ScrmVocTopicService} / {@link ScrmVocInsightService} /
 * {@link ScrmVocStatsService} 四个兄弟服务实现, 自身仅负责聚合与转发。
 * </p>
 *
 * @author Hsi Chu
 */
@Service
@RequiredArgsConstructor
public class ScrmVocService {

    /** 声音子域服务 */
    private final ScrmVocVoiceService voiceService;
    /** 主题子域服务 */
    private final ScrmVocTopicService topicService;
    /** 洞察子域服务 */
    private final ScrmVocInsightService insightService;
    /** 统计子域服务 */
    private final ScrmVocStatsService statsService;

    // ============================================================
    // 声音管理 (委托声音子域服务)
    // ============================================================

    /** 创建客户声音。 */
    public ScrmVocVoiceEntity createVoice(ScrmVocVoiceDto dto) throws ScrmException {
        return voiceService.createVoice(dto);
    }

    /** 更新客户声音。 */
    public ScrmVocVoiceEntity updateVoice(Long id, ScrmVocVoiceDto dto) throws ScrmException {
        return voiceService.updateVoice(id, dto);
    }

    /** 删除客户声音。 */
    public void deleteVoice(Long id) throws ScrmException {
        voiceService.deleteVoice(id);
    }

    /** 查询声音详情。 */
    public ScrmVocVoiceEntity getVoice(Long id) throws ScrmException {
        return voiceService.getVoice(id);
    }

    /** 按声音编号查询声音。 */
    public ScrmVocVoiceEntity getVoiceByNo(String voiceNo) throws ScrmException {
        return voiceService.getVoiceByNo(voiceNo);
    }

    /** 分页查询声音。 */
    public Page<ScrmVocVoiceEntity> listVoices(String source, String voiceType, String sentiment, String priority,
                                                String status, String category, Long customerId, String keyword,
                                                LocalDateTime startTime, LocalDateTime endTime, Pageable pageable) {
        return voiceService.listVoices(source, voiceType, sentiment, priority, status, category, customerId, keyword,
                startTime, endTime, pageable);
    }

    /** 按客户分页查询声音。 */
    public Page<ScrmVocVoiceEntity> getVoicesByCustomer(Long customerId, Pageable pageable) {
        return voiceService.getVoicesByCustomer(customerId, pageable);
    }

    /** 按主题分页查询声音。 */
    public Page<ScrmVocVoiceEntity> getVoicesByTopic(Long topicId, Pageable pageable) throws ScrmException {
        return voiceService.getVoicesByTopic(topicId, pageable);
    }

    /** 分析单条声音。 */
    public ScrmVocVoiceEntity analyzeVoice(ScrmVocAnalysisDto dto) throws ScrmException {
        return voiceService.analyzeVoice(dto);
    }

    /** 批量分析声音。 */
    public Map<String, Integer> batchAnalyze(List<Long> voiceIds) {
        return voiceService.batchAnalyze(voiceIds);
    }

    /** 分配声音处理人。 */
    public ScrmVocVoiceEntity assignVoice(Long id, String assigneeId, String department) throws ScrmException {
        return voiceService.assignVoice(id, assigneeId, department);
    }

    /** 解决声音。 */
    public ScrmVocVoiceEntity resolveVoice(Long id, String resolution, String resolvedBy) throws ScrmException {
        return voiceService.resolveVoice(id, resolution, resolvedBy);
    }

    /** 关闭声音。 */
    public ScrmVocVoiceEntity closeVoice(Long id, Integer satisfaction, String closedBy) throws ScrmException {
        return voiceService.closeVoice(id, satisfaction, closedBy);
    }

    /** 归档声音。 */
    public ScrmVocVoiceEntity archiveVoice(Long id, String archivedBy) throws ScrmException {
        return voiceService.archiveVoice(id, archivedBy);
    }

    /** 验证声音。 */
    public ScrmVocVoiceEntity verifyVoice(Long id, String verifiedBy) throws ScrmException {
        return voiceService.verifyVoice(id, verifiedBy);
    }

    /** 回复声音。 */
    public ScrmVocVoiceEntity respondToVoice(ScrmVocResponseDto dto) throws ScrmException {
        return voiceService.respondToVoice(dto);
    }

    /** 查询新声音。 */
    public Page<ScrmVocVoiceEntity> getNewVoices(Pageable pageable) {
        return voiceService.getNewVoices(pageable);
    }

    /** 查询待处理声音。 */
    public Page<ScrmVocVoiceEntity> getPendingVoices(Pageable pageable) {
        return voiceService.getPendingVoices(pageable);
    }

    /** 查询紧急声音。 */
    public Page<ScrmVocVoiceEntity> getUrgentVoices(Pageable pageable) {
        return voiceService.getUrgentVoices(pageable);
    }

    /** 查询负面声音。 */
    public Page<ScrmVocVoiceEntity> getNegativeVoices(Pageable pageable) {
        return voiceService.getNegativeVoices(pageable);
    }

    /** 查询未解决声音。 */
    public Page<ScrmVocVoiceEntity> getUnresolvedVoices(Pageable pageable) {
        return voiceService.getUnresolvedVoices(pageable);
    }

    /** 客户声音时间线。 */
    public List<ScrmVocVoiceEntity> getVoiceTimeline(Long customerId) {
        return voiceService.getVoiceTimeline(customerId);
    }

    /** 查询相似声音。 */
    public List<ScrmVocVoiceEntity> getSimilarVoices(Long voiceId) throws ScrmException {
        return voiceService.getSimilarVoices(voiceId);
    }

    // ============================================================
    // 主题管理 (委托主题子域服务)
    // ============================================================

    /** 创建主题。 */
    public ScrmVocTopicEntity createTopic(ScrmVocTopicDto dto) throws ScrmException {
        return topicService.createTopic(dto);
    }

    /** 更新主题。 */
    public ScrmVocTopicEntity updateTopic(Long id, ScrmVocTopicDto dto) throws ScrmException {
        return topicService.updateTopic(id, dto);
    }

    /** 删除主题。 */
    public void deleteTopic(Long id) throws ScrmException {
        topicService.deleteTopic(id);
    }

    /** 查询主题详情。 */
    public ScrmVocTopicEntity getTopic(Long id) throws ScrmException {
        return topicService.getTopic(id);
    }

    /** 按主题编码查询主题。 */
    public ScrmVocTopicEntity getTopicByCode(String code) throws ScrmException {
        return topicService.getTopicByCode(code);
    }

    /** 分页查询主题。 */
    public Page<ScrmVocTopicEntity> listTopics(String category, Boolean enabled, String keyword, Pageable pageable) {
        return topicService.listTopics(category, enabled, keyword, pageable);
    }

    /** 查询主题树。 */
    public List<Map<String, Object>> getTopicTree() {
        return topicService.getTopicTree();
    }

    /** 查询子主题。 */
    public List<ScrmVocTopicEntity> getChildTopics(Long parentId) {
        return topicService.getChildTopics(parentId);
    }

    /** 将主题分配给声音。 */
    public ScrmVocVoiceEntity assignTopicToVoice(Long voiceId, Long topicId) throws ScrmException {
        return voiceService.assignTopicToVoice(voiceId, topicId);
    }

    /** 更新主题统计。 */
    public ScrmVocTopicEntity updateTopicStats(Long id) throws ScrmException {
        return topicService.updateTopicStats(id);
    }

    /** 主题识别。 */
    public List<ScrmVocTopicEntity> identifyTopics(Long voiceId) throws ScrmException {
        return topicService.identifyTopics(voiceId);
    }

    /** 查询热点主题。 */
    public List<ScrmVocTopicEntity> getHotTopics(int limit) {
        return topicService.getHotTopics(limit);
    }

    /** 查询新兴主题。 */
    public List<ScrmVocTopicEntity> getEmergingTopics(int limit) {
        return topicService.getEmergingTopics(limit);
    }

    /** 主题趋势。 */
    public Map<String, Object> getTopicTrend(Long topicId, int months) throws ScrmException {
        return topicService.getTopicTrend(topicId, months);
    }

    /** 主题情感分布。 */
    public Map<String, Object> getTopicSentiment(Long topicId) throws ScrmException {
        return topicService.getTopicSentiment(topicId);
    }

    /** 合并主题。 */
    public ScrmVocTopicEntity mergeTopics(Long sourceId, Long targetId) throws ScrmException {
        return topicService.mergeTopics(sourceId, targetId);
    }

    // ============================================================
    // 洞察管理 (委托洞察子域服务)
    // ============================================================

    /** 创建洞察。 */
    public ScrmVocInsightEntity createInsight(ScrmVocInsightDto dto) throws ScrmException {
        return insightService.createInsight(dto);
    }

    /** 更新洞察。 */
    public ScrmVocInsightEntity updateInsight(Long id, ScrmVocInsightDto dto) throws ScrmException {
        return insightService.updateInsight(id, dto);
    }

    /** 删除洞察。 */
    public void deleteInsight(Long id) throws ScrmException {
        insightService.deleteInsight(id);
    }

    /** 查询洞察详情。 */
    public ScrmVocInsightEntity getInsight(Long id) throws ScrmException {
        return insightService.getInsight(id);
    }

    /** 分页查询洞察。 */
    public Page<ScrmVocInsightEntity> listInsights(String insightType, String status, String impactLevel,
                                                    String priority, String keyword, Pageable pageable) {
        return insightService.listInsights(insightType, status, impactLevel, priority, keyword, pageable);
    }

    /** 发布洞察。 */
    public ScrmVocInsightEntity publishInsight(Long id, String publishedBy) throws ScrmException {
        return insightService.publishInsight(id, publishedBy);
    }

    /** 归档洞察。 */
    public ScrmVocInsightEntity archiveInsight(Long id) throws ScrmException {
        return insightService.archiveInsight(id);
    }

    /** 分享洞察。 */
    public ScrmVocInsightEntity shareInsight(Long id, String sharedWith) throws ScrmException {
        return insightService.shareInsight(id, sharedWith);
    }

    /** 生成洞察。 */
    public List<ScrmVocInsightEntity> generateInsights(Long topicId, String period) throws ScrmException {
        return insightService.generateInsights(topicId, period);
    }

    /** 自动生成洞察。 */
    public List<ScrmVocInsightEntity> autoGenerateInsights(String period) {
        return insightService.autoGenerateInsights(period);
    }

    /** 按主题查询洞察。 */
    public List<ScrmVocInsightEntity> getInsightsByTopic(Long topicId) {
        return insightService.getInsightsByTopic(topicId);
    }

    /** 查询可行动洞察。 */
    public Page<ScrmVocInsightEntity> getActionableInsights(Pageable pageable) {
        return insightService.getActionableInsights(pageable);
    }

    /** 添加洞察反馈。 */
    public ScrmVocInsightEntity addFeedback(Long id, int rating, String comment) throws ScrmException {
        return insightService.addFeedback(id, rating, comment);
    }

    /** 查询 Top 洞察。 */
    public List<ScrmVocInsightEntity> getTopInsights(int limit) {
        return insightService.getTopInsights(limit);
    }

    // ============================================================
    // 统计 (委托统计子域服务)
    // ============================================================

    /** VoC 统计。 */
    public Map<String, Object> getVocStats(LocalDateTime startTime, LocalDateTime endTime) {
        return statsService.getVocStats(startTime, endTime);
    }

    /** 情感分布。 */
    public Map<String, Object> getSentimentDistribution(LocalDateTime startTime, LocalDateTime endTime) {
        return statsService.getSentimentDistribution(startTime, endTime);
    }

    /** 来源渠道分布。 */
    public Map<String, Object> getSourceDistribution(LocalDateTime startTime, LocalDateTime endTime) {
        return statsService.getSourceDistribution(startTime, endTime);
    }

    /** 分类分布。 */
    public Map<String, Object> getCategoryDistribution(LocalDateTime startTime, LocalDateTime endTime) {
        return statsService.getCategoryDistribution(startTime, endTime);
    }

    /** 情感趋势。 */
    public Map<String, Object> getSentimentTrend(int months) {
        return statsService.getSentimentTrend(months);
    }

    /** 解决统计。 */
    public Map<String, Object> getResolutionStats(LocalDateTime startTime, LocalDateTime endTime) {
        return statsService.getResolutionStats(startTime, endTime);
    }

    /** VoC 趋势。 */
    public Map<String, Object> getVocTrend(int months) {
        return statsService.getVocTrend(months);
    }

    /** 客户 VoC 统计。 */
    public Map<String, Object> getCustomerVoCStats(Long customerId) {
        return statsService.getCustomerVoCStats(customerId);
    }

    /** 主题统计。 */
    public Map<String, Object> getTopicStats() {
        return statsService.getTopicStats();
    }

    /** VoC 概览。 */
    public Map<String, Object> getVocOverview() {
        return statsService.getVocOverview();
    }
}
