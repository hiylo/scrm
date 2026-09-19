/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmExternalContactSyncService.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.service;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.dto.ScrmExternalContactMappingDto;
import org.hiylo.scrm.dto.ScrmExternalContactSyncConfigDto;
import org.hiylo.scrm.dto.ScrmExternalContactSyncLogDto;
import org.hiylo.scrm.dto.ScrmExternalContactSyncTaskDto;
import org.hiylo.scrm.dto.ScrmSyncTriggerDto;
import org.hiylo.scrm.entity.ScrmExternalContactMappingEntity;
import org.hiylo.scrm.entity.ScrmExternalContactSyncConfigEntity;
import org.hiylo.scrm.entity.ScrmExternalContactSyncLogEntity;
import org.hiylo.scrm.entity.ScrmExternalContactSyncTaskEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.repository.ScrmExternalContactMappingRepository;
import org.hiylo.scrm.repository.ScrmExternalContactSyncConfigRepository;
import org.hiylo.scrm.repository.ScrmExternalContactSyncLogRepository;
import org.hiylo.scrm.repository.ScrmExternalContactSyncTaskRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * SCRM 外部联系人同步服务。
 * <p>
 * 承载企业微信 / 抖音 / 快手 / 小红书等平台外部联系人同步全流程能力:
 * 同步配置管理 (增删改查与启用禁用、连接测试)、同步任务管理 (创建、查询、取消、重试、触发同步),
 * 同步日志查询、联系人映射管理 (按平台/客户/外部 ID 查询与合并、移除)、同步执行
 * (增量 / 全量, 当前为模拟实现, 待对接企微 API) 以及同步统计 (概览 / 配置 / 趋势)。
 * </p>
 * <p>
 * 所有写操作写入当前用户归属账号, 实现数据隔离; {@link #executeSync} 为
 * 模拟实现, 生成随机外部联系人并创建 / 更新映射, 记录同步日志与计数, 方法签名完整, 待对接企微 API。
 * </p>
 *
 * @author Hsi Chu
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmExternalContactSyncService {

    // ==================== 平台常量 ====================

    /** 平台: 企业微信 */
    private static final String PLATFORM_WORK_WECHAT = "WORK_WECHAT";
    /** 平台: 抖音 */
    private static final String PLATFORM_DOUYIN = "DOUYIN";
    /** 平台: 快手 */
    private static final String PLATFORM_KUAISHOU = "KUAISHOU";
    /** 平台: 小红书 */
    private static final String PLATFORM_XIAOHONGSHU = "XIAOHONGSHU";
    /** 平台: 其他 */
    private static final String PLATFORM_OTHER = "OTHER";

    /** 合法的平台 */
    private static final List<String> VALID_PLATFORMS = List.of(
            PLATFORM_WORK_WECHAT, PLATFORM_DOUYIN, PLATFORM_KUAISHOU,
            PLATFORM_XIAOHONGSHU, PLATFORM_OTHER);

    // ==================== 同步模式 / 方向 / 频率 ====================

    /** 同步模式: 增量 */
    private static final String MODE_INCREMENTAL = "INCREMENTAL";
    /** 同步模式: 全量 */
    private static final String MODE_FULL = "FULL";

    /** 同步方向: 单向入库 */
    private static final String DIRECTION_ONE_WAY_IN = "ONE_WAY_IN";
    /** 同步方向: 单向出库 */
    private static final String DIRECTION_ONE_WAY_OUT = "ONE_WAY_OUT";
    /** 同步方向: 双向 */
    private static final String DIRECTION_BIDIRECTIONAL = "BIDIRECTIONAL";

    /** 同步频率: 实时 */
    private static final String FREQUENCY_REALTIME = "REALTIME";
    /** 同步频率: 每小时 */
    private static final String FREQUENCY_HOURLY = "HOURLY";
    /** 同步频率: 每天 */
    private static final String FREQUENCY_DAILY = "DAILY";
    /** 同步频率: 每周 */
    private static final String FREQUENCY_WEEKLY = "WEEKLY";
    /** 同步频率: 手动 */
    private static final String FREQUENCY_MANUAL = "MANUAL";

    // ==================== 任务状态 / 触发者类型 ====================

    /** 任务状态: 待执行 */
    private static final String STATUS_PENDING = "PENDING";
    /** 任务状态: 执行中 */
    private static final String STATUS_RUNNING = "RUNNING";
    /** 任务状态: 成功 */
    private static final String STATUS_SUCCESS = "SUCCESS";
    /** 任务状态: 失败 */
    private static final String STATUS_FAILED = "FAILED";
    /** 任务状态: 已取消 */
    private static final String STATUS_CANCELLED = "CANCELLED";

    /** 触发者类型: 手动 */
    private static final String TRIGGER_MANUAL = "MANUAL";
    /** 触发者类型: 定时 */
    private static final String TRIGGER_SCHEDULED = "SCHEDULED";
    /** 触发者类型: 系统 */
    private static final String TRIGGER_SYSTEM = "SYSTEM";

    // ==================== 同步状态 / 操作类型 / 处理状态 ====================

    /** 映射同步状态: 活跃 */
    private static final String SYNC_STATUS_ACTIVE = "ACTIVE";
    /** 映射同步状态: 已删除 */
    private static final String SYNC_STATUS_DELETED = "DELETED";
    /** 映射同步状态: 已合并 */
    private static final String SYNC_STATUS_MERGED = "MERGED";

    /** 操作类型: 新增 */
    private static final String OP_CREATE = "CREATE";
    /** 操作类型: 更新 */
    private static final String OP_UPDATE = "UPDATE";
    /** 操作类型: 删除 */
    private static final String OP_DELETE = "DELETE";
    /** 操作类型: 跳过 */
    private static final String OP_SKIP = "SKIP";
    /** 操作类型: 合并 */
    private static final String OP_MERGE = "MERGE";

    /** 处理状态: 成功 */
    private static final String LOG_STATUS_SUCCESS = "SUCCESS";
    /** 处理状态: 失败 */
    private static final String LOG_STATUS_FAILED = "FAILED";
    /** 处理状态: 跳过 */
    private static final String LOG_STATUS_SKIPPED = "SKIPPED";

    /** 最后同步状态: 部分 */
    private static final String LAST_SYNC_PARTIAL = "PARTIAL";

    // ==================== 默认值 ====================

    /** 默认同步模式 */
    private static final String DEFAULT_SYNC_MODE = MODE_INCREMENTAL;
    /** 默认同步方向 */
    private static final String DEFAULT_SYNC_DIRECTION = DIRECTION_BIDIRECTIONAL;
    /** 默认同步频率 */
    private static final String DEFAULT_SYNC_FREQUENCY = FREQUENCY_HOURLY;
    /** 默认是否自动创建客户 */
    private static final boolean DEFAULT_AUTO_CREATE_CUSTOMER = true;
    /** 默认是否自动合并重复 */
    private static final boolean DEFAULT_AUTO_MERGE_DUPLICATE = false;
    /** 默认启用状态 */
    private static final boolean DEFAULT_ENABLED = true;
    /** 默认最后同步数量 */
    private static final int DEFAULT_LAST_SYNC_COUNT = 0;
    /** 默认计数初值 */
    private static final int DEFAULT_COUNT = 0;
    /** 默认操作人 (请求头未透传时使用) */
    private static final String DEFAULT_OPERATOR = "scrm-system";
    /** 默认触发者类型 */
    private static final String DEFAULT_TRIGGER_TYPE = TRIGGER_MANUAL;
    /** 模拟同步生成的联系人数量 */
    private static final int MOCK_CONTACT_COUNT = 10;
    /** 同步趋势默认天数 */
    private static final int DEFAULT_TREND_DAYS = 7;
    /** 趋势最大天数 */
    private static final int MAX_TREND_DAYS = 90;

    // ==================== 数据访问层 ====================

    /** 同步配置数据访问层 */
    private final ScrmExternalContactSyncConfigRepository configRepository;

    /** 同步任务数据访问层 */
    private final ScrmExternalContactSyncTaskRepository taskRepository;

    /** 同步日志数据访问层 */
    private final ScrmExternalContactSyncLogRepository logRepository;

    /** 联系人映射数据访问层 */
    private final ScrmExternalContactMappingRepository mappingRepository;

    // ============================================================
    // 配置管理
    // ============================================================

    /**
     * 创建同步配置。
     *
     * @param dto 配置参数
     * @return 创建后的配置
     * @throws ScrmException 参数非法
     */
    @Transactional
    public ScrmExternalContactSyncConfigDto createConfig(ScrmExternalContactSyncConfigDto dto)
            throws ScrmException {
        validateConfigDto(dto, false);
        ScrmExternalContactSyncConfigEntity entity = new ScrmExternalContactSyncConfigEntity();
        entity.setConfigName(dto.getConfigName());
        entity.setPlatform(dto.getPlatform());
        entity.setCorpId(dto.getCorpId());
        entity.setAgentId(dto.getAgentId());
        entity.setSecret(dto.getSecret());
        entity.setSyncMode(dto.getSyncMode() != null ? dto.getSyncMode() : DEFAULT_SYNC_MODE);
        entity.setSyncDirection(dto.getSyncDirection() != null ? dto.getSyncDirection() : DEFAULT_SYNC_DIRECTION);
        entity.setSyncFrequency(dto.getSyncFrequency() != null ? dto.getSyncFrequency() : DEFAULT_SYNC_FREQUENCY);
        entity.setLastSyncCount(DEFAULT_LAST_SYNC_COUNT);
        entity.setAutoCreateCustomer(dto.getAutoCreateCustomer() != null
                ? dto.getAutoCreateCustomer() : DEFAULT_AUTO_CREATE_CUSTOMER);
        entity.setAutoMergeDuplicate(dto.getAutoMergeDuplicate() != null
                ? dto.getAutoMergeDuplicate() : DEFAULT_AUTO_MERGE_DUPLICATE);
        entity.setFieldMapping(dto.getFieldMapping());
        entity.setEnabled(dto.getEnabled() != null ? dto.getEnabled() : DEFAULT_ENABLED);
        entity.setCreatedBy(dto.getCreatedBy());
        entity = configRepository.save(entity);
        log.info("创建外部联系人同步配置: id={}, configName={}, platform={}",
                entity.getId(), entity.getConfigName(), entity.getPlatform());
        return toConfigDto(entity);
    }

    /**
     * 更新同步配置 (字段非空才覆盖)。
     *
     * @param id  配置 ID
     * @param dto 配置参数
     * @return 更新后的配置
     * @throws ScrmException 配置不存在 / 参数非法
     */
    @Transactional
    public ScrmExternalContactSyncConfigDto updateConfig(Long id, ScrmExternalContactSyncConfigDto dto)
            throws ScrmException {
        ScrmExternalContactSyncConfigEntity entity = findConfigOrThrow(id);
        validateConfigDto(dto, true);
        if (dto.getConfigName() != null) entity.setConfigName(dto.getConfigName());
        if (dto.getPlatform() != null) entity.setPlatform(dto.getPlatform());
        if (dto.getCorpId() != null) entity.setCorpId(dto.getCorpId());
        if (dto.getAgentId() != null) entity.setAgentId(dto.getAgentId());
        if (dto.getSecret() != null) entity.setSecret(dto.getSecret());
        if (dto.getSyncMode() != null) entity.setSyncMode(dto.getSyncMode());
        if (dto.getSyncDirection() != null) entity.setSyncDirection(dto.getSyncDirection());
        if (dto.getSyncFrequency() != null) entity.setSyncFrequency(dto.getSyncFrequency());
        if (dto.getAutoCreateCustomer() != null) entity.setAutoCreateCustomer(dto.getAutoCreateCustomer());
        if (dto.getAutoMergeDuplicate() != null) entity.setAutoMergeDuplicate(dto.getAutoMergeDuplicate());
        if (dto.getFieldMapping() != null) entity.setFieldMapping(dto.getFieldMapping());
        if (dto.getEnabled() != null) entity.setEnabled(dto.getEnabled());
        if (dto.getCreatedBy() != null) entity.setCreatedBy(dto.getCreatedBy());
        entity = configRepository.save(entity);
        log.info("更新外部联系人同步配置: id={}, configName={}", entity.getId(), entity.getConfigName());
        return toConfigDto(entity);
    }

    /**
     * 删除同步配置。
     *
     * @param id 配置 ID
     * @throws ScrmException 配置不存在
     */
    @Transactional
    public void deleteConfig(Long id) throws ScrmException {
        ScrmExternalContactSyncConfigEntity entity = findConfigOrThrow(id);
        configRepository.delete(entity);
        log.info("删除外部联系人同步配置: id={}, configName={}", id, entity.getConfigName());
    }

    /**
     * 查询同步配置详情。
     *
     * @param id 配置 ID
     * @return 配置 DTO
     * @throws ScrmException 配置不存在
     */
    @Transactional(readOnly = true)
    public ScrmExternalContactSyncConfigDto getConfig(Long id) throws ScrmException {
        return toConfigDto(findConfigOrThrow(id));
    }

    /**
     * 分页查询同步配置, 支持按平台、启用状态与关键字过滤。
     *
     * @param platform 平台过滤 (可空)
     * @param enabled  启用状态过滤 (可空)
     * @param keyword  关键字模糊匹配配置名称 (可空)
     * @param pageable 分页参数
     * @return 配置分页结果 (按 createTime DESC)
     */
    @Transactional(readOnly = true)
    public Page<ScrmExternalContactSyncConfigDto> listConfigs(String platform, Boolean enabled,
                                                                String keyword, Pageable pageable) {
        Specification<ScrmExternalContactSyncConfigEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (platform != null && !platform.isBlank()) {
                predicates.add(cb.equal(root.get("platform"), platform));
            }
            if (enabled != null) {
                predicates.add(cb.equal(root.get("enabled"), enabled));
            }
            if (keyword != null && !keyword.isBlank()) {
                predicates.add(cb.like(root.get("configName"), "%" + keyword + "%"));
            }
            query.orderBy(cb.desc(root.get("createTime")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return configRepository.findAll(spec, pageable).map(this::toConfigDto);
    }

    /**
     * 启用同步配置。
     *
     * @param id 配置 ID
     * @return 更新后的配置
     * @throws ScrmException 配置不存在
     */
    @Transactional
    public ScrmExternalContactSyncConfigDto enableConfig(Long id) throws ScrmException {
        ScrmExternalContactSyncConfigEntity entity = findConfigOrThrow(id);
        entity.setEnabled(true);
        entity = configRepository.save(entity);
        log.info("启用同步配置: id={}, configName={}", id, entity.getConfigName());
        return toConfigDto(entity);
    }

    /**
     * 禁用同步配置。
     *
     * @param id 配置 ID
     * @return 更新后的配置
     * @throws ScrmException 配置不存在
     */
    @Transactional
    public ScrmExternalContactSyncConfigDto disableConfig(Long id) throws ScrmException {
        ScrmExternalContactSyncConfigEntity entity = findConfigOrThrow(id);
        entity.setEnabled(false);
        entity = configRepository.save(entity);
        log.info("禁用同步配置: id={}, configName={}", id, entity.getConfigName());
        return toConfigDto(entity);
    }

    /**
     * 测试同步配置连接 (模拟实现)。
     * <p>校验配置必要字段 (corpId / agentId / secret) 完整性, 返回模拟连接结果, 待对接企微 API。</p>
     *
     * @param id 配置 ID
     * @return 连接测试结果 Map
     * @throws ScrmException 配置不存在 / 必要字段缺失
     */
    @Transactional(readOnly = true)
    public Map<String, Object> testConnection(Long id) throws ScrmException {
        ScrmExternalContactSyncConfigEntity entity = findConfigOrThrow(id);
        if (entity.getCorpId() == null || entity.getCorpId().isBlank()) {
            throw ScrmException.badRequest("企业 ID 不能为空, 无法测试连接");
        }
        if (entity.getSecret() == null || entity.getSecret().isBlank()) {
            throw ScrmException.badRequest("应用密钥不能为空, 无法测试连接");
        }
        // 模拟连接测试: 待对接企微 API (wxwork /cgi-bin/service/get_login_info)
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("connected", true);
        result.put("platform", entity.getPlatform());
        result.put("corpId", entity.getCorpId());
        result.put("agentId", entity.getAgentId());
        result.put("message", "连接测试成功 (模拟, 待对接企微 API)");
        log.info("测试同步配置连接 (模拟): id={}, configName={}, platform={}",
                id, entity.getConfigName(), entity.getPlatform());
        return result;
    }

    // ============================================================
    // 任务管理
    // ============================================================

    /**
     * 创建同步任务。
     * <p>校验配置存在且启用, 写入账号 ID 与默认状态 (PENDING) 后持久化。</p>
     *
     * @param dto 任务参数
     * @return 创建后的任务
     * @throws ScrmException 参数非法 / 配置不存在或已禁用
     */
    @Transactional
    public ScrmExternalContactSyncTaskDto createTask(ScrmExternalContactSyncTaskDto dto)
            throws ScrmException {
        if (dto.getConfigId() == null) {
            throw ScrmException.badRequest("同步配置 ID 不能为空");
        }
        if (dto.getTaskName() == null || dto.getTaskName().isBlank()) {
            throw ScrmException.badRequest("任务名称不能为空");
        }
        if (dto.getSyncMode() == null || dto.getSyncMode().isBlank()) {
            throw ScrmException.badRequest("同步模式不能为空");
        }
        // 校验配置存在且归属当前账号
        ScrmExternalContactSyncConfigEntity config = findConfigOrThrow(dto.getConfigId());
        if (Boolean.FALSE.equals(config.getEnabled())) {
            throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                    "同步配置已禁用, 不允许创建任务: configId=" + dto.getConfigId());
        }
        ScrmExternalContactSyncTaskEntity entity = new ScrmExternalContactSyncTaskEntity();
        entity.setConfigId(dto.getConfigId());
        entity.setTaskName(dto.getTaskName());
        entity.setSyncMode(dto.getSyncMode());
        entity.setStatus(dto.getStatus() != null ? dto.getStatus() : STATUS_PENDING);
        entity.setTotalRecords(DEFAULT_COUNT);
        entity.setSuccessCount(DEFAULT_COUNT);
        entity.setFailedCount(DEFAULT_COUNT);
        entity.setNewCount(DEFAULT_COUNT);
        entity.setUpdateCount(DEFAULT_COUNT);
        entity.setSkipCount(DEFAULT_COUNT);
        entity.setTriggeredBy(dto.getTriggeredBy() != null ? dto.getTriggeredBy() : DEFAULT_OPERATOR);
        entity.setTriggeredByType(dto.getTriggeredByType() != null
                ? dto.getTriggeredByType() : DEFAULT_TRIGGER_TYPE);
        entity = taskRepository.save(entity);
        log.info("创建外部联系人同步任务: id={}, taskName={}, configId={}, syncMode={}",
                entity.getId(), entity.getTaskName(), entity.getConfigId(), entity.getSyncMode());
        return toTaskDto(entity);
    }

    /**
     * 查询同步任务详情。
     *
     * @param id 任务 ID
     * @return 任务 DTO
     * @throws ScrmException 任务不存在
     */
    @Transactional(readOnly = true)
    public ScrmExternalContactSyncTaskDto getTask(Long id) throws ScrmException {
        return toTaskDto(findTaskOrThrow(id));
    }

    /**
     * 分页查询同步任务, 支持按配置 ID、状态与时间区间过滤。
     *
     * @param configId  配置 ID 过滤 (可空)
     * @param status    任务状态过滤 (可空)
     * @param startTime 起始时间过滤 (可空, 按 startTime 字段)
     * @param endTime   截止时间过滤 (可空, 按 startTime 字段)
     * @param pageable  分页参数
     * @return 任务分页结果 (按 createTime DESC)
     */
    @Transactional(readOnly = true)
    public Page<ScrmExternalContactSyncTaskDto> listTasks(Long configId, String status,
                                                            LocalDateTime startTime, LocalDateTime endTime,
                                                            Pageable pageable) {
        Specification<ScrmExternalContactSyncTaskEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (configId != null) {
                predicates.add(cb.equal(root.get("configId"), configId));
            }
            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (startTime != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("startTime"), startTime));
            }
            if (endTime != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("startTime"), endTime));
            }
            query.orderBy(cb.desc(root.get("createTime")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return taskRepository.findAll(spec, pageable).map(this::toTaskDto);
    }

    /**
     * 取消同步任务 (仅 PENDING / RUNNING 状态可取消)。
     *
     * @param id 任务 ID
     * @return 更新后的任务
     * @throws ScrmException 任务不存在 / 状态非法
     */
    @Transactional
    public ScrmExternalContactSyncTaskDto cancelTask(Long id) throws ScrmException {
        ScrmExternalContactSyncTaskEntity entity = findTaskOrThrow(id);
        if (!STATUS_PENDING.equals(entity.getStatus()) && !STATUS_RUNNING.equals(entity.getStatus())) {
            throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                    "任务状态非法, 仅 PENDING / RUNNING 可取消: currentStatus=" + entity.getStatus());
        }
        entity.setStatus(STATUS_CANCELLED);
        entity.setEndTime(LocalDateTime.now());
        entity = taskRepository.save(entity);
        log.info("取消同步任务: id={}, taskName={}", id, entity.getTaskName());
        return toTaskDto(entity);
    }

    /**
     * 触发同步: 创建任务 → 执行 → 记录日志 → 更新映射。
     * <p>同步模式缺省时回退到配置自身的 syncMode; 触发者类型默认 MANUAL。</p>
     *
     * @param triggerDto 触发参数
     * @return 执行后的任务
     * @throws ScrmException 配置不存在 / 已禁用
     */
    @Transactional
    public ScrmExternalContactSyncTaskDto triggerSync(ScrmSyncTriggerDto triggerDto) throws ScrmException {
        if (triggerDto == null || triggerDto.getConfigId() == null) {
            throw ScrmException.badRequest("同步配置 ID 不能为空");
        }
        ScrmExternalContactSyncConfigEntity config = findConfigOrThrow(triggerDto.getConfigId());
        if (Boolean.FALSE.equals(config.getEnabled())) {
            throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                    "同步配置已禁用, 不允许触发同步: configId=" + triggerDto.getConfigId());
        }
        String syncMode = triggerDto.getSyncMode() != null ? triggerDto.getSyncMode() : config.getSyncMode();
        // 创建任务
        ScrmExternalContactSyncTaskEntity task = new ScrmExternalContactSyncTaskEntity();
        task.setConfigId(config.getId());
        task.setTaskName(config.getConfigName() + " - " + syncMode + " -"
                + LocalDateTime.now().toString().substring(0, 19));
        task.setSyncMode(syncMode);
        task.setStatus(STATUS_PENDING);
        task.setTotalRecords(DEFAULT_COUNT);
        task.setSuccessCount(DEFAULT_COUNT);
        task.setFailedCount(DEFAULT_COUNT);
        task.setNewCount(DEFAULT_COUNT);
        task.setUpdateCount(DEFAULT_COUNT);
        task.setSkipCount(DEFAULT_COUNT);
        task.setTriggeredBy(DEFAULT_OPERATOR);
        task.setTriggeredByType(DEFAULT_TRIGGER_TYPE);
        task = taskRepository.save(task);
        log.info("触发外部联系人同步: taskId={}, configId={}, syncMode={}",
                task.getId(), config.getId(), syncMode);
        // 执行同步
        return executeSync(task.getId());
    }

    /**
     * 重试失败任务: 创建新任务 (沿用原任务配置与同步模式) 并执行。
     *
     * @param id 原任务 ID
     * @return 重试生成的新任务
     * @throws ScrmException 原任务不存在 / 状态非 FAILED
     */
    @Transactional
    public ScrmExternalContactSyncTaskDto retryTask(Long id) throws ScrmException {
        ScrmExternalContactSyncTaskEntity origin = findTaskOrThrow(id);
        if (!STATUS_FAILED.equals(origin.getStatus())) {
            throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                    "任务状态非法, 仅 FAILED 可重试: currentStatus=" + origin.getStatus());
        }
        ScrmExternalContactSyncTaskEntity retry = new ScrmExternalContactSyncTaskEntity();
        retry.setConfigId(origin.getConfigId());
        retry.setTaskName(origin.getTaskName() + " - 重试");
        retry.setSyncMode(origin.getSyncMode());
        retry.setStatus(STATUS_PENDING);
        retry.setTotalRecords(DEFAULT_COUNT);
        retry.setSuccessCount(DEFAULT_COUNT);
        retry.setFailedCount(DEFAULT_COUNT);
        retry.setNewCount(DEFAULT_COUNT);
        retry.setUpdateCount(DEFAULT_COUNT);
        retry.setSkipCount(DEFAULT_COUNT);
        retry.setTriggeredBy(DEFAULT_OPERATOR);
        retry.setTriggeredByType(TRIGGER_SYSTEM);
        retry = taskRepository.save(retry);
        log.info("重试同步任务: originTaskId={}, retryTaskId={}", id, retry.getId());
        return executeSync(retry.getId());
    }

    // ============================================================
    // 日志管理
    // ============================================================

    /**
     * 分页查询同步日志, 支持按任务 ID、操作类型与处理状态过滤。
     *
     * @param taskId        任务 ID 过滤 (可空)
     * @param operationType 操作类型过滤 (可空)
     * @param status        处理状态过滤 (可空)
     * @param pageable      分页参数
     * @return 日志分页结果 (按 processedAt DESC)
     */
    @Transactional(readOnly = true)
    public Page<ScrmExternalContactSyncLogDto> listLogs(Long taskId, String operationType,
                                                          String status, Pageable pageable) {
        Specification<ScrmExternalContactSyncLogEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (taskId != null) {
                predicates.add(cb.equal(root.get("taskId"), taskId));
            }
            if (operationType != null && !operationType.isBlank()) {
                predicates.add(cb.equal(root.get("operationType"), operationType));
            }
            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            query.orderBy(cb.desc(root.get("processedAt")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return logRepository.findAll(spec, pageable).map(this::toLogDto);
    }

    /**
     * 查询同步日志详情。
     *
     * @param id 日志 ID
     * @return 日志 DTO
     * @throws ScrmException 日志不存在
     */
    @Transactional(readOnly = true)
    public ScrmExternalContactSyncLogDto getLog(Long id) throws ScrmException {
        return toLogDto(findLogOrThrow(id));
    }

    /**
     * 查询指定任务的全部同步日志 (按处理时间倒序)。
     *
     * @param taskId 任务 ID
     * @return 日志列表
     * @throws ScrmException 任务不存在
     */
    @Transactional(readOnly = true)
    public List<ScrmExternalContactSyncLogDto> getTaskLogs(Long taskId) throws ScrmException {
        findTaskOrThrow(taskId);
        return logRepository.findByTaskIdOrderByProcessedAtDesc(taskId).stream()
                .map(this::toLogDto)
                .collect(Collectors.toList());
    }

    // ============================================================
    // 映射管理
    // ============================================================

    /**
     * 查询联系人映射详情。
     *
     * @param id 映射 ID
     * @return 映射 DTO
     * @throws ScrmException 映射不存在
     */
    @Transactional(readOnly = true)
    public ScrmExternalContactMappingDto getMapping(Long id) throws ScrmException {
        return toMappingDto(findMappingOrThrow(id));
    }

    /**
     * 按平台与外部联系人 ID 查询映射。
     *
     * @param platform          平台
     * @param externalContactId 平台外部联系人 ID
     * @return 映射 DTO (不存在返回 null)
     * @throws ScrmException 参数非法
     */
    @Transactional(readOnly = true)
    public ScrmExternalContactMappingDto getMappingByExternal(String platform, String externalContactId)
            throws ScrmException {
        if (platform == null || platform.isBlank()) {
            throw ScrmException.badRequest("平台不能为空");
        }
        if (externalContactId == null || externalContactId.isBlank()) {
            throw ScrmException.badRequest("外部联系人 ID 不能为空");
        }
        Optional<ScrmExternalContactMappingEntity> opt = mappingRepository
                .findByPlatformAndExternalContactId(platform, externalContactId);
        return opt.map(this::toMappingDto).orElse(null);
    }

    /**
     * 按客户 ID 查询映射列表 (一个客户可能映射到多个平台联系人)。
     *
     * @param customerId SCRM 客户 ID
     * @return 映射列表
     */
    @Transactional(readOnly = true)
    public List<ScrmExternalContactMappingDto> getMappingByCustomer(Long customerId) {
        return mappingRepository.findByCustomerId(customerId).stream()
                .map(this::toMappingDto)
                .collect(Collectors.toList());
    }

    /**
     * 分页查询联系人映射, 支持按平台、同步状态与关键字过滤。
     *
     * @param platform   平台过滤 (可空)
     * @param syncStatus 同步状态过滤 (可空)
     * @param keyword    关键字模糊匹配外部名称 / 客户名称 (可空)
     * @param pageable   分页参数
     * @return 映射分页结果 (按 lastSyncAt DESC)
     */
    @Transactional(readOnly = true)
    public Page<ScrmExternalContactMappingDto> listMappings(String platform, String syncStatus,
                                                              String keyword, Pageable pageable) {
        Specification<ScrmExternalContactMappingEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (platform != null && !platform.isBlank()) {
                predicates.add(cb.equal(root.get("platform"), platform));
            }
            if (syncStatus != null && !syncStatus.isBlank()) {
                predicates.add(cb.equal(root.get("syncStatus"), syncStatus));
            }
            if (keyword != null && !keyword.isBlank()) {
                String kw = "%" + keyword + "%";
                predicates.add(cb.or(
                        cb.like(root.get("externalName"), kw),
                        cb.like(root.get("customerName"), kw),
                        cb.like(root.get("externalContactId"), kw)
                ));
            }
            query.orderBy(cb.desc(root.get("lastSyncAt")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return mappingRepository.findAll(spec, pageable).map(this::toMappingDto);
    }

    /**
     * 移除联系人映射 (软删除: syncStatus 置 DELETED)。
     *
     * @param id 映射 ID
     * @throws ScrmException 映射不存在
     */
    @Transactional
    public void removeMapping(Long id) throws ScrmException {
        ScrmExternalContactMappingEntity entity = findMappingOrThrow(id);
        entity.setSyncStatus(SYNC_STATUS_DELETED);
        mappingRepository.save(entity);
        log.info("移除联系人映射 (软删除): id={}, platform={}, externalContactId={}",
                id, entity.getPlatform(), entity.getExternalContactId());
    }

    // ============================================================
    // 同步执行
    // ============================================================

    /**
     * 执行同步任务 (模拟实现)。
     * <p>
     * 流程: 任务置 RUNNING → 生成 MOCK_CONTACT_COUNT 个随机外部联系人 → 逐个创建 / 更新映射 →
     * 记录同步日志 → 累加计数 → 任务置 SUCCESS → 更新配置最后同步状态。
     * 待对接企微 API: 通过 corpId + secret 获取 access_token, 调用
     * /cgi-bin/externalcontact/list 获取外部联系人列表。
     * </p>
     *
     * @param taskId 任务 ID
     * @return 执行后的任务
     * @throws ScrmException 任务不存在 / 状态非法
     */
    @Transactional
    public ScrmExternalContactSyncTaskDto executeSync(Long taskId) throws ScrmException {
        ScrmExternalContactSyncTaskEntity task = findTaskOrThrow(taskId);
        if (!STATUS_PENDING.equals(task.getStatus()) && !STATUS_RUNNING.equals(task.getStatus())) {
            throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                    "任务状态非法, 仅 PENDING / RUNNING 可执行: currentStatus=" + task.getStatus());
        }
        ScrmExternalContactSyncConfigEntity config = findConfigOrThrow(task.getConfigId());
        long startMs = System.currentTimeMillis();
        task.setStatus(STATUS_RUNNING);
        task.setStartTime(LocalDateTime.now());
        task = taskRepository.save(task);

        int total = 0, success = 0, failed = 0, newCount = 0, updateCount = 0, skipCount = 0;
        // 模拟同步: 生成随机联系人 (待对接企微 API)
        for (int i = 0; i < MOCK_CONTACT_COUNT; i++) {
            total++;
            String externalContactId = config.getPlatform().toLowerCase() + "_ext_"
                    + System.currentTimeMillis() + "_" + i;
            String externalName = "外部联系人-" + ThreadLocalRandom.current().nextInt(1000, 9999);
            try {
                ScrmExternalContactSyncLogEntity logEntry = new ScrmExternalContactSyncLogEntity();
                logEntry.setTaskId(taskId);
                logEntry.setExternalContactId(externalContactId);
                logEntry.setExternalName(externalName);
                logEntry.setExternalAvatar("https://cdn.example.com/avatar/" + externalContactId + ".png");
                logEntry.setProcessedAt(LocalDateTime.now());

                // 查找已有映射
                Optional<ScrmExternalContactMappingEntity> existing = mappingRepository
                        .findByPlatformAndExternalContactId(
                                config.getPlatform(), externalContactId);
                if (existing.isPresent()) {
                    // 更新映射
                    ScrmExternalContactMappingEntity mapping = existing.get();
                    mapping.setExternalName(externalName);
                    mapping.setLastSyncAt(LocalDateTime.now());
                    mapping.setSyncStatus(SYNC_STATUS_ACTIVE);
                    mappingRepository.save(mapping);
                    logEntry.setOperationType(OP_UPDATE);
                    logEntry.setCustomerId(mapping.getCustomerId());
                    logEntry.setCustomerName(mapping.getCustomerName());
                    logEntry.setFieldChanges("{\"externalName\":\"" + jsonEscape(externalName) + "\"}");
                    logEntry.setStatus(LOG_STATUS_SUCCESS);
                    updateCount++;
                } else {
                    // 创建映射 (customerId 使用随机值, 待对接客户主数据)
                    ScrmExternalContactMappingEntity mapping = new ScrmExternalContactMappingEntity();
                    mapping.setPlatform(config.getPlatform());
                    mapping.setExternalContactId(externalContactId);
                    mapping.setExternalName(externalName);
                    mapping.setExternalAvatar(logEntry.getExternalAvatar());
                    mapping.setCustomerId((long) ThreadLocalRandom.current().nextInt(100000, 999999));
                    mapping.setCustomerName(externalName);
                    mapping.setFollowStatus("NORMAL");
                    mapping.setLastSyncAt(LocalDateTime.now());
                    mapping.setSyncStatus(SYNC_STATUS_ACTIVE);
                    mappingRepository.save(mapping);
                    logEntry.setOperationType(OP_CREATE);
                    logEntry.setCustomerId(mapping.getCustomerId());
                    logEntry.setCustomerName(mapping.getCustomerName());
                    logEntry.setFieldChanges("{\"externalName\":\"" + jsonEscape(externalName) + "\"}");
                    logEntry.setStatus(LOG_STATUS_SUCCESS);
                    newCount++;
                }
                logRepository.save(logEntry);
                success++;
            } catch (Exception e) {
                log.warn("同步联系人失败: taskId={}, externalContactId={}", taskId, externalContactId, e);
                ScrmExternalContactSyncLogEntity logEntry = new ScrmExternalContactSyncLogEntity();
                logEntry.setTaskId(taskId);
                logEntry.setExternalContactId(externalContactId);
                logEntry.setExternalName(externalName);
                logEntry.setOperationType(OP_CREATE);
                logEntry.setStatus(LOG_STATUS_FAILED);
                logEntry.setErrorMessage(truncate(e.getMessage(), 480));
                logEntry.setProcessedAt(LocalDateTime.now());
                logRepository.save(logEntry);
                failed++;
            }
        }

        // 更新任务统计与状态
        task.setTotalRecords(total);
        task.setSuccessCount(success);
        task.setFailedCount(failed);
        task.setNewCount(newCount);
        task.setUpdateCount(updateCount);
        task.setSkipCount(skipCount);
        task.setEndTime(LocalDateTime.now());
        task.setDurationMs((int) (System.currentTimeMillis() - startMs));
        // 任务状态: 全部成功 → SUCCESS, 全部失败 → FAILED, 部分成功 → SUCCESS (失败计数记录在 failedCount)
        if (failed > 0 && success == 0) {
            task.setStatus(STATUS_FAILED);
            task.setErrorMessage("全部联系人同步失败, 失败数=" + failed);
        } else {
            task.setStatus(STATUS_SUCCESS);
        }
        task = taskRepository.save(task);

        // 更新配置最后同步状态
        config.setLastSyncAt(LocalDateTime.now());
        config.setLastSyncCount(success);
        if (failed == 0) {
            config.setLastSyncStatus(LOG_STATUS_SUCCESS);
        } else if (success == 0) {
            config.setLastSyncStatus(STATUS_FAILED);
        } else {
            config.setLastSyncStatus(LAST_SYNC_PARTIAL);
        }
        configRepository.save(config);

        log.info("同步任务执行完成: taskId={}, total={}, success={}, failed={}, new={}, update={}, durationMs={}",
                taskId, total, success, failed, newCount, updateCount, task.getDurationMs());
        return toTaskDto(task);
    }

    /**
     * 增量同步 (基于配置创建并执行任务)。
     *
     * @param configId 配置 ID
     * @return 执行后的任务
     * @throws ScrmException 配置不存在
     */
    @Transactional
    public ScrmExternalContactSyncTaskDto processIncremental(Long configId) throws ScrmException {
        findConfigOrThrow(configId);
        ScrmSyncTriggerDto trigger = new ScrmSyncTriggerDto();
        trigger.setConfigId(configId);
        trigger.setSyncMode(MODE_INCREMENTAL);
        log.info("增量同步: configId={}", configId);
        return triggerSync(trigger);
    }

    /**
     * 全量同步 (基于配置创建并执行任务)。
     *
     * @param configId 配置 ID
     * @return 执行后的任务
     * @throws ScrmException 配置不存在
     */
    @Transactional
    public ScrmExternalContactSyncTaskDto processFull(Long configId) throws ScrmException {
        findConfigOrThrow(configId);
        ScrmSyncTriggerDto trigger = new ScrmSyncTriggerDto();
        trigger.setConfigId(configId);
        trigger.setSyncMode(MODE_FULL);
        log.info("全量同步: configId={}", configId);
        return triggerSync(trigger);
    }

    /**
     * 合并联系人: 将映射指向目标客户, 原映射 syncStatus 置 MERGED。
     *
     * @param mappingId        映射 ID
     * @param targetCustomerId 目标客户 ID
     * @return 更新后的映射
     * @throws ScrmException 映射不存在 / 目标客户 ID 与原客户 ID 相同
     */
    @Transactional
    public ScrmExternalContactMappingDto mergeContact(Long mappingId, Long targetCustomerId)
            throws ScrmException {
        ScrmExternalContactMappingEntity mapping = findMappingOrThrow(mappingId);
        if (targetCustomerId == null) {
            throw ScrmException.badRequest("目标客户 ID 不能为空");
        }
        if (targetCustomerId.equals(mapping.getCustomerId())) {
            throw ScrmException.badRequest("目标客户 ID 与原客户 ID 相同, 无需合并");
        }
        Long originCustomerId = mapping.getCustomerId();
        mapping.setCustomerId(targetCustomerId);
        mapping.setSyncStatus(SYNC_STATUS_MERGED);
        mapping.setLastSyncAt(LocalDateTime.now());
        mapping = mappingRepository.save(mapping);
        log.info("合并联系人映射: mappingId={}, originCustomerId={}, targetCustomerId={}",
                mappingId, originCustomerId, targetCustomerId);
        return toMappingDto(mapping);
    }

    // ============================================================
    // 统计
    // ============================================================

    /**
     * 同步统计概览: 任务数、成功率、新增、更新、失败。
     *
     * @param startTime 起始时间 (可空, 按 startTime 字段)
     * @param endTime   截止时间 (可空, 按 startTime 字段)
     * @return 统计结果 Map
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getSyncStats(LocalDateTime startTime, LocalDateTime endTime) {
        Map<String, Object> stats = new LinkedHashMap<>();
        // 任务状态分布
        List<Object[]> byStatus = taskRepository.countByStatus();
        Map<String, Long> statusDist = new LinkedHashMap<>();
        statusDist.put(STATUS_PENDING, 0L);
        statusDist.put(STATUS_RUNNING, 0L);
        statusDist.put(STATUS_SUCCESS, 0L);
        statusDist.put(STATUS_FAILED, 0L);
        statusDist.put(STATUS_CANCELLED, 0L);
        long totalTasks = 0L;
        for (Object[] row : byStatus) {
            String s = (String) row[0];
            long c = row[1] == null ? 0L : ((Number) row[1]).longValue();
            statusDist.put(s, c);
            totalTasks += c;
        }
        stats.put("statusDistribution", statusDist);
        stats.put("totalTasks", totalTasks);
        // 时间区间内聚合统计
        Object[] agg = taskRepository.aggregateStats(startTime, endTime);
        long total = agg == null || agg[0] == null ? 0L : ((Number) agg[0]).longValue();
        long successTasks = agg == null || agg[1] == null ? 0L : ((Number) agg[1]).longValue();
        long totalNew = agg == null || agg[2] == null ? 0L : ((Number) agg[2]).longValue();
        long totalUpdate = agg == null || agg[3] == null ? 0L : ((Number) agg[3]).longValue();
        long totalFailed = agg == null || agg[4] == null ? 0L : ((Number) agg[4]).longValue();
        stats.put("timeRangeTotalTasks", total);
        stats.put("timeRangeSuccessTasks", successTasks);
        stats.put("successRate", total == 0 ? 0.0 : (double) successTasks / total);
        stats.put("totalNew", totalNew);
        stats.put("totalUpdate", totalUpdate);
        stats.put("totalFailed", totalFailed);
        stats.put("startTime", startTime);
        stats.put("endTime", endTime);
        return stats;
    }

    /**
     * 配置统计: 指定配置的任务数、成功率、新增、更新、失败。
     *
     * @param configId 配置 ID
     * @return 统计结果 Map
     * @throws ScrmException 配置不存在
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getConfigStats(Long configId) throws ScrmException {
        findConfigOrThrow(configId);
        Map<String, Object> stats = new LinkedHashMap<>();
        Object[] agg = taskRepository.aggregateStatsByConfig(configId);
        long total = agg == null || agg[0] == null ? 0L : ((Number) agg[0]).longValue();
        long successTasks = agg == null || agg[1] == null ? 0L : ((Number) agg[1]).longValue();
        long totalNew = agg == null || agg[2] == null ? 0L : ((Number) agg[2]).longValue();
        long totalUpdate = agg == null || agg[3] == null ? 0L : ((Number) agg[3]).longValue();
        long totalFailed = agg == null || agg[4] == null ? 0L : ((Number) agg[4]).longValue();
        stats.put("configId", configId);
        stats.put("totalTasks", total);
        stats.put("successTasks", successTasks);
        stats.put("successRate", total == 0 ? 0.0 : (double) successTasks / total);
        stats.put("totalNew", totalNew);
        stats.put("totalUpdate", totalUpdate);
        stats.put("totalFailed", totalFailed);
        return stats;
    }

    /**
     * 同步趋势: 近 N 天每日任务创建数。
     *
     * @param days 天数 (缺省 7, 上限 90)
     * @return 趋势列表, 每项 {date, count}
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getSyncTrend(Integer days) {
        int d = days == null || days <= 0 ? DEFAULT_TREND_DAYS : Math.min(days, MAX_TREND_DAYS);
        LocalDateTime from = LocalDateTime.now().minusDays(d);
        List<Object[]> rows = taskRepository.dailyCountByCreateTime(from);
        // 补全缺失日期
        Map<String, Long> dateCount = new LinkedHashMap<>();
        for (Object[] row : rows) {
            String date = (String) row[0];
            long c = row[1] == null ? 0L : ((Number) row[1]).longValue();
            dateCount.put(date, c);
        }
        List<Map<String, Object>> trend = new ArrayList<>();
        java.time.LocalDate today = java.time.LocalDate.now();
        for (int i = d - 1; i >= 0; i--) {
            String date = today.minusDays(i).toString();
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("date", date);
            item.put("count", dateCount.getOrDefault(date, 0L));
            trend.add(item);
        }
        return trend;
    }

    // ============================================================
    // 内部工具方法
    // ============================================================

    /**
     * 校验配置 DTO 字段合法性。
     *
     * @param dto    配置参数
     * @param update 是否为更新场景 (更新时部分字段允许为空)
     * @throws ScrmException 参数非法
     */
    private void validateConfigDto(ScrmExternalContactSyncConfigDto dto, boolean update)
            throws ScrmException {
        if (dto == null) {
            throw ScrmException.badRequest("配置参数不能为空");
        }
        if (!update) {
            if (dto.getConfigName() == null || dto.getConfigName().isBlank()) {
                throw ScrmException.badRequest("配置名称不能为空");
            }
            if (dto.getPlatform() == null || dto.getPlatform().isBlank()) {
                throw ScrmException.badRequest("平台不能为空");
            }
        }
        if (dto.getPlatform() != null && !dto.getPlatform().isBlank() && !VALID_PLATFORMS.contains(dto.getPlatform())) {
            throw ScrmException.badRequest("平台类型不支持: " + dto.getPlatform());
        }
    }

    /**
     * 按主键查询配置, 不存在抛 404。
     */
    private ScrmExternalContactSyncConfigEntity findConfigOrThrow(Long id) throws ScrmException {
        ScrmExternalContactSyncConfigEntity entity = configRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "同步配置不存在: id=" + id));

        return entity;
    }

    /**
     * 按主键查询任务, 不存在抛 404。
     */
    private ScrmExternalContactSyncTaskEntity findTaskOrThrow(Long id) throws ScrmException {
        ScrmExternalContactSyncTaskEntity entity = taskRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "同步任务不存在: id=" + id));

        return entity;
    }

    /**
     * 按主键查询日志, 不存在抛 404。
     */
    private ScrmExternalContactSyncLogEntity findLogOrThrow(Long id) throws ScrmException {
        ScrmExternalContactSyncLogEntity entity = logRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "同步日志不存在: id=" + id));

        return entity;
    }

    /**
     * 按主键查询映射, 不存在抛 404。
     */
    private ScrmExternalContactMappingEntity findMappingOrThrow(Long id) throws ScrmException {
        ScrmExternalContactMappingEntity entity = mappingRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "联系人映射不存在: id=" + id));

        return entity;
    }

    /**
     * 配置实体转 DTO。
     */
    private ScrmExternalContactSyncConfigDto toConfigDto(ScrmExternalContactSyncConfigEntity entity) {
        ScrmExternalContactSyncConfigDto dto = new ScrmExternalContactSyncConfigDto();
        dto.setId(entity.getId());
        dto.setConfigName(entity.getConfigName());
        dto.setPlatform(entity.getPlatform());
        dto.setCorpId(entity.getCorpId());
        dto.setAgentId(entity.getAgentId());
        dto.setSecret(entity.getSecret());
        dto.setSyncMode(entity.getSyncMode());
        dto.setSyncDirection(entity.getSyncDirection());
        dto.setSyncFrequency(entity.getSyncFrequency());
        dto.setLastSyncAt(entity.getLastSyncAt());
        dto.setLastSyncStatus(entity.getLastSyncStatus());
        dto.setLastSyncCount(entity.getLastSyncCount());
        dto.setAutoCreateCustomer(entity.getAutoCreateCustomer());
        dto.setAutoMergeDuplicate(entity.getAutoMergeDuplicate());
        dto.setFieldMapping(entity.getFieldMapping());
        dto.setEnabled(entity.getEnabled());
        dto.setCreatedBy(entity.getCreatedBy());
        dto.setCreateTime(entity.getCreateTime());
        dto.setUpdateTime(entity.getUpdateTime());
        dto.setVersion(entity.getVersion());
        return dto;
    }

    /**
     * 任务实体转 DTO。
     */
    private ScrmExternalContactSyncTaskDto toTaskDto(ScrmExternalContactSyncTaskEntity entity) {
        ScrmExternalContactSyncTaskDto dto = new ScrmExternalContactSyncTaskDto();
        dto.setId(entity.getId());
        dto.setConfigId(entity.getConfigId());
        dto.setTaskName(entity.getTaskName());
        dto.setSyncMode(entity.getSyncMode());
        dto.setStatus(entity.getStatus());
        dto.setStartTime(entity.getStartTime());
        dto.setEndTime(entity.getEndTime());
        dto.setDurationMs(entity.getDurationMs());
        dto.setTotalRecords(entity.getTotalRecords());
        dto.setSuccessCount(entity.getSuccessCount());
        dto.setFailedCount(entity.getFailedCount());
        dto.setNewCount(entity.getNewCount());
        dto.setUpdateCount(entity.getUpdateCount());
        dto.setSkipCount(entity.getSkipCount());
        dto.setErrorMessage(entity.getErrorMessage());
        dto.setTriggeredBy(entity.getTriggeredBy());
        dto.setTriggeredByType(entity.getTriggeredByType());
        dto.setCreateTime(entity.getCreateTime());
        dto.setUpdateTime(entity.getUpdateTime());
        dto.setVersion(entity.getVersion());
        return dto;
    }

    /**
     * 日志实体转 DTO。
     */
    private ScrmExternalContactSyncLogDto toLogDto(ScrmExternalContactSyncLogEntity entity) {
        ScrmExternalContactSyncLogDto dto = new ScrmExternalContactSyncLogDto();
        dto.setId(entity.getId());
        dto.setTaskId(entity.getTaskId());
        dto.setExternalContactId(entity.getExternalContactId());
        dto.setExternalName(entity.getExternalName());
        dto.setExternalAvatar(entity.getExternalAvatar());
        dto.setOperationType(entity.getOperationType());
        dto.setCustomerId(entity.getCustomerId());
        dto.setCustomerName(entity.getCustomerName());
        dto.setFieldChanges(entity.getFieldChanges());
        dto.setStatus(entity.getStatus());
        dto.setErrorMessage(entity.getErrorMessage());
        dto.setProcessedAt(entity.getProcessedAt());
        dto.setCreateTime(entity.getCreateTime());
        dto.setUpdateTime(entity.getUpdateTime());
        dto.setVersion(entity.getVersion());
        return dto;
    }

    /**
     * 映射实体转 DTO。
     */
    private ScrmExternalContactMappingDto toMappingDto(ScrmExternalContactMappingEntity entity) {
        ScrmExternalContactMappingDto dto = new ScrmExternalContactMappingDto();
        dto.setId(entity.getId());
        dto.setPlatform(entity.getPlatform());
        dto.setExternalContactId(entity.getExternalContactId());
        dto.setExternalUserId(entity.getExternalUserId());
        dto.setCustomerId(entity.getCustomerId());
        dto.setCustomerName(entity.getCustomerName());
        dto.setExternalName(entity.getExternalName());
        dto.setExternalAvatar(entity.getExternalAvatar());
        dto.setExternalCorpId(entity.getExternalCorpId());
        dto.setUnionId(entity.getUnionId());
        dto.setOpenId(entity.getOpenId());
        dto.setFollowUserId(entity.getFollowUserId());
        dto.setFollowStatus(entity.getFollowStatus());
        dto.setLastSyncAt(entity.getLastSyncAt());
        dto.setSyncStatus(entity.getSyncStatus());
        dto.setCreateTime(entity.getCreateTime());
        dto.setUpdateTime(entity.getUpdateTime());
        dto.setVersion(entity.getVersion());
        return dto;
    }

    /**
     * JSON 字符串转义 (反斜杠 / 引号 / 换行 / 回车 / 制表符)。
     *
     * @param s 原始字符串
     * @return 转义后的字符串
     */
    private static String jsonEscape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    /**
     * 截断字符串到指定最大长度。
     *
     * @param s 字符串
     * @param max 最大长度
     * @return 截断后的字符串
     */
    private static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
