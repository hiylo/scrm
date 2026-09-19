/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmSegmentService.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.dto.ScrmSegmentCalculationResultDto;
import org.hiylo.scrm.dto.ScrmSegmentCompareDto;
import org.hiylo.scrm.dto.ScrmSegmentDto;
import org.hiylo.scrm.entity.ScrmCustomerEntity;
import org.hiylo.scrm.entity.ScrmSegmentEntity;
import org.hiylo.scrm.entity.ScrmSegmentHistoryEntity;
import org.hiylo.scrm.entity.ScrmSegmentMemberEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.repository.ScrmCustomerRepository;
import org.hiylo.scrm.repository.ScrmSegmentHistoryRepository;
import org.hiylo.scrm.repository.ScrmSegmentMemberRepository;
import org.hiylo.scrm.repository.ScrmSegmentRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * SCRM 客户分群服务。
 * <p>
 * 承载动态客户分群的核心能力: 分群定义增删改查与激活停用, 分群条件评估与成员动态计算
 * (单分群 / 批量 / 预览), 成员管理 (手动添加 / 批量添加 / 移除 / 资格校验), 历史快照记录
* 与趋势分析, 分群对比, 分群统计与重叠分析。所有写操作写入当前用户归属账号
* 实现数据隔离。
 * </p>
 * <p>
 * 计算流程: 加载分群条件 → 遍历当前账号客户 → 逐客户评估条件 ({@link CustomerConditionEvaluator}
 * 共享实现, 与工作流条件节点同一份逻辑) →
 * 命中客户加入分群, 不再命中客户标记离开 → 更新分群成员数与最后计算时间。条件评估支持
 * eq/ne/gt/lt/gte/lte/between/contains/in 操作符, 字段支持 customer_name / order_count /
 * total_amount / last_interaction_days / registration_days / engagement_score / customer_level /
 * tag / rfm_segment。
 * </p>
 *
 * @author Hsi Chu
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmSegmentService {

    /** 默认分群类型 */
    private static final String DEFAULT_SEGMENT_TYPE = "DYNAMIC";

    /** 默认条件组合 */
    private static final String DEFAULT_CONDITION_TYPE = "ALL";

    /** 默认状态 */
    private static final String DEFAULT_STATUS = "ACTIVE";

    /** 默认计算频率 */
    private static final String DEFAULT_CALCULATION_FREQUENCY = "DAILY";

    /** 默认是否自动更新 */
    private static final boolean DEFAULT_AUTO_UPDATE = true;

    /** 默认成员数初值 */
    private static final int DEFAULT_MEMBER_COUNT = 0;

    /** 静态分群类型 */
    private static final String SEGMENT_TYPE_STATIC = "STATIC";

    /** 条件组合: 全部满足 */
    private static final String CONDITION_TYPE_ALL = CustomerConditionEvaluator.CONDITION_TYPE_ALL;
    /** 条件组合: 任一满足 */
    private static final String CONDITION_TYPE_ANY = CustomerConditionEvaluator.CONDITION_TYPE_ANY;
    /** 条件组合: 全不满足 */
    private static final String CONDITION_TYPE_NONE = CustomerConditionEvaluator.CONDITION_TYPE_NONE;

    /** 成员来源: 自动计算 */
    private static final String SOURCE_AUTO = "AUTO";
    /** 成员来源: 手动添加 */
    private static final String SOURCE_MANUAL = "MANUAL";
    /** 成员来源: 导入 */
    private static final String SOURCE_IMPORT = "IMPORT";

    /** 复制分群编码后缀 */
    private static final String COPY_CODE_SUFFIX = "_copy";

    /** 复制分群名称前缀 */
    private static final String COPY_NAME_PREFIX = "副本-";

    /** 样本成员上限 (预览 / 计算结果返回) */
    private static final int SAMPLE_MEMBERS_LIMIT = 20;

    /** 默认匹配分数 */
    private static final double DEFAULT_MATCH_SCORE = 1.0;

    /** 合法的分群类型 */
    private static final List<String> VALID_SEGMENT_TYPES = List.of("DYNAMIC", "STATIC", "HYBRID");

    /** 合法的条件组合 */
    private static final List<String> VALID_CONDITION_TYPES = List.of(CONDITION_TYPE_ALL,
            CONDITION_TYPE_ANY, CONDITION_TYPE_NONE);

    /** 合法的状态 */
    private static final List<String> VALID_STATUS = List.of("ACTIVE", "INACTIVE", "DRAFT");

    /** 合法的计算频率 */
    private static final List<String> VALID_CALCULATION_FREQUENCIES =
            List.of("REALTIME", "HOURLY", "DAILY", "WEEKLY", "MANUAL");

    /** 合法的操作符 (与 {@link CustomerConditionEvaluator} 单一来源) */
    private static final List<String> VALID_OPERATORS = CustomerConditionEvaluator.VALID_OPERATORS;

    /** 可用条件字段 (与 {@link CustomerConditionEvaluator} 单一来源) */
    private static final List<String> AVAILABLE_FIELDS = CustomerConditionEvaluator.AVAILABLE_FIELDS;

    /** 分群数据访问层 */
    private final ScrmSegmentRepository segmentRepository;

    /** 分群成员数据访问层 */
    private final ScrmSegmentMemberRepository memberRepository;

    /** 分群历史数据访问层 */
    private final ScrmSegmentHistoryRepository historyRepository;

    /** 客户数据访问层 (查询客户属性用于条件评估) */
    private final ScrmCustomerRepository customerRepository;

    /** JSON 解析器 (解析 conditions / matchDetails) */
    private final ObjectMapper objectMapper;

    /** 客户条件评估共享组件 (与工作流条件节点复用同一实现) */
    private final CustomerConditionEvaluator conditionEvaluator;

    // ============================================================
    // 分群管理
    // ============================================================

    /**
     * 创建客户分群。
     * <p>校验 segmentCode 唯一与 conditions JSON 合法性后写入归属账号 ID 持久化,
     * segmentType / conditionType / status / calculationFrequency / autoUpdate 缺省时填默认值。</p>
     *
     * @param dto 分群参数
     * @return 创建后的分群
     * @throws ScrmException 参数非法 / 编码重复
     */
    @Transactional
    public ScrmSegmentEntity createSegment(ScrmSegmentDto dto) throws ScrmException {
        validateSegmentDto(dto, false);
        if (segmentRepository.existsBySegmentCode(dto.getSegmentCode())) {
            throw ScrmException.conflict("分群编码已存在: " + dto.getSegmentCode());
        }
        ScrmSegmentEntity entity = new ScrmSegmentEntity();
        entity.setSegmentName(dto.getSegmentName());
        entity.setSegmentCode(dto.getSegmentCode());
        entity.setDescription(dto.getDescription());
        entity.setSegmentType(dto.getSegmentType() != null ? dto.getSegmentType() : DEFAULT_SEGMENT_TYPE);
        entity.setCategory(dto.getCategory());
        entity.setConditionType(dto.getConditionType() != null ? dto.getConditionType() : DEFAULT_CONDITION_TYPE);
        entity.setConditions(dto.getConditions());
        entity.setStatus(dto.getStatus() != null ? dto.getStatus() : DEFAULT_STATUS);
        entity.setMemberCount(DEFAULT_MEMBER_COUNT);
        entity.setCalculationFrequency(dto.getCalculationFrequency() != null
                ? dto.getCalculationFrequency() : DEFAULT_CALCULATION_FREQUENCY);
        entity.setAutoUpdate(dto.getAutoUpdate() != null ? dto.getAutoUpdate() : DEFAULT_AUTO_UPDATE);
        entity.setColor(dto.getColor());
        entity.setIcon(dto.getIcon());
        entity.setTags(dto.getTags());
        entity.setCreatedBy(dto.getCreatedBy());
        entity = segmentRepository.save(entity);
        log.info("创建客户分群: id={}, segmentName={}, segmentCode={}, segmentType={}",
                entity.getId(), entity.getSegmentName(), entity.getSegmentCode(), entity.getSegmentType());
        return entity;
    }

    /**
     * 更新客户分群（字段非空才覆盖, segmentCode 不允许修改）。
     *
     * @param id  分群 ID
     * @param dto 分群参数
     * @return 更新后的分群
     * @throws ScrmException 分群不存在 / 参数非法
     */
    @Transactional
    public ScrmSegmentEntity updateSegment(Long id, ScrmSegmentDto dto) throws ScrmException {
        ScrmSegmentEntity entity = findSegmentOrThrow(id);
        validateSegmentDto(dto, true);
        if (dto.getSegmentName() != null) entity.setSegmentName(dto.getSegmentName());
        if (dto.getDescription() != null) entity.setDescription(dto.getDescription());
        if (dto.getSegmentType() != null) entity.setSegmentType(dto.getSegmentType());
        if (dto.getCategory() != null) entity.setCategory(dto.getCategory());
        if (dto.getConditionType() != null) entity.setConditionType(dto.getConditionType());
        if (dto.getConditions() != null) entity.setConditions(dto.getConditions());
        if (dto.getStatus() != null) entity.setStatus(dto.getStatus());
        if (dto.getCalculationFrequency() != null) entity.setCalculationFrequency(dto.getCalculationFrequency());
        if (dto.getAutoUpdate() != null) entity.setAutoUpdate(dto.getAutoUpdate());
        if (dto.getColor() != null) entity.setColor(dto.getColor());
        if (dto.getIcon() != null) entity.setIcon(dto.getIcon());
        if (dto.getTags() != null) entity.setTags(dto.getTags());
        if (dto.getCreatedBy() != null) entity.setCreatedBy(dto.getCreatedBy());
        entity = segmentRepository.save(entity);
        log.info("更新客户分群: id={}, segmentName={}", entity.getId(), entity.getSegmentName());
        return entity;
    }

    /**
     * 删除客户分群 (连同成员与历史快照一并清理)。
     *
     * @param id 分群 ID
     * @throws ScrmException 分群不存在
     */
    @Transactional
    public void deleteSegment(Long id) throws ScrmException {
        ScrmSegmentEntity entity = findSegmentOrThrow(id);
        // 清理成员与历史快照
        List<ScrmSegmentMemberEntity> members = memberRepository
                .findBySegmentIdAndIsCurrentMemberTrue(id);
        if (!members.isEmpty()) {
            memberRepository.deleteAll(members);
        }
        List<ScrmSegmentHistoryEntity> histories = historyRepository
                .findBySegmentIdAndSnapshotDateBetweenOrderBySnapshotDateAsc(
                         id, LocalDate.MIN, LocalDate.MAX);
        if (!histories.isEmpty()) {
            historyRepository.deleteAll(histories);
        }
        segmentRepository.delete(entity);
        log.info("删除客户分群: id={}, segmentName={}", id, entity.getSegmentName());
    }

    /**
     * 查询分群详情。
     *
     * @param id 分群 ID
     * @return 分群实体
     * @throws ScrmException 分群不存在
     */
    @Transactional(readOnly = true)
    public ScrmSegmentEntity getSegment(Long id) throws ScrmException {
        return findSegmentOrThrow(id);
    }

    /**
     * 按编码查询分群详情。
     *
     * @param code 分群编码
     * @return 分群实体
     * @throws ScrmException 分群不存在
     */
    @Transactional(readOnly = true)
    public ScrmSegmentEntity getSegmentByCode(String code) throws ScrmException {
        return segmentRepository.findBySegmentCode(code)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "分群不存在: code=" + code));
    }

    /**
     * 分页查询分群, 支持按类型、分类、状态与关键字过滤。
     *
     * @param segmentType 分群类型过滤（可空）
     * @param category    分类过滤（可空）
     * @param status      状态过滤（可空）
     * @param keyword     分群名称关键字模糊匹配（可空）
     * @param pageable    分页参数
     * @return 分群分页结果 (按 createTime DESC)
     */
    @Transactional(readOnly = true)
    public Page<ScrmSegmentEntity> listSegments(String segmentType, String category, String status,
                                                  String keyword, Pageable pageable) {
        Specification<ScrmSegmentEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (segmentType != null && !segmentType.isBlank()) {
                predicates.add(cb.equal(root.get("segmentType"), segmentType));
            }
            if (category != null && !category.isBlank()) {
                predicates.add(cb.equal(root.get("category"), category));
            }
            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (keyword != null && !keyword.isBlank()) {
                predicates.add(cb.like(root.get("segmentName"), "%" + keyword + "%"));
            }
            query.orderBy(cb.desc(root.get("createTime")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return segmentRepository.findAll(spec, pageable);
    }

    /**
     * 激活分群 (状态置为 ACTIVE)。
     *
     * @param id 分群 ID
     * @return 更新后的分群
     * @throws ScrmException 分群不存在
     */
    @Transactional
    public ScrmSegmentEntity activateSegment(Long id) throws ScrmException {
        ScrmSegmentEntity entity = findSegmentOrThrow(id);
        entity.setStatus(DEFAULT_STATUS);
        entity = segmentRepository.save(entity);
        log.info("激活客户分群: id={}, segmentName={}", id, entity.getSegmentName());
        return entity;
    }

    /**
     * 停用分群 (状态置为 INACTIVE)。
     *
     * @param id 分群 ID
     * @return 更新后的分群
     * @throws ScrmException 分群不存在
     */
    @Transactional
    public ScrmSegmentEntity deactivateSegment(Long id) throws ScrmException {
        ScrmSegmentEntity entity = findSegmentOrThrow(id);
        entity.setStatus("INACTIVE");
        entity = segmentRepository.save(entity);
        log.info("停用客户分群: id={}, segmentName={}", id, entity.getSegmentName());
        return entity;
    }

    /**
     * 复制分群 (创建副本, 编码追加 _copy 后缀, 名称加 "副本-" 前缀, 成员数归零)。
     *
     * @param id 源分群 ID
     * @return 复制创建的新分群
     * @throws ScrmException 源分群不存在 / 编码冲突
     */
    @Transactional
    public ScrmSegmentEntity copySegment(Long id) throws ScrmException {
        ScrmSegmentEntity source = findSegmentOrThrow(id);
        String newCode = source.getSegmentCode() + COPY_CODE_SUFFIX;
        if (segmentRepository.existsBySegmentCode(newCode)) {
            throw ScrmException.conflict("副本分群编码已存在: " + newCode);
        }
        ScrmSegmentEntity copy = new ScrmSegmentEntity();
        copy.setSegmentName(COPY_NAME_PREFIX + source.getSegmentName());
        copy.setSegmentCode(newCode);
        copy.setDescription(source.getDescription());
        copy.setSegmentType(source.getSegmentType());
        copy.setCategory(source.getCategory());
        copy.setConditionType(source.getConditionType());
        copy.setConditions(source.getConditions());
        copy.setStatus("DRAFT");
        copy.setMemberCount(DEFAULT_MEMBER_COUNT);
        copy.setCalculationFrequency(source.getCalculationFrequency());
        copy.setAutoUpdate(source.getAutoUpdate());
        copy.setColor(source.getColor());
        copy.setIcon(source.getIcon());
        copy.setTags(source.getTags());
        copy.setCreatedBy(source.getCreatedBy());
        copy = segmentRepository.save(copy);
        log.info("复制客户分群: sourceId={}, newId={}, newCode={}", id, copy.getId(), newCode);
        return copy;
    }

    /**
     * 将分群转为静态分群 (segmentType 置为 STATIC, autoUpdate 置为 false, 保留当前成员不再动态更新)。
     *
     * @param id 分群 ID
     * @return 更新后的分群
     * @throws ScrmException 分群不存在
     */
    @Transactional
    public ScrmSegmentEntity convertToStatic(Long id) throws ScrmException {
        ScrmSegmentEntity entity = findSegmentOrThrow(id);
        entity.setSegmentType(SEGMENT_TYPE_STATIC);
        entity.setAutoUpdate(false);
        entity.setCalculationFrequency("MANUAL");
        entity = segmentRepository.save(entity);
        log.info("客户分群转为静态: id={}, segmentName={}", id, entity.getSegmentName());
        return entity;
    }

    // ============================================================
    // 计算
    // ============================================================

    /**
     * 计算分群成员 (动态分群核心计算)。
     * <p>
     * 流程:
     * <ol>
*   <li>校验分群存在且归属当前账号</li>
*   <li>将分群 AUTO 来源的当前成员批量标记为已离开 (清场, 保留手动添加成员)</li>
*   <li>遍历账号客户, 构建客户上下文, 评估分群条件</li>
     *   <li>命中客户加入分群 (新建或重新激活成员关系)</li>
     *   <li>统计新增 / 流失成员数, 更新分群 memberCount 与 lastCalculatedAt</li>
     * </ol>
     * </p>
     *
     * @param id 分群 ID
     * @return 计算结果 (匹配数 / 新增数 / 流失数 / 样本成员)
     * @throws ScrmException 分群不存在
     */
    @Transactional
    public ScrmSegmentCalculationResultDto calculateSegment(Long id) throws ScrmException {
        ScrmSegmentEntity segment = findSegmentOrThrow(id);
        LocalDateTime now = LocalDateTime.now();
        // 清场: 将 AUTO 来源的当前成员标记为已离开
        List<ScrmSegmentMemberEntity> previousMembers = memberRepository
                .findBySegmentIdAndIsCurrentMemberTrue(id);
        Set<Long> previousAutoCustomerIds = previousMembers.stream()
                .filter(m -> SOURCE_AUTO.equals(m.getSource()))
                .map(ScrmSegmentMemberEntity::getCustomerId)
                .collect(Collectors.toSet());
        memberRepository.markAutoMembersLeft(id, now);
        // 遍历客户评估条件
        List<ScrmCustomerEntity> customers = customerRepository.findAll();
        List<Map<String, Object>> conditions = conditionEvaluator.parseConditions(segment.getConditions());
        String conditionType = segment.getConditionType();
        List<ScrmSegmentMemberEntity> newMembers = new ArrayList<>();
        List<Map<String, Object>> sampleMembers = new ArrayList<>();
        List<Long> matchedCustomerIds = new ArrayList<>();
        Map<Long, Map<String, Object>> matchDetailsByCustomer = new LinkedHashMap<>();
        for (ScrmCustomerEntity customer : customers) {
            Map<String, Object> matchDetails = conditionEvaluator.evaluate(customer, conditions, conditionType);
            if (matchDetails == null) {
                continue;
            }
            matchedCustomerIds.add(customer.getId());
            matchDetailsByCustomer.put(customer.getId(), matchDetails);
            if (sampleMembers.size() < SAMPLE_MEMBERS_LIMIT) {
                Map<String, Object> sample = new LinkedHashMap<>();
                sample.put("customerId", customer.getId());
                sample.put("customerName", customer.getNickname());
                sample.put("matchScore", DEFAULT_MATCH_SCORE);
                sampleMembers.add(sample);
            }
        }
        // 批量查询已有成员关系 (含已离开的), 避免循环内逐条查询
        Map<Long, ScrmSegmentMemberEntity> existingMemberMap = new LinkedHashMap<>();
        if (!matchedCustomerIds.isEmpty()) {
            existingMemberMap = memberRepository.findBySegmentIdAndCustomerIdIn(id, matchedCustomerIds).stream()
                    .collect(Collectors.toMap(ScrmSegmentMemberEntity::getCustomerId, m -> m, (a, b) -> a));
        }
        for (ScrmCustomerEntity customer : customers) {
            Map<String, Object> matchDetails = matchDetailsByCustomer.get(customer.getId());
            if (matchDetails == null) {
                continue;
            }
            // 查找已有成员关系 (含已离开的), 存在则重新激活, 否则新建
            ScrmSegmentMemberEntity existing = existingMemberMap.get(customer.getId());
            ScrmSegmentMemberEntity member;
            if (existing != null) {
                member = existing;
                member.setIsCurrentMember(true);
                member.setLeftAt(null);
                member.setJoinedAt(now);
                member.setMatchScore(DEFAULT_MATCH_SCORE);
                member.setMatchDetails(toJson(matchDetails));
                member.setSource(SOURCE_AUTO);
            } else {
                member = new ScrmSegmentMemberEntity();
                member.setSegmentId(id);
                member.setCustomerId(customer.getId());
                member.setCustomerName(customer.getNickname());
                member.setCustomerLevel(customer.getLifecycle());
                member.setJoinedAt(now);
                member.setMatchScore(DEFAULT_MATCH_SCORE);
                member.setMatchDetails(toJson(matchDetails));
                member.setIsCurrentMember(true);
                member.setSource(SOURCE_AUTO);
            }
            newMembers.add(member);
        }
        if (!newMembers.isEmpty()) {
            memberRepository.saveAll(newMembers);
        }
        // 统计新增 / 流失
        Set<Long> matchedCustomerIdSet = newMembers.stream()
                .map(ScrmSegmentMemberEntity::getCustomerId)
                .collect(Collectors.toSet());
        int addedCount = (int) matchedCustomerIdSet.stream()
                .filter(cid -> !previousAutoCustomerIds.contains(cid))
                .count();
        int removedCount = (int) previousAutoCustomerIds.stream()
                .filter(cid -> !matchedCustomerIdSet.contains(cid))
                .count();
        // 更新分群成员数与最后计算时间
        long currentMemberCount = memberRepository.countBySegmentIdAndIsCurrentMemberTrue(id);
        segment.setMemberCount((int) currentMemberCount);
        segment.setLastCalculatedAt(now);
        segmentRepository.save(segment);
        ScrmSegmentCalculationResultDto result = new ScrmSegmentCalculationResultDto();
        result.setSegmentId(id);
        result.setTotalMatched(newMembers.size());
        result.setAddedCount(addedCount);
        result.setRemovedCount(removedCount);
        result.setSampleMembers(sampleMembers);
        log.info("计算客户分群成员: segmentId={}, totalMatched={}, added={}, removed={}",
                id, newMembers.size(), addedCount, removedCount);
        return result;
    }

    /**
     * 批量计算所有活跃分群。
     * <p>遍历账号下状态为 ACTIVE 的分群逐一计算, 单个分群失败跳过, 不阻断其他分群。</p>
     *
     * @return 批量计算结果: {total, calculated, failed}
     */
    @Transactional
    public Map<String, Integer> batchCalculateSegments() {
        List<ScrmSegmentEntity> segments = segmentRepository.findByStatus(DEFAULT_STATUS);
        int calculated = 0;
        int failed = 0;
        for (ScrmSegmentEntity segment : segments) {
            try {
                calculateSegment(segment.getId());
                calculated++;
            } catch (Exception e) {
                failed++;
                log.warn("批量计算分群失败, 跳过: segmentId={}, err={}", segment.getId(), e.getMessage());
            }
        }
        Map<String, Integer> result = new LinkedHashMap<>();
        result.put("total", segments.size());
        result.put("calculated", calculated);
        result.put("failed", failed);
        log.info("批量计算分群完成:, total={}, calculated={}, failed={}", segments.size(), calculated, failed);
        return result;
    }

    /**
     * 预览分群 (不保存成员, 仅返回匹配数量与样本)。
     *
     * @param conditions    条件 JSON 字符串
     * @param conditionType 条件组合: ALL / ANY / NONE
     * @return 预览结果 (匹配数 / 样本成员)
     * @throws ScrmException 条件 JSON 非法
     */
    @Transactional(readOnly = true)
    public ScrmSegmentCalculationResultDto previewSegment(String conditions,
            String conditionType) throws ScrmException {
        if (conditions == null || conditions.isBlank()) {
            throw ScrmException.badRequest("条件 JSON 不能为空");
        }
        if (conditionType == null || conditionType.isBlank()) {
            conditionType = DEFAULT_CONDITION_TYPE;
        }
        if (!VALID_CONDITION_TYPES.contains(conditionType)) {
            throw ScrmException.badRequest(
                    "条件组合非法: " + conditionType + ", 仅支持 " + VALID_CONDITION_TYPES);
        }
        List<Map<String, Object>> parsedConditions = conditionEvaluator.parseConditions(conditions);
        if (parsedConditions.isEmpty()) {
            throw ScrmException.badRequest("条件 JSON 解析为空或格式错误");
        }
        List<ScrmCustomerEntity> customers = customerRepository.findAll();
        List<Map<String, Object>> sampleMembers = new ArrayList<>();
        int matched = 0;
        for (ScrmCustomerEntity customer : customers) {
            Map<String, Object> matchDetails = conditionEvaluator.evaluate(customer, parsedConditions, conditionType);
            if (matchDetails == null) {
                continue;
            }
            matched++;
            if (sampleMembers.size() < SAMPLE_MEMBERS_LIMIT) {
                Map<String, Object> sample = new LinkedHashMap<>();
                sample.put("customerId", customer.getId());
                sample.put("customerName", customer.getNickname());
                sample.put("matchScore", DEFAULT_MATCH_SCORE);
                sampleMembers.add(sample);
            }
        }
        ScrmSegmentCalculationResultDto result = new ScrmSegmentCalculationResultDto();
        result.setTotalMatched(matched);
        result.setAddedCount(matched);
        result.setRemovedCount(0);
        result.setSampleMembers(sampleMembers);
        log.info("预览分群:, conditionType={}, matched={}", conditionType, matched);
        return result;
    }

    /**
     * 获取分群计算结果 (实时计算当前匹配数, 不修改成员)。
     *
     * @param id 分群 ID
     * @return 计算结果
     * @throws ScrmException 分群不存在
     */
    @Transactional(readOnly = true)
    public ScrmSegmentCalculationResultDto getCalculationResult(Long id) throws ScrmException {
        ScrmSegmentEntity segment = findSegmentOrThrow(id);
        List<Map<String, Object>> conditions = conditionEvaluator.parseConditions(segment.getConditions());
        List<ScrmCustomerEntity> customers = customerRepository.findAll();
        List<Map<String, Object>> sampleMembers = new ArrayList<>();
        int matched = 0;
        for (ScrmCustomerEntity customer : customers) {
            Map<String, Object> matchDetails = conditionEvaluator.evaluate(
                    customer, conditions, segment.getConditionType());
            if (matchDetails == null) {
                continue;
            }
            matched++;
            if (sampleMembers.size() < SAMPLE_MEMBERS_LIMIT) {
                Map<String, Object> sample = new LinkedHashMap<>();
                sample.put("customerId", customer.getId());
                sample.put("customerName", customer.getNickname());
                sampleMembers.add(sample);
            }
        }
        ScrmSegmentCalculationResultDto result = new ScrmSegmentCalculationResultDto();
        result.setSegmentId(id);
        result.setTotalMatched(matched);
        result.setSampleMembers(sampleMembers);
        return result;
    }

    // ============================================================
    // 成员管理
    // ============================================================

    /**
     * 分页查询分群当前成员 (按加入时间倒序)。
     *
     * @param id       分群 ID
     * @param pageable 分页参数
     * @return 成员分页结果
     * @throws ScrmException 分群不存在
     */
    @Transactional(readOnly = true)
    public Page<ScrmSegmentMemberEntity> getMembers(Long id, Pageable pageable) throws ScrmException {
        findSegmentOrThrow(id);
        return memberRepository.findBySegmentIdAndIsCurrentMemberTrueOrderByJoinedAtDesc(
                 id, pageable);
    }

    /**
     * 获取分群当前成员数。
     *
     * @param id 分群 ID
     * @return 当前成员数
     * @throws ScrmException 分群不存在
     */
    @Transactional(readOnly = true)
    public long getMemberCount(Long id) throws ScrmException {
        findSegmentOrThrow(id);
        return memberRepository.countBySegmentIdAndIsCurrentMemberTrue(id);
    }

    /**
     * 手动添加分群成员。
     * <p>若成员关系已存在则重新激活 (isCurrentMember 置 true, 清空 leftAt), 否则新建。</p>
     *
     * @param segmentId  分群 ID
     * @param customerId 客户 ID
     * @param source     来源 (可空, 默认 MANUAL)
     * @return 成员实体
     * @throws ScrmException 分群 / 客户不存在
     */
    @Transactional
    public ScrmSegmentMemberEntity addMember(Long segmentId, Long customerId, String source) throws ScrmException {
        findSegmentOrThrow(segmentId);
        findCustomerOrThrow(customerId);
        String memberSource = source != null ? source : SOURCE_MANUAL;
        LocalDateTime now = LocalDateTime.now();
        Optional<ScrmSegmentMemberEntity> existing = memberRepository
                .findBySegmentIdAndCustomerId(segmentId, customerId);
        ScrmSegmentMemberEntity member;
        if (existing.isPresent()) {
            member = existing.get();
            member.setIsCurrentMember(true);
            member.setLeftAt(null);
            member.setJoinedAt(now);
            member.setSource(memberSource);
        } else {
            ScrmCustomerEntity customer = customerRepository.findById(customerId).orElse(null);
            member = new ScrmSegmentMemberEntity();
            member.setSegmentId(segmentId);
            member.setCustomerId(customerId);
            member.setCustomerName(customer != null ? customer.getNickname() : null);
            member.setCustomerLevel(customer != null ? customer.getLifecycle() : null);
            member.setJoinedAt(now);
            member.setMatchScore(DEFAULT_MATCH_SCORE);
            member.setIsCurrentMember(true);
            member.setSource(memberSource);
        }
        member = memberRepository.save(member);
        // 同步更新分群成员数
        updateSegmentMemberCount(segmentId);
        log.info("添加分群成员: segmentId={}, customerId={}, source={}", segmentId, customerId, memberSource);
        return member;
    }

    /**
     * 移除分群成员 (标记为已离开, 不删除记录)。
     *
     * @param segmentId  分群 ID
     * @param customerId 客户 ID
     * @throws ScrmException 分群不存在 / 成员不存在
     */
    @Transactional
    public void removeMember(Long segmentId, Long customerId) throws ScrmException {
        findSegmentOrThrow(segmentId);
        ScrmSegmentMemberEntity member = memberRepository
                .findBySegmentIdAndCustomerId(segmentId, customerId)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "分群成员不存在: segmentId=" + segmentId + ", customerId=" + customerId));
        member.setIsCurrentMember(false);
        member.setLeftAt(LocalDateTime.now());
        memberRepository.save(member);
        updateSegmentMemberCount(segmentId);
        log.info("移除分群成员: segmentId={}, customerId={}", segmentId, customerId);
    }

    /**
     * 批量添加分群成员。
     *
     * @param segmentId   分群 ID
     * @param customerIds 客户 ID 列表
     * @return 添加结果: {total, added, skipped}
     * @throws ScrmException 分群不存在
     */
    @Transactional
    public Map<String, Integer> batchAddMembers(Long segmentId, List<Long> customerIds) throws ScrmException {
        findSegmentOrThrow(segmentId);
        if (customerIds == null || customerIds.isEmpty()) {
            Map<String, Integer> empty = new LinkedHashMap<>();
            empty.put("total", 0);
            empty.put("added", 0);
            empty.put("skipped", 0);
            return empty;
        }
        int added = 0;
        int skipped = 0;
        List<Long> validCustomerIds = customerIds.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        skipped += customerIds.size() - validCustomerIds.size();
        List<ScrmSegmentMemberEntity> newMembers = new ArrayList<>();
        if (!validCustomerIds.isEmpty()) {
            // 批量加载客户, 缺失客户计为跳过
            List<ScrmCustomerEntity> customers = customerRepository.findAllById(validCustomerIds);
            Set<Long> existingCustomerIds = customers.stream()
                    .map(ScrmCustomerEntity::getId)
                    .collect(Collectors.toSet());
            skipped += validCustomerIds.size() - existingCustomerIds.size();
            Map<Long, ScrmCustomerEntity> customerMap = customers.stream()
                    .collect(Collectors.toMap(ScrmCustomerEntity::getId, c -> c, (a, b) -> a));
            // 批量查询已有成员关系, 避免循环内逐条查询
            Map<Long, ScrmSegmentMemberEntity> existingMemberMap = Collections.emptyMap();
            if (!existingCustomerIds.isEmpty()) {
                existingMemberMap = memberRepository.findBySegmentIdAndCustomerIdIn(segmentId, existingCustomerIds)
                        .stream()
                        .collect(Collectors.toMap(ScrmSegmentMemberEntity::getCustomerId, m -> m, (a, b) -> a));
            }
            LocalDateTime now = LocalDateTime.now();
            for (Long customerId : validCustomerIds) {
                ScrmCustomerEntity customer = customerMap.get(customerId);
                if (customer == null) {
                    continue;
                }
                try {
                    ScrmSegmentMemberEntity existing = existingMemberMap.get(customerId);
                    ScrmSegmentMemberEntity member;
                    if (existing != null) {
                        member = existing;
                        member.setIsCurrentMember(true);
                        member.setLeftAt(null);
                        member.setJoinedAt(now);
                        member.setSource(SOURCE_IMPORT);
                    } else {
                        member = new ScrmSegmentMemberEntity();
                        member.setSegmentId(segmentId);
                        member.setCustomerId(customerId);
                        member.setCustomerName(customer.getNickname());
                        member.setCustomerLevel(customer.getLifecycle());
                        member.setJoinedAt(now);
                        member.setMatchScore(DEFAULT_MATCH_SCORE);
                        member.setIsCurrentMember(true);
                        member.setSource(SOURCE_IMPORT);
                    }
                    newMembers.add(member);
                    added++;
                } catch (Exception e) {
                    skipped++;
                    log.warn("批量添加分群成员失败, 跳过: segmentId={}, customerId={}, err={}",
                            segmentId, customerId, e.getMessage());
                }
            }
        }
        if (!newMembers.isEmpty()) {
            memberRepository.saveAll(newMembers);
            updateSegmentMemberCount(segmentId);
        }
        Map<String, Integer> result = new LinkedHashMap<>();
        result.put("total", customerIds.size());
        result.put("added", added);
        result.put("skipped", skipped);
        log.info("批量添加分群成员: segmentId={}, total={}, added={}, skipped={}",
                segmentId, customerIds.size(), added, skipped);
        return result;
    }

    /**
     * 检查客户是否为分群当前成员。
     *
     * @param segmentId  分群 ID
     * @param customerId 客户 ID
     * @return 成员关系 (不存在返回 null)
     * @throws ScrmException 分群不存在
     */
    @Transactional(readOnly = true)
    public ScrmSegmentMemberEntity checkMembership(Long segmentId, Long customerId) throws ScrmException {
        findSegmentOrThrow(segmentId);
        return memberRepository.findBySegmentIdAndCustomerId(segmentId, customerId)
                .orElse(null);
    }

    /**
     * 获取客户所在的所有当前分群成员关系。
     *
     * @param customerId 客户 ID
     * @return 成员关系列表
     */
    @Transactional(readOnly = true)
    public List<ScrmSegmentMemberEntity> getCustomerSegments(Long customerId) {
        return memberRepository.findByCustomerIdAndIsCurrentMemberTrue(customerId);
    }

    // ============================================================
    // 历史
    // ============================================================

    /**
     * 记录分群当日快照。
     * <p>若当日快照已存在则更新, 否则新建。快照包含成员数、新增 / 流失数、平均消费指标与等级分布。</p>
     *
     * @param segmentId 分群 ID
     * @return 历史快照实体
     * @throws ScrmException 分群不存在
     */
    @Transactional
    public ScrmSegmentHistoryEntity recordHistory(Long segmentId) throws ScrmException {
        ScrmSegmentEntity segment = findSegmentOrThrow(segmentId);
        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();
        List<ScrmSegmentMemberEntity> currentMembers = memberRepository
                .findBySegmentIdAndIsCurrentMemberTrue(segmentId);
        int memberCount = currentMembers.size();
        // 等级分布 (基于 customerLevel 字段)
        Map<String, Integer> levelDistribution = new LinkedHashMap<>();
        for (ScrmSegmentMemberEntity member : currentMembers) {
            String level = member.getCustomerLevel() != null ? member.getCustomerLevel() : "UNKNOWN";
            levelDistribution.merge(level, 1, Integer::sum);
        }
        // 查询是否已有当日快照
        List<ScrmSegmentHistoryEntity> existing = historyRepository
                .findBySegmentIdAndSnapshotDateBetweenOrderBySnapshotDateAsc(
                         segmentId, today, today);
        ScrmSegmentHistoryEntity history;
        if (!existing.isEmpty()) {
            history = existing.get(0);
        } else {
            history = new ScrmSegmentHistoryEntity();
            history.setSegmentId(segmentId);
        }
        history.setSnapshotDate(today);
        history.setMemberCount(memberCount);
        history.setAddedCount(0);
        history.setRemovedCount(0);
        history.setAvgOrderCount(0.0);
        history.setAvgTotalAmount(0.0);
        history.setAvgEngagementScore(0.0);
        history.setTopLevels(toJson(levelDistribution));
        history.setTopTags(segment.getTags());
        history.setCalculatedAt(now);
        history = historyRepository.save(history);
        log.info("记录分群历史快照: segmentId={}, snapshotDate={}, memberCount={}",
                segmentId, today, memberCount);
        return history;
    }

    /**
     * 分页查询分群历史快照 (按快照日期倒序)。
     *
     * @param segmentId 分群 ID
     * @param pageable  分页参数
     * @return 历史快照分页结果
     * @throws ScrmException 分群不存在
     */
    @Transactional(readOnly = true)
    public Page<ScrmSegmentHistoryEntity> getHistory(Long segmentId, Pageable pageable) throws ScrmException {
        findSegmentOrThrow(segmentId);
        return historyRepository.findBySegmentIdOrderBySnapshotDateDesc(
                 segmentId, pageable);
    }

    /**
     * 分群趋势 (近 N 天成员数变化)。
     *
     * @param segmentId 分群 ID
     * @param days      天数 (默认 30)
     * @return 趋势列表 [{snapshotDate, memberCount, addedCount, removedCount}]
     * @throws ScrmException 分群不存在
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getSegmentTrend(Long segmentId, Integer days) throws ScrmException {
        findSegmentOrThrow(segmentId);
        int queryDays = days != null && days > 0 ? days : 30;
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(queryDays - 1L);
        List<ScrmSegmentHistoryEntity> histories = historyRepository
                .findBySegmentIdAndSnapshotDateBetweenOrderBySnapshotDateAsc(
                         segmentId, startDate, endDate);
        List<Map<String, Object>> trend = new ArrayList<>();
        for (ScrmSegmentHistoryEntity history : histories) {
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("snapshotDate", history.getSnapshotDate());
            point.put("memberCount", history.getMemberCount());
            point.put("addedCount", history.getAddedCount());
            point.put("removedCount", history.getRemovedCount());
            trend.add(point);
        }
        return trend;
    }

    /**
     * 对比两个分群 (交集 / 差集 / 各维度对比)。
     *
     * @param compareDto 对比参数 (segmentId1, segmentId2)
     * @return 对比结果 {segment1, segment2, intersection, onlyIn1, onlyIn2, intersectionCount, onlyIn1Count, onlyIn2Count}
     * @throws ScrmException 分群不存在
     */
    @Transactional(readOnly = true)
    public Map<String, Object> compareSegments(ScrmSegmentCompareDto compareDto) throws ScrmException {
        if (compareDto == null) {
            throw ScrmException.badRequest("对比参数不能为空");
        }
        ScrmSegmentEntity segment1 = findSegmentOrThrow(compareDto.getSegmentId1());
        ScrmSegmentEntity segment2 = findSegmentOrThrow(compareDto.getSegmentId2());
        Set<Long> members1 = memberRepository.findBySegmentIdAndIsCurrentMemberTrue(
                         segment1.getId()).stream()
                .map(ScrmSegmentMemberEntity::getCustomerId)
                .collect(Collectors.toSet());
        Set<Long> members2 = memberRepository.findBySegmentIdAndIsCurrentMemberTrue(
                         segment2.getId()).stream()
                .map(ScrmSegmentMemberEntity::getCustomerId)
                .collect(Collectors.toSet());
        Set<Long> intersection = new HashSet<>(members1);
        intersection.retainAll(members2);
        Set<Long> onlyIn1 = new HashSet<>(members1);
        onlyIn1.removeAll(members2);
        Set<Long> onlyIn2 = new HashSet<>(members2);
        onlyIn2.removeAll(members1);
        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, Object> seg1Info = new LinkedHashMap<>();
        seg1Info.put("id", segment1.getId());
        seg1Info.put("segmentName", segment1.getSegmentName());
        seg1Info.put("memberCount", members1.size());
        result.put("segment1", seg1Info);
        Map<String, Object> seg2Info = new LinkedHashMap<>();
        seg2Info.put("id", segment2.getId());
        seg2Info.put("segmentName", segment2.getSegmentName());
        seg2Info.put("memberCount", members2.size());
        result.put("segment2", seg2Info);
        result.put("intersection", intersection);
        result.put("onlyIn1", onlyIn1);
        result.put("onlyIn2", onlyIn2);
        result.put("intersectionCount", intersection.size());
        result.put("onlyIn1Count", onlyIn1.size());
        result.put("onlyIn2Count", onlyIn2.size());
        return result;
    }

    // ============================================================
    // 统计
    // ============================================================

    /**
     * 分群统计概览: 总数、各类型数、各状态数、总覆盖客户数。
     *
     * @return 统计结果 Map
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getSegmentStats() {
        List<ScrmSegmentEntity> segments = segmentRepository.findAll(
                (root, query, cb) -> cb.and());
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total", segments.size());
        // 各类型数
        Map<String, Long> typeCount = new LinkedHashMap<>();
        for (String type : VALID_SEGMENT_TYPES) {
            typeCount.put(type, 0L);
        }
        for (ScrmSegmentEntity segment : segments) {
            typeCount.merge(segment.getSegmentType(), 1L, Long::sum);
        }
        stats.put("typeCount", typeCount);
        // 各状态数
        Map<String, Long> statusCount = new LinkedHashMap<>();
        for (String status : VALID_STATUS) {
            statusCount.put(status, 0L);
        }
        for (ScrmSegmentEntity segment : segments) {
            statusCount.merge(segment.getStatus(), 1L, Long::sum);
        }
        stats.put("statusCount", statusCount);
        // 总覆盖客户数 (被至少一个分群覆盖)
        long coveredCustomers = memberRepository.countDistinctCurrentMemberCustomers();
        stats.put("coveredCustomers", coveredCustomers);
        return stats;
    }

    /**
     * 分类分布 (按 category 聚合分群数与成员总数)。
     *
     * @return 分类分布列表 [{category, segmentCount, totalMembers}]
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getCategoryDistribution() {
        List<ScrmSegmentEntity> segments = segmentRepository.findAll(
                (root, query, cb) -> cb.and());
        Map<String, List<ScrmSegmentEntity>> grouped = new LinkedHashMap<>();
        for (ScrmSegmentEntity segment : segments) {
            String category = segment.getCategory() != null ? segment.getCategory() : "UNCATEGORIZED";
            grouped.computeIfAbsent(category, k -> new ArrayList<>()).add(segment);
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<String, List<ScrmSegmentEntity>> entry : grouped.entrySet()) {
            Map<String, Object> dist = new LinkedHashMap<>();
            dist.put("category", entry.getKey());
            dist.put("segmentCount", entry.getValue().size());
            int totalMembers = entry.getValue().stream()
                    .mapToInt(s -> s.getMemberCount() != null ? s.getMemberCount() : 0)
                    .sum();
            dist.put("totalMembers", totalMembers);
            result.add(dist);
        }
        return result;
    }

    /**
     * 最大分群 (按成员数降序取前 N)。
     *
     * @param limit 返回数量 (默认 10)
     * @return 分群列表 [{id, segmentName, segmentCode, memberCount, segmentType, category}]
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getTopSegments(Integer limit) {
        int queryLimit = limit != null && limit > 0 ? limit : 10;
        List<ScrmSegmentEntity> segments = segmentRepository.findAll(
                (root, query, cb) -> cb.and(),
                PageRequest.of(0, queryLimit,
                        Sort.by(Sort.Direction.DESC, "memberCount"))).getContent();
        List<Map<String, Object>> result = new ArrayList<>();
        for (ScrmSegmentEntity segment : segments) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", segment.getId());
            entry.put("segmentName", segment.getSegmentName());
            entry.put("segmentCode", segment.getSegmentCode());
            entry.put("memberCount", segment.getMemberCount());
            entry.put("segmentType", segment.getSegmentType());
            entry.put("category", segment.getCategory());
            result.add(entry);
        }
        return result;
    }

    /**
     * 分群重叠分析 (客户同时属于多个分群)。
     *
     * @return 重叠分析结果 [{customerId, customerName, segmentCount, segmentIds}]
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getSegmentOverlap() {
        List<ScrmSegmentEntity> segments = segmentRepository.findAll(
                (root, query, cb) -> cb.and());
        // 批量查询所有分群当前成员, 按分群 ID 分组, 避免循环内逐分群查询
        List<Long> segmentIds = segments.stream()
                .map(ScrmSegmentEntity::getId)
                .collect(Collectors.toList());
        Map<Long, List<ScrmSegmentMemberEntity>> membersBySegment = new LinkedHashMap<>();
        if (!segmentIds.isEmpty()) {
            membersBySegment = memberRepository.findBySegmentIdInAndIsCurrentMemberTrue(segmentIds).stream()
                    .collect(Collectors.groupingBy(ScrmSegmentMemberEntity::getSegmentId));
        }
        // 收集每个客户所属的分群
        Map<Long, List<Long>> customerSegments = new LinkedHashMap<>();
        Map<Long, String> customerNames = new LinkedHashMap<>();
        for (ScrmSegmentEntity segment : segments) {
            List<ScrmSegmentMemberEntity> members = membersBySegment.getOrDefault(
                    segment.getId(), Collections.emptyList());
            for (ScrmSegmentMemberEntity member : members) {
                customerSegments.computeIfAbsent(member.getCustomerId(), k -> new ArrayList<>())
                        .add(segment.getId());
                if (member.getCustomerName() != null) {
                    customerNames.putIfAbsent(member.getCustomerId(), member.getCustomerName());
                }
            }
        }
        // 仅返回属于多个分群的客户
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<Long, List<Long>> entry : customerSegments.entrySet()) {
            if (entry.getValue().size() < 2) {
                continue;
            }
            Map<String, Object> overlap = new LinkedHashMap<>();
            overlap.put("customerId", entry.getKey());
            overlap.put("customerName", customerNames.get(entry.getKey()));
            overlap.put("segmentCount", entry.getValue().size());
            overlap.put("segmentIds", entry.getValue());
            result.add(overlap);
        }
        result.sort((a, b) -> Integer.compare((Integer) b.get("segmentCount"), (Integer) a.get("segmentCount")));
        return result;
    }

    // ============================================================
    // 条件评估
    // ============================================================

    /**
     * 评估单个条件 (基于 {@link CustomerConditionEvaluator} 共享实现)。
     * <p>根据 field 从客户上下文取值, 按 operator 与 value 比较。支持 eq/ne/gt/lt/gte/lte/
     * between/contains/in 操作符。</p>
     *
     * @param customerId 客户 ID
     * @param field      条件字段
     * @param operator   操作符
     * @param value      条件值
     * @return 条件是否满足
     */
    @Transactional(readOnly = true)
    public boolean evaluateCondition(Long customerId, String field, String operator, Object value) {
        ScrmCustomerEntity customer = customerRepository.findById(customerId).orElse(null);
        if (customer == null) {
            return false;
        }
        Map<String, Object> context = conditionEvaluator.buildCustomerContext(customer);
        return conditionEvaluator.evaluateSingle(context.get(field), operator, value);
    }

    // ============================================================
    // 内部工具方法
    // ============================================================

    /**
     * 校验分群参数。
     * <p>
     * 创建场景 (partial=false): segmentName / segmentCode / conditions 必填。
     * 更新场景 (partial=true): 允许字段为空, 仅校验非空字段的合法性。conditions 非空时校验 JSON 可解析。
     * </p>
     *
     * @param dto     分群参数
     * @param partial 是否为部分更新场景
     * @throws ScrmException 参数非法
     */
    private void validateSegmentDto(ScrmSegmentDto dto, boolean partial) throws ScrmException {
        if (dto == null) {
            throw ScrmException.badRequest("分群参数不能为空");
        }
        if (dto.getSegmentName() != null) {
            if (dto.getSegmentName().isBlank()) {
                throw ScrmException.badRequest("分群名称不能为空");
            }
        } else if (!partial) {
            throw ScrmException.badRequest("分群名称不能为空");
        }
        if (dto.getSegmentCode() != null) {
            if (dto.getSegmentCode().isBlank()) {
                throw ScrmException.badRequest("分群编码不能为空");
            }
        } else if (!partial) {
            throw ScrmException.badRequest("分群编码不能为空");
        }
        if (dto.getSegmentType() != null && !VALID_SEGMENT_TYPES.contains(dto.getSegmentType())) {
            throw ScrmException.badRequest(
                    "分群类型非法: " + dto.getSegmentType() + ", 仅支持 " + VALID_SEGMENT_TYPES);
        }
        if (dto.getConditionType() != null && !VALID_CONDITION_TYPES.contains(dto.getConditionType())) {
            throw ScrmException.badRequest(
                    "条件组合非法: " + dto.getConditionType() + ", 仅支持 " + VALID_CONDITION_TYPES);
        }
        if (dto.getStatus() != null && !VALID_STATUS.contains(dto.getStatus())) {
            throw ScrmException.badRequest(
                    "状态非法: " + dto.getStatus() + ", 仅支持 " + VALID_STATUS);
        }
        if (dto.getCalculationFrequency() != null && !VALID_CALCULATION_FREQUENCIES.contains(dto.getCalculationFrequency())) {
            throw ScrmException.badRequest(
                    "计算频率非法: " + dto.getCalculationFrequency() + ", 仅支持 " + VALID_CALCULATION_FREQUENCIES);
        }
        if (dto.getConditions() != null) {
            if (dto.getConditions().isBlank()) {
                throw ScrmException.badRequest("条件 JSON 不能为空");
            }
            try {
                List<Map<String, Object>> parsed = objectMapper.readValue(
                        dto.getConditions(), new TypeReference<List<Map<String, Object>>>() {});
                for (Map<String, Object> condition : parsed) {
                    String field = (String) condition.get("field");
                    String operator = (String) condition.get("operator");
                    if (field == null || field.isBlank()) {
                        throw ScrmException.badRequest("条件字段不能为空: " + condition);
                    }
                    if (!AVAILABLE_FIELDS.contains(field)) {
                        throw ScrmException.badRequest(
                                "条件字段非法: " + field + ", 仅支持 " + AVAILABLE_FIELDS);
                    }
                    if (operator == null || !VALID_OPERATORS.contains(operator)) {
                        throw ScrmException.badRequest(
                                "操作符非法: " + operator + ", 仅支持 " + VALID_OPERATORS);
                    }
                }
            } catch (ScrmException e) {
                throw e;
            } catch (Exception e) {
                throw ScrmException.badRequest("条件 JSON 解析失败: " + e.getMessage());
            }
        } else if (!partial) {
            throw ScrmException.badRequest("条件 JSON 不能为空");
        }
    }

    /**
     * 将对象序列化为 JSON 字符串。
     *
     * @param obj 对象
     * @return JSON 字符串, 序列化失败返回 "{}"
     */
    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            log.warn("对象序列化为 JSON 失败: {}", e.getMessage());
            return "{}";
        }
    }

    /**
     * 同步更新分群成员数 (成员增删后调用)。
     *
     * @param segmentId 分群 ID
     */
    private void updateSegmentMemberCount(Long segmentId) {
        try {
            long count = memberRepository.countBySegmentIdAndIsCurrentMemberTrue(segmentId);
            segmentRepository.findById(segmentId).ifPresent(segment -> {
                segment.setMemberCount((int) count);
                segmentRepository.save(segment);
            });
        } catch (Exception e) {
            log.warn("更新分群成员数失败, 忽略: segmentId={}, err={}", segmentId, e.getMessage());
        }
    }

    /**
     * 按主键查询分群, 不存在抛异常, 并校验归属账号。
     *
     * @param id 分群 ID
     * @return 分群实体
     * @throws ScrmException 分群不存在
     */
    private ScrmSegmentEntity findSegmentOrThrow(Long id) throws ScrmException {
        ScrmSegmentEntity entity = segmentRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "客户分群不存在: id=" + id));
        return entity;
    }

    /**
     * 按主键查询客户, 不存在抛异常, 并校验归属账号。
     *
     * @param id 客户 ID
     * @return 客户实体
     * @throws ScrmException 客户不存在
     */
    private ScrmCustomerEntity findCustomerOrThrow(Long id) throws ScrmException {
        ScrmCustomerEntity customer = customerRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "客户不存在: id=" + id));
        return customer;
    }

}
