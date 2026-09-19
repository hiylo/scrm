/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCampaignTemplateService.java
 * Date : 2026/07/27 02:41:22
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.dto.ScrmCampaignTemplateDto;
import org.hiylo.scrm.entity.ScrmCampaignTemplateEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.repository.ScrmCampaignTemplateRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * SCRM SOP 模板服务。
 * <p>
 * 写入归属账号实现数据隔离。所有写操作抛出 {@link ScrmException} 携带语义化错误码。
 * </p>
 *
 * @author Hsi Chu
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmCampaignTemplateService {

    /** SOP 模板数据访问层 */
    private final ScrmCampaignTemplateRepository templateRepository;

    /**
     * 根据主键查询 SOP 模板。
     *
     * @param id 模板 ID
     * @return 模板 DTO
     * @throws ScrmException 模板不存在
     */
    @Transactional(readOnly = true)
    public ScrmCampaignTemplateDto findById(Long id) throws ScrmException {
        return toDto(findOrThrow(id));
    }

    /**
     * 根据任务类型查询 SOP 模板列表。
     *
     * @param campaignType 任务类型：AUTO_ADD_FRIEND / AUTO_POST / AUTO_CHAT / AUTO_NURTURE / AUTO_REPLY
     * @return 模板列表
     */
    @Transactional(readOnly = true)
    public List<ScrmCampaignTemplateDto> findByCampaignType(String campaignType) {
        return templateRepository.findByCampaignType(campaignType).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    /**
     * 根据平台类型查询 SOP 模板列表。
     *
     * @param platformType 平台类型
     * @return 模板列表
     */
    @Transactional(readOnly = true)
    public List<ScrmCampaignTemplateDto> findByPlatformType(String platformType) {
        return templateRepository.findByPlatformType(platformType).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    /**
     * 创建 SOP 模板。
     * <p>
     * 写入归属账号 ID 后持久化，返回创建后的模板。
     * </p>
     *
     * @param dto 模板参数
     * @return 创建后的模板
     */
    @Transactional
    public ScrmCampaignTemplateDto create(ScrmCampaignTemplateDto dto) {
        ScrmCampaignTemplateEntity entity = new ScrmCampaignTemplateEntity();
        entity.setTemplateName(dto.getTemplateName());
        entity.setCampaignType(dto.getCampaignType());
        entity.setPlatformType(dto.getPlatformType());
        entity.setTemplateContent(dto.getTemplateContent());
        entity.setDescription(dto.getDescription());
        entity = templateRepository.save(entity);
        log.info("创建 SOP 模板: id={}, templateName={}, campaignType={}",
                entity.getId(), entity.getTemplateName(), entity.getCampaignType());
        return toDto(entity);
    }

    /**
     * 更新 SOP 模板（字段非空才覆盖）。
     *
     * @param id  模板 ID
     * @param dto 模板参数
     * @return 更新后的模板
     * @throws ScrmException 模板不存在
     */
    @Transactional
    public ScrmCampaignTemplateDto update(Long id, ScrmCampaignTemplateDto dto) throws ScrmException {
        ScrmCampaignTemplateEntity entity = findOrThrow(id);
        if (dto.getTemplateName() != null) entity.setTemplateName(dto.getTemplateName());
        if (dto.getCampaignType() != null) entity.setCampaignType(dto.getCampaignType());
        if (dto.getPlatformType() != null) entity.setPlatformType(dto.getPlatformType());
        if (dto.getTemplateContent() != null) entity.setTemplateContent(dto.getTemplateContent());
        if (dto.getDescription() != null) entity.setDescription(dto.getDescription());
        entity = templateRepository.save(entity);
        log.info("更新 SOP 模板: id={}", id);
        return toDto(entity);
    }

    /**
     * 删除 SOP 模板。
     *
     * @param id 模板 ID
     * @throws ScrmException 模板不存在
     */
    @Transactional
    public void delete(Long id) throws ScrmException {
        findOrThrow(id);
        templateRepository.deleteById(id);
        log.info("删除 SOP 模板: id={}", id);
    }

    /**
     * 分页查询 SOP 模板，支持按任务类型与平台类型过滤。
     * <p>
     * 使用 JPA Specification 在数据库层完成过滤, 避免全表加载。
     * </p>
     *
     * @param campaignType 任务类型过滤（可空）
     * @param platformType 平台类型过滤（可空）
     * @param page         页码（从 0 开始）
     * @param size         每页大小
     * @return 模板分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmCampaignTemplateDto> list(String campaignType, String platformType, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createTime"));
        Specification<ScrmCampaignTemplateEntity> spec = buildTemplateSpec(campaignType, platformType);
        Page<ScrmCampaignTemplateEntity> entities = templateRepository.findAll(spec, pageable);
        return entities.map(this::toDto);
    }

    /**
     * 构建 SOP 模板查询条件 Specification
     */
    private Specification<ScrmCampaignTemplateEntity> buildTemplateSpec(String campaignType, String platformType) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            // 数据隔离: 始终按当前用户可见账号范围过滤
            if (campaignType != null && !campaignType.isBlank()) {
                predicates.add(cb.equal(cb.lower(root.get("campaignType")), campaignType.toLowerCase()));
            }
            if (platformType != null && !platformType.isBlank()) {
                predicates.add(cb.equal(cb.lower(root.get("platformType")), platformType.toLowerCase()));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    // ============================================================
    // 内部工具方法
    // ============================================================

    /**
     * 按主键查询模板，不存在抛异常
     */
    private ScrmCampaignTemplateEntity findOrThrow(Long id) throws ScrmException {
        ScrmCampaignTemplateEntity entity = templateRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.SCRM_CAMPAIGN_TEMPLATE_NOT_FOUND,
                        "SOP 模板不存在: id=" + id));
        // 数据隔离: 校验模板归属当前账号

        return entity;
    }

    /**
     * 实体转 DTO
     */
    private ScrmCampaignTemplateDto toDto(ScrmCampaignTemplateEntity entity) {
        ScrmCampaignTemplateDto dto = new ScrmCampaignTemplateDto();
        dto.setId(entity.getId());
        dto.setTemplateName(entity.getTemplateName());
        dto.setCampaignType(entity.getCampaignType());
        dto.setPlatformType(entity.getPlatformType());
        dto.setTemplateContent(entity.getTemplateContent());
        dto.setDescription(entity.getDescription());
        dto.setCreateTime(entity.getCreateTime());
        dto.setUpdateTime(entity.getUpdateTime());
        dto.setVersion(entity.getVersion());
        return dto;
    }
}
