/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : AccountOverviewVo.java
 * Date : 2026/07/27 02:41:22
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 账号概览 VO, 描述 SCRM 平台账号总数、平台分布、登录态分布与在线率。
 *
 * @author Hsi Chu
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountOverviewVo {

    /** 账号总数 */
    private Long totalAccounts;

    /** 各平台账号分布（key=平台类型, value=账号数） */
    private Map<String, Long> platformDistribution;

    /** 登录态分布（key=LOGIN/LOGOUT/FROZEN/UNKNOWN, value=账号数） */
    private Map<String, Long> loginStateDistribution;

    /** 在线率（LOGIN 状态账号数 / 总账号数, 0~1） */
    private Double onlineRate;

    /** 不健康账号数（离线 LOGOUT + 冻结 FROZEN） */
    private Long unhealthyCount;

    /** 健康检测记录总数（来自 scrm_account_health, 用于看板展示检测覆盖度） */
    private Long totalHealthChecks;
}
