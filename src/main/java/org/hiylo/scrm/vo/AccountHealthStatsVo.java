/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : AccountHealthStatsVo.java
 * Date : 2026/07/27 02:41:22
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 账号健康度统计 VO, 描述当前账号池的健康概览。
 *
 * @author Hsi Chu
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountHealthStatsVo {

    /** 账号总数 */
    private Long totalAccounts;

    /** 健康账号数（LOGIN 状态） */
    private Long healthyCount;

    /** 离线账号数（LOGOUT 状态） */
    private Long offlineCount;

    /** 冻结账号数（FROZEN 状态） */
    private Long frozenCount;

    /** 不健康账号数（离线 + 冻结） */
    private Long unhealthyCount;

    /** 在线率（健康账号 / 总账号, 0~1） */
    private Double onlineRate;
}
