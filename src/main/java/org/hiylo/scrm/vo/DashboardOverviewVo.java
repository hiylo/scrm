/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : DashboardOverviewVo.java
 * Date : 2026/07/27 02:41:22
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 看板综合概览 VO, 聚合账号 / 任务 / 客户 / 会话 / 风控各维度的概览数据。
 *
 * @author Hsi Chu
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardOverviewVo {

    /** 账号概览 */
    private AccountOverviewVo accountOverview;

    /** 任务概览 */
    private CampaignOverviewVo campaignOverview;

    /** 客户概览 */
    private CustomerOverviewVo customerOverview;

    /** 会话概览 */
    private ConversationOverviewVo conversationOverview;

    /** 风控概览 */
    private RiskOverviewVo riskOverview;
}
