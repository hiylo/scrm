/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmAccountHealthService.java
 * Date : 2026/07/27 02:41:22
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.entity.ScrmAccountEntity;
import org.hiylo.scrm.entity.ScrmAccountHealthEntity;
import org.hiylo.scrm.execution.ExecutionSessionStatus;
import org.hiylo.scrm.execution.TaskExecutionService;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.repository.ScrmAccountHealthRepository;
import org.hiylo.scrm.repository.ScrmAccountRepository;
import org.hiylo.scrm.vo.AccountHealthStatsVo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * SCRM 账号健康度检测服务。
 * <p>
 * 提供单个 / 批量账号健康度检测能力, 检测流程:
 * <ol>
 *   <li>记录检测前登录态 previousState</li>
 *   <li>通过 {@link ScrmAccountService#checkPlatformAccountAvailable} 检查平台配置可用性
 *       (仅支持企业微信, 委托 {@code WeworkService.isAvailable})</li>
 *   <li>通过 {@link TaskExecutionService#getSessionStatus} 检查设备 / 会话状态 (如有 deviceId)</li>
 *   <li>根据检查结果映射 currentState (HEALTHY=LOGIN / OFFLINE=LOGOUT / FROZEN=FROZEN)</li>
 *   <li>状态变化时调用 {@link ScrmAccountService#updateLoginState} 同步登录态并记录日志</li>
 *   <li>保存 {@link ScrmAccountHealthEntity} 检测记录</li>
 * </ol>
 * 定时任务由 {@code AccountHealthScheduler} 触发, 也可通过 Controller 手动触发。
 * </p>
 *
 * @author Hsi Chu
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScrmAccountHealthService {

    /** 检测结果: 健康 */
    private static final String RESULT_HEALTHY = "HEALTHY";

    /** 检测结果: 离线 */
    private static final String RESULT_OFFLINE = "OFFLINE";

    /** 检测结果: 冻结 */
    private static final String RESULT_FROZEN = "FROZEN";

    /** 检测结果: 未知 */
    private static final String RESULT_UNKNOWN = "UNKNOWN";

    /** 检测结果: 检测异常 (平台不可达等) */
    private static final String RESULT_ERROR = "ERROR";

    /** 登录态: 已登录 */
    private static final String STATE_LOGIN = "LOGIN";

    /** 登录态: 已登出 */
    private static final String STATE_LOGOUT = "LOGOUT";

    /** 登录态: 已冻结 */
    private static final String STATE_FROZEN = "FROZEN";

    /** 批量检测分页大小 */
    private static final int BATCH_SIZE = 50;

    /** 健康检测记录数据访问层 */
    private final ScrmAccountHealthRepository healthRepository;

    /** 账号数据访问层 */
    private final ScrmAccountRepository accountRepository;

    /** 账号服务 (复用平台可用性检查 / 登录态更新能力) */
    private final ScrmAccountService accountService;

    /** 营销任务执行引擎扩展点, 用于检查设备 / 会话状态 */
    private final TaskExecutionService taskExecutionService;

    /**
     * 检测单个账号健康度。
     * <p>
     * 检测流程: 平台可用性 → 设备 / 会话状态 → 状态映射 → 状态变更同步 → 保存检测记录。
     * </p>
     *
     * @param accountId 账号 ID
     * @return 健康检测记录
     * @throws ScrmException 账号不存在
     */
    @Transactional
    public ScrmAccountHealthEntity checkAccountHealth(Long accountId) throws ScrmException {
        ScrmAccountEntity account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.SCRM_ACCOUNT_NOT_FOUND,
                        "SCRM 账号不存在: id=" + accountId));

        String previousState = account.getLoginState();
        String platformType = account.getPlatformType();
        String platformAccountUid = account.getPlatformAccountUid();
        String deviceId = account.getDeviceId();

        StringBuilder detail = new StringBuilder();
        String checkResult;
        String currentState;

        // 1. 冻结账号不自动解冻, 直接记录为 FROZEN
        if (STATE_FROZEN.equals(previousState)) {
            checkResult = RESULT_FROZEN;
            currentState = STATE_FROZEN;
            detail.append("accountFrozen=keep;");
        } else {
            // 2. 检查平台配置可用性 (仅支持企业微信, 委托 WeworkService.isAvailable)
            boolean platformAvailable =
                    accountService.checkPlatformAccountAvailable(platformType, platformAccountUid);
            detail.append("platformAvailable=").append(platformAvailable).append(';');

            if (!platformAvailable) {
                // 平台不可达, 不变更账号状态, 标记为 ERROR
                checkResult = RESULT_ERROR;
                currentState = previousState;
            } else {
                // 3. 检查设备 / 会话状态 (如有 deviceId)
                boolean sessionHealthy = checkSessionStatus(deviceId, detail);
                if (sessionHealthy) {
                    checkResult = RESULT_HEALTHY;
                    currentState = STATE_LOGIN;
                } else {
                    checkResult = RESULT_OFFLINE;
                    currentState = STATE_LOGOUT;
                }
            }
        }

        detail.append("previousState=").append(previousState)
                .append(",currentState=").append(currentState);

        // 4. 状态变化时同步登录态 (设置请求上下文, 保证登录日志归属正确账号)
        if (!currentState.equals(previousState)) {
            try {
                accountService.updateLoginState(accountId, currentState,
                        "健康度检测状态变更: " + checkResult);
            } catch (Exception e) {
                log.warn("健康检测更新登录态失败: accountId={}, state={}, err={}",
                        accountId, currentState, e.getMessage());
                detail.append(";updateLoginStateFailed=").append(e.getMessage());
            }
        }

        // 5. 保存健康检测记录
        ScrmAccountHealthEntity health = new ScrmAccountHealthEntity();
        health.setAccountId(accountId);
        health.setCheckResult(checkResult);
        health.setPreviousState(previousState);
        health.setCurrentState(currentState);
        health.setDetail(detail.toString());
        health.setCheckedAt(LocalDateTime.now());
        return healthRepository.save(health);
    }

    /**
     * 批量检测所有账号 (分页遍历)。
     * <p>
     * 单账号检测异常不影响整体流程, 异常计数累加并继续下一个账号。
     * </p>
     *
     * @return 检测摘要 (检测数 / 异常数)
     */
    public CheckSummary checkAllAccounts() {
        int checkedCount = 0;
        int errorCount = 0;
        int page = 0;
        Page<ScrmAccountEntity> batch;
        do {
            batch = accountRepository.findAll(PageRequest.of(page, BATCH_SIZE));
            for (ScrmAccountEntity account : batch.getContent()) {
                try {
                    checkAccountHealth(account.getId());
                    checkedCount++;
                } catch (Exception e) {
                    errorCount++;
                    log.warn("账号健康检测失败: accountId={}, err={}",
                            account.getId(), e.getMessage());
                }
            }
            page++;
        } while (!batch.isLast());
        log.info("批量健康检测完成: checked={}, errors={}", checkedCount, errorCount);
        return new CheckSummary(checkedCount, errorCount);
    }

    /**
     * 获取指定账号最新一条健康检测记录。
     *
     * @param accountId 账号 ID
     * @return 最新健康记录 (无记录返回 null)
     */
    @Transactional(readOnly = true)
    public ScrmAccountHealthEntity getLatestHealth(Long accountId) {
        return healthRepository.findTopByAccountIdOrderByCheckedAtDesc(accountId).orElse(null);
    }

    /**
     * 分页查询指定账号的健康检测历史, 检测时间倒序。
     *
     * @param accountId 账号 ID
     * @param page      页码 (从 0 开始)
     * @param size      每页大小
     * @return 健康记录分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmAccountHealthEntity> getHealthHistory(Long accountId, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.max(size, 1));
        return healthRepository.findByAccountIdOrderByCheckedAtDesc(accountId, pageable);
    }

    /**
     * 分页查询离线账号的健康检测记录 (checkResult=OFFLINE)。
     *
     * @param page 页码 (从 0 开始)
     * @param size 每页大小
     * @return 离线检测记录分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmAccountHealthEntity> getOfflineAccounts(int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.max(size, 1));
        return healthRepository.findByCheckResult(RESULT_OFFLINE, pageable);
    }

    /**
     * 账号健康度统计: 总账号数 / 健康 / 离线 / 冻结 / 不健康 / 在线率。
     * <p>
     * 数据按归属账号隔离, 直接查 {@link ScrmAccountRepository}
     * 获取当前账号池登录态分布。
     * </p>
     *
     * @return 健康度统计 VO
     */
    @Transactional(readOnly = true)
    public AccountHealthStatsVo getHealthStats() {
        long total = accountRepository.count();
        long healthy = accountRepository.countByLoginState(STATE_LOGIN);
        long offline = accountRepository.countByLoginState(STATE_LOGOUT);
        long frozen = accountRepository.countByLoginState(STATE_FROZEN);
        // 未检测或未知状态的账号也计入不健康 (total - healthy - offline - frozen)
        long unknown = total - healthy - offline - frozen;
        long unhealthy = offline + frozen + unknown;
        double onlineRate = total > 0 ? (double) healthy / total : 0.0;
        return AccountHealthStatsVo.builder()
                .totalAccounts(total)
                .healthyCount(healthy)
                .offlineCount(offline)
                .frozenCount(frozen)
                .unhealthyCount(unhealthy)
                .onlineRate(onlineRate)
                .build();
    }

    /**
     * 检查设备 / 会话状态。
     * <p>
     * 通过 {@link TaskExecutionService#getSessionStatus} 查询会话状态 (传入 deviceId 作为会话标识),
     * 若会话处于 failed / terminated / error 状态则视为离线。
     * 无外部执行引擎或会话不存在时不视为离线 (避免误告警), 仅记录详情。
     * </p>
     *
     * @param deviceId 设备 ID (可空)
     * @param detail   检测详情累积器
     * @return true 表示会话健康 / 无法判定; false 表示会话明确失败
     */
    private boolean checkSessionStatus(String deviceId, StringBuilder detail) {
        if (deviceId == null || deviceId.isBlank()) {
            // 无设备绑定的账号无法校验会话, 默认视为健康
            detail.append("noDevice;");
            return true;
        }
        try {
            ExecutionSessionStatus status = taskExecutionService.getSessionStatus(deviceId);
            if (status == null || status.getStatus() == null) {
                // 无执行引擎或会话不存在, 不视为离线
                detail.append("sessionNotFound;");
                return true;
            }
            String statusText = status.getStatus();
            detail.append("sessionStatus=").append(statusText).append(';');
            String lower = statusText.toLowerCase();
            if (lower.contains("fail") || lower.contains("terminate") || lower.contains("error")) {
                return false;
            }
            return true;
        } catch (Exception e) {
            detail.append("sessionCheckError=").append(e.getMessage()).append(';');
            return true;
        }
    }

    /**
     * 批量检测摘要: 检测数与异常数。
     * @author Hsi Chu
     */
    @Getter
    @AllArgsConstructor
    public static class CheckSummary {
        /** 已检测账号数 */
        private final int checkedCount;

        /** 检测异常账号数 */
        private final int errorCount;
    }
}
