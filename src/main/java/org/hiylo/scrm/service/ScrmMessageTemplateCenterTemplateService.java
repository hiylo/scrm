/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmMessageTemplateCenterTemplateService.java
 * Date : 2026/09/19 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.dto.ScrmMessageTemplateCenterDto;
import org.hiylo.scrm.dto.ScrmTemplateQueryDto;
import org.hiylo.scrm.entity.ScrmMessageTemplateCenterEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.repository.ScrmMessageTemplateCenterRepository;
import org.hiylo.scrm.repository.ScrmMessageTemplateGroupRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * SCRM 消息模板中心模板管理子域服务。
 * <p>
 * 承载消息模板的创建 / 更新 / 删除 / 查询 / 发布 / 归档 / 复制 / 标准模板标记 / 使用统计,
 * 提供 {@code findTemplateOrThrow} 供渲染 / 版本 / 审批 / 统计兄弟类以 package 级访问复用。
 * 分组统计刷新委托给 {@link ScrmMessageTemplateCenterGroupService}。
 * </p>
 *
 * @author Hsi Chu
 * @since 2026-09-19
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmMessageTemplateCenterTemplateService {

    /** 模板定义数据访问层 */
    private final ScrmMessageTemplateCenterRepository centerRepository;

    /** 模板分组数据访问层 (同步分组名称) */
    private final ScrmMessageTemplateGroupRepository groupRepository;

    /** 分组管理子域服务 (刷新分组统计) */
    private final ScrmMessageTemplateCenterGroupService groupService;

    /**
     * 创建模板。
     *
     * @param dto 模板参数
     * @return 创建后的模板
     * @throws ScrmException 模板编码已存在 / 参数非法
     */
    @Transactional
    public ScrmMessageTemplateCenterEntity createTemplate(ScrmMessageTemplateCenterDto dto) throws ScrmException {
        if (dto == null) {
            throw ScrmException.badRequest("模板参数不能为空");
        }
        if (centerRepository.findByTemplateCode(dto.getTemplateCode()).isPresent()) {
            throw ScrmException.conflict("模板编码已存在: templateCode=" + dto.getTemplateCode());
        }
        ScrmMessageTemplateCenterEntity entity = new ScrmMessageTemplateCenterEntity();
        entity.setTemplateName(dto.getTemplateName());
        entity.setTemplateCode(dto.getTemplateCode());
        entity.setGroupId(dto.getGroupId());
        entity.setGroupName(dto.getGroupName());
        entity.setDescription(dto.getDescription());
        entity.setTemplateType(dto.getTemplateType());
        entity.setChannels(dto.getChannels());
        entity.setSubject(dto.getSubject());
        entity.setContent(dto.getContent());
        entity.setPlainContent(dto.getPlainContent());
        entity.setHtmlContent(dto.getHtmlContent());
        entity.setWechatLink(dto.getWechatLink());
        entity.setMiniProgramPath(dto.getMiniProgramPath());
        entity.setVariables(dto.getVariables());
        entity.setAttachments(dto.getAttachments());
        entity.setCategory(dto.getCategory());
        entity.setTags(dto.getTags());
        entity.setApplicableScenarios(dto.getApplicableScenarios());
        entity.setLanguage(dto.getLanguage() != null ? dto.getLanguage() : "zh_CN");
        entity.setStatus(dto.getStatus() != null ? dto.getStatus() : "DRAFT");
        entity.setVersionNumber(1);
        entity.setReviewStatus("PENDING");
        entity.setUsageCount(0);
        entity.setSuccessRate(0.0);
        entity.setAvgResponseRate(0.0);
        entity.setIsStandard(false);
        entity.setCreatedBy(dto.getCreatedBy());
        entity = centerRepository.save(entity);
        // 若指定了分组, 同步分组名称并刷新分组统计
        if (dto.getGroupId() != null) {
            syncGroupName(entity);
            groupService.updateGroupStats(dto.getGroupId());
        }
        log.info("创建消息模板: id={}, templateCode={}", entity.getId(), entity.getTemplateCode());
        return entity;
    }

    /**
     * 更新模板（字段非空才覆盖）。
     *
     * @param id  模板 ID
     * @param dto 模板参数
     * @return 更新后的模板
     * @throws ScrmException 模板不存在 / 模板编码冲突
     */
    @Transactional
    public ScrmMessageTemplateCenterEntity updateTemplate(
            Long id, ScrmMessageTemplateCenterDto dto) throws ScrmException {
        ScrmMessageTemplateCenterEntity entity = findTemplateOrThrow(id);
        if (dto == null) {
            throw ScrmException.badRequest("模板参数不能为空");
        }
        if (dto.getTemplateCode() != null && !dto.getTemplateCode().equals(entity.getTemplateCode())) {
            centerRepository.findByTemplateCode(dto.getTemplateCode())
                    .ifPresent(other -> {
                        if (!other.getId().equals(id)) {
                            throw ScrmException.conflict("模板编码已被其他模板占用: templateCode=" + dto.getTemplateCode());
                        }
                    });
            entity.setTemplateCode(dto.getTemplateCode());
        }
        Long oldGroupId = entity.getGroupId();
        if (dto.getGroupId() != null) entity.setGroupId(dto.getGroupId());
        if (dto.getGroupName() != null) entity.setGroupName(dto.getGroupName());
        if (dto.getTemplateName() != null) entity.setTemplateName(dto.getTemplateName());
        if (dto.getDescription() != null) entity.setDescription(dto.getDescription());
        if (dto.getTemplateType() != null) entity.setTemplateType(dto.getTemplateType());
        if (dto.getChannels() != null) entity.setChannels(dto.getChannels());
        if (dto.getSubject() != null) entity.setSubject(dto.getSubject());
        if (dto.getContent() != null) entity.setContent(dto.getContent());
        if (dto.getPlainContent() != null) entity.setPlainContent(dto.getPlainContent());
        if (dto.getHtmlContent() != null) entity.setHtmlContent(dto.getHtmlContent());
        if (dto.getWechatLink() != null) entity.setWechatLink(dto.getWechatLink());
        if (dto.getMiniProgramPath() != null) entity.setMiniProgramPath(dto.getMiniProgramPath());
        if (dto.getVariables() != null) entity.setVariables(dto.getVariables());
        if (dto.getAttachments() != null) entity.setAttachments(dto.getAttachments());
        if (dto.getCategory() != null) entity.setCategory(dto.getCategory());
        if (dto.getTags() != null) entity.setTags(dto.getTags());
        if (dto.getApplicableScenarios() != null) entity.setApplicableScenarios(dto.getApplicableScenarios());
        if (dto.getLanguage() != null) entity.setLanguage(dto.getLanguage());
        if (dto.getStatus() != null) entity.setStatus(dto.getStatus());
        if (dto.getCreatedBy() != null) entity.setCreatedBy(dto.getCreatedBy());
        // 分组变更时同步分组名称并刷新新旧分组统计
        if (dto.getGroupId() != null && !dto.getGroupId().equals(oldGroupId)) {
            syncGroupName(entity);
            if (oldGroupId != null) {
                groupService.updateGroupStats(oldGroupId);
            }
            groupService.updateGroupStats(dto.getGroupId());
        }
        entity = centerRepository.save(entity);
        log.info("更新消息模板: id={}, templateCode={}", entity.getId(), entity.getTemplateCode());
        return entity;
    }

    /**
     * 删除模板。
     *
     * @param id 模板 ID
     * @throws ScrmException 模板不存在
     */
    @Transactional
    public void deleteTemplate(Long id) throws ScrmException {
        ScrmMessageTemplateCenterEntity entity = findTemplateOrThrow(id);
        centerRepository.deleteById(id);
        if (entity.getGroupId() != null) {
            groupService.updateGroupStats(entity.getGroupId());
        }
        log.info("删除消息模板: id={}", id);
    }

    /**
     * 查询模板详情。
     *
     * @param id 模板 ID
     * @return 模板实体
     * @throws ScrmException 模板不存在
     */
    @Transactional(readOnly = true)
    public ScrmMessageTemplateCenterEntity getTemplate(Long id) throws ScrmException {
        return findTemplateOrThrow(id);
    }

    /**
     * 按模板编码查询模板。
     *
     * @param code 模板编码
     * @return 模板实体
     * @throws ScrmException 模板不存在
     */
    @Transactional(readOnly = true)
    public ScrmMessageTemplateCenterEntity getTemplateByCode(String code) throws ScrmException {
        return centerRepository.findByTemplateCode(code)
                .orElseThrow(() -> ScrmException.notFound("消息模板不存在: templateCode=" + code));
    }

    /**
     * 分页查询模板列表, 支持按分组、类型、渠道、状态、分类、关键字组合过滤。
     *
     * @param queryDto 查询条件
     * @param pageable 分页参数
     * @return 模板分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmMessageTemplateCenterEntity> listTemplates(ScrmTemplateQueryDto queryDto, Pageable pageable) {
        Specification<ScrmMessageTemplateCenterEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (queryDto != null) {
                if (queryDto.getGroupId() != null) {
                    predicates.add(cb.equal(root.get("groupId"), queryDto.getGroupId()));
                }
                if (queryDto.getTemplateType() != null && !queryDto.getTemplateType().isBlank()) {
                    predicates.add(cb.equal(root.get("templateType"), queryDto.getTemplateType()));
                }
                if (queryDto.getChannels() != null && !queryDto.getChannels().isBlank()) {
                    predicates.add(cb.like(root.get("channels"), "%" + queryDto.getChannels() + "%"));
                }
                if (queryDto.getStatus() != null && !queryDto.getStatus().isBlank()) {
                    predicates.add(cb.equal(root.get("status"), queryDto.getStatus()));
                }
                if (queryDto.getCategory() != null && !queryDto.getCategory().isBlank()) {
                    predicates.add(cb.equal(root.get("category"), queryDto.getCategory()));
                }
                if (queryDto.getKeyword() != null && !queryDto.getKeyword().isBlank()) {
                    String kw = "%" + queryDto.getKeyword().toLowerCase() + "%";
                    predicates.add(cb.or(
                            cb.like(cb.lower(root.get("templateName")), kw),
                            cb.like(cb.lower(root.get("templateCode")), kw)
                    ));
                }
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return centerRepository.findAll(spec, pageable);
    }

    /**
     * 发布模板。
     *
     * @param id 模板 ID
     * @return 更新后的模板
     * @throws ScrmException 模板不存在
     */
    @Transactional
    public ScrmMessageTemplateCenterEntity publishTemplate(Long id) throws ScrmException {
        ScrmMessageTemplateCenterEntity entity = findTemplateOrThrow(id);
        entity.setStatus("PUBLISHED");
        entity = centerRepository.save(entity);
        log.info("发布消息模板: id={}", id);
        return entity;
    }

    /**
     * 归档模板。
     *
     * @param id 模板 ID
     * @return 更新后的模板
     * @throws ScrmException 模板不存在
     */
    @Transactional
    public ScrmMessageTemplateCenterEntity archiveTemplate(Long id) throws ScrmException {
        ScrmMessageTemplateCenterEntity entity = findTemplateOrThrow(id);
        entity.setStatus("ARCHIVED");
        entity = centerRepository.save(entity);
        log.info("归档消息模板: id={}", id);
        return entity;
    }

    /**
     * 复制模板 (生成新编码的草稿副本)。
     *
     * @param id      源模板 ID
     * @param newCode 新模板编码
     * @return 复制后的模板
     * @throws ScrmException 源模板不存在 / 新编码已存在
     */
    @Transactional
    public ScrmMessageTemplateCenterEntity duplicateTemplate(Long id, String newCode) throws ScrmException {
        ScrmMessageTemplateCenterEntity source = findTemplateOrThrow(id);
        if (newCode == null || newCode.isBlank()) {
            throw ScrmException.badRequest("新模板编码不能为空");
        }
        if (centerRepository.findByTemplateCode(newCode).isPresent()) {
            throw ScrmException.conflict("模板编码已存在: templateCode=" + newCode);
        }
        ScrmMessageTemplateCenterEntity copy = new ScrmMessageTemplateCenterEntity();
        copy.setTemplateName(source.getTemplateName() + " (副本)");
        copy.setTemplateCode(newCode);
        copy.setGroupId(source.getGroupId());
        copy.setGroupName(source.getGroupName());
        copy.setDescription(source.getDescription());
        copy.setTemplateType(source.getTemplateType());
        copy.setChannels(source.getChannels());
        copy.setSubject(source.getSubject());
        copy.setContent(source.getContent());
        copy.setPlainContent(source.getPlainContent());
        copy.setHtmlContent(source.getHtmlContent());
        copy.setWechatLink(source.getWechatLink());
        copy.setMiniProgramPath(source.getMiniProgramPath());
        copy.setVariables(source.getVariables());
        copy.setAttachments(source.getAttachments());
        copy.setCategory(source.getCategory());
        copy.setTags(source.getTags());
        copy.setApplicableScenarios(source.getApplicableScenarios());
        copy.setLanguage(source.getLanguage());
        copy.setStatus("DRAFT");
        copy.setVersionNumber(1);
        copy.setReviewStatus("PENDING");
        copy.setUsageCount(0);
        copy.setSuccessRate(0.0);
        copy.setAvgResponseRate(0.0);
        copy.setIsStandard(false);
        copy.setCreatedBy(source.getCreatedBy());
        copy = centerRepository.save(copy);
        if (copy.getGroupId() != null) {
            groupService.updateGroupStats(copy.getGroupId());
        }
        log.info("复制消息模板: sourceId={}, newId={}, newCode={}", id, copy.getId(), newCode);
        return copy;
    }

    /**
     * 将模板设为标准模板。
     *
     * @param id 模板 ID
     * @return 更新后的模板
     * @throws ScrmException 模板不存在
     */
    @Transactional
    public ScrmMessageTemplateCenterEntity setStandard(Long id) throws ScrmException {
        ScrmMessageTemplateCenterEntity entity = findTemplateOrThrow(id);
        entity.setIsStandard(true);
        entity = centerRepository.save(entity);
        log.info("设为标准模板: id={}", id);
        return entity;
    }

    /**
     * 按分组分页查询模板。
     *
     * @param groupId  分组 ID
     * @param pageable 分页参数
     * @return 模板分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmMessageTemplateCenterEntity> getTemplatesByGroup(Long groupId, Pageable pageable) {
        return centerRepository.findByGroupId(groupId, pageable);
    }

    /**
     * 按渠道分页查询模板 (channels 字段包含该渠道)。
     *
     * @param channel  渠道
     * @param pageable 分页参数
     * @return 模板分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmMessageTemplateCenterEntity> getTemplatesByChannel(String channel, Pageable pageable) {
        return centerRepository.findByChannelsContaining(channel, pageable);
    }

    /**
     * 按场景分页查询模板 (applicableScenarios 字段包含该场景)。
     *
     * @param scenario 场景
     * @param pageable 分页参数
     * @return 模板分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmMessageTemplateCenterEntity> getTemplatesByScenario(String scenario, Pageable pageable) {
        return centerRepository.findByApplicableScenariosContaining(
                 scenario, pageable);
    }

    /**
     * 更新模板使用统计: 使用次数 +1, 刷新最后使用时间, 按成功标志刷新成功率 (滑动平均)。
     *
     * @param id      模板 ID
     * @param success 是否成功 (null 表示不更新成功率)
     * @throws ScrmException 模板不存在
     */
    @Transactional
    public void incrementUsage(Long id, Boolean success) throws ScrmException {
        ScrmMessageTemplateCenterEntity entity = findTemplateOrThrow(id);
        int newCount = (entity.getUsageCount() == null ? 0 : entity.getUsageCount()) + 1;
        entity.setUsageCount(newCount);
        entity.setLastUsedAt(LocalDateTime.now());
        if (success != null) {
            double oldRate = entity.getSuccessRate() == null ? 0.0 : entity.getSuccessRate();
            double newRate = (oldRate * (newCount - 1) + (success ? 1.0 : 0.0)) / newCount;
            entity.setSuccessRate(newRate);
        }
        centerRepository.save(entity);
    }

    /**
     * 按主键查询模板, 不存在抛异常。
     *
     * @param id 模板 ID
     * @return 模板实体
     * @throws ScrmException 模板不存在
     */
    ScrmMessageTemplateCenterEntity findTemplateOrThrow(Long id) throws ScrmException {
        return centerRepository.findById(id)
                .orElseThrow(() -> ScrmException.notFound("消息模板不存在: id=" + id));
    }

    /**
     * 同步模板的分组名称 (按 groupId 查询分组名回填 groupName)。
     *
     * @param entity 模板实体
     */
    private void syncGroupName(ScrmMessageTemplateCenterEntity entity) {
        if (entity.getGroupId() == null) {
            return;
        }
        groupRepository.findById(entity.getGroupId()).ifPresent(g -> entity.setGroupName(g.getGroupName()));
    }
}