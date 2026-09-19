/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmIdentityMergeIdentityService.java
 * Date : 2026/09/19 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.dto.ScrmCustomerIdentityDto;
import org.hiylo.scrm.entity.ScrmCustomerEntity;
import org.hiylo.scrm.entity.ScrmCustomerIdentityEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.repository.ScrmCustomerIdentityRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * SCRM 客户身份合并服务 - 身份标识管理子域。
 * <p>
 * 承载跨平台客户身份标识的增删改查 / 主身份管理 / 验证 / 按身份查找客户能力。
 * 操作人从 {@link org.hiylo.scrm.config.UserContext} 获取。
 * </p>
 *
 * @author Hsi Chu
 * @since 2026-09-19
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmIdentityMergeIdentityService {

    /** 合法的身份类型 */
    private static final List<String> VALID_IDENTITY_TYPES = List.of(
            "PHONE", "EMAIL", "WECHAT_OPENID", "WECHAT_UNIONID", "WORK_WECHAT_EXTERNAL_ID",
            "DOUYIN_OPENID", "KUAISHOU_OPENID", "XIAOHONGSHU_OPENID", "WEIBO_UID",
            "ALIPAY_USERID", "QQ_OPENID", "ID_CARD", "PASSPORT", "USERNAME", "DEVICE_ID", "CUSTOM");

    /** 合法的平台 */
    private static final List<String> VALID_PLATFORMS = List.of(
            "WECHAT", "WORK_WECHAT", "DOUYIN", "KUAISHOU", "XIAOHONGSHU", "WEIBO", "ALIPAY", "QQ", "WEB", "APP");

    /** 合法的来源 */
    private static final List<String> VALID_SOURCES = List.of(
            "REGISTRATION", "IMPORT", "MERGE", "OAUTH", "MANUAL", "SYSTEM");

    /** 客户身份数据访问层 */
    private final ScrmCustomerIdentityRepository identityRepository;

    /** 合并任务与执行子域服务 (共享客户/身份查询) */
    private final ScrmIdentityMergeTaskService taskService;

    // ============================================================
    // 身份标识管理
    // ============================================================

    /**
     * 添加客户身份标识。
     * <p>校验身份类型 / 平台 / 来源合法性, 同客户同类型身份唯一性后写入账号 ID 持久化。
     * 若标记为主身份, 自动取消该客户其他主身份。</p>
     *
     * @param dto 身份参数
     * @return 创建后的身份
     * @throws ScrmException 参数非法 / 身份已存在
     */
    @Transactional
    public ScrmCustomerIdentityEntity addIdentity(ScrmCustomerIdentityDto dto) throws ScrmException {
        validateIdentityDto(dto, false);
        // 校验客户存在
        taskService.findCustomerOrThrow(dto.getCustomerId());
        // 校验同客户同类型的身份唯一性
        if (identityRepository.findByCustomerIdAndIdentityType(
                 dto.getCustomerId(), dto.getIdentityType()).isPresent()) {
            throw ScrmException.conflict("客户已存在该类型身份: customerId=" + dto.getCustomerId()
                    + ", type=" + dto.getIdentityType());
        }
        // 校验身份值全局唯一 (同账号同类型同值视为同一身份)
        if (identityRepository.findByIdentityTypeAndIdentityValue(
                 dto.getIdentityType(), dto.getIdentityValue()).isPresent()) {
            throw ScrmException.conflict("身份值已被其他客户占用: type=" + dto.getIdentityType()
                    + ", value=" + dto.getIdentityValue());
        }
        ScrmCustomerIdentityEntity entity = new ScrmCustomerIdentityEntity();
        entity.setCustomerId(dto.getCustomerId());
        entity.setCustomerName(dto.getCustomerName());
        entity.setIdentityType(dto.getIdentityType());
        entity.setIdentityValue(dto.getIdentityValue());
        entity.setPlatform(dto.getPlatform());
        entity.setIsPrimary(dto.getIsPrimary() != null ? dto.getIsPrimary() : Boolean.FALSE);
        entity.setIsVerified(dto.getIsVerified() != null ? dto.getIsVerified() : Boolean.FALSE);
        entity.setVerifiedAt(Boolean.TRUE.equals(entity.getIsVerified()) ? LocalDateTime.now() : null);
        entity.setSource(dto.getSource() != null ? dto.getSource() : ScrmIdentityMergeTaskService.SOURCE_MANUAL);
        entity.setMetadata(dto.getMetadata());
        entity.setIsActive(Boolean.TRUE);
        // 若为主身份, 取消该客户其他主身份
        if (Boolean.TRUE.equals(entity.getIsPrimary())) {
            clearOtherPrimaryIdentities(dto.getCustomerId(), null);
        }
        entity = identityRepository.save(entity);
        log.info("添加客户身份: id={}, customerId={}, type={}",
                entity.getId(), entity.getCustomerId(), entity.getIdentityType());
        return entity;
    }

    /**
     * 移除客户身份标识 (软删除, 置为非活跃)。
     *
     * @param id 身份 ID
     * @throws ScrmException 身份不存在
     */
    @Transactional
    public void removeIdentity(Long id) throws ScrmException {
        ScrmCustomerIdentityEntity entity = findIdentityOrThrow(id);
        entity.setIsActive(Boolean.FALSE);
        entity.setIsPrimary(Boolean.FALSE);
        identityRepository.save(entity);
        log.info("移除客户身份: id={}, customerId={}", id, entity.getCustomerId());
    }

    /**
     * 查询身份详情。
     *
     * @param id 身份 ID
     * @return 身份实体
     * @throws ScrmException 身份不存在
     */
    @Transactional(readOnly = true)
    public ScrmCustomerIdentityEntity getIdentity(Long id) throws ScrmException {
        return findIdentityOrThrow(id);
    }

    /**
     * 分页查询客户身份, 支持按客户 / 身份类型 / 平台过滤。
     *
     * @param customerId   客户 ID 过滤 (可空)
     * @param identityType 身份类型过滤 (可空)
     * @param platform     平台过滤 (可空)
     * @param pageable     分页参数
     * @return 身份分页结果 (按 isPrimary DESC, createTime DESC)
     */
    @Transactional(readOnly = true)
    public Page<ScrmCustomerIdentityEntity> listIdentities(Long customerId, String identityType,
                                                            String platform, Pageable pageable) {
        Specification<ScrmCustomerIdentityEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (customerId != null) {
                predicates.add(cb.equal(root.get("customerId"), customerId));
            }
            if (identityType != null && !identityType.isBlank()) {
                predicates.add(cb.equal(root.get("identityType"), identityType));
            }
            if (platform != null && !platform.isBlank()) {
                predicates.add(cb.equal(root.get("platform"), platform));
            }
            query.orderBy(cb.desc(root.get("isPrimary")), cb.desc(root.get("createTime")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return identityRepository.findAll(spec, pageable);
    }

    /**
     * 获取客户主身份。
     *
     * @param customerId 客户 ID
     * @return 主身份实体
     * @throws ScrmException 主身份不存在
     */
    @Transactional(readOnly = true)
    public ScrmCustomerIdentityEntity getPrimaryIdentity(Long customerId) throws ScrmException {
        if (customerId == null) {
            throw ScrmException.badRequest("客户 ID 不能为空");
        }
        return identityRepository.findByCustomerIdAndIsPrimary(customerId, Boolean.TRUE)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "客户主身份不存在: customerId=" + customerId));
    }

    /**
     * 设置客户主身份 (取消原主身份, 设置新主身份)。
     *
     * @param customerId    客户 ID
     * @param identityType  身份类型
     * @param identityValue 身份值
     * @return 更新后的主身份
     * @throws ScrmException 身份不存在
     */
    @Transactional
    public ScrmCustomerIdentityEntity setPrimaryIdentity(Long customerId, String identityType,
                                                          String identityValue) throws ScrmException {
        if (customerId == null) {
            throw ScrmException.badRequest("客户 ID 不能为空");
        }
        if (identityType == null || identityType.isBlank()) {
            throw ScrmException.badRequest("身份类型不能为空");
        }
        if (identityValue == null || identityValue.isBlank()) {
            throw ScrmException.badRequest("身份值不能为空");
        }
        ScrmCustomerIdentityEntity entity = identityRepository
                .findByIdentityTypeAndIdentityValue(identityType, identityValue)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "身份不存在: type=" + identityType + ", value=" + identityValue));
        if (!Objects.equals(entity.getCustomerId(), customerId)) {
            throw ScrmException.badRequest("身份不属于指定客户: customerId=" + customerId);
        }
        if (Boolean.FALSE.equals(entity.getIsActive())) {
            throw ScrmException.badRequest("身份已停用, 无法设为主身份");
        }
        // 取消该客户其他主身份
        clearOtherPrimaryIdentities(customerId, entity.getId());
        entity.setIsPrimary(Boolean.TRUE);
        entity = identityRepository.save(entity);
        log.info("设置主身份: id={}, customerId={}, type={}", entity.getId(), customerId, identityType);
        return entity;
    }

    /**
     * 验证身份 (标记已验证, 记录验证时间)。
     *
     * @param id 身份 ID
     * @return 更新后的身份
     * @throws ScrmException 身份不存在
     */
    @Transactional
    public ScrmCustomerIdentityEntity verifyIdentity(Long id) throws ScrmException {
        ScrmCustomerIdentityEntity entity = findIdentityOrThrow(id);
        entity.setIsVerified(Boolean.TRUE);
        entity.setVerifiedAt(LocalDateTime.now());
        entity = identityRepository.save(entity);
        log.info("验证身份: id={}, customerId={}", id, entity.getCustomerId());
        return entity;
    }

    /**
     * 按身份查找客户 (按身份类型 + 身份值查找归属客户)。
     *
     * @param identityType  身份类型
     * @param identityValue 身份值
     * @return 客户实体
     * @throws ScrmException 身份不存在 / 客户不存在
     */
    @Transactional(readOnly = true)
    public ScrmCustomerEntity findCustomerByIdentity(String identityType, String identityValue)
            throws ScrmException {
        if (identityType == null || identityType.isBlank()) {
            throw ScrmException.badRequest("身份类型不能为空");
        }
        if (identityValue == null || identityValue.isBlank()) {
            throw ScrmException.badRequest("身份值不能为空");
        }
        ScrmCustomerIdentityEntity identity = identityRepository
                .findByIdentityTypeAndIdentityValue(identityType, identityValue)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "身份不存在: type=" + identityType + ", value=" + identityValue));
        return taskService.findCustomerOrThrow(identity.getCustomerId());
    }

    /**
     * 分页查询全部身份 (跨客户), 支持按身份类型 / 平台 / 关键词过滤。
     *
     * @param identityType 身份类型过滤 (可空)
     * @param platform     平台过滤 (可空)
     * @param keyword      关键词过滤 (可空, 前缀匹配身份值)
     * @param pageable     分页参数
     * @return 身份分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmCustomerIdentityEntity> listAllIdentities(String identityType, String platform,
                                                               String keyword, Pageable pageable) {
        Specification<ScrmCustomerIdentityEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (identityType != null && !identityType.isBlank()) {
                predicates.add(cb.equal(root.get("identityType"), identityType));
            }
            if (platform != null && !platform.isBlank()) {
                predicates.add(cb.equal(root.get("platform"), platform));
            }
            if (keyword != null && !keyword.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("identityValue")),
                        keyword.toLowerCase() + "%"));
            }
            query.orderBy(cb.desc(root.get("createTime")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return identityRepository.findAll(spec, pageable);
    }

    // ============================================================
    // 内部工具方法
    // ============================================================

    /**
     * 校验身份参数。
     *
     * @param dto     身份参数
     * @param partial 是否为部分更新场景
     * @throws ScrmException 参数非法
     */
    private void validateIdentityDto(ScrmCustomerIdentityDto dto, boolean partial) throws ScrmException {
        if (dto == null) {
            throw ScrmException.badRequest("身份参数不能为空");
        }
        if (dto.getCustomerId() == null) {
            throw ScrmException.badRequest("客户 ID 不能为空");
        }
        if (dto.getIdentityType() != null) {
            if (!VALID_IDENTITY_TYPES.contains(dto.getIdentityType())) {
                throw ScrmException.badRequest(
                        "身份类型非法: " + dto.getIdentityType() + ", 仅支持 " + VALID_IDENTITY_TYPES);
            }
        } else if (!partial) {
            throw ScrmException.badRequest("身份类型不能为空");
        }
        if (dto.getIdentityValue() == null || dto.getIdentityValue().isBlank()) {
            if (!partial) {
                throw ScrmException.badRequest("身份值不能为空");
            }
        }
        if (dto.getPlatform() != null && !dto.getPlatform().isBlank() && !VALID_PLATFORMS.contains(dto.getPlatform())) {
            throw ScrmException.badRequest(
                    "平台非法: " + dto.getPlatform() + ", 仅支持 " + VALID_PLATFORMS);
        }
        if (dto.getSource() != null && !dto.getSource().isBlank() && !VALID_SOURCES.contains(dto.getSource())) {
            throw ScrmException.badRequest(
                    "来源非法: " + dto.getSource() + ", 仅支持 " + VALID_SOURCES);
        }
    }

    /**
     * 取消客户其他主身份 (设置新主身份时调用)。
     *
     * @param customerId 客户 ID
     * @param excludeId  排除的身份 ID (可空)
     */
    private void clearOtherPrimaryIdentities(Long customerId, Long excludeId) {
        List<ScrmCustomerIdentityEntity> identities = identityRepository
                .findByCustomerIdOrderByIsPrimaryDescCreateTimeDesc(customerId);
        List<ScrmCustomerIdentityEntity> changedIdentities = new ArrayList<>();
        for (ScrmCustomerIdentityEntity identity : identities) {
            if (Boolean.TRUE.equals(identity.getIsPrimary())
                    && (excludeId == null || !excludeId.equals(identity.getId()))) {
                identity.setIsPrimary(Boolean.FALSE);
                changedIdentities.add(identity);
            }
        }
        if (!changedIdentities.isEmpty()) {
            identityRepository.saveAll(changedIdentities);
        }
    }

    /**
     * 主键查询身份, 不存在抛异常。
     *
     * @param id 身份 ID
     * @return 身份实体
     * @throws ScrmException 身份不存在
     */
    private ScrmCustomerIdentityEntity findIdentityOrThrow(Long id) throws ScrmException {
        ScrmCustomerIdentityEntity entity = identityRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "客户身份不存在: id=" + id));
        return entity;
    }
}