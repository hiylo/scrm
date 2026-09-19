/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCustomerLifecycleStageService.java
 * Date : 2026/09/19 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.dto.ScrmLifecycleStageDto;
import org.hiylo.scrm.entity.ScrmLifecycleStageEntity;
import org.hiylo.scrm.entity.ScrmLifecycleTransitionEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.repository.ScrmLifecycleStageRepository;
import org.hiylo.scrm.repository.ScrmLifecycleTransitionRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * SCRM 客户生命周期阶段管理服务。
 * <p>
 * 承载生命周期阶段的管理能力: 阶段 CRUD / 启停 / 编码与类别查询 / 重排 / 统计刷新 /
 * 阶段树 / 上下阶段 / 阶段流转图。部分写操作复用 {@link ScrmLifecycleService} 的既有能力。
 * </p>
 *
 * @author Hsi Chu
 * @since 2026/09/19
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmCustomerLifecycleStageService {

    /** 既有生命周期服务 (复用阶段定义 / 流转规则 / 转换历史能力) */
    private final ScrmLifecycleService scrmLifecycleService;

    /** 阶段数据访问层 */
    private final ScrmLifecycleStageRepository stageRepository;

    /** 流转规则数据访问层 */
    private final ScrmLifecycleTransitionRepository transitionRepository;

    /**
     * 创建生命周期阶段。
     *
     * @param dto 阶段参数
     * @return 创建后的阶段
     * @throws ScrmException 参数非法 / 编码重复
     */
    @Transactional
    public ScrmLifecycleStageEntity createStage(ScrmLifecycleStageDto dto) throws ScrmException {
        return scrmLifecycleService.createStage(dto);
    }

    /**
     * 更新生命周期阶段 (字段非空才覆盖)。
     *
     * @param id  阶段 ID
     * @param dto 阶段参数
     * @return 更新后的阶段
     * @throws ScrmException 阶段不存在 / 参数非法 / 编码重复
     */
    @Transactional
    public ScrmLifecycleStageEntity updateStage(Long id, ScrmLifecycleStageDto dto) throws ScrmException {
        return scrmLifecycleService.updateStage(id, dto);
    }

    /**
     * 删除生命周期阶段 (阶段下有客户时拒绝)。
     *
     * @param id 阶段 ID
     * @throws ScrmException 阶段不存在 / 阶段下仍有客户
     */
    @Transactional
    public void deleteStage(Long id) throws ScrmException {
        scrmLifecycleService.deleteStage(id);
    }

    /**
     * 查询阶段详情。
     *
     * @param id 阶段 ID
     * @return 阶段实体
     * @throws ScrmException 阶段不存在
     */
    @Transactional(readOnly = true)
    public ScrmLifecycleStageEntity getStage(Long id) throws ScrmException {
        return scrmLifecycleService.getStage(id);
    }

    /**
     * 按编码查询阶段。
     *
     * @param code 阶段编码
     * @return 阶段实体
     * @throws ScrmException 阶段不存在
     */
    @Transactional(readOnly = true)
    public ScrmLifecycleStageEntity getStageByCode(String code) throws ScrmException {
        return scrmLifecycleService.getStageByCode(code);
    }

    /**
     * 分页查询阶段, 支持按类别 / 启用状态过滤。
     *
     * @param stageCategory 阶段类别过滤 (可空)
     * @param enabled       启用状态过滤 (可空)
     * @param pageable      分页参数
     * @return 阶段分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmLifecycleStageEntity> listStages(String stageCategory, Boolean enabled, Pageable pageable) {
        return scrmLifecycleService.listStages(stageCategory, enabled, pageable);
    }

    /**
     * 按阶段类别查询全部阶段 (按 stageOrder ASC)。
     *
     * @param category 阶段类别
     * @return 阶段列表
     * @throws ScrmException 阶段类别为空
     */
    @Transactional(readOnly = true)
    public List<ScrmLifecycleStageEntity> getStagesByCategory(String category) throws ScrmException {
        if (category == null || category.isBlank()) {
            throw ScrmException.badRequest("阶段类别不能为空");
        }
        return stageRepository.findAllByOrderByStageOrderAsc().stream()
                .filter(s -> category.equals(s.getStageCategory()))
                .collect(Collectors.toList());
    }

    /**
     * 阶段树: 按类别分组, 每组按 stageOrder ASC。
     *
     * @return 阶段树 (类别 → 阶段列表)
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getStageTree() {
        List<ScrmLifecycleStageEntity> stages = stageRepository.findAllByOrderByStageOrderAsc();
        Map<String, List<ScrmLifecycleStageEntity>> grouped = stages.stream()
                .collect(Collectors.groupingBy(ScrmLifecycleStageEntity::getStageCategory,
                        LinkedHashMap::new, Collectors.toList()));
        Map<String, Object> tree = new LinkedHashMap<>();
        tree.put("categories", grouped.keySet());
        tree.put("tree", grouped);
        tree.put("total", stages.size());
        return tree;
    }

    /**
     * 查询指定阶段的下一阶段 (stageOrder 更大的阶段)。
     *
     * @param id 阶段 ID
     * @return 下一阶段列表
     * @throws ScrmException 阶段不存在
     */
    @Transactional(readOnly = true)
    public List<ScrmLifecycleStageEntity> getNextStages(Long id) throws ScrmException {
        ScrmLifecycleStageEntity current = getStage(id);
        return stageRepository.findAllByOrderByStageOrderAsc().stream()
                .filter(s -> s.getStageOrder() != null && current.getStageOrder() != null && s.getStageOrder() > current.getStageOrder())
                .collect(Collectors.toList());
    }

    /**
     * 查询指定阶段的上一阶段 (stageOrder 更小的阶段)。
     *
     * @param id 阶段 ID
     * @return 上一阶段列表
     * @throws ScrmException 阶段不存在
     */
    @Transactional(readOnly = true)
    public List<ScrmLifecycleStageEntity> getPreviousStages(Long id) throws ScrmException {
        ScrmLifecycleStageEntity current = getStage(id);
        return stageRepository.findAllByOrderByStageOrderAsc().stream()
                .filter(s -> s.getStageOrder() != null && current.getStageOrder() != null && s.getStageOrder() < current.getStageOrder())
                .collect(Collectors.toList());
    }

    /**
     * 启用阶段。
     *
     * @param id 阶段 ID
     * @return 更新后的阶段
     * @throws ScrmException 阶段不存在
     */
    @Transactional
    public ScrmLifecycleStageEntity enableStage(Long id) throws ScrmException {
        return scrmLifecycleService.enableStage(id);
    }

    /**
     * 禁用阶段。
     *
     * @param id 阶段 ID
     * @return 更新后的阶段
     * @throws ScrmException 阶段不存在
     */
    @Transactional
    public ScrmLifecycleStageEntity disableStage(Long id) throws ScrmException {
        return scrmLifecycleService.disableStage(id);
    }

    /**
     * 刷新阶段统计 (客户数 / 平均停留 / 转化率)。
     *
     * @param id 阶段 ID
     * @return 更新后的阶段
     * @throws ScrmException 阶段不存在
     */
    @Transactional
    public ScrmLifecycleStageEntity updateStageStats(Long id) throws ScrmException {
        return scrmLifecycleService.updateStageStats(id);
    }

    /**
     * 重排阶段顺序。
     *
     * @param stageOrders 阶段 ID → 新顺序映射
     * @return 更新后的阶段列表 (按 stageOrder ASC)
     * @throws ScrmException 阶段不存在 / 顺序映射为空
     */
    @Transactional
    public List<ScrmLifecycleStageEntity> reorderStages(Map<Long, Integer> stageOrders) throws ScrmException {
        return scrmLifecycleService.reorderStages(stageOrders);
    }

    /**
     * 阶段流转图: 节点 (阶段) + 边 (流转规则)。
     *
     * @return 流转图 {nodes, edges}
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getStageFlow() {
        List<ScrmLifecycleStageEntity> stages = stageRepository.findAllByOrderByStageOrderAsc();
        List<Map<String, Object>> nodes = new ArrayList<>();
        for (ScrmLifecycleStageEntity s : stages) {
            Map<String, Object> n = new LinkedHashMap<>();
            n.put("id", s.getId());
            n.put("stageName", s.getStageName());
            n.put("stageCode", s.getStageCode());
            n.put("stageCategory", s.getStageCategory());
            n.put("stageOrder", s.getStageOrder());
            n.put("isStartStage", s.getIsStartStage());
            n.put("isEndStage", s.getIsEndStage());
            n.put("isChurnStage", s.getIsChurnStage());
            n.put("enabled", s.getEnabled());
            nodes.add(n);
        }
        List<ScrmLifecycleTransitionEntity> rules = transitionRepository
                .findByIsEnabledOrderByPriorityDesc(Boolean.TRUE);
        List<Map<String, Object>> edges = new ArrayList<>();
        for (ScrmLifecycleTransitionEntity r : rules) {
            Map<String, Object> e = new LinkedHashMap<>();
            e.put("transitionId", r.getId());
            e.put("fromStageId", r.getFromStageId());
            e.put("toStageId", r.getToStageId());
            e.put("transitionType", r.getTransitionType());
            e.put("priority", r.getPriority());
            edges.add(e);
        }
        Map<String, Object> flow = new LinkedHashMap<>();
        flow.put("nodes", nodes);
        flow.put("edges", edges);
        return flow;
    }
}
