/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCustomer360Vo.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hiylo.scrm.dto.ScrmCustomerDto;
import org.hiylo.scrm.entity.ScrmConversationMessageEntity;
import org.hiylo.scrm.entity.ScrmCustomerGroupEntity;
import org.hiylo.scrm.entity.ScrmCustomerLifecycleHistoryEntity;
import org.hiylo.scrm.entity.ScrmTagCustomerEntity;
import org.hiylo.scrm.entity.ScrmCustomerTimelineEntity;
import org.hiylo.scrm.entity.ScrmJourneyEnrollmentEntity;
import org.hiylo.scrm.entity.ScrmOpportunityEntity;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 客户 360° 全景视图 VO。
 * <p>
 * 聚合客户基本信息、标签、分组、旅程进度、商机、会话消息、跟进记录、
 * 时间线事件与互动统计为统一视图, 供客户详情页一站式展示。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScrmCustomer360Vo {

    /** 客户基本信息 */
    private ScrmCustomerDto customer;

    /** 客户标签列表 */
    private List<ScrmTagCustomerEntity> tags;

    /** 客户所属分组列表 */
    private List<ScrmCustomerGroupEntity> groups;

    /** 客户分级 (可空, 通常从标签 "level" 推导) */
    private String level;

    /** 生命周期变更历史 */
    private List<ScrmCustomerLifecycleHistoryEntity> lifecycleHistory;

    /** 进行中商机列表 */
    private List<ScrmOpportunityEntity> opportunities;

    /** 旅程进度列表 */
    private List<ScrmJourneyEnrollmentEntity> journeyEnrollments;

    /** 待跟进任务列表 (依赖跟进任务模块, 模块未接入时返回空列表) */
    private List<Object> followUpTasks;

    /** 最近跟进记录 (limit 10, 依赖跟进记录模块, 模块未接入时返回空列表) */
    private List<Object> followUpRecords;

    /** 最近会话消息 (limit 20) */
    private List<ScrmConversationMessageEntity> recentMessages;

    /** 最近时间线事件 (limit 50) */
    private List<ScrmCustomerTimelineEntity> recentTimeline;

    /** 互动统计 */
    private InteractionStats stats;

    /**
     * 互动统计内嵌 VO。
     * @author Hsi Chu
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InteractionStats {

        /** 总消息数 */
        private Long totalMessages;

        /** 总跟进次数 (依赖跟进记录模块, 模块未接入时为 0) */
        private Long totalFollowUps;

        /** 最后互动时间 */
        private LocalDateTime lastInteractionAt;

        /** 客户天数 (从创建至今) */
        private Long customerDays;

        /** 商机总额 (进行中商机金额合计) */
        private Double totalOpportunityAmount;
    }
}
