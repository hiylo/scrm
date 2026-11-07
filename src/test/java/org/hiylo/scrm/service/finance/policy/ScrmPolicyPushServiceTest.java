/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm-finance
 * File : ScrmPolicyPushServiceTest.java
 * Date : 2026/10/23 12:30:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service.finance.policy;

import org.hiylo.scrm.config.ScrmPolicyPushProperties;
import org.hiylo.scrm.entity.ScrmCustomerEntity;
import org.hiylo.scrm.entity.ScrmKnowledgeArticleEntity;
import org.hiylo.scrm.entity.ScrmPolicyPushTaskEntity;
import org.hiylo.scrm.entity.ScrmSegmentMemberEntity;
import org.hiylo.scrm.entity.ScrmTagCustomerEntity;
import org.hiylo.scrm.entity.ScrmTagEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.repository.ScrmCustomerRepository;
import org.hiylo.scrm.repository.ScrmKnowledgeArticleRepository;
import org.hiylo.scrm.repository.ScrmNotificationRepository;
import org.hiylo.scrm.repository.ScrmPolicyPushTaskRepository;
import org.hiylo.scrm.repository.ScrmSegmentMemberRepository;
import org.hiylo.scrm.repository.ScrmTagCustomerRepository;
import org.hiylo.scrm.repository.ScrmTagRepository;
import org.hiylo.scrm.service.ScrmNotificationCenterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ScrmPolicyPushService} 单元测试（政策匹配推送域）。
 * <p>
 * 锁定关键业务语义（T08D 验收判据）:
 * <ul>
 *   <li>政策条目未发布/未审核 → 禁止建推送任务（一级闸门守卫）</li>
 *   <li>草稿期预计算命中客户快照（Segment 优先, 标签兜底, 全量兜底）</li>
 *   <li>任务状态机: DRAFT → PENDING_AUDIT → APPROVED → PUBLISHING → COMPLETED;
 *       驳回 → REJECTED（零对外消息）; 未过审禁止发布（二级闸门守卫）</li>
 *   <li>抽样核验清单 + 幂等发布 + 配额</li>
 * </ul>
 * </p>
 *
 * @author Hsi Chu
 */
@DisplayName("ScrmPolicyPushService 政策推送守卫与匹配")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ScrmPolicyPushServiceTest {

    /** 推送任务仓库 Mock */
    @Mock
    private ScrmPolicyPushTaskRepository taskRepository;
    /** 知识条目仓库 Mock */
    @Mock
    private ScrmKnowledgeArticleRepository articleRepository;
    /** 客户仓库 Mock */
    @Mock
    private ScrmCustomerRepository customerRepository;
    /** 标签仓库 Mock */
    @Mock
    private ScrmTagRepository tagRepository;
    /** 客户标签仓库 Mock */
    @Mock
    private ScrmTagCustomerRepository tagCustomerRepository;
    /** 分群成员仓库 Mock */
    @Mock
    private ScrmSegmentMemberRepository segmentMemberRepository;
    /** 通知中心门面 Mock */
    @Mock
    private ScrmNotificationCenterService notificationCenterService;
    /** 通知记录仓库 Mock（配额/幂等） */
    @Mock
    private ScrmNotificationRepository notificationRepository;

    /** 被测服务 */
    private ScrmPolicyPushService service;

    @BeforeEach
    void setUp() {
        service = new ScrmPolicyPushService(taskRepository, articleRepository, customerRepository,
                tagRepository, tagCustomerRepository, segmentMemberRepository,
                notificationCenterService, notificationRepository, new ScrmPolicyPushProperties());
    }

    @Test
    @DisplayName("createTask: 政策条目已发布+已审核 → 创建并预计算快照")
    void createTask_publishedPolicyPrecompute() throws Exception {
        ScrmKnowledgeArticleEntity article = buildArticle(1L, "POLICY", "PUBLISHED", "APPROVED");
        when(articleRepository.findById(1L)).thenReturn(Optional.of(article));
        when(taskRepository.findByPolicyArticleId(1L)).thenReturn(List.of());
        // 标签匹配: TAG_VAT 命中客户 100/200
        ScrmTagEntity tag = new ScrmTagEntity();
        tag.setId(50L);
        tag.setTagCode("TAG_VAT");
        when(tagRepository.findByTagCode("TAG_VAT")).thenReturn(Optional.of(tag));
        when(tagCustomerRepository.findByTagId(50L, org.springframework.data.domain.PageRequest.of(0, 5000)))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(
                        tagLink(100L), tagLink(200L))));
        when(taskRepository.save(any(ScrmPolicyPushTaskEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        org.hiylo.scrm.dto.ScrmPolicyPushTaskDto dto = new org.hiylo.scrm.dto.ScrmPolicyPushTaskDto();
        dto.setPolicyArticleId(1L);
        dto.setTaxType("VAT");
        dto.setTagCodes("TAG_VAT");

        ScrmPolicyPushTaskEntity result = service.createTask(dto);

        assertThat(result.getStatus()).isEqualTo("DRAFT");
        assertThat(result.getExpectedRecipientCount()).isEqualTo(2);
        assertThat(result.getSnapshotCustomerIds()).isEqualTo("100,200");
    }

    @Test
    @DisplayName("createTask: 政策条目未审核 → 拒绝（一级闸门守卫）")
    void createTask_unreviewedPolicyRejected() {
        ScrmKnowledgeArticleEntity article = buildArticle(1L, "POLICY", "PUBLISHED", "PENDING");
        when(articleRepository.findById(1L)).thenReturn(Optional.of(article));

        assertThatThrownBy(() -> service.createTask(new org.hiylo.scrm.dto.ScrmPolicyPushTaskDto() {{
            setPolicyArticleId(1L);
        }}))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("未发布/未审核");
        verify(taskRepository, never()).save(any());
    }

    @Test
    @DisplayName("createTask: 非 POLICY 类型知识条目 → 拒绝")
    void createTask_nonPolicyRejected() {
        ScrmKnowledgeArticleEntity article = buildArticle(1L, "ARTICLE", "PUBLISHED", "APPROVED");
        when(articleRepository.findById(1L)).thenReturn(Optional.of(article));

        org.hiylo.scrm.dto.ScrmPolicyPushTaskDto dto = new org.hiylo.scrm.dto.ScrmPolicyPushTaskDto();
        dto.setPolicyArticleId(1L);

        assertThatThrownBy(() -> service.createTask(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("仅 POLICY 类型");
    }

    @Test
    @DisplayName("matchCustomers: segmentId 优先命中当前成员")
    void matchCustomers_segmentPriority() {
        ScrmPolicyPushTaskEntity task = new ScrmPolicyPushTaskEntity();
        task.setSegmentId(9L);
        task.setTaxType("ALL");
        ScrmSegmentMemberEntity member = new ScrmSegmentMemberEntity();
        member.setCustomerId(300L);
        when(segmentMemberRepository.findBySegmentIdAndIsCurrentMemberTrue(9L))
                .thenReturn(List.of(member));

        List<Long> matched = service.matchCustomers(task);

        assertThat(matched).containsExactly(300L);
    }

    @Test
    @DisplayName("matchCustomers: 无标签无分段 → 全量客户兜底")
    void matchCustomers_fullFallback() {
        ScrmPolicyPushTaskEntity task = new ScrmPolicyPushTaskEntity();
        task.setTaxType("ALL");
        ScrmCustomerEntity c1 = new ScrmCustomerEntity();
        c1.setId(1L);
        ScrmCustomerEntity c2 = new ScrmCustomerEntity();
        c2.setId(2L);
        when(customerRepository.findAll()).thenReturn(List.of(c1, c2));

        List<Long> matched = service.matchCustomers(task);

        assertThat(matched).containsExactly(1L, 2L);
    }

    @Test
    @DisplayName("状态机: DRAFT → PENDING_AUDIT → APPROVED → PUBLISHING → COMPLETED")
    void stateMachine_fullFlow() throws Exception {
        ScrmPolicyPushTaskEntity task = buildTask("DRAFT", "PENDING");
        task.setSnapshotCustomerIds("100,200");
        task.setAuditMode("FULL");
        when(taskRepository.findById(1L)).thenReturn(Optional.of(task));
        when(articleRepository.findById(1L))
                .thenReturn(Optional.of(buildArticle(1L, "POLICY", "PUBLISHED", "APPROVED")));
        when(taskRepository.save(any(ScrmPolicyPushTaskEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(notificationRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class)))
                .thenReturn(List.of());
        ScrmCustomerEntity c = new ScrmCustomerEntity();
        c.setNickname("客户100");
        when(customerRepository.findById(100L)).thenReturn(Optional.of(c));
        when(customerRepository.findById(200L)).thenReturn(Optional.empty());

        ScrmPolicyPushTaskEntity submitted = service.submit(1L);
        assertThat(submitted.getStatus()).isEqualTo("PENDING_AUDIT");
        assertThat(submitted.getAuditStatus()).isEqualTo("PENDING");

        ScrmPolicyPushTaskEntity approved = service.approve(1L, "auditor-1", null);
        assertThat(approved.getStatus()).isEqualTo("APPROVED");
        assertThat(approved.getAuditStatus()).isEqualTo("APPROVED");

        ScrmPolicyPushTaskEntity published = service.publish(1L);
        assertThat(published.getStatus()).isEqualTo("COMPLETED");
        assertThat(published.getCompletedAt()).isNotNull();
        // 每个命中客户推一条内部提醒
        verify(notificationCenterService, org.mockito.Mockito.atLeastOnce())
                .sendNotification(any(org.hiylo.scrm.dto.ScrmNotificationSendDto.class));
    }

    @Test
    @DisplayName("状态机守卫: 未过审（APPROVED 前）禁止发布 → CONFLICT（二级闸门）")
    void publish_beforeApprovedRejected() {
        ScrmPolicyPushTaskEntity task = buildTask("DRAFT", "PENDING");
        when(taskRepository.findById(1L)).thenReturn(Optional.of(task));

        assertThatThrownBy(() -> service.publish(1L))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("仅 APPROVED / FAILED");
        verify(notificationCenterService, never()).sendNotification(any());
    }

    @Test
    @DisplayName("审核驳回 → REJECTED, 零对外消息")
    void reject_blocksExternalMessages() throws Exception {
        ScrmPolicyPushTaskEntity task = buildTask("PENDING_AUDIT", "SAMPLING_AUDIT");
        when(taskRepository.findById(1L)).thenReturn(Optional.of(task));
        when(taskRepository.save(any(ScrmPolicyPushTaskEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ScrmPolicyPushTaskEntity rejected = service.reject(1L, "auditor-1", "口径有误");

        assertThat(rejected.getStatus()).isEqualTo("REJECTED");
        assertThat(rejected.getAuditStatus()).isEqualTo("REJECTED");
        verify(notificationCenterService, never()).sendNotification(any());
    }

    @Test
    @DisplayName("samplingList: 取快照前 N 个命中客户供人工核验")
    void samplingList_topN() throws Exception {
        ScrmPolicyPushTaskEntity task = buildTask("PENDING_AUDIT", "SAMPLING_AUDIT");
        task.setSnapshotCustomerIds("100,200,300");
        when(taskRepository.findById(1L)).thenReturn(Optional.of(task));
        ScrmCustomerEntity c = new ScrmCustomerEntity();
        c.setNickname("客户100");
        when(customerRepository.findById(100L)).thenReturn(Optional.of(c));

        List<java.util.Map<String, Object>> list = service.samplingList(1L);

        assertThat(list).hasSize(3);
        assertThat(list.get(0).get("customerName")).isEqualTo("客户100");
    }

    // ==================== 工具 ====================

    private ScrmKnowledgeArticleEntity buildArticle(Long id, String articleType, String status,
                                                    String reviewStatus) {
        ScrmKnowledgeArticleEntity a = new ScrmKnowledgeArticleEntity();
        a.setId(id);
        a.setArticleType(articleType);
        a.setStatus(status);
        a.setReviewStatus(reviewStatus);
        a.setArticleCode("POL-001");
        a.setTitle("关于增值税小规模纳税人减免政策的公告");
        a.setKeywords("财税2026-01号");
        a.setSummary("自2026年起小规模纳税人季度销售额免征增值税标准提高。");
        return a;
    }

    private ScrmPolicyPushTaskEntity buildTask(String status, String auditStatus) {
        ScrmPolicyPushTaskEntity t = new ScrmPolicyPushTaskEntity();
        t.setId(1L);
        t.setTaskNo("PP-20261023-0001");
        t.setPolicyArticleId(1L);
        t.setPolicyCode("财税2026-01号");
        t.setPolicyTitle("关于增值税小规模纳税人减免政策的公告");
        t.setTaxType("VAT");
        t.setSizeRange("ALL");
        t.setEffectiveFrom(LocalDate.of(2026, 1, 1));
        t.setEffectiveUntil(LocalDate.of(2026, 12, 31));
        t.setAuditMode("FULL");
        t.setAuditStatus(auditStatus);
        t.setStatus(status);
        return t;
    }

    private ScrmTagCustomerEntity tagLink(Long customerId) {
        ScrmTagCustomerEntity link = new ScrmTagCustomerEntity();
        link.setCustomerId(customerId);
        return link;
    }
}
