/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm
 * File : MarketingReviewGateTest.java
 * Date : 2026/10/23 16:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.hiylo.scrm.entity.ScrmMessageTemplateCenterEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.repository.ScrmMessageTemplateCenterRepository;
import org.hiylo.scrm.repository.ScrmMessageTemplateGroupRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 人工审核闸门单元测试（立项方案 §2 模块④: AI 生成内容未经审核不得分发）。
 * <p>
 * 覆盖两处既有链路加固:
 * <ol>
 *   <li>{@code ScrmMessageTemplateCenterTemplateService.publishTemplate} — 仅
 *       reviewStatus=APPROVED 可发布, PENDING/DRAFT/REJECTED 一律 403;</li>
 *   <li>{@code ScrmMessageTemplateCenterRenderService.renderForChannel} — 仅
 *       reviewStatus=APPROVED 可渲染分发, DRAFT 生成变体不得直接渲染使用。</li>
 * </ol>
 * </p>
 *
 * @author Hsi Chu
 */
@DisplayName("人工审核闸门单元测试 (publish/render)")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MarketingReviewGateTest {

    /** 模板中心仓储 Mock */
    @Mock
    private ScrmMessageTemplateCenterRepository centerRepository;

    /** 模板分组仓储 Mock */
    @Mock
    private ScrmMessageTemplateGroupRepository groupRepository;

    /** 被测服务 (模板管理子域) */
    private ScrmMessageTemplateCenterTemplateService templateService;

    /** 被测服务 (渲染子域) */
    private ScrmMessageTemplateCenterRenderService renderService;

    /** 分组服务 Mock */
    private ScrmMessageTemplateCenterGroupService groupService;

    /**
     * 测试前装配被测对象。
     */
    @BeforeEach
    void setUp() {
        groupService = new ScrmMessageTemplateCenterGroupService(groupRepository, centerRepository);
        templateService = new ScrmMessageTemplateCenterTemplateService(
                centerRepository, groupRepository, groupService);
        renderService = new ScrmMessageTemplateCenterRenderService(templateService, new ObjectMapper());
    }

    /**
     * 构造模板实体 (指定审核状态)。
     */
    private ScrmMessageTemplateCenterEntity buildTemplate(Long id, String reviewStatus) {
        ScrmMessageTemplateCenterEntity t = new ScrmMessageTemplateCenterEntity();
        t.setId(id);
        t.setTemplateCode("TAX_MKT_OPENING_001");
        t.setTemplateName("开业套餐");
        t.setTemplateType("TEXT");
        t.setChannels("WORK_WECHAT");
        t.setContent("恭喜开业！{{客户类型}}详情以合同为准。");
        t.setReviewStatus(reviewStatus);
        t.setStatus("DRAFT");
        return t;
    }

    // ============================================================
    // publishTemplate 闸门
    // ============================================================

    @Test
    @DisplayName("publishTemplate: reviewStatus=APPROVED 可发布")
    void publish_approved() throws ScrmException {
        when(centerRepository.findById(1L)).thenReturn(Optional.of(buildTemplate(1L, "APPROVED")));
        when(centerRepository.save(any(ScrmMessageTemplateCenterEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ScrmMessageTemplateCenterEntity result = templateService.publishTemplate(1L);

        assertThat(result.getStatus()).isEqualTo("PUBLISHED");
        verify(centerRepository).save(any(ScrmMessageTemplateCenterEntity.class));
    }

    @Test
    @DisplayName("publishTemplate: reviewStatus=PENDING (未审核) 拒绝发布并抛 403")
    void publish_pendingRejected() {
        when(centerRepository.findById(1L)).thenReturn(Optional.of(buildTemplate(1L, "PENDING")));

        assertThatThrownBy(() -> templateService.publishTemplate(1L))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("未审核通过")
                .hasMessageContaining("PENDING");
        verify(centerRepository, never()).save(any(ScrmMessageTemplateCenterEntity.class));
    }

    @Test
    @DisplayName("publishTemplate: reviewStatus=REJECTED (审核驳回) 拒绝发布")
    void publish_rejected() {
        when(centerRepository.findById(1L)).thenReturn(Optional.of(buildTemplate(1L, "REJECTED")));

        assertThatThrownBy(() -> templateService.publishTemplate(1L))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("未审核通过");
    }

    @Test
    @DisplayName("publishTemplate: reviewStatus=DRAFT (AI 生成未提交审核) 拒绝发布")
    void publish_draft() {
        when(centerRepository.findById(1L)).thenReturn(Optional.of(buildTemplate(1L, "PENDING")));

        assertThatThrownBy(() -> templateService.publishTemplate(1L))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("未审核通过");
    }

    // ============================================================
    // renderForChannel 闸门
    // ============================================================

    @Test
    @DisplayName("renderForChannel: reviewStatus=APPROVED 可渲染分发")
    void render_approved() throws ScrmException {
        when(centerRepository.findById(1L)).thenReturn(Optional.of(buildTemplate(1L, "APPROVED")));

        Map<String, Object> variables = new HashMap<>();
        variables.put("客户类型", "初创公司");
        Map<String, Object> result = renderService.renderForChannel(1L, "WORK_WECHAT", variables);

        assertThat(result.get("templateId")).isEqualTo(1L);
        assertThat(result.get("channel")).isEqualTo("WORK_WECHAT");
    }

    @Test
    @DisplayName("renderForChannel: reviewStatus=PENDING (DRAFT 变体) 拒绝渲染分发并抛 403")
    void render_pendingRejected() {
        when(centerRepository.findById(1L)).thenReturn(Optional.of(buildTemplate(1L, "PENDING")));

        assertThatThrownBy(() -> renderService.renderForChannel(1L, "WORK_WECHAT", new HashMap<>()))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("禁止渲染分发")
                .hasMessageContaining("PENDING");
    }

    @Test
    @DisplayName("renderForChannel: reviewStatus=REJECTED 拒绝渲染分发")
    void render_rejected() {
        when(centerRepository.findById(1L)).thenReturn(Optional.of(buildTemplate(1L, "REJECTED")));

        assertThatThrownBy(() -> renderService.renderForChannel(1L, "WORK_WECHAT", new HashMap<>()))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("禁止渲染分发");
    }

    @Test
    @DisplayName("审核闭环: APPROVED 模板发布成功 (DRAFT→PENDING_REVIEW→APPROVED→PUBLISHED 终态)")
    void reviewFlow_fullCycle() throws ScrmException {
        // 模拟已走完 submitForReview + review(APPROVE) 的实体: reviewStatus=APPROVED
        ScrmMessageTemplateCenterEntity approved = buildTemplate(2L, "APPROVED");
        approved.setStatus("PENDING_REVIEW");
        when(centerRepository.findById(2L)).thenReturn(Optional.of(approved));
        when(centerRepository.save(any(ScrmMessageTemplateCenterEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ScrmMessageTemplateCenterEntity published = templateService.publishTemplate(2L);

        assertThat(published.getStatus()).isEqualTo("PUBLISHED");
        assertThat(published.getReviewStatus()).isEqualTo("APPROVED");
    }
}