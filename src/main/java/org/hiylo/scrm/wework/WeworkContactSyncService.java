/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : WeworkContactSyncService.java
 * Date : 2026/07/29 21:19:51
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.wework;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hiylo.scrm.entity.ScrmAccountEntity;
import org.hiylo.scrm.entity.ScrmCustomerEntity;
import org.hiylo.scrm.entity.ScrmCustomerGroupEntity;
import org.hiylo.scrm.entity.ScrmCustomerGroupMemberEntity;
import org.hiylo.scrm.repository.ScrmAccountRepository;
import org.hiylo.scrm.repository.ScrmCustomerGroupMemberRepository;
import org.hiylo.scrm.repository.ScrmCustomerGroupRepository;
import org.hiylo.scrm.repository.ScrmCustomerRepository;
import org.hiylo.scrm.repository.ScrmPlatformConfigRepository;
import org.hiylo.scrm.integration.wework.dto.WeworkExternalContactDto;
import org.hiylo.scrm.integration.wework.service.WeworkService;
import org.hiylo.scrm.integration.wework.dto.WeworkGroupChatDto;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 企业微信外部联系人同步服务
 * <p>
 * 定期从企业微信开放 API 拉取外部联系人和客户群数据，同步到 SCRM 本地数据表。
 * 同步策略为 upsert：已存在的记录更新昵称/头像，不存在的记录新建。
 * 支持批处理、API 限流、错误跟踪和干跑模式。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class WeworkContactSyncService {

    /** 企微平台类型标识 */
    private static final String PLATFORM_TYPE_WEWORK = "wework";

    /** 新客户默认生命周期 */
    private static final String LIFECYCLE_NEW = "NEW";

    /** 批处理大小：每批处理的外部联系人数 */
    private static final int BATCH_SIZE = 50;

    /** API 调用间隔（毫秒）：避免触发企微频率限制 */
    private static final long API_CALL_DELAY_MS = 100;

    /** 企微频率限制错误码 */
    private static final String ERROR_CODE_RATE_LIMIT = "45001";

    /** 频率限制重试最大次数 */
    private static final int RATE_LIMIT_MAX_RETRIES = 3;

    /** 频率限制初始退避时间（毫秒） */
    private static final long RATE_LIMIT_INITIAL_BACKOFF_MS = 5000;

    /** 企业微信平台服务（解析平台凭据并调用企微 API） */
    private final WeworkService weworkService;
    /** 客户数据访问层 */
    private final ScrmCustomerRepository customerRepository;
    /** 客户群数据访问层 */
    private final ScrmCustomerGroupRepository customerGroupRepository;
    /** 客户群成员数据访问层 */
    private final ScrmCustomerGroupMemberRepository customerGroupMemberRepository;
    /** 账号数据访问层（用于按账号归属过滤客户） */
    private final ScrmAccountRepository accountRepository;
    /** 平台配置数据访问层（获取企微应用凭据） */
    private final ScrmPlatformConfigRepository platformConfigRepository;

    /**
     * 同步外部联系人
     * <p>
     * 遍历所有企微账号，逐一拉取外部联系人列表及详情，
     * upsert 到 scrm_customer 表。支持批处理、限流和错误跟踪。
     * </p>
     *
     * @return 同步结果统计
     */
    public SyncResult syncExternalContacts() {
        return syncExternalContacts(false);
    }

    /**
     * 同步外部联系人（支持干跑模式）
     * <p>
     * 遍历所有企微账号，逐一拉取外部联系人列表及详情，
     * upsert 到 scrm_customer 表。支持批处理、限流和错误跟踪。
     * </p>
     *
     * @param dryRun   是否为干跑模式：仅统计不实际写入数据库
     * @return 同步结果统计
     */
    public SyncResult syncExternalContacts(boolean dryRun) {
                                List<ScrmAccountEntity> accounts = accountRepository.findByPlatformType(
                                         PLATFORM_TYPE_WEWORK);
        if (accounts.isEmpty()) {
            log.info("[企微同步] 无企微账号, 跳过外部联系人同步");
            return new SyncResult(0, 0, 0, 0, List.of());
        }

        SyncResult result = new SyncResult();

        for (ScrmAccountEntity account : accounts) {
            try {
                List<WeworkExternalContactDto> contacts = weworkService.getExternalContactList(
                         account.getPlatformAccountUid());
                if (contacts == null || contacts.isEmpty()) {
                    log.debug("[企微同步] 账号 {} 无外部联系人", account.getPlatformAccountUid());
                    continue;
                }

                // 批处理：每 BATCH_SIZE 个联系人作为一批
                for (int i = 0; i < contacts.size(); i += BATCH_SIZE) {
                    int end = Math.min(i + BATCH_SIZE, contacts.size());
                    List<WeworkExternalContactDto> batch = contacts.subList(i, end);
                    log.debug("[企微同步] 处理批次 {}/{}: size={}", (i / BATCH_SIZE) + 1,
                            (contacts.size() + BATCH_SIZE - 1) / BATCH_SIZE, batch.size());

                    List<ScrmCustomerEntity> toSave = new ArrayList<>();
                    for (WeworkExternalContactDto contact : batch) {
                        try {
                            WeworkExternalContactDto detail = getExternalContactDetailWithRetry(
                                     contact.getExternalUserId());
                            if (detail == null || !detail.isSuccess()) {
                                result.failedCount++;
                                result.errors.add(String.format("获取详情失败: externalUserId=%s, error=%s",
                                        contact.getExternalUserId(),
                                        detail != null ? detail.getErrorMessage() : "null"));
                                continue;
                            }
                            result.total++;

                            if (dryRun) {
                                // 干跑模式：只统计，不写入
                                Optional<ScrmCustomerEntity> existing = customerRepository
                                        .findByPlatformTypeAndPlatformCustomerUid(PLATFORM_TYPE_WEWORK,
                                                detail.getExternalUserId());
                                if (existing.isPresent()) {
                                    result.updatedCount++;
                                } else {
                                    result.newCount++;
                                }
                            } else {
                                Optional<ScrmCustomerEntity> existing = customerRepository
                                        .findByPlatformTypeAndPlatformCustomerUid(PLATFORM_TYPE_WEWORK,
                                                detail.getExternalUserId());

                                if (existing.isPresent()) {
                                    ScrmCustomerEntity entity = existing.get();
                                    boolean changed = false;
                                    if (detail.getName() != null && !detail.getName().equals(entity.getNickname())) {
                                        entity.setNickname(detail.getName());
                                        changed = true;
                                    }
                                    if (detail.getAvatar() != null && !detail.getAvatar().equals(entity.getAvatarUrl())) {
                                        entity.setAvatarUrl(detail.getAvatar());
                                        changed = true;
                                    }
                                    if (changed) {
                                        toSave.add(entity);
                                        result.updatedCount++;
                                    }
                                } else {
                                    ScrmCustomerEntity entity = new ScrmCustomerEntity();
                                    entity.setPlatformType(PLATFORM_TYPE_WEWORK);
                                    entity.setPlatformCustomerUid(detail.getExternalUserId());
                                    entity.setNickname(detail.getName());
                                    entity.setAvatarUrl(detail.getAvatar());
                                    entity.setOwnerAccountId(account.getId());
                                    entity.setLifecycle(LIFECYCLE_NEW);
                                    toSave.add(entity);
                                    result.newCount++;
                                }
                            }
                        } catch (Exception e) {
                            result.failedCount++;
                            result.errors.add(String.format("同步异常: externalUserId=%s, error=%s",
                                    contact.getExternalUserId(), e.getMessage()));
                            log.error("[企微同步] 同步外部联系人异常: externalUserId={}, error={}",
                                    contact.getExternalUserId(), e.getMessage(), e);
                        }

                        // API 调用间隔，避免触发频率限制
                        rateLimitDelay();
                    }

                    // 批量保存
                    if (!dryRun && !toSave.isEmpty()) {
                        customerRepository.saveAll(toSave);
                        log.debug("[企微同步] 批量保存 {} 条客户记录", toSave.size());
                    }
                }
            } catch (Exception e) {
                result.errors.add(String.format("拉取账号 %s 联系人列表异常: %s",
                        account.getPlatformAccountUid(), e.getMessage()));
                log.error("[企微同步] 拉取账号 {} 外部联系人列表异常: {}",
                        account.getPlatformAccountUid(), e.getMessage(), e);
            }
        }

        if (dryRun) {
            log.info("[企微同步] 干跑完成:, total={}, new={}, updated={}, failed={}", result.total, result.newCount, result.updatedCount, result.failedCount);
        } else {
            log.info("[企微同步] 同步完成: {} total, {} new, {} updated, {} failed",
                    result.total, result.newCount, result.updatedCount, result.failedCount);
        }
        return result;
    }

    /**
     * 同步外部联系人（按指定账号同步）
     * <p>
     * 仅同步指定账号的外部联系人，用于手动触发场景。
     * </p>
     *
     * @param accountId 账号 ID
     * @return 同步结果统计
     */
    public SyncResult syncExternalContactsByAccount(Long accountId) {
        return syncExternalContactsByAccount(accountId, false);
    }

    /**
     * 同步外部联系人（按指定账号同步，支持干跑模式）
     *
     * @param accountId 账号 ID
     * @param dryRun    是否为干跑模式
     * @return 同步结果统计
     */
    public SyncResult syncExternalContactsByAccount(Long accountId, boolean dryRun) {
        ScrmAccountEntity account = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("账号不存在: " + accountId));

        if (!PLATFORM_TYPE_WEWORK.equalsIgnoreCase(account.getPlatformType())) {
            throw new IllegalArgumentException("仅支持企微账号同步: " + account.getPlatformType());
        }

        SyncResult result = new SyncResult();

        try {
            List<WeworkExternalContactDto> contacts = weworkService.getExternalContactList(
                     account.getPlatformAccountUid());
            if (contacts == null || contacts.isEmpty()) {
                log.info("[企微同步] 账号 {} 无外部联系人", account.getPlatformAccountUid());
                return result;
            }

            // 批处理
            for (int i = 0; i < contacts.size(); i += BATCH_SIZE) {
                int end = Math.min(i + BATCH_SIZE, contacts.size());
                List<WeworkExternalContactDto> batch = contacts.subList(i, end);

                List<ScrmCustomerEntity> toSave = new ArrayList<>();
                for (WeworkExternalContactDto contact : batch) {
                    try {
                        WeworkExternalContactDto detail = getExternalContactDetailWithRetry(
                                 contact.getExternalUserId());
                        if (detail == null || !detail.isSuccess()) {
                            result.failedCount++;
                            result.errors.add(String.format("获取详情失败: externalUserId=%s", contact.getExternalUserId()));
                            continue;
                        }
                        result.total++;

                        if (dryRun) {
                            Optional<ScrmCustomerEntity> existing = customerRepository
                                    .findByPlatformTypeAndPlatformCustomerUid(PLATFORM_TYPE_WEWORK,
                                            detail.getExternalUserId());
                            if (existing.isPresent()) {
                                result.updatedCount++;
                            } else {
                                result.newCount++;
                            }
                        } else {
                            Optional<ScrmCustomerEntity> existing = customerRepository
                                    .findByPlatformTypeAndPlatformCustomerUid(PLATFORM_TYPE_WEWORK,
                                            detail.getExternalUserId());

                            if (existing.isPresent()) {
                                ScrmCustomerEntity entity = existing.get();
                                boolean changed = false;
                                if (detail.getName() != null && !detail.getName().equals(entity.getNickname())) {
                                    entity.setNickname(detail.getName());
                                    changed = true;
                                }
                                if (detail.getAvatar() != null && !detail.getAvatar().equals(entity.getAvatarUrl())) {
                                    entity.setAvatarUrl(detail.getAvatar());
                                    changed = true;
                                }
                                if (changed) {
                                    toSave.add(entity);
                                    result.updatedCount++;
                                }
                            } else {
                                ScrmCustomerEntity entity = new ScrmCustomerEntity();
                                entity.setPlatformType(PLATFORM_TYPE_WEWORK);
                                entity.setPlatformCustomerUid(detail.getExternalUserId());
                                entity.setNickname(detail.getName());
                                entity.setAvatarUrl(detail.getAvatar());
                                entity.setOwnerAccountId(account.getId());
                                entity.setLifecycle(LIFECYCLE_NEW);
                                toSave.add(entity);
                                result.newCount++;
                            }
                        }
                    } catch (Exception e) {
                        result.failedCount++;
                        result.errors.add(String.format("同步异常: externalUserId=%s, error=%s",
                                contact.getExternalUserId(), e.getMessage()));
                        log.error("[企微同步] 同步外部联系人异常: externalUserId={}, error={}",
                                contact.getExternalUserId(), e.getMessage(), e);
                    }

                    rateLimitDelay();
                }

                if (!dryRun && !toSave.isEmpty()) {
                    customerRepository.saveAll(toSave);
                }
            }
        } catch (Exception e) {
            result.errors.add(String.format("拉取账号 %s 联系人列表异常: %s",
                    account.getPlatformAccountUid(), e.getMessage()));
            log.error("[企微同步] 拉取账号 {} 外部联系人列表异常: {}",
                    account.getPlatformAccountUid(), e.getMessage(), e);
        }

        if (dryRun) {
            log.info("[企微同步] 干跑完成（按账号）:, accountId={}, total={}, new={}, updated={}, failed={}", accountId, result.total, result.newCount, result.updatedCount, result.failedCount);
        } else {
            log.info("[企微同步] 同步完成（按账号）:, accountId={}, total={}, new={}, updated={}, failed={}", accountId, result.total, result.newCount, result.updatedCount, result.failedCount);
        }
        return result;
    }

    /**
     * 同步客户群
     * <p>
     * 拉取企微客户群列表及详情，upsert 到 scrm_customer_group 和 scrm_customer_group_member。
     * </p>
     *
     * @return 同步结果统计
     */
    public SyncResult syncCustomerGroups() {
        return syncCustomerGroups(false);
    }

    /**
     * 同步客户群（支持干跑模式）
     *
     * @param dryRun   是否为干跑模式
     * @return 同步结果统计
     */
    public SyncResult syncCustomerGroups(boolean dryRun) {
        List<WeworkGroupChatDto> groups;
        try {
            groups = weworkService.getGroupChatList(0, 100);
        } catch (Exception e) {
            log.error("[企微同步] 拉取客户群列表失败:, error={}", e.getMessage(), e);
            SyncResult result = new SyncResult();
            result.errors.add("拉取客户群列表失败: " + e.getMessage());
            return result;
        }

        if (groups == null || groups.isEmpty()) {
            log.info("[企微同步] 无客户群数据, 跳过同步:");
            return new SyncResult();
        }

        SyncResult result = new SyncResult();
        result.total = groups.size();

        for (WeworkGroupChatDto groupSummary : groups) {
            try {
                WeworkGroupChatDto detail = weworkService.getGroupChatDetail(
                        groupSummary.getChatId());
                if (detail == null || !detail.isSuccess()) {
                    result.failedCount++;
                    result.errors.add(String.format("获取客户群详情失败: chatId=%s", groupSummary.getChatId()));
                    continue;
                }

                if (dryRun) {
                    // 干跑模式：只统计
                    Optional<ScrmCustomerGroupEntity> existingGroup = customerGroupRepository
                            .findByPlatformGroupUid(detail.getChatId());
                    if (existingGroup.isPresent()) {
                        result.updatedCount++;
                    } else {
                        result.newCount++;
                    }
                } else {
                    // 查找或创建分组（按 platformGroupUid upsert）
                    Optional<ScrmCustomerGroupEntity> existingGroup = customerGroupRepository
                            .findByPlatformGroupUid(detail.getChatId());

                    ScrmCustomerGroupEntity groupEntity;
                    if (existingGroup.isPresent()) {
                        groupEntity = existingGroup.get();
                        // 更新群名称、成员数等字段
                        if (detail.getName() != null) {
                            groupEntity.setGroupName(detail.getName());
                        }
                        if (detail.getMembers() != null) {
                            groupEntity.setMemberCount(detail.getMembers().size());
                        }
                        if (detail.getOwnerUserId() != null) {
                            groupEntity.setOwnerUserId(detail.getOwnerUserId());
                        }
                        groupEntity = customerGroupRepository.save(groupEntity);
                        updatedCountInResult(result);
                    } else {
                        groupEntity = new ScrmCustomerGroupEntity();
                        groupEntity.setPlatformType(PLATFORM_TYPE_WEWORK);
                        groupEntity.setPlatformGroupUid(detail.getChatId());
                        groupEntity.setGroupName(detail.getName());
                        groupEntity.setDescription("企微客户群: " + detail.getChatId());
                        if (detail.getMembers() != null) {
                            groupEntity.setMemberCount(detail.getMembers().size());
                        }
                        if (detail.getOwnerUserId() != null) {
                            groupEntity.setOwnerUserId(detail.getOwnerUserId());
                        }
                        // 使用第一个企微账号作为归属
                        List<ScrmAccountEntity> accounts = accountRepository.findByPlatformType(
                                PLATFORM_TYPE_WEWORK);
                        groupEntity.setOwnerAccountId(!accounts.isEmpty() ? accounts.get(0).getId() : 0L);
                        groupEntity = customerGroupRepository.save(groupEntity);
                        newCountInResult(result);
                    }

                    // 重新同步群成员：先删除旧成员，再添加新成员
                    customerGroupMemberRepository.deleteByGroupId(groupEntity.getId());

                    // 同步群成员到 scrm_customer_group_member
                    if (detail.getMembers() != null) {
                        for (WeworkGroupChatDto.GroupMember member : detail.getMembers()) {
                            // 查找或创建对应的客户记录
                            Optional<ScrmCustomerEntity> customerOpt = customerRepository
                                    .findByPlatformTypeAndPlatformCustomerUid(PLATFORM_TYPE_WEWORK, member.getUserId());

                            Long customerId;
                            if (customerOpt.isPresent()) {
                                customerId = customerOpt.get().getId();
                            } else {
                                // 自动创建客户记录
                                ScrmCustomerEntity customer = new ScrmCustomerEntity();
                                customer.setPlatformType(PLATFORM_TYPE_WEWORK);
                                customer.setPlatformCustomerUid(member.getUserId());
                                customer.setNickname(member.getUserId());
        List<ScrmAccountEntity> accounts = accountRepository.findByPlatformType(
                PLATFORM_TYPE_WEWORK);
                                customer.setOwnerAccountId(!accounts.isEmpty() ? accounts.get(0).getId() : 0L);
                                customer.setLifecycle(LIFECYCLE_NEW);
                                customer = customerRepository.save(customer);
                                customerId = customer.getId();
                            }

                            // 添加群成员关联
                            ScrmCustomerGroupMemberEntity memberEntity = new ScrmCustomerGroupMemberEntity();
                            memberEntity.setGroupId(groupEntity.getId());
                            memberEntity.setCustomerId(customerId);
                            customerGroupMemberRepository.save(memberEntity);
                        }
                    }
                }
            } catch (Exception e) {
                result.failedCount++;
                result.errors.add(String.format("同步客户群异常: chatId=%s, error=%s",
                        groupSummary.getChatId(), e.getMessage()));
                log.error("[企微同步] 同步客户群异常: chatId={}, error={}",
                        groupSummary.getChatId(), e.getMessage(), e);
            }

            rateLimitDelay();
        }

        log.info("[企微同步] 客户群同步完成:, total={}, new={}, updated={}, failed={}", result.total, result.newCount, result.updatedCount, result.failedCount);
        return result;
    }

    // ==================== 内部方法 ====================

    /**
     * 带重试的外部联系人详情获取
     * <p>
     * 如果遇到 45001（频率限制）错误码，按指数退避重试。
     * </p>
     *
     * @param externalUserId 外部联系人 ID
     * @return 详情 DTO（可能为 null 或失败状态）
     */
    private WeworkExternalContactDto getExternalContactDetailWithRetry(String externalUserId) {
        for (int attempt = 0; attempt <= RATE_LIMIT_MAX_RETRIES; attempt++) {
            try {
                WeworkExternalContactDto detail = weworkService.getExternalContactDetail(
                        externalUserId);

                // 检查是否是频率限制错误
                if (detail != null && !detail.isSuccess() && ERROR_CODE_RATE_LIMIT.equals(detail.getErrorCode())) {
                    if (attempt < RATE_LIMIT_MAX_RETRIES) {
                        long backoffMs = RATE_LIMIT_INITIAL_BACKOFF_MS * (1L << attempt);
                        log.warn("[企微同步] 遇到频率限制 (45001), 第 {} 次重试, 等待 {}ms: externalUserId={}",
                                attempt + 1, backoffMs, externalUserId);
                        sleep(backoffMs);
                        continue;
                    }
                    log.error("[企微同步] 频率限制重试耗尽: externalUserId={}", externalUserId);
                }

                return detail;
            } catch (Exception e) {
                if (attempt < RATE_LIMIT_MAX_RETRIES) {
                    long backoffMs = RATE_LIMIT_INITIAL_BACKOFF_MS * (1L << attempt);
                    log.warn("[企微同步] API 调用异常, 第 {} 次重试, 等待 {}ms: externalUserId={}, error={}",
                            attempt + 1, backoffMs, externalUserId, e.getMessage());
                    sleep(backoffMs);
                    continue;
                }
                throw e;
            }
        }
        return null;
    }

    /**
     * API 调用间隔延迟，避免触发企微频率限制
     */
    private void rateLimitDelay() {
        sleep(API_CALL_DELAY_MS);
    }

    /**
     * 安全休眠
     *
     * @param millis 休眠毫秒数
     */
    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("[企微同步] 休眠被中断");
        }
    }

    /**
     * 在 SyncResult 中递增 newCount（客户群同步用）
     */
    private void newCountInResult(SyncResult result) {
        result.newCount++;
    }

    /**
     * 在 SyncResult 中递增 updatedCount（客户群同步用）
     */
    private void updatedCountInResult(SyncResult result) {
        result.updatedCount++;
    }

    /**
     * 同步结果统计
     *
     * @author Hsi Chu
     * @since V1.0
     */
    public static class SyncResult {
        /** 本次同步的外部联系人总数 */
        private int total;
        /** 新增客户数 */
        private int newCount;
        /** 更新客户数 */
        private int updatedCount;
        /** 同步失败条数 */
        private int failedCount;
        /** 失败明细 (每行为一条错误说明) */
        private List<String> errors;

        /**
         * 无参构造，所有计数初始化为 0，错误列表为空。
         */
        public SyncResult() {
            this.total = 0;
            this.newCount = 0;
            this.updatedCount = 0;
            this.failedCount = 0;
            this.errors = new ArrayList<>();
        }

        /**
         * 三参构造，初始化总数、新增数和更新数。
         *
         * @param total        外部联系人总数
         * @param newCount     新增客户数
         * @param updatedCount 更新客户数
         */
        public SyncResult(int total, int newCount, int updatedCount) {
            this.total = total;
            this.newCount = newCount;
            this.updatedCount = updatedCount;
            this.failedCount = 0;
            this.errors = new ArrayList<>();
        }

        /**
         * 五参构造，初始化所有同步结果字段。
         *
         * @param total        外部联系人总数
         * @param newCount     新增客户数
         * @param updatedCount 更新客户数
         * @param failedCount  同步失败条数
         * @param errors       失败明细列表，可为空（空时初始化为空列表）
         */
        public SyncResult(int total, int newCount, int updatedCount, int failedCount, List<String> errors) {
            this.total = total;
            this.newCount = newCount;
            this.updatedCount = updatedCount;
            this.failedCount = failedCount;
            this.errors = errors != null ? errors : new ArrayList<>();
        }

        /**
         * 获取外部联系人总数。
         *
         * @return 总数
         */
        public int getTotal() {
            return total;
        }

        /**
         * 获取新增客户数。
         *
         * @return 新增客户数
         */
        public int getNewCount() {
            return newCount;
        }

        /**
         * 获取更新客户数。
         *
         * @return 更新客户数
         */
        public int getUpdatedCount() {
            return updatedCount;
        }

        /**
         * 获取同步失败条数。
         *
         * @return 失败条数
         */
        public int getFailedCount() {
            return failedCount;
        }

        /**
         * 获取失败明细列表。
         *
         * @return 失败明细列表
         */
        public List<String> getErrors() {
            return errors;
        }
    }
}
