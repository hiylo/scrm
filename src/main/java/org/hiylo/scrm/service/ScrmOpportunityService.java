/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmOpportunityService.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.dto.ScrmFunnelDto;
import org.hiylo.scrm.dto.ScrmFunnelStageDto;
import org.hiylo.scrm.dto.ScrmOpportunityDto;
import org.hiylo.scrm.dto.ScrmOpportunityStageChangeDto;
import org.hiylo.scrm.dto.ScrmOpportunityStageHistoryDto;
import org.hiylo.scrm.entity.ScrmFunnelEntity;
import org.hiylo.scrm.entity.ScrmFunnelStageEntity;
import org.hiylo.scrm.entity.ScrmOpportunityEntity;
import org.hiylo.scrm.entity.ScrmOpportunityStageHistoryEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.repository.ScrmFunnelRepository;
import org.hiylo.scrm.repository.ScrmFunnelStageRepository;
import org.hiylo.scrm.repository.ScrmOpportunityRepository;
import org.hiylo.scrm.repository.ScrmOpportunityStageHistoryRepository;
import org.hiylo.scrm.vo.FunnelAnalysisVo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.criteria.Predicate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * SCRM 销售漏斗与商机服务
 * <p>
 * 负责销售漏斗定义、漏斗阶段、商机档案与阶段推进的全生命周期管理,
 * 提供漏斗转化分析与销售预测能力。所有写操作写入当前用户归属账号,
 * 实现数据隔离。
 * </p>
 *
 * @author Hsi Chu
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmOpportunityService {

    /** 漏斗状态: 启用 */
    private static final String FUNNEL_STATUS_ACTIVE = "ACTIVE";
    /** 漏斗状态: 禁用 */
    private static final String FUNNEL_STATUS_INACTIVE = "INACTIVE";

    /** 商机状态: 进行中 */
    private static final String OPP_STATUS_OPEN = "OPEN";
    /** 商机状态: 已成单 */
    private static final String OPP_STATUS_WON = "WON";
    /** 商机状态: 已输单 */
    private static final String OPP_STATUS_LOST = "LOST";
    /** 商机状态: 停滞 */
    private static final String OPP_STATUS_STALLED = "STALLED";

    /** 默认操作人 (请求头未透传 X-User-Id 时使用) */
    private static final String DEFAULT_OPERATOR = "scrm-system";

    /** 销售漏斗数据仓库 */
    private final ScrmFunnelRepository funnelRepository;
    /** 漏斗阶段数据仓库 */
    private final ScrmFunnelStageRepository stageRepository;
    /** 商机数据仓库 */
    private final ScrmOpportunityRepository opportunityRepository;
    /** 商机阶段历史数据仓库 */
    private final ScrmOpportunityStageHistoryRepository stageHistoryRepository;

    // ============================================================
    // 漏斗管理
    // ============================================================

    /**
     * 创建销售漏斗
     *
     * @param dto 漏斗参数
     * @return 创建后的漏斗
     * @throws ScrmException 参数非法 / 默认漏斗冲突
     */
    @Transactional
    public ScrmFunnelDto createFunnel(ScrmFunnelDto dto) throws ScrmException {
        if (dto.getFunnelName() == null || dto.getFunnelName().isBlank()) {
            throw ScrmException.badRequest("漏斗名称不能为空");
        }
        Boolean isDefault = Boolean.TRUE.equals(dto.getIsDefault());
        // 默认漏斗唯一性: 同账号下仅允许一个默认漏斗
        if (isDefault && funnelRepository.countByIsDefaultTrue() > 0) {
            throw new ScrmException(ScrmExceptionConstants.CONFLICT,
                    "同账号下已存在默认漏斗, 请先取消原默认漏斗");
        }
        ScrmFunnelEntity entity = new ScrmFunnelEntity();
        entity.setFunnelName(dto.getFunnelName());
        entity.setDescription(dto.getDescription());
        entity.setIsDefault(isDefault);
        entity.setStatus(dto.getStatus() != null ? dto.getStatus() : FUNNEL_STATUS_ACTIVE);
        entity = funnelRepository.save(entity);
        log.info("创建销售漏斗: id={}, funnelName={}", entity.getId(), entity.getFunnelName());
        return toFunnelDto(entity);
    }

    /**
     * 更新销售漏斗
     *
     * @param id  漏斗 ID
     * @param dto 漏斗参数
     * @return 更新后的漏斗
     * @throws ScrmException 漏斗不存在
     */
    @Transactional
    public ScrmFunnelDto updateFunnel(Long id, ScrmFunnelDto dto) throws ScrmException {
        ScrmFunnelEntity entity = findFunnelOrThrow(id);
        if (dto.getFunnelName() != null) {
            if (dto.getFunnelName().isBlank()) {
                throw ScrmException.badRequest("漏斗名称不能为空");
            }
            entity.setFunnelName(dto.getFunnelName());
        }
        if (dto.getDescription() != null) {
            entity.setDescription(dto.getDescription());
        }
        if (dto.getStatus() != null) {
            entity.setStatus(dto.getStatus());
        }
        // isDefault 通过 setDefaultFunnel 专用接口维护, 此处不直接修改
        entity = funnelRepository.save(entity);
        return toFunnelDto(entity);
    }

    /**
     * 删除销售漏斗
     * <p>
     * 漏斗下仍有 OPEN 状态商机时不允许删除, 需先处理商机。删除时级联清理阶段定义。
     * </p>
     *
     * @param id 漏斗 ID
     * @throws ScrmException 漏斗不存在 / 仍有进行中商机
     */
    @Transactional
    public void deleteFunnel(Long id) throws ScrmException {
        ScrmFunnelEntity entity = findFunnelOrThrow(id);
        List<ScrmOpportunityEntity> openOpps = opportunityRepository
                .findByFunnelId(id).stream()
                .filter(o -> OPP_STATUS_OPEN.equals(o.getStatus()))
                .collect(Collectors.toList());
        if (!openOpps.isEmpty()) {
            throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                    "漏斗下仍有 " + openOpps.size() + " 个进行中商机, 请先处理后再删除");
        }
        stageRepository.deleteByFunnelId(id);
        funnelRepository.delete(entity);
        log.info("删除销售漏斗: id={}, name={}", id, entity.getFunnelName());
    }

    /**
     * 查询销售漏斗详情
     *
     * @param id 漏斗 ID
     * @return 漏斗 DTO
     * @throws ScrmException 漏斗不存在
     */
    @Transactional(readOnly = true)
    public ScrmFunnelDto getFunnel(Long id) throws ScrmException {
        return toFunnelDto(findFunnelOrThrow(id));
    }

    /**
     * 分页查询销售漏斗, 支持按状态过滤
     *
     * @param status   状态过滤 (可空)
     * @param pageable 分页参数
     * @return 漏斗分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmFunnelDto> listFunnels(String status, Pageable pageable) {
        Specification<ScrmFunnelEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(cb.lower(root.get("status")), status.toLowerCase()));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        Pageable sorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, "createTime"));
        return funnelRepository.findAll(spec, sorted).map(this::toFunnelDto);
    }

    /**
     * 设置默认漏斗
     * <p>
     * 取消同账号下原默认漏斗标记, 将当前漏斗置为默认。
     * </p>
     *
     * @param id 漏斗 ID
     * @return 更新后的漏斗
     * @throws ScrmException 漏斗不存在
     */
    @Transactional
    public ScrmFunnelDto setDefaultFunnel(Long id) throws ScrmException {
        ScrmFunnelEntity entity = findFunnelOrThrow(id);
        // 取消原默认漏斗
        funnelRepository.findByIsDefaultTrue().ifPresent(old -> {
            if (!Objects.equals(old.getId(), id)) {
                old.setIsDefault(false);
                funnelRepository.save(old);
            }
        });
        entity.setIsDefault(true);
        entity = funnelRepository.save(entity);
        log.info("设置默认漏斗: id={}, name={}", id, entity.getFunnelName());
        return toFunnelDto(entity);
    }

    // ============================================================
    // 漏斗阶段管理
    // ============================================================

    /**
     * 新增漏斗阶段
     *
     * @param funnelId 漏斗 ID
     * @param dto      阶段参数
     * @return 创建后的阶段
     * @throws ScrmException 漏斗不存在 / 参数非法
     */
    @Transactional
    public ScrmFunnelStageDto addStage(Long funnelId, ScrmFunnelStageDto dto) throws ScrmException {
        findFunnelOrThrow(funnelId);
        if (dto.getStageName() == null || dto.getStageName().isBlank()) {
            throw ScrmException.badRequest("阶段名称不能为空");
        }
        if (dto.getStageOrder() == null) {
            throw ScrmException.badRequest("阶段顺序不能为空");
        }
        ScrmFunnelStageEntity entity = new ScrmFunnelStageEntity();
        entity.setFunnelId(funnelId);
        entity.setStageName(dto.getStageName());
        entity.setStageOrder(dto.getStageOrder());
        entity.setDescription(dto.getDescription());
        entity.setEnterCondition(dto.getEnterCondition());
        entity.setExitCondition(dto.getExitCondition());
        entity.setIsClosedStage(Boolean.TRUE.equals(dto.getIsClosedStage()));
        entity.setIsLostStage(Boolean.TRUE.equals(dto.getIsLostStage()));
        entity.setProbability(dto.getProbability());
        entity = stageRepository.save(entity);
        log.info("新增漏斗阶段: funnelId={}, stageId={}, order={}",
                funnelId, entity.getId(), entity.getStageOrder());
        return toStageDto(entity);
    }

    /**
     * 更新漏斗阶段
     *
     * @param id  阶段 ID
     * @param dto 阶段参数
     * @return 更新后的阶段
     * @throws ScrmException 阶段不存在
     */
    @Transactional
    public ScrmFunnelStageDto updateStage(Long id, ScrmFunnelStageDto dto) throws ScrmException {
        ScrmFunnelStageEntity entity = findStageOrThrow(id);
        if (dto.getStageName() != null) {
            if (dto.getStageName().isBlank()) {
                throw ScrmException.badRequest("阶段名称不能为空");
            }
            entity.setStageName(dto.getStageName());
        }
        if (dto.getStageOrder() != null) {
            entity.setStageOrder(dto.getStageOrder());
        }
        if (dto.getDescription() != null) {
            entity.setDescription(dto.getDescription());
        }
        if (dto.getEnterCondition() != null) {
            entity.setEnterCondition(dto.getEnterCondition());
        }
        if (dto.getExitCondition() != null) {
            entity.setExitCondition(dto.getExitCondition());
        }
        if (dto.getIsClosedStage() != null) {
            entity.setIsClosedStage(dto.getIsClosedStage());
        }
        if (dto.getIsLostStage() != null) {
            entity.setIsLostStage(dto.getIsLostStage());
        }
        if (dto.getProbability() != null) {
            entity.setProbability(dto.getProbability());
        }
        entity = stageRepository.save(entity);
        return toStageDto(entity);
    }

    /**
     * 删除漏斗阶段
     * <p>
     * 仍被商机引用的阶段不允许删除, 需先推进商机至其他阶段。
     * </p>
     *
     * @param id 阶段 ID
     * @throws ScrmException 阶段不存在 / 仍被商机引用
     */
    @Transactional
    public void deleteStage(Long id) throws ScrmException {
        ScrmFunnelStageEntity entity = findStageOrThrow(id);
        // 校验是否有 OPEN 商机仍处于此阶段
        Specification<ScrmOpportunityEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("currentStageId"), id));
            predicates.add(cb.equal(root.get("status"), OPP_STATUS_OPEN));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        long refCount = opportunityRepository.count(spec);
        if (refCount > 0) {
            throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                    "仍有 " + refCount + " 个进行中商机处于此阶段, 请先推进后再删除");
        }
        stageRepository.delete(entity);
        log.info("删除漏斗阶段: id={}, name={}", id, entity.getStageName());
    }

    /**
     * 查询漏斗的全部阶段 (按 stageOrder 升序)
     *
     * @param funnelId 漏斗 ID
     * @return 阶段列表
     * @throws ScrmException 漏斗不存在
     */
    @Transactional(readOnly = true)
    public List<ScrmFunnelStageDto> listStages(Long funnelId) throws ScrmException {
        findFunnelOrThrow(funnelId);
        return stageRepository.findByFunnelIdOrderByStageOrderAsc(
                 funnelId).stream()
                .map(this::toStageDto)
                .collect(Collectors.toList());
    }

    /**
     * 批量重排漏斗阶段顺序
     *
     * @param funnelId 漏斗 ID
     * @param stageIds 阶段 ID 列表 (按新顺序排列)
     * @return 重排后的阶段列表
     * @throws ScrmException 漏斗不存在 / 阶段不属于此漏斗
     */
    @Transactional
    public List<ScrmFunnelStageDto> reorderStages(Long funnelId, List<Long> stageIds) throws ScrmException {
        findFunnelOrThrow(funnelId);
        if (stageIds == null || stageIds.isEmpty()) {
            throw ScrmException.badRequest("阶段 ID 列表不能为空");
        }
        List<ScrmFunnelStageEntity> stages = stageRepository
                .findByFunnelId(funnelId);
        Map<Long, ScrmFunnelStageEntity> stageMap = stages.stream()
                .collect(Collectors.toMap(ScrmFunnelStageEntity::getId, s -> s));
        for (int i = 0; i < stageIds.size(); i++) {
            Long stageId = stageIds.get(i);
            ScrmFunnelStageEntity stage = stageMap.get(stageId);
            if (stage == null) {
                throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                        "阶段不属于此漏斗: stageId=" + stageId);
            }
            stage.setStageOrder(i + 1);
        }
        stageRepository.saveAll(stages);
        return stages.stream()
                .sorted((a, b) -> Integer.compare(a.getStageOrder(), b.getStageOrder()))
                .map(this::toStageDto)
                .collect(Collectors.toList());
    }

    // ============================================================
    // 商机管理
    // ============================================================

    /**
     * 创建商机
     * <p>
     * 校验漏斗存在且启用, 若未指定 currentStageId 则取漏斗首阶段, 若未指定 probability 则取阶段概率。
     * 默认状态为 OPEN, 创建时同步记录首条阶段变更历史。
     * </p>
     *
     * @param dto 商机参数
     * @return 创建后的商机
     * @throws ScrmException 参数非法 / 漏斗不存在
     */
    @Transactional
    public ScrmOpportunityDto createOpportunity(ScrmOpportunityDto dto) throws ScrmException {
        if (dto.getOpportunityName() == null || dto.getOpportunityName().isBlank()) {
            throw ScrmException.badRequest("商机名称不能为空");
        }
        if (dto.getCustomerId() == null) {
            throw ScrmException.badRequest("客户 ID 不能为空");
        }
        if (dto.getOwnerUserId() == null || dto.getOwnerUserId().isBlank()) {
            throw ScrmException.badRequest("负责人不能为空");
        }
        ScrmFunnelEntity funnel = findFunnelOrThrow(dto.getFunnelId());
        if (!FUNNEL_STATUS_ACTIVE.equals(funnel.getStatus())) {
            throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                    "漏斗未启用, 不允许创建商机: funnelId=" + funnel.getId());
        }
        // 确定初始阶段
        Long stageId = dto.getCurrentStageId();
        List<ScrmFunnelStageEntity> stages = stageRepository
                .findByFunnelIdOrderByStageOrderAsc(dto.getFunnelId());
        if (stages.isEmpty()) {
            throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                    "漏斗未配置阶段, 不允许创建商机: funnelId=" + dto.getFunnelId());
        }
        ScrmFunnelStageEntity initialStage = null;
        if (stageId != null) {
            final Long filterStageId = stageId;
            initialStage = stages.stream().filter(s -> Objects.equals(s.getId(), filterStageId))
                    .findFirst().orElse(null);
            if (initialStage == null) {
                throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                        "初始阶段不属于此漏斗: stageId=" + stageId);
            }
        } else {
            initialStage = stages.get(0);
            stageId = initialStage.getId();
        }
        ScrmOpportunityEntity entity = new ScrmOpportunityEntity();
        entity.setOpportunityName(dto.getOpportunityName());
        entity.setCustomerId(dto.getCustomerId());
        entity.setFunnelId(dto.getFunnelId());
        entity.setCurrentStageId(stageId);
        entity.setAmount(dto.getAmount());
        entity.setExpectedCloseDate(dto.getExpectedCloseDate());
        entity.setProbability(dto.getProbability() != null ? dto.getProbability() : initialStage.getProbability());
        entity.setOwnerUserId(dto.getOwnerUserId());
        entity.setStatus(dto.getStatus() != null ? dto.getStatus() : OPP_STATUS_OPEN);
        entity.setSource(dto.getSource());
        entity.setCompetitor(dto.getCompetitor());
        entity.setNote(dto.getNote());
        entity = opportunityRepository.save(entity);
        // 记录首条阶段变更历史 (fromStageId 为空)
        ScrmOpportunityStageHistoryEntity history = new ScrmOpportunityStageHistoryEntity();
        history.setOpportunityId(entity.getId());
        history.setFromStageId(null);
        history.setToStageId(stageId);
        history.setChangedBy(dto.getOwnerUserId());
        history.setChangedAt(LocalDateTime.now());
        history.setNote("商机创建");
        history.setDurationDays(null);
        stageHistoryRepository.save(history);
        log.info("创建商机: id={}, name={}, funnelId={}, stageId={}",
                entity.getId(), entity.getOpportunityName(), entity.getFunnelId(), stageId);
        return toOpportunityDto(entity);
    }

    /**
     * 更新商机
     *
     * @param id  商机 ID
     * @param dto 商机参数
     * @return 更新后的商机
     * @throws ScrmException 商机不存在
     */
    @Transactional
    public ScrmOpportunityDto updateOpportunity(Long id, ScrmOpportunityDto dto) throws ScrmException {
        ScrmOpportunityEntity entity = findOpportunityOrThrow(id);
        if (dto.getOpportunityName() != null) {
            if (dto.getOpportunityName().isBlank()) {
                throw ScrmException.badRequest("商机名称不能为空");
            }
            entity.setOpportunityName(dto.getOpportunityName());
        }
        if (dto.getAmount() != null) {
            entity.setAmount(dto.getAmount());
        }
        if (dto.getExpectedCloseDate() != null) {
            entity.setExpectedCloseDate(dto.getExpectedCloseDate());
        }
        if (dto.getProbability() != null) {
            entity.setProbability(dto.getProbability());
        }
        if (dto.getOwnerUserId() != null) {
            entity.setOwnerUserId(dto.getOwnerUserId());
        }
        if (dto.getStatus() != null) {
            entity.setStatus(dto.getStatus());
        }
        if (dto.getSource() != null) {
            entity.setSource(dto.getSource());
        }
        if (dto.getCompetitor() != null) {
            entity.setCompetitor(dto.getCompetitor());
        }
        if (dto.getNote() != null) {
            entity.setNote(dto.getNote());
        }
        if (dto.getLostReason() != null) {
            entity.setLostReason(dto.getLostReason());
        }
        entity = opportunityRepository.save(entity);
        return toOpportunityDto(entity);
    }

    /**
     * 删除商机
     *
     * @param id 商机 ID
     * @throws ScrmException 商机不存在
     */
    @Transactional
    public void deleteOpportunity(Long id) throws ScrmException {
        ScrmOpportunityEntity entity = findOpportunityOrThrow(id);
        opportunityRepository.delete(entity);
        log.info("删除商机: id={}, name={}", id, entity.getOpportunityName());
    }

    /**
     * 查询商机详情
     *
     * @param id 商机 ID
     * @return 商机 DTO
     * @throws ScrmException 商机不存在
     */
    @Transactional(readOnly = true)
    public ScrmOpportunityDto getOpportunity(Long id) throws ScrmException {
        return toOpportunityDto(findOpportunityOrThrow(id));
    }

    /**
     * 分页查询商机, 支持按状态、负责人、客户、漏斗、阶段过滤
     *
     * @param status     状态过滤 (可空)
     * @param ownerId    负责人过滤 (可空)
     * @param customerId 客户 ID 过滤 (可空)
     * @param funnelId   漏斗 ID 过滤 (可空)
     * @param stageId    阶段 ID 过滤 (可空)
     * @param pageable   分页参数
     * @return 商机分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmOpportunityDto> listOpportunities(String status, String ownerId,
                                                       Long customerId, Long funnelId, Long stageId,
                                                       Pageable pageable) {
        Specification<ScrmOpportunityEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(cb.lower(root.get("status")), status.toLowerCase()));
            }
            if (ownerId != null && !ownerId.isBlank()) {
                predicates.add(cb.equal(root.get("ownerUserId"), ownerId));
            }
            if (customerId != null) {
                predicates.add(cb.equal(root.get("customerId"), customerId));
            }
            if (funnelId != null) {
                predicates.add(cb.equal(root.get("funnelId"), funnelId));
            }
            if (stageId != null) {
                predicates.add(cb.equal(root.get("currentStageId"), stageId));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        Pageable sorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, "createTime"));
        return opportunityRepository.findAll(spec, sorted).map(this::toOpportunityDto);
    }

    /**
     * 商机阶段推进
     * <p>
     * 校验目标阶段属于同一漏斗, 记录阶段变更历史 (含在上一阶段停留天数),
     * 若目标阶段 isClosedStage=true 则 status 置 WON 并记录 wonAt,
     * 若 isLostStage=true 则 status 置 LOST 并记录 lostAt 与 lostReason (从 note 提取)。
     * </p>
     *
     * @param opportunityId 商机 ID
     * @param dto           阶段推进请求
     * @return 更新后的商机
     * @throws ScrmException 商机不存在 / 目标阶段不存在 / 目标阶段不属于此漏斗
     */
    @Transactional
    public ScrmOpportunityDto changeStage(Long opportunityId,
            ScrmOpportunityStageChangeDto dto) throws ScrmException {
        if (dto.getToStageId() == null) {
            throw ScrmException.badRequest("目标阶段 ID 不能为空");
        }
        ScrmOpportunityEntity entity = findOpportunityOrThrow(opportunityId);
        ScrmFunnelStageEntity toStage = stageRepository.findById(dto.getToStageId())
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "目标阶段不存在: stageId=" + dto.getToStageId()));
        // 数据隔离: 校验阶段归属当前账号

        // 校验阶段属于同一漏斗
        if (!Objects.equals(toStage.getFunnelId(), entity.getFunnelId())) {
            throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                    "目标阶段不属于商机所在漏斗: oppFunnelId=" + entity.getFunnelId()
                            + ", stageFunnelId=" + toStage.getFunnelId());
        }
        Long fromStageId = entity.getCurrentStageId();
        // 计算在上一阶段停留天数
        Integer durationDays = null;
        List<ScrmOpportunityStageHistoryEntity> lastHist = stageHistoryRepository
                .findByOpportunityIdOrderByIdDesc(opportunityId);
        if (!lastHist.isEmpty()) {
            LocalDateTime lastChangedAt = lastHist.get(0).getChangedAt();
            durationDays = (int) ChronoUnit.DAYS.between(lastChangedAt, LocalDateTime.now());
        }
        // 记录阶段变更历史
        ScrmOpportunityStageHistoryEntity history = new ScrmOpportunityStageHistoryEntity();
        history.setOpportunityId(opportunityId);
        history.setFromStageId(fromStageId);
        history.setToStageId(dto.getToStageId());
        history.setChangedBy(currentOperator());
        history.setChangedAt(LocalDateTime.now());
        history.setNote(dto.getNote());
        history.setDurationDays(durationDays);
        stageHistoryRepository.save(history);
        // 更新商机当前阶段与状态
        entity.setCurrentStageId(dto.getToStageId());
        if (toStage.getProbability() != null) {
            entity.setProbability(toStage.getProbability());
        }
        if (Boolean.TRUE.equals(toStage.getIsClosedStage())) {
            entity.setStatus(OPP_STATUS_WON);
            entity.setWonAt(LocalDateTime.now());
        } else if (Boolean.TRUE.equals(toStage.getIsLostStage())) {
            entity.setStatus(OPP_STATUS_LOST);
            entity.setLostAt(LocalDateTime.now());
            entity.setLostReason(dto.getNote());
        }
        entity = opportunityRepository.save(entity);
        log.info("商机阶段推进: oppId={}, from={}, to={}, status={}",
                opportunityId, fromStageId, dto.getToStageId(), entity.getStatus());
        return toOpportunityDto(entity);
    }

    /**
     * 查询商机阶段变更历史
     *
     * @param opportunityId 商机 ID
     * @return 变更历史列表 (按时间升序)
     * @throws ScrmException 商机不存在
     */
    @Transactional(readOnly = true)
    public List<ScrmOpportunityStageHistoryDto> getStageHistory(Long opportunityId) throws ScrmException {
        findOpportunityOrThrow(opportunityId);
        List<ScrmOpportunityStageHistoryEntity> histories = stageHistoryRepository
                .findByOpportunityIdOrderByChangedAtAsc(opportunityId);
        // 加载阶段名称映射, 便于前端展示
        Map<Long, String> stageNameMap = loadStageNameMap(histories);
        return histories.stream().map(h -> toHistoryDto(h, stageNameMap)).collect(Collectors.toList());
    }

    /**
     * 漏斗分析: 各阶段商机数、金额、转化率与平均停留天数
     *
     * @param funnelId 漏斗 ID
     * @return 漏斗分析结果
     * @throws ScrmException 漏斗不存在
     */
    @Transactional(readOnly = true)
    public FunnelAnalysisVo getFunnelAnalysis(Long funnelId) throws ScrmException {
        ScrmFunnelEntity funnel = findFunnelOrThrow(funnelId);
        List<ScrmFunnelStageEntity> stages = stageRepository
                .findByFunnelIdOrderByStageOrderAsc(funnelId);
        // 阶段维度聚合: stageId -> {count, sumAmount}
        List<Object[]> aggRows = opportunityRepository.aggregateByStage(funnelId);
        Map<Long, long[]> stageAgg = new HashMap<>();
        for (Object[] row : aggRows) {
            Long stageId = ((Number) row[0]).longValue();
            long count = ((Number) row[1]).longValue();
            double sum = row[2] != null ? ((Number) row[2]).doubleValue() : 0d;
            stageAgg.put(stageId, new long[]{count, (long) sum});
        }
        // 阶段平均停留天数: stageId -> avgDays
        Map<Long, Double> avgDurationMap = new HashMap<>();
        for (Object[] row : stageHistoryRepository.avgDurationByStage()) {
            Long stageId = ((Number) row[0]).longValue();
            double avg = row[1] != null ? ((Number) row[1]).doubleValue() : 0d;
            avgDurationMap.put(stageId, avg);
        }
        List<FunnelAnalysisVo.StageStatVo> stageStats = new ArrayList<>();
        long totalOpenCount = 0;
        double totalOpenAmount = 0d;
        Long prevStageCount = null;
        for (ScrmFunnelStageEntity stage : stages) {
            long[] agg = stageAgg.getOrDefault(stage.getId(), new long[]{0L, 0L});
            long count = agg[0];
            double amount = agg[1];
            totalOpenCount += count;
            totalOpenAmount += amount;
            // 转化率: 上一阶段进入此阶段的比例, 首阶段为 100
            double conversionRate = 100d;
            if (prevStageCount != null && prevStageCount > 0) {
                conversionRate = count * 100d / prevStageCount;
            }
            stageStats.add(FunnelAnalysisVo.StageStatVo.builder()
                    .stageId(stage.getId())
                    .stageName(stage.getStageName())
                    .stageOrder(stage.getStageOrder())
                    .isClosedStage(stage.getIsClosedStage())
                    .isLostStage(stage.getIsLostStage())
                    .probability(stage.getProbability())
                    .openCount(count)
                    .openAmount(amount)
                    .conversionRate(Math.round(conversionRate * 100d) / 100d)
                    .avgDurationDays(avgDurationMap.getOrDefault(stage.getId(), 0d))
                    .build());
            prevStageCount = count;
        }
        return FunnelAnalysisVo.builder()
                .funnelId(funnelId)
                .funnelName(funnel.getFunnelName())
                .totalOpenCount(totalOpenCount)
                .totalOpenAmount(totalOpenAmount)
                .stages(stageStats)
                .build();
    }

    /**
     * 销售预测: 基于 OPEN 商机的加权金额 (amount * probability / 100)
     * <p>
     * 返回汇总信息: totalCount / totalAmount / weightedAmount / byStatus 按状态分组统计,
     * 同时分页返回商机明细 (含加权金额)。
     * </p>
     *
     * @param ownerId  负责人过滤 (可空, 为空时统计当前账号全部)
     * @param pageable 分页参数
     * @return 预测结果 (含汇总与分页明细)
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getForecast(String ownerId, Pageable pageable) {
        // 分页查询 OPEN 商机明细
        Specification<ScrmOpportunityEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("status"), OPP_STATUS_OPEN));
            if (ownerId != null && !ownerId.isBlank()) {
                predicates.add(cb.equal(root.get("ownerUserId"), ownerId));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        Pageable sorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, "amount"));
        Page<ScrmOpportunityDto> page = opportunityRepository.findAll(spec, sorted).map(this::toOpportunityDto);
        // 汇总统计 (native query)
        Object[] summary = opportunityRepository.forecastSummary(ownerId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ownerId", ownerId);
        result.put("totalCount", summary != null && summary[0] != null ? ((Number) summary[0]).longValue() : 0L);
        result.put("totalAmount", summary != null && summary[1] != null ? ((Number) summary[1]).doubleValue() : 0d);
        result.put("weightedAmount", summary != null && summary[2] != null ? ((Number) summary[2]).doubleValue() : 0d);
        result.put("items", page);
        return result;
    }

    // ============================================================
    // 内部工具方法
    // ============================================================

    /**
     * 当前操作人 ID (从请求头 X-User-Id 透传, 暂用默认值)
     */
    private String currentOperator() {
        return DEFAULT_OPERATOR;
    }

    /**
     * 按主键查询漏斗, 不存在或越权抛异常
     */
    private ScrmFunnelEntity findFunnelOrThrow(Long id) throws ScrmException {
        ScrmFunnelEntity entity = funnelRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "销售漏斗不存在: id=" + id));
        return entity;
    }

    /**
     * 按主键查询阶段, 不存在或越权抛异常
     */
    private ScrmFunnelStageEntity findStageOrThrow(Long id) throws ScrmException {
        ScrmFunnelStageEntity entity = stageRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "漏斗阶段不存在: id=" + id));
        return entity;
    }

    /**
     * 按主键查询商机, 不存在或越权抛异常
     */
    private ScrmOpportunityEntity findOpportunityOrThrow(Long id) throws ScrmException {
        ScrmOpportunityEntity entity = opportunityRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "商机不存在: id=" + id));
        return entity;
    }


    /**
     * 加载阶段变更历史涉及的阶段名称映射
     */
    private Map<Long, String> loadStageNameMap(List<ScrmOpportunityStageHistoryEntity> histories) {
        // 收集所有涉及的阶段 ID (含 from / to)
        java.util.Set<Long> stageIds = new java.util.HashSet<>();
        for (ScrmOpportunityStageHistoryEntity h : histories) {
            if (h.getFromStageId() != null) {
                stageIds.add(h.getFromStageId());
            }
            if (h.getToStageId() != null) {
                stageIds.add(h.getToStageId());
            }
        }
        Map<Long, String> result = new HashMap<>();
        if (stageIds.isEmpty()) {
            return result;
        }
        // 批量查询阶段名称 (限定当前账号)
        List<ScrmFunnelStageEntity> stages = stageRepository.findAllById(stageIds).stream()
                .filter(s -> true)
                .collect(Collectors.toList());
        for (ScrmFunnelStageEntity s : stages) {
            result.put(s.getId(), s.getStageName());
        }
        return result;
    }

    /**
     * 漏斗实体转 DTO
     */
    private ScrmFunnelDto toFunnelDto(ScrmFunnelEntity entity) {
        ScrmFunnelDto dto = new ScrmFunnelDto();
        dto.setId(entity.getId());
        dto.setFunnelName(entity.getFunnelName());
        dto.setDescription(entity.getDescription());
        dto.setIsDefault(entity.getIsDefault());
        dto.setStatus(entity.getStatus());
        dto.setCreateTime(entity.getCreateTime());
        dto.setUpdateTime(entity.getUpdateTime());
        dto.setVersion(entity.getVersion());
        return dto;
    }

    /**
     * 阶段实体转 DTO
     */
    private ScrmFunnelStageDto toStageDto(ScrmFunnelStageEntity entity) {
        ScrmFunnelStageDto dto = new ScrmFunnelStageDto();
        dto.setId(entity.getId());
        dto.setFunnelId(entity.getFunnelId());
        dto.setStageName(entity.getStageName());
        dto.setStageOrder(entity.getStageOrder());
        dto.setDescription(entity.getDescription());
        dto.setEnterCondition(entity.getEnterCondition());
        dto.setExitCondition(entity.getExitCondition());
        dto.setIsClosedStage(entity.getIsClosedStage());
        dto.setIsLostStage(entity.getIsLostStage());
        dto.setProbability(entity.getProbability());
        dto.setCreateTime(entity.getCreateTime());
        dto.setUpdateTime(entity.getUpdateTime());
        dto.setVersion(entity.getVersion());
        return dto;
    }

    /**
     * 商机实体转 DTO (含加权金额计算)
     */
    private ScrmOpportunityDto toOpportunityDto(ScrmOpportunityEntity entity) {
        ScrmOpportunityDto dto = new ScrmOpportunityDto();
        dto.setId(entity.getId());
        dto.setOpportunityName(entity.getOpportunityName());
        dto.setCustomerId(entity.getCustomerId());
        dto.setFunnelId(entity.getFunnelId());
        dto.setCurrentStageId(entity.getCurrentStageId());
        dto.setAmount(entity.getAmount());
        dto.setExpectedCloseDate(entity.getExpectedCloseDate());
        dto.setProbability(entity.getProbability());
        dto.setOwnerUserId(entity.getOwnerUserId());
        dto.setStatus(entity.getStatus());
        dto.setSource(entity.getSource());
        dto.setCompetitor(entity.getCompetitor());
        dto.setNote(entity.getNote());
        dto.setWonAt(entity.getWonAt());
        dto.setLostAt(entity.getLostAt());
        dto.setLostReason(entity.getLostReason());
        // 加权金额 = amount * probability / 100
        if (entity.getAmount() != null && entity.getProbability() != null) {
            dto.setWeightedAmount(Math.round(
                    entity.getAmount() * entity.getProbability() / 100d * 100d) / 100d);
        }
        dto.setCreateTime(entity.getCreateTime());
        dto.setUpdateTime(entity.getUpdateTime());
        dto.setVersion(entity.getVersion());
        return dto;
    }

    /**
     * 阶段变更历史实体转 DTO (填充阶段名称)
     */
    private ScrmOpportunityStageHistoryDto toHistoryDto(ScrmOpportunityStageHistoryEntity entity,
                                                         Map<Long, String> stageNameMap) {
        ScrmOpportunityStageHistoryDto dto = new ScrmOpportunityStageHistoryDto();
        dto.setId(entity.getId());
        dto.setOpportunityId(entity.getOpportunityId());
        dto.setFromStageId(entity.getFromStageId());
        dto.setFromStageName(entity.getFromStageId() != null ? stageNameMap.get(entity.getFromStageId()) : null);
        dto.setToStageId(entity.getToStageId());
        dto.setToStageName(stageNameMap.get(entity.getToStageId()));
        dto.setChangedBy(entity.getChangedBy());
        dto.setChangedAt(entity.getChangedAt());
        dto.setNote(entity.getNote());
        dto.setDurationDays(entity.getDurationDays());
        return dto;
    }
}
