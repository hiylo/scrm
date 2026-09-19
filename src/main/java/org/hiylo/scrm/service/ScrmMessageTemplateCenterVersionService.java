/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmMessageTemplateCenterVersionService.java
 * Date : 2026/09/19 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.entity.ScrmMessageTemplateCenterEntity;
import org.hiylo.scrm.entity.ScrmMessageTemplateVersionEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.repository.ScrmMessageTemplateCenterRepository;
import org.hiylo.scrm.repository.ScrmMessageTemplateVersionRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * SCRM 消息模板中心版本管理子域服务。
 * <p>
 * 承载模板版本的创建 / 查询 / 激活 / 回滚 / 对比, 提供 {@code findVersionOrThrow} 校验版本存在性。
 * 模板实体查询复用 {@link ScrmMessageTemplateCenterTemplateService} 的 {@code findTemplateOrThrow}。
 * </p>
 *
 * @author Hsi Chu
 * @since 2026-09-19
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmMessageTemplateCenterVersionService {

    /** 模板版本数据访问层 */
    private final ScrmMessageTemplateVersionRepository versionRepository;

    /** 模板定义数据访问层 */
    private final ScrmMessageTemplateCenterRepository centerRepository;

    /** 模板管理子域服务 (查询模板实体) */
    private final ScrmMessageTemplateCenterTemplateService templateService;

    /**
     * 创建新版本: 将当前模板内容固化为版本快照, 标记为 ACTIVE (最新保存状态), 归档其他版本,
     * 模板 versionNumber 与 currentVersionId 同步更新。
     *
     * @param templateId 模板 ID
     * @param changeLog  变更说明（可空）
     * @return 创建后的版本
     * @throws ScrmException 模板不存在
     */
    @Transactional
    public ScrmMessageTemplateVersionEntity createVersion(Long templateId, String changeLog) throws ScrmException {
        ScrmMessageTemplateCenterEntity template = templateService.findTemplateOrThrow(templateId);
        // 计算下一版本号: 现有最大版本号 + 1, 无版本记录时取模板当前 versionNumber
        int nextVersionNumber = template.getVersionNumber() == null ? 1 : template.getVersionNumber();
        ScrmMessageTemplateVersionEntity latest = versionRepository
                .findTopByTemplateIdOrderByVersionNumberDesc(templateId).orElse(null);
        if (latest != null && latest.getVersionNumber() != null && latest.getVersionNumber() >= nextVersionNumber) {
            nextVersionNumber = latest.getVersionNumber() + 1;
        }
        // 归档其他 ACTIVE 版本
        versionRepository.archiveOtherActiveVersions(templateId);
        ScrmMessageTemplateVersionEntity version = new ScrmMessageTemplateVersionEntity();
        version.setTemplateId(templateId);
        version.setVersionNumber(nextVersionNumber);
        version.setSubject(template.getSubject());
        version.setContent(template.getContent());
        version.setPlainContent(template.getPlainContent());
        version.setHtmlContent(template.getHtmlContent());
        version.setVariables(template.getVariables());
        version.setChangeLog(changeLog);
        version.setStatus("ACTIVE");
        version.setCreatedFromVersion(template.getVersionNumber());
        version.setCreatedBy(template.getCreatedBy());
        version = versionRepository.save(version);
        // 同步模板版本号与当前版本指针
        template.setVersionNumber(nextVersionNumber);
        template.setCurrentVersionId(version.getId());
        centerRepository.save(template);
        log.info("创建模板版本: templateId={}, versionNumber={}", templateId, nextVersionNumber);
        return version;
    }

    /**
     * 查询版本详情。
     *
     * @param id 版本 ID
     * @return 版本实体
     * @throws ScrmException 版本不存在
     */
    @Transactional(readOnly = true)
    public ScrmMessageTemplateVersionEntity getVersion(Long id) throws ScrmException {
        return findVersionOrThrow(id);
    }

    /**
     * 分页查询模板版本列表 (按版本号降序)。
     *
     * @param templateId 模板 ID
     * @param pageable   分页参数
     * @return 版本分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmMessageTemplateVersionEntity> listVersions(Long templateId, Pageable pageable) {
        return versionRepository.findByTemplateIdOrderByVersionNumberDesc(
                 templateId, pageable);
    }

    /**
     * 激活版本: 将指定版本内容回填到模板, 标记为 ACTIVE, 归档其他版本, 同步模板版本号与指针。
     *
     * @param versionId 版本 ID
     * @return 激活后的版本
     * @throws ScrmException 版本不存在
     */
    @Transactional
    public ScrmMessageTemplateVersionEntity activateVersion(Long versionId) throws ScrmException {
        ScrmMessageTemplateVersionEntity version = findVersionOrThrow(versionId);
        ScrmMessageTemplateCenterEntity template = templateService.findTemplateOrThrow(version.getTemplateId());
        // 归档其他 ACTIVE 版本
        versionRepository.archiveOtherActiveVersions(version.getTemplateId());
        version.setStatus("ACTIVE");
        version = versionRepository.save(version);
        // 模板内容回填
        template.setSubject(version.getSubject());
        template.setContent(version.getContent());
        template.setPlainContent(version.getPlainContent());
        template.setHtmlContent(version.getHtmlContent());
        template.setVariables(version.getVariables());
        template.setVersionNumber(version.getVersionNumber());
        template.setCurrentVersionId(version.getId());
        centerRepository.save(template);
        log.info("激活模板版本: templateId={}, versionId={}, versionNumber={}",
                version.getTemplateId(), versionId, version.getVersionNumber());
        return version;
    }

    /**
     * 回滚到指定版本: 与激活版本等价, 将模板内容恢复为该版本内容。
     *
     * @param templateId 模板 ID
     * @param versionId  目标版本 ID
     * @return 回滚后的版本
     * @throws ScrmException 模板/版本不存在 / 版本不属于该模板
     */
    @Transactional
    public ScrmMessageTemplateVersionEntity rollbackToVersion(
            Long templateId, Long versionId) throws ScrmException {
        ScrmMessageTemplateVersionEntity version = findVersionOrThrow(versionId);
        if (!version.getTemplateId().equals(templateId)) {
            throw ScrmException.badRequest("版本不属于该模板: templateId=" + templateId + ", versionId=" + versionId);
        }
        return activateVersion(versionId);
    }

    /**
     * 对比两个版本差异 (subject/content/plainContent/htmlContent/variables 字段级对比)。
     *
     * @param versionId1 版本 ID 1
     * @param versionId2 版本 ID 2
     * @return 对比结果
     * @throws ScrmException 版本不存在
     */
    @Transactional(readOnly = true)
    public Map<String, Object> compareVersions(Long versionId1, Long versionId2) throws ScrmException {
        ScrmMessageTemplateVersionEntity v1 = findVersionOrThrow(versionId1);
        ScrmMessageTemplateVersionEntity v2 = findVersionOrThrow(versionId2);
        List<Map<String, Object>> differences = new ArrayList<>();
        differences.add(compareField("subject", v1.getSubject(), v2.getSubject()));
        differences.add(compareField("content", v1.getContent(), v2.getContent()));
        differences.add(compareField("plainContent", v1.getPlainContent(), v2.getPlainContent()));
        differences.add(compareField("htmlContent", v1.getHtmlContent(), v2.getHtmlContent()));
        differences.add(compareField("variables", v1.getVariables(), v2.getVariables()));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("version1Id", versionId1);
        result.put("version1Number", v1.getVersionNumber());
        result.put("version2Id", versionId2);
        result.put("version2Number", v2.getVersionNumber());
        result.put("differences", differences);
        return result;
    }

    /**
     * 按主键查询版本, 不存在抛异常。
     *
     * @param id 版本 ID
     * @return 版本实体
     * @throws ScrmException 版本不存在
     */
    private ScrmMessageTemplateVersionEntity findVersionOrThrow(Long id) throws ScrmException {
        return versionRepository.findById(id)
                .orElseThrow(() -> ScrmException.notFound("模板版本不存在: id=" + id));
    }

    /**
     * 对比单字段差异。
     *
     * @param field 字段名
     * @param val1  版本 1 的值
     * @param val2  版本 2 的值
     * @return 字段对比结果
     */
    private Map<String, Object> compareField(String field, String val1, String val2) {
        Map<String, Object> diff = new LinkedHashMap<>();
        diff.put("field", field);
        diff.put("changed", !Objects.equals(val1, val2));
        diff.put("version1Value", val1);
        diff.put("version2Value", val2);
        return diff;
    }
}