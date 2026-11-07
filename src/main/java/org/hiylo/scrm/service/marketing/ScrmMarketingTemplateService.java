/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm
 * File : ScrmMarketingTemplateService.java
 * Date : 2026/10/23 16:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service.marketing;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hiylo.scrm.dto.ScrmMarketingBatchGenerateDto;
import org.hiylo.scrm.dto.ScrmMarketingBatchSaveDto;
import org.hiylo.scrm.dto.ScrmMarketingVariantDto;
import org.hiylo.scrm.dto.ScrmMessageTemplateCenterDto;
import org.hiylo.scrm.entity.ScrmMessageTemplateCenterEntity;
import org.hiylo.scrm.entity.ScrmMessageTemplateGroupEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.service.ScrmMessageTemplateCenterService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 财税营销变体落库服务（AI 生成通过项 → MessageTemplateCenter DRAFT）。
 * <p>
 * 将 {@link ScrmMarketingBatchGenerationService} 通过合规校验的变体保存为
 * {@code scrm_message_template_center} 的 DRAFT 行（复用既有模板中心服务, 不新增表）:
 * 变体标题入 subject、变体正文入 content/plain_content、语气/风险提示写入 description
 * 与 attachements 说明字段; 分组继承源模板分组; status=DRAFT / review_status=PENDING,
 * 进入人工审核队列（DRAFT → PENDING_REVIEW → APPROVED/REJECTED）。
 * 生成编码规则: {@code AI_{源模板编码}_{时间戳后6位}_{序号}}。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmMarketingTemplateService {

    /** 模板中心服务 (门面, 落库走既有创建模板链路, 保证分组/统计一致) */
    private final ScrmMessageTemplateCenterService templateCenterService;

    /** 批量生成服务 (落库前重新生成 + 合规校验, 防止绕过) */
    private final ScrmMarketingBatchGenerationService generationService;

    /**
     * 保存 AI 批量生成变体为 DRAFT 模板。
     * <p>
     * 服务端基于源模板重新调用 AI 生成并执行合规校验: 生成失败/全部不合规时抛
     * {@link ScrmException}, 不产生任何 DRAFT 行; 通过的变体逐个落库为
     * DRAFT (review_status=PENDING), 进入人工审核队列。
     * </p>
     *
     * @param dto 保存请求 (源模板编码 + 生成参数)
     * @return 已保存的 DRAFT 模板列表
     * @throws ScrmException 源模板不存在 / AI 不可用 / 全部变体不合规
     */
    public List<ScrmMessageTemplateCenterEntity> saveGeneratedDrafts(ScrmMarketingBatchSaveDto dto)
            throws ScrmException {
        if (dto == null || dto.getSourceTemplateCode() == null || dto.getSourceTemplateCode().isBlank()) {
            throw ScrmException.badRequest("源模板编码不能为空");
        }
        ScrmMessageTemplateCenterEntity sourceTemplate =
                templateCenterService.getTemplateByCode(dto.getSourceTemplateCode());
        // 重新生成: 与 generate 端点同一链路, 保证落库内容已过合规校验
        ScrmMarketingBatchGenerateDto genDto = new ScrmMarketingBatchGenerateDto();
        genDto.setTemplateCode(sourceTemplate.getTemplateCode());
        genDto.setVariables(dto.getVariables());
        genDto.setTone(dto.getTone());
        genDto.setChannel(dto.getChannel());
        genDto.setVariantCount(dto.getVariantCount());
        genDto.setCharLimit(dto.getCharLimit());
        genDto.setGeneratedBy(dto.getGeneratedBy());
        Map<String, Object> result = generationService.batchGenerate(genDto);
        @SuppressWarnings("unchecked")
        List<ScrmMarketingVariantDto> variants = (List<ScrmMarketingVariantDto>) result.get("variants");
        if (variants == null || variants.isEmpty()) {
            String error = result.get("error") == null ? "未生成合规变体" : String.valueOf(result.get("error"));
            throw ScrmException.badRequest("AI 生成结果未通过合规校验, 未落库: " + error);
        }
        return saveVariantsAsDrafts(sourceTemplate, genDto, variants);
    }

    /**
     * 将批量生成通过的变体保存为 DRAFT 模板。
     *
     * @param sourceTemplate 源模板 (V12 财税模板或任意模板)
     * @param dto            生成请求 (含生成人)
     * @param variants       通过合规校验的变体列表
     * @return 已保存的模板列表
     */
    public List<ScrmMessageTemplateCenterEntity> saveVariantsAsDrafts(
            ScrmMessageTemplateCenterEntity sourceTemplate,
            ScrmMarketingBatchGenerateDto dto,
            List<ScrmMarketingVariantDto> variants) {
        List<ScrmMessageTemplateCenterEntity> saved = new ArrayList<>();
        if (variants == null || variants.isEmpty()) {
            return saved;
        }
        String groupName = resolveGroupName(sourceTemplate);
        Long groupId = sourceTemplate.getGroupId();
        String baseCode = sourceTemplate.getTemplateCode();
        long ts = System.currentTimeMillis() % 1_000_000L;
        int seq = 0;
        for (ScrmMarketingVariantDto variant : variants) {
            seq++;
            String newCode = buildAiCode(baseCode, ts, seq);
            ScrmMessageTemplateCenterDto dto2 = new ScrmMessageTemplateCenterDto();
            dto2.setTemplateName("AI生成·" + variant.getTitle());
            dto2.setTemplateCode(newCode);
            dto2.setGroupId(groupId);
            dto2.setGroupName(groupName);
            dto2.setDescription(buildDescription(variant, dto));
            dto2.setTemplateType("TEXT");
            dto2.setChannels(sourceTemplate.getChannels());
            dto2.setSubject(variant.getTitle());
            dto2.setContent(variant.getContent());
            dto2.setPlainContent(variant.getContent());
            dto2.setCategory(sourceTemplate.getCategory());
            dto2.setApplicableScenarios(sourceTemplate.getApplicableScenarios());
            dto2.setLanguage("zh_CN");
            dto2.setStatus("DRAFT");
            dto2.setCreatedBy(dto.getGeneratedBy());
            try {
                ScrmMessageTemplateCenterEntity entity = templateCenterService.createTemplate(dto2);
                saved.add(entity);
                log.info("AI 生成变体落库 DRAFT: id={}, templateCode={}, sourceCode={}",
                        entity.getId(), entity.getTemplateCode(), baseCode);
            } catch (ScrmException e) {
                // 单条编码冲突等: 跳过并记日志, 不阻断批量
                log.warn("AI 生成变体落库失败: templateCode={}, err={}",
                        newCode, e.getMessage());
            }
        }
        return saved;
    }

    /**
     * 解析源模板分组名 (groupId 冗余字段 groupName 缺失时回退编码)。
     *
     * @param sourceTemplate 源模板
     * @return 分组名称
     */
    private String resolveGroupName(ScrmMessageTemplateCenterEntity sourceTemplate) {
        if (sourceTemplate.getGroupName() != null && !sourceTemplate.getGroupName().isBlank()) {
            return sourceTemplate.getGroupName();
        }
        if (sourceTemplate.getGroupId() == null) {
            return null;
        }
        try {
            ScrmMessageTemplateGroupEntity group =
                    templateCenterService.getGroup(sourceTemplate.getGroupId());
            return group.getGroupName();
        } catch (ScrmException e) {
            return null;
        }
    }

    /**
     * 构建描述: 来源 + 语气 + 人工重点审核提示 (riskNotes 落到 description, 审核人核对)。
     *
     * @param variant 变体
     * @param dto     生成请求
     * @return 描述文本 (≤500)
     */
    private String buildDescription(ScrmMarketingVariantDto variant, ScrmMarketingBatchGenerateDto dto) {
        StringBuilder sb = new StringBuilder("AI批量生成变体, 人工审核后分发");
        sb.append(" | 语气: ").append(variant.getTone() == null || variant.getTone().isBlank()
                ? (dto.getTone() == null ? "亲切自然" : dto.getTone()) : variant.getTone());
        if (variant.getRiskNotes() != null && !variant.getRiskNotes().isEmpty()) {
            sb.append(" | 重点审核: ").append(String.join(";", variant.getRiskNotes()));
        }
        String desc = sb.toString();
        return desc.length() <= 500 ? desc : desc.substring(0, 500);
    }

    /**
     * 生成 AI 变体模板编码: AI_{源编码}_{时间戳后缀6位}_{序号} (≤50)。
     *
     * @param baseCode 源模板编码
     * @param ts       时间戳后缀
     * @param seq      序号
     * @return 新编码
     */
    private String buildAiCode(String baseCode, long ts, int seq) {
        String prefix = baseCode == null || baseCode.isBlank() ? "TAX_MKT" : baseCode;
        String code = "AI_" + prefix + "_" + ts + "_" + seq;
        return code.length() <= 50 ? code : code.substring(0, 50);
    }
}