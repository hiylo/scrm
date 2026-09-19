/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmPointsStatsService.java
 * Date : 2026/09/19 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import lombok.RequiredArgsConstructor;

import org.hiylo.scrm.dto.ScrmPointsAccountDto;
import org.hiylo.scrm.repository.ScrmPointsAccountRepository;
import org.hiylo.scrm.repository.ScrmPointsExchangeRecordRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * SCRM 积分统计服务 (统计排行子域)。
 * <p>
 * 承载积分总览 / 客户积分排行榜 / 兑换统计, 账户 DTO 转换复用
 * {@link ScrmPointsAccountService} 的包级能力。
 * </p>
 *
 * @author Hsi Chu
 * @since 2026-09-19
 */
@Service
@RequiredArgsConstructor
public class ScrmPointsStatsService {

    /** 积分账户数据访问层 */
    private final ScrmPointsAccountRepository accountRepository;
    /** 积分兑换记录数据访问层 */
    private final ScrmPointsExchangeRecordRepository exchangeRecordRepository;

    /** 积分账户子域服务 (账户 DTO 转换) */
    private final ScrmPointsAccountService accountService;

    /**
     * 积分总览 (总发放 / 总消耗 / 总过期 / 活跃账户数)。
     *
     * @return 积分总览统计
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getPointsStats() {
        Object[] stats = accountRepository.getPointsStats();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalEarned", toLong(stats, 0));
        result.put("totalRedeemed", toLong(stats, 1));
        result.put("totalExpired", toLong(stats, 2));
        result.put("activeAccounts", toLong(stats, 3));
        return result;
    }

    /**
     * 积分排行榜 (按当前可用积分倒序)。
     *
     * @param pageable 分页参数
     * @return 账户分页结果 (按 currentPoints DESC)
     */
    @Transactional(readOnly = true)
    public Page<ScrmPointsAccountDto> getCustomerPointsRanking(Pageable pageable) {
        return accountRepository
                .findAllByOrderByCurrentPointsDesc(pageable)
                .map(accountService::toAccountDto);
    }

    /**
     * 兑换统计 (指定时间区间内的兑换记录数与消耗积分总额)。
     *
     * @param startTime 起始时间（可空）
     * @param endTime   截止时间（可空）
     * @return 兑换统计
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getExchangeStats(LocalDateTime startTime, LocalDateTime endTime) {
        Object[] stats = exchangeRecordRepository.getExchangeStats(
                 startTime, endTime);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("recordCount", toLong(stats, 0));
        result.put("totalPointsCost", toLong(stats, 1));
        result.put("startTime", startTime);
        result.put("endTime", endTime);
        return result;
    }

    /**
     * 将统计结果数组的指定位置转为 long。
     *
     * @param stats 统计结果数组
     * @param index 索引
     * @return long 值
     */
    private long toLong(Object[] stats, int index) {
        if (stats == null || index >= stats.length || stats[index] == null) {
            return 0L;
        }
        if (stats[index] instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(stats[index].toString());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}