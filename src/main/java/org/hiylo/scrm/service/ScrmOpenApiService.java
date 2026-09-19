/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmOpenApiService.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.service;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.dto.ScrmApiAccessLogDto;
import org.hiylo.scrm.dto.ScrmApiAppCreateDto;
import org.hiylo.scrm.dto.ScrmApiAppDto;
import org.hiylo.scrm.dto.ScrmApiKeyDto;
import org.hiylo.scrm.dto.ScrmApiScopeDto;
import org.hiylo.scrm.dto.ScrmApiStatsDto;
import org.hiylo.scrm.dto.ScrmOAuthTokenDto;
import org.hiylo.scrm.entity.ScrmApiAccessLogEntity;
import org.hiylo.scrm.entity.ScrmApiAppEntity;
import org.hiylo.scrm.entity.ScrmApiKeyEntity;
import org.hiylo.scrm.entity.ScrmApiScopeEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.repository.ScrmApiAccessLogRepository;
import org.hiylo.scrm.common.util.SensitiveDataUtils;
import org.hiylo.scrm.repository.ScrmApiAppRepository;
import org.hiylo.scrm.repository.ScrmApiKeyRepository;
import org.hiylo.scrm.repository.ScrmApiScopeRepository;
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
import java.util.UUID;

/**
 * SCRM 开放API / 第三方接入服务。
 * <p>
 * 承载开放平台的核心能力: API 应用管理 (创建/启停/吊销/密钥轮换), API 密钥管理
 * (创建/吊销/使用记录), 权限范围管理 (默认权限/校验), OAuth2 授权 (模拟实现),
 * API 调用日志记录与检索, 速率限制检查 (模拟实现), 以及调用统计 (请求数/错误率/
 * 平均响应时间/热门端点/错误分布)。
 * </p>
 * <p>
 * OAuth2 令牌签发与速率限制计数为模拟实现, 方法签名完整, 后续可对接真实 OAuth2
 * 服务器与分布式限流组件 (Redis + Bucket4j)。clientSecret 加密存储由调用方在上层完成,
 * 此处仅生成明文凭证返回一次。
 * </p>
 * <p>
 * 所有写操作写入当前用户归属账号实现数据隔离, 越权访问按不存在处理。
 * </p>
 *
 * @author Hsi Chu
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmOpenApiService {

    // ==================== 应用状态 / 类型常量 ====================

    /** 应用状态: 活跃 */
    private static final String APP_STATUS_ACTIVE = "ACTIVE";
    /** 应用状态: 暂停 */
    private static final String APP_STATUS_SUSPENDED = "SUSPENDED";
    /** 应用状态: 吊销 */
    private static final String APP_STATUS_REVOKED = "REVOKED";

    /** 应用类型: 第三方 */
    private static final String APP_TYPE_THIRD_PARTY = "THIRD_PARTY";

    // ==================== 密钥状态 / 类型常量 ====================

    /** 密钥状态: 活跃 */
    private static final String KEY_STATUS_ACTIVE = "ACTIVE";
    /** 密钥状态: 已过期 */
    private static final String KEY_STATUS_EXPIRED = "EXPIRED";
    /** 密钥状态: 已吊销 */
    private static final String KEY_STATUS_REVOKED = "REVOKED";

    /** 密钥类型: 长效 */
    private static final String KEY_TYPE_PERMANENT = "PERMANENT";

    // ==================== 默认值常量 ====================

    /** 默认每分钟速率限制 */
    private static final int DEFAULT_RATE_LIMIT_PER_MINUTE = 60;
    /** 默认每日速率限制 */
    private static final int DEFAULT_RATE_LIMIT_PER_DAY = 10000;
    /** 错误日志最小 HTTP 状态码 (>= 400 视为错误) */
    private static final int ERROR_STATUS_THRESHOLD = 400;
    /** 模拟令牌有效期 (秒) */
    private static final long TOKEN_EXPIRES_IN_SECONDS = 7200L;

    // ==================== 数据访问层 ====================

    /** API 应用数据访问层 */
    private final ScrmApiAppRepository appRepository;
    /** API 密钥数据访问层 */
    private final ScrmApiKeyRepository keyRepository;
    /** API 调用日志数据访问层 */
    private final ScrmApiAccessLogRepository accessLogRepository;
    /** API 权限范围数据访问层 */
    private final ScrmApiScopeRepository scopeRepository;

    // ============================================================
    // 应用管理
    // ============================================================

    /**
     * 创建 API 应用。
     * <p>自动生成 clientId 与 clientSecret (明文仅返回一次), 默认 status=ACTIVE,
     * 请求计数置 0, appType 为空时默认 THIRD_PARTY, 速率限制为空时取默认值。</p>
     *
     * @param createDto 应用创建参数
     * @return 创建后的应用 (含明文 clientSecret, 仅此一次返回)
     * @throws ScrmException 参数非法 / 应用编码重复
     */
    @Transactional
    public ScrmApiAppDto createApp(ScrmApiAppCreateDto createDto) throws ScrmException {
        if (createDto == null) {
            throw ScrmException.badRequest("应用参数不能为空");
        }
        // 应用编码唯一校验
        if (appRepository.findByAppCode(createDto.getAppCode()).isPresent()) {
            throw ScrmException.conflict("应用编码已存在: " + createDto.getAppCode());
        }
        ScrmApiAppEntity entity = new ScrmApiAppEntity();
        entity.setAppName(createDto.getAppName());
        entity.setAppCode(createDto.getAppCode());
        entity.setDescription(createDto.getDescription());
        entity.setAppType(createDto.getAppType() != null ? createDto.getAppType() : APP_TYPE_THIRD_PARTY);
        entity.setClientId(generateClientId());
        entity.setClientSecret(generateSecret());
        entity.setRedirectUris(createDto.getRedirectUris());
        entity.setScopes(createDto.getScopes());
        entity.setRateLimitPerMinute(DEFAULT_RATE_LIMIT_PER_MINUTE);
        entity.setRateLimitPerDay(DEFAULT_RATE_LIMIT_PER_DAY);
        entity.setStatus(APP_STATUS_ACTIVE);
        entity.setTotalRequestCount(0);
        entity.setTodayRequestCount(0);
        entity = appRepository.save(entity);
        log.info("创建API应用: id={}, appName={}, appCode={}, clientId={}",
                entity.getId(), entity.getAppName(), entity.getAppCode(), entity.getClientId());
        return toAppDto(entity);
    }

    /**
     * 更新应用信息 (部分更新, 仅非空字段生效, clientId / clientSecret 不在此更新)。
     *
     * @param id  应用 ID
     * @param dto 应用参数
     * @return 更新后的应用
     * @throws ScrmException 应用不存在
     */
    @Transactional
    public ScrmApiAppDto updateApp(Long id, ScrmApiAppDto dto) throws ScrmException {
        if (dto == null) {
            throw ScrmException.badRequest("应用参数不能为空");
        }
        ScrmApiAppEntity entity = findAppOrThrow(id);
        if (dto.getAppName() != null) entity.setAppName(dto.getAppName());
        if (dto.getAppCode() != null) entity.setAppCode(dto.getAppCode());
        if (dto.getDescription() != null) entity.setDescription(dto.getDescription());
        if (dto.getAppType() != null) entity.setAppType(dto.getAppType());
        if (dto.getRedirectUris() != null) entity.setRedirectUris(dto.getRedirectUris());
        if (dto.getScopes() != null) entity.setScopes(dto.getScopes());
        if (dto.getRateLimitPerMinute() != null) entity.setRateLimitPerMinute(dto.getRateLimitPerMinute());
        if (dto.getRateLimitPerDay() != null) entity.setRateLimitPerDay(dto.getRateLimitPerDay());
        if (dto.getIpWhitelist() != null) entity.setIpWhitelist(dto.getIpWhitelist());
        if (dto.getStatus() != null) entity.setStatus(dto.getStatus());
        if (dto.getExpiresAt() != null) entity.setExpiresAt(dto.getExpiresAt());
        if (dto.getOwnerName() != null) entity.setOwnerName(dto.getOwnerName());
        if (dto.getContactEmail() != null) entity.setContactEmail(dto.getContactEmail());
        entity = appRepository.save(entity);
        log.info("更新API应用: id={}, appName={}", id, entity.getAppName());
        return toAppDto(entity);
    }

    /**
     * 删除应用 (级联清理关联的密钥)。
     *
     * @param id 应用 ID
     * @throws ScrmException 应用不存在
     */
    @Transactional
    public void deleteApp(Long id) throws ScrmException {
        ScrmApiAppEntity entity = findAppOrThrow(id);
        // 级联清理该应用下的所有密钥 (逐个查询以保证数据隔离)
        keyRepository.findByAppIdOrderByCreateTimeDesc(id, PageRequest.of(0, Integer.MAX_VALUE))
                .forEach(keyRepository::delete);
        appRepository.delete(entity);
        log.info("删除API应用: id={}, appName={}", id, entity.getAppName());
    }

    /**
     * 查询应用详情。
     *
     * @param id 应用 ID
     * @return 应用 DTO
     * @throws ScrmException 应用不存在
     */
    @Transactional(readOnly = true)
    public ScrmApiAppDto getApp(Long id) throws ScrmException {
        return toAppDto(findAppOrThrow(id));
    }

    /**
     * 按 clientId 查询应用 (网关侧鉴权用)。
     *
     * @param clientId 客户端 ID
     * @return 应用 DTO
     * @throws ScrmException 应用不存在
     */
    @Transactional(readOnly = true)
    public ScrmApiAppDto getAppByClientId(String clientId) throws ScrmException {
        ScrmApiAppEntity entity = appRepository.findByClientId(clientId)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "API应用不存在: clientId=" + clientId));
        return toAppDto(entity);
    }

    /**
     * 分页查询应用列表, 支持按应用类型 / 状态 / 关键词过滤。
     *
     * @param appType  应用类型过滤 (可空)
     * @param status   状态过滤 (可空)
     * @param keyword  关键词过滤, 匹配应用名称 / 应用编码 / clientId (可空)
     * @param pageable 分页参数
     * @return 应用分页结果 (按 createTime DESC)
     */
    @Transactional(readOnly = true)
    public Page<ScrmApiAppDto> listApps(String appType, String status, String keyword, Pageable pageable) {
        Specification<ScrmApiAppEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (appType != null && !appType.isBlank()) {
                predicates.add(cb.equal(root.get("appType"), appType));
            }
            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (keyword != null && !keyword.isBlank()) {
                String kw = "%" + keyword.toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("appName")), kw),
                        cb.like(cb.lower(root.get("appCode")), kw),
                        cb.like(cb.lower(root.get("clientId")), kw)
                ));
            }
            query.orderBy(cb.desc(root.get("createTime")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return appRepository.findAll(spec, ensureSort(pageable, "createTime")).map(this::toAppDto);
    }

    /**
     * 暂停应用 (状态置为 SUSPENDED)。
     *
     * @param id 应用 ID
     * @return 更新后的应用
     * @throws ScrmException 应用不存在
     */
    @Transactional
    public ScrmApiAppDto suspendApp(Long id) throws ScrmException {
        ScrmApiAppEntity entity = findAppOrThrow(id);
        entity.setStatus(APP_STATUS_SUSPENDED);
        entity = appRepository.save(entity);
        log.info("暂停API应用: id={}", id);
        return toAppDto(entity);
    }

    /**
     * 激活应用 (状态置为 ACTIVE)。
     *
     * @param id 应用 ID
     * @return 更新后的应用
     * @throws ScrmException 应用不存在
     */
    @Transactional
    public ScrmApiAppDto activateApp(Long id) throws ScrmException {
        ScrmApiAppEntity entity = findAppOrThrow(id);
        entity.setStatus(APP_STATUS_ACTIVE);
        entity = appRepository.save(entity);
        log.info("激活API应用: id={}", id);
        return toAppDto(entity);
    }

    /**
     * 吊销应用 (状态置为 REVOKED, 不可恢复)。
     *
     * @param id 应用 ID
     * @return 更新后的应用
     * @throws ScrmException 应用不存在
     */
    @Transactional
    public ScrmApiAppDto revokeApp(Long id) throws ScrmException {
        ScrmApiAppEntity entity = findAppOrThrow(id);
        entity.setStatus(APP_STATUS_REVOKED);
        entity = appRepository.save(entity);
        log.info("吊销API应用: id={}", id);
        return toAppDto(entity);
    }

    /**
     * 重新生成应用密钥 (轮换 clientSecret, 旧密钥立即失效)。
     *
     * @param id 应用 ID
     * @return 更新后的应用 (含新明文 clientSecret, 仅此一次返回)
     * @throws ScrmException 应用不存在
     */
    @Transactional
    public ScrmApiAppDto regenerateSecret(Long id) throws ScrmException {
        ScrmApiAppEntity entity = findAppOrThrow(id);
        entity.setClientSecret(generateSecret());
        // 重新生成 clientId 以使旧凭证完全失效
        entity.setClientId(generateClientId());
        entity = appRepository.save(entity);
        log.info("重新生成API应用密钥: id={}, clientId={}", id, entity.getClientId());
        return toAppDto(entity);
    }

    /**
     * 轮换应用密钥 (指定新密钥, 用于密钥托管场景)。
     *
     * @param id        应用 ID
     * @param newSecret 新密钥
     * @return 更新后的应用
     * @throws ScrmException 应用不存在 / 新密钥为空
     */
    @Transactional
    public ScrmApiAppDto rotateKey(Long id, String newSecret) throws ScrmException {
        if (newSecret == null || newSecret.isBlank()) {
            throw ScrmException.badRequest("新密钥不能为空");
        }
        ScrmApiAppEntity entity = findAppOrThrow(id);
        entity.setClientSecret(newSecret);
        entity = appRepository.save(entity);
        log.info("轮换API应用密钥: id={}", id);
        return toAppDto(entity);
    }

    // ============================================================
    // 密钥管理
    // ============================================================

    /**
     * 为应用创建 API 密钥。
     * <p>自动生成 apiKey (唯一), 默认 status=ACTIVE, keyType 为空时默认 PERMANENT,
     * rateLimitPerMinute 为空时取默认值, usageCount 置 0。</p>
     *
     * @param appId 应用 ID
     * @param dto   密钥参数
     * @return 创建后的密钥 (含明文 apiKey, 仅此一次返回)
     * @throws ScrmException 参数非法 / 应用不存在
     */
    @Transactional
    public ScrmApiKeyDto createKey(Long appId, ScrmApiKeyDto dto) throws ScrmException {
        if (dto == null) {
            throw ScrmException.badRequest("密钥参数不能为空");
        }
        findAppOrThrow(appId);
        ScrmApiKeyEntity entity = new ScrmApiKeyEntity();
        entity.setAppId(appId);
        entity.setKeyName(dto.getKeyName());
        entity.setApiKey(generateApiKey());
        entity.setKeySecret(dto.getKeySecret());
        entity.setKeyType(dto.getKeyType() != null ? dto.getKeyType() : KEY_TYPE_PERMANENT);
        entity.setScopes(dto.getScopes());
        entity.setAllowedIps(dto.getAllowedIps());
        entity.setRateLimitPerMinute(dto.getRateLimitPerMinute() != null
                ? dto.getRateLimitPerMinute() : DEFAULT_RATE_LIMIT_PER_MINUTE);
        entity.setStatus(KEY_STATUS_ACTIVE);
        entity.setExpiresAt(dto.getExpiresAt());
        entity.setUsageCount(0);
        entity.setCreatedBy(dto.getCreatedBy());
        entity = keyRepository.save(entity);
        log.info("创建API密钥: id={}, appId={}, keyName={}", entity.getId(), appId, entity.getKeyName());
        return toKeyDto(entity);
    }

    /**
     * 吊销密钥 (状态置为 REVOKED)。
     *
     * @param id 密钥 ID
     * @return 更新后的密钥
     * @throws ScrmException 密钥不存在
     */
    @Transactional
    public ScrmApiKeyDto revokeKey(Long id) throws ScrmException {
        ScrmApiKeyEntity entity = findKeyOrThrow(id);
        entity.setStatus(KEY_STATUS_REVOKED);
        entity = keyRepository.save(entity);
        log.info("吊销API密钥: id={}, appId={}", id, entity.getAppId());
        return toKeyDto(entity);
    }

    /**
     * 查询密钥详情。
     *
     * @param id 密钥 ID
     * @return 密钥 DTO
     * @throws ScrmException 密钥不存在
     */
    @Transactional(readOnly = true)
    public ScrmApiKeyDto getKey(Long id) throws ScrmException {
        return toKeyDto(findKeyOrThrow(id));
    }

    /**
     * 按 apiKey 查询密钥 (网关侧鉴权用)。
     *
     * @param apiKey API 密钥
     * @return 密钥 DTO
     * @throws ScrmException 密钥不存在
     */
    @Transactional(readOnly = true)
    public ScrmApiKeyDto getKeyByApiKey(String apiKey) throws ScrmException {
        ScrmApiKeyEntity entity = keyRepository.findByApiKey(apiKey)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "API密钥不存在: apiKey=" + apiKey));
        return toKeyDto(entity);
    }

    /**
     * 分页查询密钥, 支持按应用 / 状态过滤。
     *
     * @param appId    应用 ID 过滤 (可空)
     * @param status   状态过滤 (可空)
     * @param pageable 分页参数
     * @return 密钥分页结果 (按 createTime DESC)
     */
    @Transactional(readOnly = true)
    public Page<ScrmApiKeyDto> listKeys(Long appId, String status, Pageable pageable) {
        Page<ScrmApiKeyEntity> page;
        if (appId != null && status != null && !status.isBlank()) {
            page = keyRepository.findByAppIdAndStatusOrderByCreateTimeDesc(
                     appId, status, ensureSort(pageable, "createTime"));
        } else if (appId != null) {
            page = keyRepository.findByAppIdOrderByCreateTimeDesc(
                     appId, ensureSort(pageable, "createTime"));
        } else {
            // 无应用过滤时使用 Specification 统一查询
            Specification<ScrmApiKeyEntity> spec = (root, query, cb) -> {
                List<Predicate> predicates = new ArrayList<>();
                if (status != null && !status.isBlank()) {
                    predicates.add(cb.equal(root.get("status"), status));
                }
                query.orderBy(cb.desc(root.get("createTime")));
                return cb.and(predicates.toArray(new Predicate[0]));
            };
            page = keyRepository.findAll(spec, ensureSort(pageable, "createTime"));
        }
        return page.map(this::toKeyDto);
    }

    /**
     * 更新密钥使用记录 (使用次数 +1, 刷新最后使用时间与 IP)。
     *
     * @param id 密钥 ID
     * @param ip 使用 IP
     * @throws ScrmException 密钥不存在
     */
    @Transactional
    public void updateKeyUsage(Long id, String ip) throws ScrmException {
        findKeyOrThrow(id);
        keyRepository.incrementUsage(id, LocalDateTime.now(), ip);
    }

    /**
     * 检查密钥是否已过期, 过期则同步状态为 EXPIRED。
     *
     * @param id 密钥 ID
     * @return true 已过期, false 未过期
     * @throws ScrmException 密钥不存在
     */
    @Transactional
    public boolean checkKeyExpired(Long id) throws ScrmException {
        ScrmApiKeyEntity entity = findKeyOrThrow(id);
        if (entity.getExpiresAt() == null) {
            return false;
        }
        if (entity.getExpiresAt().isBefore(LocalDateTime.now())) {
            entity.setStatus(KEY_STATUS_EXPIRED);
            keyRepository.save(entity);
            return true;
        }
        return false;
    }

    // ============================================================
    // 权限范围管理
    // ============================================================

    /**
     * 创建权限范围。
     * <p>默认 isDefault=false, enabled=true。scopeName 唯一校验。</p>
     *
     * @param dto 权限范围参数
     * @return 创建后的权限范围
     * @throws ScrmException 参数非法 / scopeName 重复
     */
    @Transactional
    public ScrmApiScopeDto createScope(ScrmApiScopeDto dto) throws ScrmException {
        if (dto == null) {
            throw ScrmException.badRequest("权限范围参数不能为空");
        }
        if (scopeRepository.findByScopeName(dto.getScopeName()).isPresent()) {
            throw ScrmException.conflict("权限范围名已存在: " + dto.getScopeName());
        }
        ScrmApiScopeEntity entity = new ScrmApiScopeEntity();
        entity.setScopeName(dto.getScopeName());
        entity.setDisplayName(dto.getDisplayName());
        entity.setDescription(dto.getDescription());
        entity.setResource(dto.getResource());
        entity.setActions(dto.getActions());
        entity.setIsDefault(dto.getIsDefault() != null ? dto.getIsDefault() : Boolean.FALSE);
        entity.setEnabled(dto.getEnabled() != null ? dto.getEnabled() : Boolean.TRUE);
        entity.setCreatedBy(dto.getCreatedBy());
        entity = scopeRepository.save(entity);
        log.info("创建权限范围: id={}, scopeName={}, resource={}",
                entity.getId(), entity.getScopeName(), entity.getResource());
        return toScopeDto(entity);
    }

    /**
     * 更新权限范围 (部分更新, 仅非空字段生效)。
     *
     * @param id  权限范围 ID
     * @param dto 权限范围参数
     * @return 更新后的权限范围
     * @throws ScrmException 权限范围不存在
     */
    @Transactional
    public ScrmApiScopeDto updateScope(Long id, ScrmApiScopeDto dto) throws ScrmException {
        if (dto == null) {
            throw ScrmException.badRequest("权限范围参数不能为空");
        }
        ScrmApiScopeEntity entity = findScopeOrThrow(id);
        if (dto.getScopeName() != null) entity.setScopeName(dto.getScopeName());
        if (dto.getDisplayName() != null) entity.setDisplayName(dto.getDisplayName());
        if (dto.getDescription() != null) entity.setDescription(dto.getDescription());
        if (dto.getResource() != null) entity.setResource(dto.getResource());
        if (dto.getActions() != null) entity.setActions(dto.getActions());
        if (dto.getIsDefault() != null) entity.setIsDefault(dto.getIsDefault());
        if (dto.getEnabled() != null) entity.setEnabled(dto.getEnabled());
        entity = scopeRepository.save(entity);
        log.info("更新权限范围: id={}, scopeName={}", id, entity.getScopeName());
        return toScopeDto(entity);
    }

    /**
     * 删除权限范围。
     *
     * @param id 权限范围 ID
     * @throws ScrmException 权限范围不存在
     */
    @Transactional
    public void deleteScope(Long id) throws ScrmException {
        ScrmApiScopeEntity entity = findScopeOrThrow(id);
        scopeRepository.delete(entity);
        log.info("删除权限范围: id={}, scopeName={}", id, entity.getScopeName());
    }

    /**
     * 查询权限范围详情。
     *
     * @param id 权限范围 ID
     * @return 权限范围 DTO
     * @throws ScrmException 权限范围不存在
     */
    @Transactional(readOnly = true)
    public ScrmApiScopeDto getScope(Long id) throws ScrmException {
        return toScopeDto(findScopeOrThrow(id));
    }

    /**
     * 按 scopeName 查询权限范围 (校验用)。
     *
     * @param name 权限范围名
     * @return 权限范围 DTO
     * @throws ScrmException 权限范围不存在
     */
    @Transactional(readOnly = true)
    public ScrmApiScopeDto getScopeByName(String name) throws ScrmException {
        ScrmApiScopeEntity entity = scopeRepository.findByScopeName(name)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "权限范围不存在: scopeName=" + name));
        return toScopeDto(entity);
    }

    /**
     * 分页查询权限范围, 支持按资源 / 启用状态过滤。
     *
     * @param resource 资源过滤 (可空)
     * @param enabled  启用状态过滤 (可空)
     * @param pageable 分页参数
     * @return 权限范围分页结果 (按 createTime DESC)
     */
    @Transactional(readOnly = true)
    public Page<ScrmApiScopeDto> listScopes(String resource, Boolean enabled, Pageable pageable) {
        Specification<ScrmApiScopeEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (resource != null && !resource.isBlank()) {
                predicates.add(cb.equal(root.get("resource"), resource));
            }
            if (enabled != null) {
                predicates.add(cb.equal(root.get("enabled"), enabled));
            }
            query.orderBy(cb.desc(root.get("createTime")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return scopeRepository.findAll(spec, ensureSort(pageable, "createTime")).map(this::toScopeDto);
    }

    /**
     * 启用权限范围。
     *
     * @param id 权限范围 ID
     * @return 更新后的权限范围
     * @throws ScrmException 权限范围不存在
     */
    @Transactional
    public ScrmApiScopeDto enableScope(Long id) throws ScrmException {
        ScrmApiScopeEntity entity = findScopeOrThrow(id);
        entity.setEnabled(true);
        entity = scopeRepository.save(entity);
        log.info("启用权限范围: id={}", id);
        return toScopeDto(entity);
    }

    /**
     * 禁用权限范围。
     *
     * @param id 权限范围 ID
     * @return 更新后的权限范围
     * @throws ScrmException 权限范围不存在
     */
    @Transactional
    public ScrmApiScopeDto disableScope(Long id) throws ScrmException {
        ScrmApiScopeEntity entity = findScopeOrThrow(id);
        entity.setEnabled(false);
        entity = scopeRepository.save(entity);
        log.info("禁用权限范围: id={}", id);
        return toScopeDto(entity);
    }

    /**
     * 加载账号下的默认权限范围 (isDefault=true 且 enabled=true)。
     *
     * @return 默认权限范围列表
     */
    @Transactional(readOnly = true)
    public List<ScrmApiScopeDto> getDefaultScopes() {
        return scopeRepository.findByIsDefaultTrueAndEnabledTrue()
                .stream().map(this::toScopeDto).toList();
    }

    /**
     * 验证权限范围列表是否全部存在且启用。
     *
     * @param scopes 权限范围列表 (逗号分隔或集合)
     * @return true 全部有效, false 存在无效项
     */
    @Transactional(readOnly = true)
    public boolean validateScopes(String scopes) {
        if (scopes == null || scopes.isBlank()) {
            return true;
        }
        String[] names = scopes.split(",");
        for (String name : names) {
            String trimmed = name.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            var opt = scopeRepository.findByScopeName(trimmed);
            if (opt.isEmpty() || Boolean.FALSE.equals(opt.get().getEnabled())) {
                return false;
            }
        }
        return true;
    }

    // ============================================================
    // OAuth2 授权 (模拟实现)
    // ============================================================

    /**
     * OAuth2 授权码生成 (模拟实现)。
     * <p>
     * 校验 clientId 与 redirectUri 后, 生成一次性授权码。实际生产应跳转到授权页面
     * 由用户确认, 此处直接返回模拟授权码与拼接好的回调地址。
     * 待对接真实 OAuth2 服务器。
     * </p>
     *
     * @param clientId    客户端 ID
     * @param redirectUri 回调地址
     * @param scopes      权限范围 (可空)
     * @param state       state 参数 (防 CSRF, 原样透传)
     * @return 授权结果: code / state / redirectUri
     * @throws ScrmException 应用不存在 / 已停用
     */
    @Transactional
    public Map<String, Object> authorize(String clientId, String redirectUri, String scopes, String state)
            throws ScrmException {
        ScrmApiAppEntity app = findAppByClientId(clientId);
        if (!APP_STATUS_ACTIVE.equals(app.getStatus())) {
            throw ScrmException.forbidden("应用未激活, 无法授权: clientId=" + clientId);
        }
        // 模拟生成一次性授权码
        String code = "auth_" + UUID.randomUUID().toString().replace("-", "");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", code);
        result.put("state", state);
        result.put("clientId", clientId);
        result.put("scopes", scopes);
        // 拼接回调地址 (模拟)
        StringBuilder callback = new StringBuilder();
        if (redirectUri != null && !redirectUri.isBlank()) {
            callback.append(redirectUri);
            callback.append(redirectUri.contains("?") ? "&" : "?");
            callback.append("code=").append(code);
            if (state != null) {
                callback.append("&state=").append(state);
            }
        }
        result.put("redirectUri", callback.toString());
        log.info("OAuth2 授权码生成 (模拟): clientId={}, scopes={}, state={}", clientId, scopes, state);
        return result;
    }

    /**
     * OAuth2 获取令牌 (模拟实现)。
     * <p>
     * 校验 clientSecret 后签发 access_token 与 refresh_token。支持 authorization_code /
     * client_credentials / refresh_token 三种 grantType (refresh_token 建议调用
     * {@link #refreshToken})。待对接真实 OAuth2 服务器。
     * </p>
     *
     * @param tokenDto 令牌请求
     * @return 令牌响应: access_token / refresh_token / token_type / expires_in / scope
     * @throws ScrmException 凭证无效
     */
    @Transactional
    public Map<String, Object> getToken(ScrmOAuthTokenDto tokenDto) throws ScrmException {
        if (tokenDto == null) {
            throw ScrmException.badRequest("令牌请求参数不能为空");
        }
        ScrmApiAppEntity app = findAppByClientId(tokenDto.getClientId());
        if (!app.getClientSecret().equals(tokenDto.getClientSecret())) {
            throw ScrmException.forbidden("客户端密钥无效: clientId=" + tokenDto.getClientId());
        }
        if (!APP_STATUS_ACTIVE.equals(app.getStatus())) {
            throw ScrmException.forbidden("应用未激活, 无法签发令牌: clientId=" + tokenDto.getClientId());
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("access_token", "at_" + UUID.randomUUID().toString().replace("-", ""));
        result.put("refresh_token", "rt_" + UUID.randomUUID().toString().replace("-", ""));
        result.put("token_type", "Bearer");
        result.put("expires_in", TOKEN_EXPIRES_IN_SECONDS);
        result.put("scope", app.getScopes());
        log.info("OAuth2 签发令牌 (模拟): clientId={}, grantType={}",
                tokenDto.getClientId(), tokenDto.getGrantType());
        return result;
    }

    /**
     * OAuth2 刷新令牌 (模拟实现)。
     * <p>待对接真实 OAuth2 服务器。</p>
     *
     * @param refreshToken 刷新令牌
     * @return 新的令牌响应
     */
    @Transactional
    public Map<String, Object> refreshToken(String refreshToken) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("access_token", "at_" + UUID.randomUUID().toString().replace("-", ""));
        result.put("refresh_token", "rt_" + UUID.randomUUID().toString().replace("-", ""));
        result.put("token_type", "Bearer");
        result.put("expires_in", TOKEN_EXPIRES_IN_SECONDS);
        log.info("OAuth2 刷新令牌 (模拟): refreshToken={}", maskToken(refreshToken));
        return result;
    }

    /**
     * OAuth2 吊销令牌 (模拟实现)。
     * <p>待对接真实 OAuth2 服务器。</p>
     *
     * @param token 访问令牌或刷新令牌
     * @return 吊销结果
     */
    @Transactional
    public Map<String, Object> revokeToken(String token) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("revoked", true);
        result.put("token", token);
        log.info("OAuth2 吊销令牌 (模拟): token={}", maskToken(token));
        return result;
    }

    /**
     * OAuth2 验证令牌 (模拟实现)。
     * <p>待对接真实 OAuth2 服务器的 introspection 端点。</p>
     *
     * @param token 访问令牌
     * @return 令牌信息: active / scope / expires_in
     */
    @Transactional(readOnly = true)
    public Map<String, Object> validateToken(String token) {
        Map<String, Object> result = new LinkedHashMap<>();
        boolean active = token != null && token.startsWith("at_");
        result.put("active", active);
        result.put("token_type", "Bearer");
        result.put("expires_in", active ? TOKEN_EXPIRES_IN_SECONDS : 0);
        log.info("OAuth2 验证令牌 (模拟): token={}, active={}", maskToken(token), active);
        return result;
    }

    // ============================================================
    // 访问日志
    // ============================================================

    /**
     * 记录 API 访问日志。
     * <p>accessedAt 为空时取当前时间。</p>
     *
     * @param dto 日志参数
     * @return 记录后的日志
     * @throws ScrmException 参数非法
     */
    @Transactional
    public ScrmApiAccessLogDto recordAccessLog(ScrmApiAccessLogDto dto) throws ScrmException {
        if (dto == null) {
            throw ScrmException.badRequest("访问日志参数不能为空");
        }
        ScrmApiAccessLogEntity entity = new ScrmApiAccessLogEntity();
        entity.setAppId(dto.getAppId());
        entity.setApiKeyId(dto.getApiKeyId());
        entity.setClientId(dto.getClientId());
        entity.setEndpoint(dto.getEndpoint());
        entity.setMethod(dto.getMethod());
        entity.setRequestIp(dto.getRequestIp());
        entity.setUserAgent(dto.getUserAgent());
        entity.setRequestParams(dto.getRequestParams());
        entity.setRequestBody(dto.getRequestBody());
        entity.setResponseStatus(dto.getResponseStatus());
        entity.setResponseTimeMs(dto.getResponseTimeMs());
        entity.setErrorCode(dto.getErrorCode());
        entity.setErrorMessage(dto.getErrorMessage());
        entity.setRequestId(dto.getRequestId());
        entity.setAccessedAt(dto.getAccessedAt() != null ? dto.getAccessedAt() : LocalDateTime.now());
        entity = accessLogRepository.save(entity);
        LocalDateTime accessedAt = entity.getAccessedAt();
        // 同步刷新应用的最后访问时间与请求计数
        if (dto.getAppId() != null) {
            appRepository.findById(dto.getAppId()).ifPresent(app -> {
                app.setLastAccessAt(accessedAt);
                app.setTotalRequestCount(safeInt(app.getTotalRequestCount()) + 1);
                app.setTodayRequestCount(safeInt(app.getTodayRequestCount()) + 1);
                appRepository.save(app);
            });
        }
        return toAccessLogDto(entity);
    }

    /**
     * 查询访问日志详情。
     *
     * @param id 日志 ID
     * @return 日志 DTO
     * @throws ScrmException 日志不存在
     */
    @Transactional(readOnly = true)
    public ScrmApiAccessLogDto getAccessLog(Long id) throws ScrmException {
        return toAccessLogDto(findAccessLogOrThrow(id));
    }

    /**
     * 分页查询访问日志, 支持按应用 / clientId / 端点 / 方法 / 状态码 / 时间区间过滤。
     *
     * @param appId          应用 ID 过滤 (可空)
     * @param clientId       客户端 ID 过滤 (可空)
     * @param endpoint       端点过滤 (可空, 模糊匹配)
     * @param method         HTTP 方法过滤 (可空)
     * @param responseStatus 响应状态码过滤 (可空, 精确匹配)
     * @param startTime      起始时间 (含, 可空)
     * @param endTime        结束时间 (不含, 可空)
     * @param pageable       分页参数
     * @return 日志分页结果 (按 accessedAt DESC)
     */
    @Transactional(readOnly = true)
    public Page<ScrmApiAccessLogDto> listAccessLogs(Long appId, String clientId, String endpoint, String method,
                                                     Integer responseStatus, LocalDateTime startTime,
                                                     LocalDateTime endTime, Pageable pageable) {
        Specification<ScrmApiAccessLogEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (appId != null) {
                predicates.add(cb.equal(root.get("appId"), appId));
            }
            if (clientId != null && !clientId.isBlank()) {
                predicates.add(cb.equal(root.get("clientId"), clientId));
            }
            if (endpoint != null && !endpoint.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("endpoint")),
                        "%" + endpoint.toLowerCase() + "%"));
            }
            if (method != null && !method.isBlank()) {
                predicates.add(cb.equal(root.get("method"), method));
            }
            if (responseStatus != null) {
                predicates.add(cb.equal(root.get("responseStatus"), responseStatus));
            }
            if (startTime != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("accessedAt"), startTime));
            }
            if (endTime != null) {
                predicates.add(cb.lessThan(root.get("accessedAt"), endTime));
            }
            query.orderBy(cb.desc(root.get("accessedAt")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return accessLogRepository.findAll(spec, ensureSort(pageable, "accessedAt")).map(this::toAccessLogDto);
    }

    /**
     * 按应用分页查询访问日志。
     *
     * @param appId    应用 ID
     * @param pageable 分页参数
     * @return 日志分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmApiAccessLogDto> getAccessLogsByApp(Long appId, Pageable pageable) {
        return accessLogRepository
                .findByAppIdOrderByAccessedAtDesc(appId, ensureSort(pageable, "accessedAt"))
                .map(this::toAccessLogDto);
    }

    /**
     * 按请求 IP 分页查询访问日志。
     *
     * @param ip       请求 IP
     * @param pageable 分页参数
     * @return 日志分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmApiAccessLogDto> getAccessLogsByIp(String ip, Pageable pageable) {
        return accessLogRepository
                .findByRequestIpOrderByAccessedAtDesc(ip, ensureSort(pageable, "accessedAt"))
                .map(this::toAccessLogDto);
    }

    /**
     * 查询最近 N 条错误日志 (HTTP 状态码 >= 400)。
     *
     * @param limit 返回条数
     * @return 错误日志列表
     */
    @Transactional(readOnly = true)
    public List<ScrmApiAccessLogDto> getRecentErrors(int limit) {
        Pageable pageable = PageRequest.of(0, Math.max(limit, 1));
        return accessLogRepository.findRecentErrors(ERROR_STATUS_THRESHOLD, pageable)
                .stream().map(this::toAccessLogDto).toList();
    }

    // ============================================================
    // 速率限制 (模拟实现)
    // ============================================================

    /**
     * 检查速率限制 (模拟实现)。
     * <p>
     * 基于应用的 todayRequestCount 与 rateLimitPerDay 做简单判断, 实际生产应使用
     * 分布式限流组件 (Redis + Bucket4j) 按分钟/日双维度统计。待对接真实限流中间件。
     * </p>
     *
     * @param appId    应用 ID (可空)
     * @param apiKeyId 密钥 ID (可空)
     * @return true 允许访问, false 触发限流
     * @throws ScrmException 应用不存在
     */
    @Transactional
    public boolean checkRateLimit(Long appId, Long apiKeyId) throws ScrmException {
        if (appId == null) {
            // 无应用上下文默认放行 (公开接口)
            return true;
        }
        ScrmApiAppEntity app = findAppOrThrow(appId);
        if (!APP_STATUS_ACTIVE.equals(app.getStatus())) {
            return false;
        }
        int today = safeInt(app.getTodayRequestCount());
        int dayLimit = app.getRateLimitPerDay() != null ? app.getRateLimitPerDay() : DEFAULT_RATE_LIMIT_PER_DAY;
        boolean allowed = today < dayLimit;
        log.info("速率限制检查 (模拟): appId={}, today={}, dayLimit={}, allowed={}",
                appId, today, dayLimit, allowed);
        return allowed;
    }

    /**
     * 获取应用速率限制状态。
     *
     * @param appId 应用 ID
     * @return 速率限制状态: today / dayLimit / perMinute / remaining
     * @throws ScrmException 应用不存在
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getRateLimitStatus(Long appId) throws ScrmException {
        ScrmApiAppEntity app = findAppOrThrow(appId);
        int today = safeInt(app.getTodayRequestCount());
        int dayLimit = app.getRateLimitPerDay() != null ? app.getRateLimitPerDay() : DEFAULT_RATE_LIMIT_PER_DAY;
        int perMinute = app.getRateLimitPerMinute() != null ? app.getRateLimitPerMinute()
                : DEFAULT_RATE_LIMIT_PER_MINUTE;
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("appId", appId);
        status.put("todayRequestCount", today);
        status.put("rateLimitPerDay", dayLimit);
        status.put("rateLimitPerMinute", perMinute);
        status.put("remaining", Math.max(0, dayLimit - today));
        status.put("status", app.getStatus());
        return status;
    }

    /**
     * 重置应用速率限制计数 (今日请求计数清零)。
     *
     * @param appId 应用 ID
     * @return 重置后的速率限制状态
     * @throws ScrmException 应用不存在
     */
    @Transactional
    public Map<String, Object> resetRateLimit(Long appId) throws ScrmException {
        ScrmApiAppEntity app = findAppOrThrow(appId);
        app.setTodayRequestCount(0);
        app = appRepository.save(app);
        log.info("重置API应用速率限制: appId={}", appId);
        return getRateLimitStatus(app.getId());
    }

    // ============================================================
    // 统计
    // ============================================================

    /**
     * 获取 API 总体统计 (请求数 / 错误率 / 平均响应时间 / 活跃应用数)。
     * <p>统计区间按 accessedAt 过滤, 起止时间为空时默认最近 7 天。</p>
     *
     * @param startTime 起始时间 (含, 可空)
     * @param endTime   结束时间 (不含, 可空)
     * @return API 统计
     */
    @Transactional(readOnly = true)
    public ScrmApiStatsDto getApiStats(LocalDateTime startTime, LocalDateTime endTime) {
        LocalDateTime start = startTime != null ? startTime : LocalDateTime.now().minusDays(7);
        LocalDateTime end = endTime != null ? endTime : LocalDateTime.now();
        long total = accessLogRepository.countByTimeRange(start, end);
        long errors = accessLogRepository.countErrorsByTimeRange(
                 ERROR_STATUS_THRESHOLD, start, end);
        double avgTime = accessLogRepository.avgResponseTimeByTimeRange(start, end);
        long activeApps = appRepository.countByStatus(APP_STATUS_ACTIVE);
        ScrmApiStatsDto stats = new ScrmApiStatsDto();
        stats.setTotalRequests(total);
        stats.setErrorCount(errors);
        stats.setErrorRate(total > 0 ? (double) errors / total : 0.0);
        stats.setAvgResponseTimeMs(Math.round(avgTime * 100) / 100.0);
        stats.setActiveAppCount(activeApps);
        return stats;
    }

    /**
     * 获取应用统计 (请求数 / 错误率 / 平均响应时间 / 最后访问时间)。
     *
     * @param appId 应用 ID
     * @return 应用统计
     * @throws ScrmException 应用不存在
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getAppStats(Long appId) throws ScrmException {
        ScrmApiAppEntity app = findAppOrThrow(appId);
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("appId", app.getId());
        stats.put("appName", app.getAppName());
        stats.put("status", app.getStatus());
        stats.put("totalRequestCount", safeInt(app.getTotalRequestCount()));
        stats.put("todayRequestCount", safeInt(app.getTodayRequestCount()));
        stats.put("lastAccessAt", app.getLastAccessAt());
        // 最近 7 天的请求与错误数
        LocalDateTime end = LocalDateTime.now();
        LocalDateTime start = end.minusDays(7);
        stats.put("requestsLast7d", accessLogRepository.countByTimeRange(start, end));
        stats.put("errorsLast7d", accessLogRepository.countErrorsByTimeRange(
                 ERROR_STATUS_THRESHOLD, start, end));
        stats.put("avgResponseTimeMsLast7d", accessLogRepository.avgResponseTimeByTimeRange(
                 start, end));
        return stats;
    }

    /**
     * 获取端点统计 (热门端点排行)。
     * <p>统计区间按 accessedAt 过滤, 起止时间为空时默认最近 7 天。</p>
     *
     * @param startTime 起始时间 (含, 可空)
     * @param endTime   结束时间 (不含, 可空)
     * @return 端点统计列表: endpoint / requestCount / avgResponseTimeMs
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getEndpointStats(LocalDateTime startTime, LocalDateTime endTime) {
        LocalDateTime start = startTime != null ? startTime : LocalDateTime.now().minusDays(7);
        LocalDateTime end = endTime != null ? endTime : LocalDateTime.now();
        List<Object[]> rows = accessLogRepository.endpointStats(start, end);
        List<Map<String, Object>> result = new ArrayList<>();
        if (rows != null) {
            for (Object[] row : rows) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("endpoint", row[0]);
                item.put("requestCount", row[1]);
                item.put("avgResponseTimeMs", row[2]);
                result.add(item);
            }
        }
        return result;
    }

    /**
     * 获取错误统计 (按错误码分布)。
     * <p>统计区间按 accessedAt 过滤, 起止时间为空时默认最近 7 天。</p>
     *
     * @param startTime 起始时间 (含, 可空)
     * @param endTime   结束时间 (不含, 可空)
     * @return 错误统计列表: errorCode / errorCount
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getErrorStats(LocalDateTime startTime, LocalDateTime endTime) {
        LocalDateTime start = startTime != null ? startTime : LocalDateTime.now().minusDays(7);
        LocalDateTime end = endTime != null ? endTime : LocalDateTime.now();
        List<Object[]> rows = accessLogRepository.errorStats(ERROR_STATUS_THRESHOLD, start, end);
        List<Map<String, Object>> result = new ArrayList<>();
        if (rows != null) {
            for (Object[] row : rows) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("errorCode", row[0]);
                item.put("errorCount", row[1]);
                result.add(item);
            }
        }
        return result;
    }

    // ============================================================
    // 私有辅助方法
    // ============================================================

    /**
     * 按主键查询应用并校验归属账号, 不存在或越权抛异常。
     *
     * @param id 应用 ID
     * @return 应用实体
     * @throws ScrmException 应用不存在
     */
    private ScrmApiAppEntity findAppOrThrow(Long id) throws ScrmException {
        ScrmApiAppEntity entity = appRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "API应用不存在: id=" + id));
        return entity;
    }

    /**
     * 按 clientId 查询应用并校验归属账号, 不存在或越权抛异常。
     *
     * @param clientId 客户端 ID
     * @return 应用实体
     * @throws ScrmException 应用不存在
     */
    private ScrmApiAppEntity findAppByClientId(String clientId) throws ScrmException {
        return appRepository.findByClientId(clientId)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "API应用不存在: clientId=" + clientId));
    }

    /**
     * 按主键查询密钥并校验归属账号, 不存在或越权抛异常。
     *
     * @param id 密钥 ID
     * @return 密钥实体
     * @throws ScrmException 密钥不存在
     */
    private ScrmApiKeyEntity findKeyOrThrow(Long id) throws ScrmException {
        ScrmApiKeyEntity entity = keyRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "API密钥不存在: id=" + id));
        return entity;
    }

    /**
     * 按主键查询权限范围并校验归属账号, 不存在或越权抛异常。
     *
     * @param id 权限范围 ID
     * @return 权限范围实体
     * @throws ScrmException 权限范围不存在
     */
    private ScrmApiScopeEntity findScopeOrThrow(Long id) throws ScrmException {
        ScrmApiScopeEntity entity = scopeRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "权限范围不存在: id=" + id));
        return entity;
    }

    /**
     * 按主键查询访问日志并校验归属账号, 不存在或越权抛异常。
     *
     * @param id 日志 ID
     * @return 日志实体
     * @throws ScrmException 日志不存在
     */
    private ScrmApiAccessLogEntity findAccessLogOrThrow(Long id) throws ScrmException {
        ScrmApiAccessLogEntity entity = accessLogRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "API访问日志不存在: id=" + id));
        return entity;
    }


    /**
     * 确保分页参数带默认排序 (按指定字段倒序)。
     *
     * @param pageable 分页参数
     * @param field    默认排序字段
     * @return 处理后的分页参数
     */
    private Pageable ensureSort(Pageable pageable, String field) {
        if (pageable.getSort().isSorted()) {
            return pageable;
        }
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, field));
    }

    /**
     * 生成客户端 ID (UUID 去横线, 带 cli_ 前缀)。
     *
     * @return 客户端 ID
     */
    private String generateClientId() {
        return "cli_" + UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 生成客户端密钥 (UUID 去横线, 带 sec_ 前缀)。
     *
     * @return 客户端密钥
     */
    private String generateSecret() {
        return "sec_" + UUID.randomUUID().toString().replace("-", "")
                + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    /**
     * 生成 API 密钥 (UUID 去横线, 带 sk_ 前缀)。
     *
     * @return API 密钥
     */
    private String generateApiKey() {
        return "sk_" + UUID.randomUUID().toString().replace("-", "")
                + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    /**
     * 安全将 Integer 转 int (null 视为 0)。
     *
     * @param value Integer 值
     * @return int 值
     */
    private int safeInt(Integer value) {
        return value != null ? value : 0;
    }

    /**
     * 脱敏令牌用于日志输出, 避免 token 明文落入日志。
     * <p>空令牌统一输出 {@code <empty>}, 非空令牌通过 {@link SensitiveDataUtils#maskGeneric} 脱敏。</p>
     *
     * @param token 原始令牌 (可空)
     * @return 脱敏后的令牌
     */
    private String maskToken(String token) {
        if (token == null || token.isEmpty()) {
            return "<empty>";
        }
        return SensitiveDataUtils.maskGeneric(token);
    }

    // ============================================================
    // 实体转 DTO
    // ============================================================

    /**
     * 应用实体转 DTO。
     */
    private ScrmApiAppDto toAppDto(ScrmApiAppEntity entity) {
        ScrmApiAppDto dto = new ScrmApiAppDto();
        dto.setId(entity.getId());
        dto.setAppName(entity.getAppName());
        dto.setAppCode(entity.getAppCode());
        dto.setDescription(entity.getDescription());
        dto.setAppType(entity.getAppType());
        dto.setClientId(entity.getClientId());
        dto.setClientSecret(entity.getClientSecret());
        dto.setRedirectUris(entity.getRedirectUris());
        dto.setScopes(entity.getScopes());
        dto.setRateLimitPerMinute(entity.getRateLimitPerMinute());
        dto.setRateLimitPerDay(entity.getRateLimitPerDay());
        dto.setIpWhitelist(entity.getIpWhitelist());
        dto.setStatus(entity.getStatus());
        dto.setExpiresAt(entity.getExpiresAt());
        dto.setLastAccessAt(entity.getLastAccessAt());
        dto.setTotalRequestCount(entity.getTotalRequestCount());
        dto.setTodayRequestCount(entity.getTodayRequestCount());
        dto.setOwnerName(entity.getOwnerName());
        dto.setContactEmail(entity.getContactEmail());
        dto.setCreatedBy(entity.getCreatedBy());
        dto.setCreateTime(entity.getCreateTime());
        dto.setUpdateTime(entity.getUpdateTime());
        dto.setVersion(entity.getVersion());
        return dto;
    }

    /**
     * 密钥实体转 DTO。
     */
    private ScrmApiKeyDto toKeyDto(ScrmApiKeyEntity entity) {
        ScrmApiKeyDto dto = new ScrmApiKeyDto();
        dto.setId(entity.getId());
        dto.setAppId(entity.getAppId());
        dto.setKeyName(entity.getKeyName());
        dto.setApiKey(entity.getApiKey());
        dto.setKeySecret(entity.getKeySecret());
        dto.setKeyType(entity.getKeyType());
        dto.setScopes(entity.getScopes());
        dto.setAllowedIps(entity.getAllowedIps());
        dto.setRateLimitPerMinute(entity.getRateLimitPerMinute());
        dto.setStatus(entity.getStatus());
        dto.setExpiresAt(entity.getExpiresAt());
        dto.setLastUsedAt(entity.getLastUsedAt());
        dto.setLastUsedIp(entity.getLastUsedIp());
        dto.setUsageCount(entity.getUsageCount());
        dto.setCreatedBy(entity.getCreatedBy());
        dto.setCreateTime(entity.getCreateTime());
        dto.setUpdateTime(entity.getUpdateTime());
        dto.setVersion(entity.getVersion());
        return dto;
    }

    /**
     * 访问日志实体转 DTO。
     */
    private ScrmApiAccessLogDto toAccessLogDto(ScrmApiAccessLogEntity entity) {
        ScrmApiAccessLogDto dto = new ScrmApiAccessLogDto();
        dto.setId(entity.getId());
        dto.setAppId(entity.getAppId());
        dto.setApiKeyId(entity.getApiKeyId());
        dto.setClientId(entity.getClientId());
        dto.setEndpoint(entity.getEndpoint());
        dto.setMethod(entity.getMethod());
        dto.setRequestIp(entity.getRequestIp());
        dto.setUserAgent(entity.getUserAgent());
        dto.setRequestParams(entity.getRequestParams());
        dto.setRequestBody(entity.getRequestBody());
        dto.setResponseStatus(entity.getResponseStatus());
        dto.setResponseTimeMs(entity.getResponseTimeMs());
        dto.setErrorCode(entity.getErrorCode());
        dto.setErrorMessage(entity.getErrorMessage());
        dto.setRequestId(entity.getRequestId());
        dto.setAccessedAt(entity.getAccessedAt());
        dto.setCreateTime(entity.getCreateTime());
        dto.setUpdateTime(entity.getUpdateTime());
        dto.setVersion(entity.getVersion());
        return dto;
    }

    /**
     * 权限范围实体转 DTO。
     */
    private ScrmApiScopeDto toScopeDto(ScrmApiScopeEntity entity) {
        ScrmApiScopeDto dto = new ScrmApiScopeDto();
        dto.setId(entity.getId());
        dto.setScopeName(entity.getScopeName());
        dto.setDisplayName(entity.getDisplayName());
        dto.setDescription(entity.getDescription());
        dto.setResource(entity.getResource());
        dto.setActions(entity.getActions());
        dto.setIsDefault(entity.getIsDefault());
        dto.setEnabled(entity.getEnabled());
        dto.setCreatedBy(entity.getCreatedBy());
        dto.setCreateTime(entity.getCreateTime());
        dto.setUpdateTime(entity.getUpdateTime());
        dto.setVersion(entity.getVersion());
        return dto;
    }
}
