/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmMessageTemplateCenterStatsService.java
 * Date : 2026/09/19 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;

import org.hiylo.scrm.entity.ScrmMessageTemplateCenterEntity;
import org.hiylo.scrm.entity.ScrmMessageTemplateGroupEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.repository.ScrmMessageTemplateCenterRepository;
import org.hiylo.scrm.repository.ScrmMessageTemplateGroupRepository;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SCRM 消息模板中心统计子域服务。
 * <p>
 * 承载模板统计 / 分组统计 / 热门模板 / 模板效果分析与渠道效果聚合,
 * 渠道拆分复用 {@link ScrmMessageTemplateCenterRenderService} 的 {@code splitChannels},
 * 模板实体查询复用 {@link ScrmMessageTemplateCenterTemplateService} 的 {@code findTemplateOrThrow}。
 * </p>
 *
 * @author Hsi Chu
 * @since 2026-09-19
 */
@Service
@RequiredArgsConstructor
public class ScrmMessageTemplateCenterStatsService {

    /** 模板定义数据访问层 */
    private final ScrmMessageTemplateCenterRepository centerRepository;

    /** 模板分组数据访问层 */
    private final ScrmMessageTemplateGroupRepository groupRepository;

    /** 模板管理子域服务 (查询模板实体) */
    private final ScrmMessageTemplateCenterTemplateService templateService;

    /**
     * 模板统计: 总数、各类型、各渠道、平均使用率与成功率。
     *
     * @param startTime 起始时间过滤（可空, 按 createTime）
     * @param endTime   结束时间过滤（可空）
     * @return 统计结果
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getTemplateStats(LocalDateTime startTime, LocalDateTime endTime) {
        List<ScrmMessageTemplateCenterEntity> templates = centerRepository.findAll(createTimeRangeSpec(startTime, endTime));
        Map<String, Long> byType = new LinkedHashMap<>();
        Map<String, Long> byChannel = new LinkedHashMap<>();
        long totalUsage = 0;
        double successSum = 0;
        for (ScrmMessageTemplateCenterEntity t : templates) {
            byType.merge(t.getTemplateType(), 1L, Long::sum);
            for (String ch : ScrmMessageTemplateCenterRenderService.splitChannels(t.getChannels())) {
                byChannel.merge(ch, 1L, Long::sum);
            }
            totalUsage += t.getUsageCount() == null ? 0 : t.getUsageCount();
            successSum += t.getSuccessRate() == null ? 0 : t.getSuccessRate();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", templates.size());
        result.put("byType", byType);
        result.put("byChannel", byChannel);
        result.put("totalUsage", totalUsage);
        result.put("avgUsage", templates.isEmpty() ? 0.0 : (double) totalUsage / templates.size());
        result.put("avgSuccessRate", templates.isEmpty() ? 0.0 : successSum / templates.size());
        return result;
    }

    /**
     * 分组统计: 各分组下模板数量。
     *
     * @return 分组统计列表
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getGroupStats() {
        List<ScrmMessageTemplateGroupEntity> groups = groupRepository.findAllByOrderBySortOrderAsc();
        List<Map<String, Object>> stats = new ArrayList<>();
        for (ScrmMessageTemplateGroupEntity g : groups) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("groupId", g.getId());
            item.put("groupName", g.getGroupName());
            item.put("groupCode", g.getGroupCode());
            item.put("groupType", g.getGroupType());
            item.put("templateCount", centerRepository.countByGroupId(g.getId()));
            item.put("enabled", g.getEnabled());
            stats.add(item);
        }
        return stats;
    }

    /**
     * 热门模板: 按使用次数降序取前 N。
     *
     * @param limit 数量
     * @return 模板列表
     */
    @Transactional(readOnly = true)
    public List<ScrmMessageTemplateCenterEntity> getTopTemplates(int limit) {
        return centerRepository
                .findAllByOrderByUsageCountDesc(
                        org.springframework.data.domain.PageRequest.of(0, Math.max(limit, 1)))
                .getContent();
    }

    /**
     * 模板效果分析: 使用次数、成功率、回复率、最后使用时间等。
     *
     * @param templateId 模板 ID
     * @return 效果分析结果
     * @throws ScrmException 模板不存在
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getTemplateEffectiveness(Long templateId) throws ScrmException {
        ScrmMessageTemplateCenterEntity t = templateService.findTemplateOrThrow(templateId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("templateId", t.getId());
        result.put("templateName", t.getTemplateName());
        result.put("templateCode", t.getTemplateCode());
        result.put("usageCount", t.getUsageCount());
        result.put("successRate", t.getSuccessRate());
        result.put("avgResponseRate", t.getAvgResponseRate());
        result.put("lastUsedAt", t.getLastUsedAt());
        result.put("versionNumber", t.getVersionNumber());
        result.put("isStandard", t.getIsStandard());
        return result;
    }

    /**
     * 渠道效果: 各渠道的模板数、累计使用次数、平均成功率。
     *
     * @param startTime 起始时间过滤（可空）
     * @param endTime   结束时间过滤（可空）
     * @return 渠道效果 Map
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getChannelEffectiveness(LocalDateTime startTime, LocalDateTime endTime) {
        List<ScrmMessageTemplateCenterEntity> templates = centerRepository.findAll(createTimeRangeSpec(startTime, endTime));
        Map<String, long[]> agg = new LinkedHashMap<>();
        Map<String, double[]> successAgg = new LinkedHashMap<>();
        for (ScrmMessageTemplateCenterEntity t : templates) {
            int usage = t.getUsageCount() == null ? 0 : t.getUsageCount();
            double rate = t.getSuccessRate() == null ? 0 : t.getSuccessRate();
            for (String ch : ScrmMessageTemplateCenterRenderService.splitChannels(t.getChannels())) {
                long[] arr = agg.computeIfAbsent(ch, k -> new long[2]);
                arr[0] += 1;
                arr[1] += usage;
                double[] sarr = successAgg.computeIfAbsent(ch, k -> new double[1]);
                sarr[0] += rate;
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, long[]> e : agg.entrySet()) {
            Map<String, Object> chStat = new LinkedHashMap<>();
            chStat.put("templateCount", e.getValue()[0]);
            chStat.put("totalUsage", e.getValue()[1]);
            double[] sarr = successAgg.get(e.getKey());
            chStat.put("avgSuccessRate", e.getValue()[0] == 0 ? 0.0 : sarr[0] / e.getValue()[0]);
            result.put(e.getKey(), chStat);
        }
        return result;
    }

    /**
     * 构建按 createTime 的时间范围过滤 Specification。
     *
     * @param startTime 起始时间
     * @param endTime   结束时间
     * @return Specification
     */
    private Specification<ScrmMessageTemplateCenterEntity> createTimeRangeSpec(LocalDateTime startTime, LocalDateTime endTime) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (startTime != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createTime"), startTime));
            }
            if (endTime != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createTime"), endTime));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}