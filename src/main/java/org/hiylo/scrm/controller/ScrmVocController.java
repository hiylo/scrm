/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmVocController.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hiylo.scrm.dto.ScrmVocAnalysisDto;
import org.hiylo.scrm.dto.ScrmVocInsightDto;
import org.hiylo.scrm.dto.ScrmVocResponseDto;
import org.hiylo.scrm.dto.ScrmVocTopicDto;
import org.hiylo.scrm.dto.ScrmVocVoiceDto;
import org.hiylo.scrm.entity.ScrmVocInsightEntity;
import org.hiylo.scrm.entity.ScrmVocTopicEntity;
import org.hiylo.scrm.entity.ScrmVocVoiceEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.rbac.annotation.RateLimit;
import org.hiylo.scrm.rbac.annotation.RequirePermission;
import org.hiylo.scrm.service.ScrmVocService;
import org.hiylo.scrm.common.OperationResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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
 * SCRM 客户之声 (VoC) 控制器。
 * <p>
 * 提供客户声音管理 (CRUD + 按编号/客户/主题查询 + 分析 + 批量分析 + 分配/解决/关闭/归档/验证/回复 +
 * 新增/待处理/紧急/负面/未解决列表 + 时间线 + 相似声音), 主题管理 (CRUD + 按编码查询 + 列表/树/子主题 +
 * 分配给声音 + 统计刷新 + 主题识别 + 热点/新兴 + 趋势/情感 + 合并), 洞察管理 (CRUD + 列表 + 发布/归档/分享 +
 * 生成/自动生成 + 按主题/可行动/Top + 反馈), 多维统计 (概览/情感/来源/分类/情感趋势/解决/VoC 趋势/
 * 客户/主题) 接口。权限由 gateway-server 统一鉴权, {@code @RequirePermission} 作为端点权限元数据声明。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Slf4j
@RestController
@RequestMapping("/scrm/voc")
@RequiredArgsConstructor
// Tag: SCRM 客户之声 -
public class ScrmVocController {

    /** VoC 服务 */
    private final ScrmVocService scrmVocService;

    // ============================================================
    // 声音管理 /voices
    // ============================================================

    /**
     * 创建客户声音。
     *
     * @param dto 声音参数
     * @return 创建后的声音
     * @throws ScrmException 参数非法
     */
    @RequirePermission(resource = "scrm_voc", action = "create")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/voices")
    public OperationResponse<ScrmVocVoiceEntity> createVoice(@Valid @RequestBody ScrmVocVoiceDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmVocService.createVoice(dto));
    }

    /**
     * 更新客户声音。
     *
     * @param id  声音 ID
     * @param dto 声音参数
     * @return 更新后的声音
     * @throws ScrmException 声音不存在 / 参数非法
     */
    @RequirePermission(resource = "scrm_voc", action = "update")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PutMapping("/voices/{id}")
    public OperationResponse<ScrmVocVoiceEntity> updateVoice(@PathVariable Long id,
                                                              @RequestBody ScrmVocVoiceDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmVocService.updateVoice(id, dto));
    }

    /**
     * 删除客户声音。
     *
     * @param id 声音 ID
     * @return 空响应
     * @throws ScrmException 声音不存在
     */
    @RequirePermission(resource = "scrm_voc", action = "delete")
    @DeleteMapping("/voices/{id}")
    public OperationResponse<Void> deleteVoice(@PathVariable Long id) throws ScrmException {
        scrmVocService.deleteVoice(id);
        return OperationResponse.build();
    }

    /**
     * 查询声音详情。
     *
     * @param id 声音 ID
     * @return 声音详情
     * @throws ScrmException 声音不存在
     */
    @RequirePermission(resource = "scrm_voc", action = "read")
    @GetMapping("/voices/{id}")
    public OperationResponse<ScrmVocVoiceEntity> getVoice(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmVocService.getVoice(id));
    }

    /**
     * 按声音编号查询声音。
     *
     * @param voiceNo 声音编号
     * @return 声音详情
     * @throws ScrmException 声音不存在
     */
    @RequirePermission(resource = "scrm_voc", action = "read")
    @GetMapping("/voices/by-no/{voiceNo}")
    public OperationResponse<ScrmVocVoiceEntity> getVoiceByNo(@PathVariable String voiceNo) throws ScrmException {
        return OperationResponse.build(scrmVocService.getVoiceByNo(voiceNo));
    }

    /**
     * 分页查询声音列表, 支持多条件过滤。
     *
     * @param source     来源渠道 (可选)
     * @param voiceType  声音类型 (可选)
     * @param sentiment  情感 (可选)
     * @param priority   优先级 (可选)
     * @param status     状态 (可选)
     * @param category   分类 (可选)
     * @param customerId 客户 ID (可选)
     * @param keyword    标题/内容关键字 (可选)
     * @param startTime  收集起始时间 (可选)
     * @param endTime    收集截止时间 (可选)
     * @param page       页码 (从 0 开始, 默认 0)
     * @param size       每页大小 (默认 20)
     * @return 声音分页结果
     */
    @RequirePermission(resource = "scrm_voc", action = "read")
    @GetMapping("/voices/list")
    public OperationResponse<Page<ScrmVocVoiceEntity>> listVoices(
            @RequestParam(required = false) String source,
            @RequestParam(required = false) String voiceType,
            @RequestParam(required = false) String sentiment,
            @RequestParam(required = false) String priority,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return OperationResponse.build(scrmVocService.listVoices(
                source, voiceType, sentiment, priority, status, category,
                customerId, keyword, startTime, endTime, pageable));
    }

    /**
     * 按客户分页查询声音。
     *
     * @param customerId 客户 ID
     * @param page       页码 (从 0 开始, 默认 0)
     * @param size       每页大小 (默认 20)
     * @return 声音分页结果
     */
    @RequirePermission(resource = "scrm_voc", action = "read")
    @GetMapping("/voices/by-customer/{customerId}")
    public OperationResponse<Page<ScrmVocVoiceEntity>> getVoicesByCustomer(
            @PathVariable Long customerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return OperationResponse.build(scrmVocService.getVoicesByCustomer(customerId, pageable));
    }

    /**
     * 按主题分页查询声音。
     *
     * @param topicId 主题 ID
     * @param page    页码 (从 0 开始, 默认 0)
     * @param size    每页大小 (默认 20)
     * @return 声音分页结果
     * @throws ScrmException 主题不存在
     */
    @RequirePermission(resource = "scrm_voc", action = "read")
    @GetMapping("/voices/by-topic/{topicId}")
    public OperationResponse<Page<ScrmVocVoiceEntity>> getVoicesByTopic(
            @PathVariable Long topicId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) throws ScrmException {
        Pageable pageable = PageRequest.of(page, size);
        return OperationResponse.build(scrmVocService.getVoicesByTopic(topicId, pageable));
    }

    /**
     * 分析单条声音 (设置情感 / 分类 / 主题 / 关键词)。
     *
     * @param dto 分析参数
     * @return 更新后的声音
     * @throws ScrmException 声音不存在 / 参数非法
     */
    @RequirePermission(resource = "scrm_voc", action = "analyze")
    @RateLimit(capacity = 20, refillTokens = 20, refillPeriodSeconds = 60)
    @PostMapping("/voices/analyze")
    public OperationResponse<ScrmVocVoiceEntity> analyzeVoice(@Valid @RequestBody ScrmVocAnalysisDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmVocService.analyzeVoice(dto));
    }

    /**
     * 批量分析声音。
     *
     * @param voiceIds 声音 ID 列表
     * @return 批量结果 {total, success, failed}
     */
    @RequirePermission(resource = "scrm_voc", action = "analyze")
    @RateLimit(capacity = 10, refillTokens = 10, refillPeriodSeconds = 60)
    @PostMapping("/voices/batch-analyze")
    public OperationResponse<Map<String, Integer>> batchAnalyze(@RequestBody List<Long> voiceIds) {
        return OperationResponse.build(scrmVocService.batchAnalyze(voiceIds));
    }

    /**
     * 分配声音处理人。
     *
     * @param id         声音 ID
     * @param assigneeId 处理人 ID
     * @param department 责任部门 (可选)
     * @return 更新后的声音
     * @throws ScrmException 声音不存在
     */
    @RequirePermission(resource = "scrm_voc", action = "assign")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/voices/assign")
    public OperationResponse<ScrmVocVoiceEntity> assignVoice(
            @RequestParam Long id,
            @RequestParam String assigneeId,
            @RequestParam(required = false) String department) throws ScrmException {
        return OperationResponse.build(scrmVocService.assignVoice(id, assigneeId, department));
    }

    /**
     * 解决声音。
     *
     * @param id         声音 ID
     * @param resolution 解决方案
     * @param resolvedBy 解决人 (可选)
     * @return 更新后的声音
     * @throws ScrmException 声音不存在
     */
    @RequirePermission(resource = "scrm_voc", action = "resolve")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/voices/resolve")
    public OperationResponse<ScrmVocVoiceEntity> resolveVoice(
            @RequestParam Long id,
            @RequestParam String resolution,
            @RequestParam(required = false) String resolvedBy) throws ScrmException {
        return OperationResponse.build(scrmVocService.resolveVoice(id, resolution, resolvedBy));
    }

    /**
     * 关闭声音。
     *
     * @param id           声音 ID
     * @param satisfaction 解决后满意度 (1-5, 可选)
     * @param closedBy     关闭人 (可选)
     * @return 更新后的声音
     * @throws ScrmException 声音不存在
     */
    @RequirePermission(resource = "scrm_voc", action = "close")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/voices/close")
    public OperationResponse<ScrmVocVoiceEntity> closeVoice(
            @RequestParam Long id,
            @RequestParam(required = false) Integer satisfaction,
            @RequestParam(required = false) String closedBy) throws ScrmException {
        return OperationResponse.build(scrmVocService.closeVoice(id, satisfaction, closedBy));
    }

    /**
     * 归档声音。
     *
     * @param id         声音 ID
     * @param archivedBy 归档人 (可选)
     * @return 更新后的声音
     * @throws ScrmException 声音不存在
     */
    @RequirePermission(resource = "scrm_voc", action = "archive")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/voices/archive")
    public OperationResponse<ScrmVocVoiceEntity> archiveVoice(
            @RequestParam Long id,
            @RequestParam(required = false) String archivedBy) throws ScrmException {
        return OperationResponse.build(scrmVocService.archiveVoice(id, archivedBy));
    }

    /**
     * 验证声音。
     *
     * @param id         声音 ID
     * @param verifiedBy 验证人 (可选)
     * @return 更新后的声音
     * @throws ScrmException 声音不存在
     */
    @RequirePermission(resource = "scrm_voc", action = "verify")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/voices/verify")
    public OperationResponse<ScrmVocVoiceEntity> verifyVoice(
            @RequestParam Long id,
            @RequestParam(required = false) String verifiedBy) throws ScrmException {
        return OperationResponse.build(scrmVocService.verifyVoice(id, verifiedBy));
    }

    /**
     * 回复声音。
     *
     * @param dto 回复参数
     * @return 更新后的声音
     * @throws ScrmException 声音不存在 / 参数非法
     */
    @RequirePermission(resource = "scrm_voc", action = "respond")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/voices/respond")
    public OperationResponse<ScrmVocVoiceEntity> respondToVoice(@Valid @RequestBody ScrmVocResponseDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmVocService.respondToVoice(dto));
    }

    /**
     * 查询新声音 (status = NEW)。
     *
     * @param page 页码 (从 0 开始, 默认 0)
     * @param size 每页大小 (默认 20)
     * @return 声音分页结果
     */
    @RequirePermission(resource = "scrm_voc", action = "read")
    @GetMapping("/voices/new")
    public OperationResponse<Page<ScrmVocVoiceEntity>> getNewVoices(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return OperationResponse.build(scrmVocService.getNewVoices(pageable));
    }

    /**
     * 查询待处理声音 (status in NEW / ANALYZING / ASSIGNED / IN_PROGRESS)。
     *
     * @param page 页码 (从 0 开始, 默认 0)
     * @param size 每页大小 (默认 20)
     * @return 声音分页结果
     */
    @RequirePermission(resource = "scrm_voc", action = "read")
    @GetMapping("/voices/pending")
    public OperationResponse<Page<ScrmVocVoiceEntity>> getPendingVoices(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return OperationResponse.build(scrmVocService.getPendingVoices(pageable));
    }

    /**
     * 查询紧急声音 (priority = URGENT)。
     *
     * @param page 页码 (从 0 开始, 默认 0)
     * @param size 每页大小 (默认 20)
     * @return 声音分页结果
     */
    @RequirePermission(resource = "scrm_voc", action = "read")
    @GetMapping("/voices/urgent")
    public OperationResponse<Page<ScrmVocVoiceEntity>> getUrgentVoices(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return OperationResponse.build(scrmVocService.getUrgentVoices(pageable));
    }

    /**
     * 查询负面声音 (sentiment = NEGATIVE)。
     *
     * @param page 页码 (从 0 开始, 默认 0)
     * @param size 每页大小 (默认 20)
     * @return 声音分页结果
     */
    @RequirePermission(resource = "scrm_voc", action = "read")
    @GetMapping("/voices/negative")
    public OperationResponse<Page<ScrmVocVoiceEntity>> getNegativeVoices(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return OperationResponse.build(scrmVocService.getNegativeVoices(pageable));
    }

    /**
     * 查询未解决声音 (status not in RESOLVED / CLOSED / ARCHIVED / IGNORED)。
     *
     * @param page 页码 (从 0 开始, 默认 0)
     * @param size 每页大小 (默认 20)
     * @return 声音分页结果
     */
    @RequirePermission(resource = "scrm_voc", action = "read")
    @GetMapping("/voices/unresolved")
    public OperationResponse<Page<ScrmVocVoiceEntity>> getUnresolvedVoices(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return OperationResponse.build(scrmVocService.getUnresolvedVoices(pageable));
    }

    /**
     * 客户声音时间线 (按客户查询声音, 按收集时间正序)。
     *
     * @param customerId 客户 ID
     * @return 声音列表
     */
    @RequirePermission(resource = "scrm_voc", action = "read")
    @GetMapping("/voices/timeline/{customerId}")
    public OperationResponse<List<ScrmVocVoiceEntity>> getVoiceTimeline(@PathVariable Long customerId) {
        return OperationResponse.build(scrmVocService.getVoiceTimeline(customerId));
    }

    /**
     * 查询相似声音 (按分类与标签匹配)。
     *
     * @param voiceId 声音 ID
     * @return 相似声音列表
     * @throws ScrmException 声音不存在
     */
    @RequirePermission(resource = "scrm_voc", action = "read")
    @GetMapping("/voices/similar/{voiceId}")
    public OperationResponse<List<ScrmVocVoiceEntity>> getSimilarVoices(@PathVariable Long voiceId)
            throws ScrmException {
        return OperationResponse.build(scrmVocService.getSimilarVoices(voiceId));
    }

    // ============================================================
    // 主题管理 /topics
    // ============================================================

    /**
     * 创建主题。
     *
     * @param dto 主题参数
     * @return 创建后的主题
     * @throws ScrmException 参数非法 / 编码重复
     */
    @RequirePermission(resource = "scrm_voc", action = "create")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/topics")
    public OperationResponse<ScrmVocTopicEntity> createTopic(@Valid @RequestBody ScrmVocTopicDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmVocService.createTopic(dto));
    }

    /**
     * 更新主题。
     *
     * @param id  主题 ID
     * @param dto 主题参数
     * @return 更新后的主题
     * @throws ScrmException 主题不存在 / 参数非法 / 编码重复
     */
    @RequirePermission(resource = "scrm_voc", action = "update")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PutMapping("/topics/{id}")
    public OperationResponse<ScrmVocTopicEntity> updateTopic(@PathVariable Long id,
                                                              @RequestBody ScrmVocTopicDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmVocService.updateTopic(id, dto));
    }

    /**
     * 删除主题。
     *
     * @param id 主题 ID
     * @return 空响应
     * @throws ScrmException 主题不存在
     */
    @RequirePermission(resource = "scrm_voc", action = "delete")
    @DeleteMapping("/topics/{id}")
    public OperationResponse<Void> deleteTopic(@PathVariable Long id) throws ScrmException {
        scrmVocService.deleteTopic(id);
        return OperationResponse.build();
    }

    /**
     * 查询主题详情。
     *
     * @param id 主题 ID
     * @return 主题详情
     * @throws ScrmException 主题不存在
     */
    @RequirePermission(resource = "scrm_voc", action = "read")
    @GetMapping("/topics/{id}")
    public OperationResponse<ScrmVocTopicEntity> getTopic(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmVocService.getTopic(id));
    }

    /**
     * 按主题编码查询主题。
     *
     * @param code 主题编码
     * @return 主题详情
     * @throws ScrmException 主题不存在
     */
    @RequirePermission(resource = "scrm_voc", action = "read")
    @GetMapping("/topics/code/{code}")
    public OperationResponse<ScrmVocTopicEntity> getTopicByCode(@PathVariable String code) throws ScrmException {
        return OperationResponse.build(scrmVocService.getTopicByCode(code));
    }

    /**
     * 分页查询主题列表, 支持按分类 / 启用状态 / 关键字过滤。
     *
     * @param category 分类过滤 (可选)
     * @param enabled  启用状态过滤 (可选)
     * @param keyword  主题名/编码关键字 (可选)
     * @param page     页码 (从 0 开始, 默认 0)
     * @param size     每页大小 (默认 20)
     * @return 主题分页结果
     */
    @RequirePermission(resource = "scrm_voc", action = "read")
    @GetMapping("/topics/list")
    public OperationResponse<Page<ScrmVocTopicEntity>> listTopics(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return OperationResponse.build(scrmVocService.listTopics(category, enabled, keyword, pageable));
    }

    /**
     * 查询主题树 (所有主题按层级组织)。
     *
     * @return 主题树 [{topic, children: [...]}]
     */
    @RequirePermission(resource = "scrm_voc", action = "read")
    @GetMapping("/topics/tree")
    public OperationResponse<List<Map<String, Object>>> getTopicTree() {
        return OperationResponse.build(scrmVocService.getTopicTree());
    }

    /**
     * 查询子主题。
     *
     * @param parentId 父主题 ID
     * @return 子主题列表
     */
    @RequirePermission(resource = "scrm_voc", action = "read")
    @GetMapping("/topics/children/{parentId}")
    public OperationResponse<List<ScrmVocTopicEntity>> getChildTopics(@PathVariable Long parentId) {
        return OperationResponse.build(scrmVocService.getChildTopics(parentId));
    }

    /**
     * 将主题分配给声音 (追加主题编码到声音 tags, 刷新主题统计)。
     *
     * @param voiceId 声音 ID
     * @param topicId 主题 ID
     * @return 更新后的声音
     * @throws ScrmException 声音 / 主题不存在
     */
    @RequirePermission(resource = "scrm_voc", action = "assign")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/topics/assign-to-voice")
    public OperationResponse<ScrmVocVoiceEntity> assignTopicToVoice(
            @RequestParam Long voiceId,
            @RequestParam Long topicId) throws ScrmException {
        return OperationResponse.build(scrmVocService.assignTopicToVoice(voiceId, topicId));
    }

    /**
     * 更新主题统计 (重新计算声音数 / 情感分布 / 评分 / 趋势 / 优先级 / 热点 / 新兴)。
     *
     * @param id 主题 ID
     * @return 更新后的主题
     * @throws ScrmException 主题不存在
     */
    @RequirePermission(resource = "scrm_voc", action = "analyze")
    @RateLimit(capacity = 20, refillTokens = 20, refillPeriodSeconds = 60)
    @PostMapping("/topics/{id}/stats")
    public OperationResponse<ScrmVocTopicEntity> updateTopicStats(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmVocService.updateTopicStats(id));
    }

    /**
     * 主题识别 (从声音内容提取关键词 → 匹配主题)。
     *
     * @param voiceId 声音 ID
     * @return 匹配的主题列表
     * @throws ScrmException 声音不存在
     */
    @RequirePermission(resource = "scrm_voc", action = "analyze")
    @RateLimit(capacity = 20, refillTokens = 20, refillPeriodSeconds = 60)
    @PostMapping("/topics/identify")
    public OperationResponse<List<ScrmVocTopicEntity>> identifyTopics(@RequestParam Long voiceId)
            throws ScrmException {
        return OperationResponse.build(scrmVocService.identifyTopics(voiceId));
    }

    /**
     * 查询热点主题 (isHotTopic = true)。
     *
     * @param limit 返回条数 (默认 10)
     * @return 热点主题列表
     */
    @RequirePermission(resource = "scrm_voc", action = "read")
    @GetMapping("/topics/hot")
    public OperationResponse<List<ScrmVocTopicEntity>> getHotTopics(
            @RequestParam(defaultValue = "10") int limit) {
        return OperationResponse.build(scrmVocService.getHotTopics(limit));
    }

    /**
     * 查询新兴主题 (isEmerging = true)。
     *
     * @param limit 返回条数 (默认 10)
     * @return 新兴主题列表
     */
    @RequirePermission(resource = "scrm_voc", action = "read")
    @GetMapping("/topics/emerging")
    public OperationResponse<List<ScrmVocTopicEntity>> getEmergingTopics(
            @RequestParam(defaultValue = "10") int limit) {
        return OperationResponse.build(scrmVocService.getEmergingTopics(limit));
    }

    /**
     * 主题趋势 (按月统计声音数与情感均分)。
     *
     * @param topicId 主题 ID
     * @param months  回溯月数 (默认 6)
     * @return 趋势结果 Map
     * @throws ScrmException 主题不存在
     */
    @RequirePermission(resource = "scrm_voc", action = "read")
    @GetMapping("/topics/trend")
    public OperationResponse<Map<String, Object>> getTopicTrend(
            @RequestParam Long topicId,
            @RequestParam(defaultValue = "6") int months) throws ScrmException {
        return OperationResponse.build(scrmVocService.getTopicTrend(topicId, months));
    }

    /**
     * 主题情感分布。
     *
     * @param topicId 主题 ID
     * @return 情感分布 Map
     * @throws ScrmException 主题不存在
     */
    @RequirePermission(resource = "scrm_voc", action = "read")
    @GetMapping("/topics/sentiment")
    public OperationResponse<Map<String, Object>> getTopicSentiment(@RequestParam Long topicId)
            throws ScrmException {
        return OperationResponse.build(scrmVocService.getTopicSentiment(topicId));
    }

    /**
     * 合并主题 (将源主题的声音关联转移到目标主题, 源主题禁用)。
     *
     * @param sourceId 源主题 ID
     * @param targetId 目标主题 ID
     * @return 目标主题
     * @throws ScrmException 主题不存在 / 源与目标相同
     */
    @RequirePermission(resource = "scrm_voc", action = "merge")
    @RateLimit(capacity = 10, refillTokens = 10, refillPeriodSeconds = 60)
    @PostMapping("/topics/merge")
    public OperationResponse<ScrmVocTopicEntity> mergeTopics(
            @RequestParam Long sourceId,
            @RequestParam Long targetId) throws ScrmException {
        return OperationResponse.build(scrmVocService.mergeTopics(sourceId, targetId));
    }

    // ============================================================
    // 洞察管理 /insights
    // ============================================================

    /**
     * 创建洞察。
     *
     * @param dto 洞察参数
     * @return 创建后的洞察
     * @throws ScrmException 参数非法
     */
    @RequirePermission(resource = "scrm_voc", action = "create")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/insights")
    public OperationResponse<ScrmVocInsightEntity> createInsight(@Valid @RequestBody ScrmVocInsightDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmVocService.createInsight(dto));
    }

    /**
     * 更新洞察。
     *
     * @param id  洞察 ID
     * @param dto 洞察参数
     * @return 更新后的洞察
     * @throws ScrmException 洞察不存在 / 参数非法
     */
    @RequirePermission(resource = "scrm_voc", action = "update")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PutMapping("/insights/{id}")
    public OperationResponse<ScrmVocInsightEntity> updateInsight(@PathVariable Long id,
                                                                  @RequestBody ScrmVocInsightDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmVocService.updateInsight(id, dto));
    }

    /**
     * 删除洞察。
     *
     * @param id 洞察 ID
     * @return 空响应
     * @throws ScrmException 洞察不存在
     */
    @RequirePermission(resource = "scrm_voc", action = "delete")
    @DeleteMapping("/insights/{id}")
    public OperationResponse<Void> deleteInsight(@PathVariable Long id) throws ScrmException {
        scrmVocService.deleteInsight(id);
        return OperationResponse.build();
    }

    /**
     * 查询洞察详情。
     *
     * @param id 洞察 ID
     * @return 洞察详情
     * @throws ScrmException 洞察不存在
     */
    @RequirePermission(resource = "scrm_voc", action = "read")
    @GetMapping("/insights/{id}")
    public OperationResponse<ScrmVocInsightEntity> getInsight(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmVocService.getInsight(id));
    }

    /**
     * 分页查询洞察, 支持按类型 / 状态 / 影响等级 / 优先级 / 关键字过滤。
     *
     * @param insightType 洞察类型 (可选)
     * @param status      状态 (可选)
     * @param impactLevel 影响等级 (可选)
     * @param priority    优先级 (可选)
     * @param keyword     标题/摘要关键字 (可选)
     * @param page        页码 (从 0 开始, 默认 0)
     * @param size        每页大小 (默认 20)
     * @return 洞察分页结果
     */
    @RequirePermission(resource = "scrm_voc", action = "read")
    @GetMapping("/insights/list")
    public OperationResponse<Page<ScrmVocInsightEntity>> listInsights(
            @RequestParam(required = false) String insightType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String impactLevel,
            @RequestParam(required = false) String priority,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return OperationResponse.build(scrmVocService.listInsights(
                insightType, status, impactLevel, priority, keyword, pageable));
    }

    /**
     * 发布洞察 (状态置为 PUBLISHED)。
     *
     * @param id          洞察 ID
     * @param publishedBy 发布人 (可选)
     * @return 更新后的洞察
     * @throws ScrmException 洞察不存在
     */
    @RequirePermission(resource = "scrm_voc", action = "publish")
    @RateLimit(capacity = 20, refillTokens = 20, refillPeriodSeconds = 60)
    @PostMapping("/insights/publish")
    public OperationResponse<ScrmVocInsightEntity> publishInsight(
            @RequestParam Long id,
            @RequestParam(required = false) String publishedBy) throws ScrmException {
        return OperationResponse.build(scrmVocService.publishInsight(id, publishedBy));
    }

    /**
     * 归档洞察 (状态置为 ARCHIVED)。
     *
     * @param id 洞察 ID
     * @return 更新后的洞察
     * @throws ScrmException 洞察不存在
     */
    @RequirePermission(resource = "scrm_voc", action = "archive")
    @RateLimit(capacity = 20, refillTokens = 20, refillPeriodSeconds = 60)
    @PostMapping("/insights/archive")
    public OperationResponse<ScrmVocInsightEntity> archiveInsight(@RequestParam Long id) throws ScrmException {
        return OperationResponse.build(scrmVocService.archiveInsight(id));
    }

    /**
     * 分享洞察 (追加分享目标用户)。
     *
     * @param id         洞察 ID
     * @param sharedWith 分享目标用户 ID 列表 (逗号分隔)
     * @return 更新后的洞察
     * @throws ScrmException 洞察不存在
     */
    @RequirePermission(resource = "scrm_voc", action = "share")
    @RateLimit(capacity = 20, refillTokens = 20, refillPeriodSeconds = 60)
    @PostMapping("/insights/share")
    public OperationResponse<ScrmVocInsightEntity> shareInsight(
            @RequestParam Long id,
            @RequestParam String sharedWith) throws ScrmException {
        return OperationResponse.build(scrmVocService.shareInsight(id, sharedWith));
    }

    /**
     * 生成洞察 (分析趋势 → 识别模式 → 生成建议)。
     *
     * @param topicId 主题 ID (可选, 为空则对所有热点主题生成)
     * @param period  分析周期 (可选, 如 2026-Q3)
     * @return 生成的洞察列表
     * @throws ScrmException 主题不存在
     */
    @RequirePermission(resource = "scrm_voc", action = "generate")
    @RateLimit(capacity = 5, refillTokens = 5, refillPeriodSeconds = 60)
    @PostMapping("/insights/generate")
    public OperationResponse<List<ScrmVocInsightEntity>> generateInsights(
            @RequestParam(required = false) Long topicId,
            @RequestParam(required = false) String period) throws ScrmException {
        return OperationResponse.build(scrmVocService.generateInsights(topicId, period));
    }

    /**
     * 自动生成洞察 (对所有热点与新兴主题自动生成)。
     *
     * @param period 分析周期 (可选)
     * @return 生成的洞察列表
     */
    @RequirePermission(resource = "scrm_voc", action = "generate")
    @RateLimit(capacity = 5, refillTokens = 5, refillPeriodSeconds = 60)
    @PostMapping("/insights/auto-generate")
    public OperationResponse<List<ScrmVocInsightEntity>> autoGenerateInsights(
            @RequestParam(required = false) String period) {
        return OperationResponse.build(scrmVocService.autoGenerateInsights(period));
    }

    /**
     * 按主题查询洞察。
     *
     * @param topicId 主题 ID
     * @return 洞察列表
     */
    @RequirePermission(resource = "scrm_voc", action = "read")
    @GetMapping("/insights/by-topic/{topicId}")
    public OperationResponse<List<ScrmVocInsightEntity>> getInsightsByTopic(@PathVariable Long topicId) {
        return OperationResponse.build(scrmVocService.getInsightsByTopic(topicId));
    }

    /**
     * 查询可行动洞察 (status in PUBLISHED / ACTED_ON, impact in HIGH / CRITICAL)。
     *
     * @param page 页码 (从 0 开始, 默认 0)
     * @param size 每页大小 (默认 20)
     * @return 洞察分页结果
     */
    @RequirePermission(resource = "scrm_voc", action = "read")
    @GetMapping("/insights/actionable")
    public OperationResponse<Page<ScrmVocInsightEntity>> getActionableInsights(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return OperationResponse.build(scrmVocService.getActionableInsights(pageable));
    }

    /**
     * 添加洞察反馈 (递增反馈数, 更新平均评分)。
     *
     * @param id      洞察 ID
     * @param rating  评分 (1-5)
     * @param comment 反馈内容 (可选)
     * @return 更新后的洞察
     * @throws ScrmException 洞察不存在 / 评分越界
     */
    @RequirePermission(resource = "scrm_voc", action = "feedback")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/insights/feedback")
    public OperationResponse<ScrmVocInsightEntity> addFeedback(
            @RequestParam Long id,
            @RequestParam int rating,
            @RequestParam(required = false) String comment) throws ScrmException {
        return OperationResponse.build(scrmVocService.addFeedback(id, rating, comment));
    }

    /**
     * 查询 Top 洞察 (按反馈评分与影响等级排序)。
     *
     * @param limit 返回条数 (默认 10)
     * @return 洞察列表
     */
    @RequirePermission(resource = "scrm_voc", action = "read")
    @GetMapping("/insights/top")
    public OperationResponse<List<ScrmVocInsightEntity>> getTopInsights(
            @RequestParam(defaultValue = "10") int limit) {
        return OperationResponse.build(scrmVocService.getTopInsights(limit));
    }

    // ============================================================
    // 统计 /stats
    // ============================================================

    /**
     * VoC 概览 (声音统计 + 解决统计 + 主题统计 + 洞察统计)。
     *
     * @return 概览 Map
     */
    @RequirePermission(resource = "scrm_voc", action = "read")
    @GetMapping("/stats/overview")
    public OperationResponse<Map<String, Object>> getVocOverview() {
        return OperationResponse.build(scrmVocService.getVocOverview());
    }

    /**
     * VoC 统计 (总数 / 各状态 / 各情感 / 各优先级)。
     *
     * @param startTime 起始时间 (可选)
     * @param endTime   截止时间 (可选)
     * @return 统计结果 Map
     */
    @RequirePermission(resource = "scrm_voc", action = "read")
    @GetMapping("/stats/sentiment")
    public OperationResponse<Map<String, Object>> getSentimentDistribution(
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime) {
        return OperationResponse.build(scrmVocService.getSentimentDistribution(startTime, endTime));
    }

    /**
     * 来源渠道分布。
     *
     * @param startTime 起始时间 (可选)
     * @param endTime   截止时间 (可选)
     * @return 来源分布 Map
     */
    @RequirePermission(resource = "scrm_voc", action = "read")
    @GetMapping("/stats/sources")
    public OperationResponse<Map<String, Object>> getSourceDistribution(
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime) {
        return OperationResponse.build(scrmVocService.getSourceDistribution(startTime, endTime));
    }

    /**
     * 分类分布。
     *
     * @param startTime 起始时间 (可选)
     * @param endTime   截止时间 (可选)
     * @return 分类分布 Map
     */
    @RequirePermission(resource = "scrm_voc", action = "read")
    @GetMapping("/stats/categories")
    public OperationResponse<Map<String, Object>> getCategoryDistribution(
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime) {
        return OperationResponse.build(scrmVocService.getCategoryDistribution(startTime, endTime));
    }

    /**
     * 情感趋势 (按月统计情感均分)。
     *
     * @param months 回溯月数 (默认 6)
     * @return 趋势结果 Map
     */
    @RequirePermission(resource = "scrm_voc", action = "read")
    @GetMapping("/stats/sentiment-trend")
    public OperationResponse<Map<String, Object>> getSentimentTrend(
            @RequestParam(defaultValue = "6") int months) {
        return OperationResponse.build(scrmVocService.getSentimentTrend(months));
    }

    /**
     * 解决统计 (解决率 / 平均解决时长 / 平均满意度)。
     *
     * @param startTime 起始时间 (可选)
     * @param endTime   截止时间 (可选)
     * @return 解决统计 Map
     */
    @RequirePermission(resource = "scrm_voc", action = "read")
    @GetMapping("/stats/resolution")
    public OperationResponse<Map<String, Object>> getResolutionStats(
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime) {
        return OperationResponse.build(scrmVocService.getResolutionStats(startTime, endTime));
    }

    /**
     * VoC 趋势 (按月统计声音数与负面数)。
     *
     * @param months 回溯月数 (默认 6)
     * @return 趋势结果 Map
     */
    @RequirePermission(resource = "scrm_voc", action = "read")
    @GetMapping("/stats/voc-trend")
    public OperationResponse<Map<String, Object>> getVocTrend(
            @RequestParam(defaultValue = "6") int months) {
        return OperationResponse.build(scrmVocService.getVocTrend(months));
    }

    /**
     * 客户 VoC 统计 (声音数 / 各情感 / 各状态 / 平均满意度)。
     *
     * @param customerId 客户 ID
     * @return 统计结果 Map
     */
    @RequirePermission(resource = "scrm_voc", action = "read")
    @GetMapping("/stats/customer/{customerId}")
    public OperationResponse<Map<String, Object>> getCustomerVoCStats(@PathVariable Long customerId) {
        return OperationResponse.build(scrmVocService.getCustomerVoCStats(customerId));
    }

    /**
     * 主题统计 (主题数 / 热点数 / 新兴数 / 平均优先级)。
     *
     * @return 统计结果 Map
     */
    @RequirePermission(resource = "scrm_voc", action = "read")
    @GetMapping("/stats/topics")
    public OperationResponse<Map<String, Object>> getTopicStats() {
        return OperationResponse.build(scrmVocService.getTopicStats());
    }
}
