/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : SegmentRecalculateTaskHandler.java
 * Date : 2026/09/19 21:20:11
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.dto.ScrmSegmentCalculationResultDto;
import org.hiylo.scrm.entity.ScrmScheduledTaskEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 客群重算调度处理器 (白名单处理器)。
 * <p>
 * 任务 {@code parameters} 支持两种书写:
 * </p>
 * <ul>
 *   <li>{@code {"segmentId":100}}: 只重算指定分群</li>
 *   <li>空白或其他内容: 批量重算当前账号下全部 ACTIVE 分群 (单个分群失败内部已跳过)</li>
 * </ul>
 * <p>返回值为统计 JSON, 便于在执行记录里直接看到重算结果。</p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SegmentRecalculateTaskHandler implements ScheduledTaskHandler {

    /** 任务参数中的分群 ID 键 */
    private static final String PARAM_SEGMENT_ID = "segmentId";

    /** JSON 解析器 */
    private final ObjectMapper objectMapper;

    /** 客户分群服务 */
    private final ScrmSegmentService segmentService;

    /**
     * 重算分群成员。
     *
     * @param task 任务配置 (读取 {@code parameters})
     * @return 统计结果 JSON
     * @throws ScrmException 参数 JSON 非法 / 分群不存在
     */
    @Override
    public String handle(ScrmScheduledTaskEntity task) throws ScrmException {
        Long segmentId = resolveSegmentId(task.getParameters());
        Map<String, Object> stats = new LinkedHashMap<>();
        if (segmentId != null) {
            ScrmSegmentCalculationResultDto result = segmentService.calculateSegment(segmentId);
            stats.put("mode", "SINGLE");
            stats.put("segmentId", segmentId);
            stats.put("totalMatched", result.getTotalMatched());
            stats.put("addedCount", result.getAddedCount());
            stats.put("removedCount", result.getRemovedCount());
        } else {
            stats.put("mode", "BATCH");
            stats.putAll(segmentService.batchCalculateSegments());
        }
        log.info("[调度-客群重算] 完成: taskId={}, stats={}", task.getId(), stats);
        return toJson(stats);
    }

    /**
     * 从任务参数中解析分群 ID。
     *
     * @param parameters 任务参数 JSON
     * @return 分群 ID, 未指定返回 null
     * @throws ScrmException 参数不是合法 JSON 对象
     */
    private Long resolveSegmentId(String parameters) throws ScrmException {
        if (parameters == null || parameters.isBlank()) {
            return null;
        }
        Map<String, Object> params;
        try {
            params = objectMapper.readValue(parameters, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
            throw ScrmException.badRequest("客群重算任务参数需为 JSON 对象: " + e.getMessage());
        }
        Object raw = params.get(PARAM_SEGMENT_ID);
        if (raw == null) {
            return null;
        }
        try {
            return raw instanceof Number number ? number.longValue() : Long.valueOf(String.valueOf(raw).trim());
        } catch (NumberFormatException e) {
            throw ScrmException.badRequest("客群重算任务参数 segmentId 非法: " + raw);
        }
    }

    /**
     * 序列化为 JSON 字符串。
     *
     * @param value 待序列化对象
     * @return JSON 字符串
     */
    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            log.warn("客群重算结果序列化失败: {}", e.getMessage());
            return String.valueOf(value);
        }
    }
}
