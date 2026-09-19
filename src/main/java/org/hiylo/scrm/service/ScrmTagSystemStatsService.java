/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmTagSystemStatsService.java
 * Date : 2026/09/19 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.entity.ScrmTagCustomerEntity;
import org.hiylo.scrm.entity.ScrmTagEntity;
import org.hiylo.scrm.entity.ScrmTagGroupEntity;
import org.hiylo.scrm.entity.ScrmTagRuleEntity;
import org.hiylo.scrm.repository.ScrmCustomerRepository;
import org.hiylo.scrm.repository.ScrmTagCustomerRepository;
import org.hiylo.scrm.repository.ScrmTagGroupRepository;
import org.hiylo.scrm.repository.ScrmTagRepository;
import org.hiylo.scrm.repository.ScrmTagRuleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * SCRM 标签体系统计分析服务。
 * <p>
 * 承载统计子域: 标签统计概览 / 标签云 / 客户标签数 / 分组覆盖率 / 规则统计。
 * 基于标签、分组、客户标签关联与规则实体的只读聚合。
 * </p>
 *
 * @author Hsi Chu
 * @since 2026-09-19
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmTagSystemStatsService {

    /** 标签定义数据访问层 */
    private final ScrmTagRepository tagRepository;

    /** 标签分组数据访问层 */
    private final ScrmTagGroupRepository groupRepository;

    /** 客户数据访问层 (统计客户总数) */
    private final ScrmCustomerRepository customerRepository;

    /** 客户标签关联数据访问层 */
    private final ScrmTagCustomerRepository tagCustomerRepository;

    /** 标签规则数据访问层 */
    private final ScrmTagRuleRepository ruleRepository;

    /**
     * 标签统计概览: 总标签数 / 各类型分布 / 各分组分布 / 覆盖率。
     *
     * @return 统计结果
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getTagStats() {
        List<ScrmTagEntity> tags = tagRepository
                .findAll((root, query, cb) -> cb.and());
        List<ScrmTagGroupEntity> groups = groupRepository
                .findAll((root, query, cb) -> cb.and());
        long totalCustomers = customerRepository
                .count((root, query, cb) -> cb.and());
        // 按类型统计
        Map<String, Long> byType = tags.stream()
                .collect(Collectors.groupingBy(ScrmTagEntity::getTagType, Collectors.counting()));
        // 按分组统计
        Map<String, Long> byGroup = tags.stream()
                .filter(t -> t.getGroupId() != null)
                .collect(Collectors.groupingBy(t -> String.valueOf(t.getGroupId()), Collectors.counting()));
        // 已打标客户数 (去重)
        List<ScrmTagCustomerEntity> allRelations = tagCustomerRepository
                .findAll((root, query, cb) -> cb.and());
        long taggedCustomers = allRelations.stream()
                .map(ScrmTagCustomerEntity::getCustomerId)
                .distinct()
                .count();
        double coverageRate = totalCustomers > 0
                ? (double) taggedCustomers / totalCustomers * 100 : 0.0;

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalTags", tags.size());
        stats.put("totalGroups", groups.size());
        stats.put("totalCustomers", totalCustomers);
        stats.put("taggedCustomers", taggedCustomers);
        stats.put("coverageRate", coverageRate);
        stats.put("byType", byType);
        stats.put("byGroup", byGroup);
        return stats;
    }

    /**
     * 标签云: 按 customer_count 倒序返回热门标签。
     *
     * @param limit 返回数量 (默认 100)
     * @return 标签云列表 (tagName / tagCode / customerCount / color)
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getTagCloud(int limit) {
        List<ScrmTagEntity> tags = tagRepository
                .findAll((root, query, cb) -> cb.and());
        return tags.stream()
                .sorted((a, b) -> Integer.compare(
                        b.getCustomerCount() != null ? b.getCustomerCount() : 0,
                        a.getCustomerCount() != null ? a.getCustomerCount() : 0))
                .limit(limit > 0 ? limit : 100)
                .map(t -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("tagId", t.getId());
                    item.put("tagName", t.getTagName());
                    item.put("tagCode", t.getTagCode());
                    item.put("customerCount", t.getCustomerCount());
                    item.put("color", t.getColor());
                    return item;
                })
                .toList();
    }

    /**
     * 客户标签数。
     *
     * @param customerId 客户 ID
     * @return 标签数
     */
    @Transactional(readOnly = true)
    public long getCustomerTagCount(Long customerId) {
        return tagCustomerRepository.countByCustomerId(customerId);
    }

    /**
     * 分组覆盖率: 各分组的标签数与覆盖客户数。
     *
     * @return 分组覆盖率列表
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getGroupCoverage() {
        List<ScrmTagGroupEntity> groups = groupRepository
                .findAll((root, query, cb) -> cb.and());
        List<Map<String, Object>> result = new ArrayList<>();
        for (ScrmTagGroupEntity group : groups) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("groupId", group.getId());
            item.put("groupName", group.getGroupName());
            item.put("groupCode", group.getGroupCode());
            item.put("tagCount", group.getTagCount());
            item.put("customerCount", group.getCustomerCount());
            result.add(item);
        }
        return result;
    }

    /**
     * 规则统计: 各规则的执行次数与匹配率。
     * <p>matchedCount 为规则累计匹配客户数 (来自规则实体), lastExecutedAt 为最近执行时间。</p>
     *
     * @return 规则统计列表
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getRuleStats() {
        List<ScrmTagRuleEntity> rules = ruleRepository
                .findAll((root, query, cb) -> cb.and());
        List<Map<String, Object>> result = new ArrayList<>();
        for (ScrmTagRuleEntity rule : rules) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("ruleId", rule.getId());
            item.put("ruleName", rule.getRuleName());
            item.put("tagId", rule.getTagId());
            item.put("status", rule.getStatus());
            item.put("executionFrequency", rule.getExecutionFrequency());
            item.put("matchedCount", rule.getMatchedCount());
            item.put("lastExecutedAt", rule.getLastExecutedAt());
            result.add(item);
        }
        return result;
    }
}
