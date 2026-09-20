/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCampaignService.java
 * Date : 2026/07/27 02:41:22
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.dto.ScrmCampaignDto;
import org.hiylo.scrm.dto.ScrmCampaignTemplateDto;
import org.hiylo.scrm.entity.ScrmAccountEntity;
import org.hiylo.scrm.entity.ScrmCampaignAccountEntity;
import org.hiylo.scrm.entity.ScrmCampaignEntity;
import org.hiylo.scrm.entity.ScrmCampaignTemplateEntity;
import org.hiylo.scrm.execution.TaskExecutionService;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.repository.ScrmAccountRepository;
import org.hiylo.scrm.repository.ScrmCampaignAccountRepository;
import org.hiylo.scrm.repository.ScrmCampaignRepository;
import org.hiylo.scrm.repository.ScrmCampaignTemplateRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * SCRM 营销任务服务
 * <p>
 * 负责营销任务的创建、更新、生命周期管理 (启动/暂停/恢复/停止),
 * 启动时通过 {@link TaskExecutionService} 扩展点提交执行任务, 同时维护任务-账号关联与 SOP 模板。
 * </p>
 *
 * @author Hsi Chu
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmCampaignService {

    /** 任务状态: 草稿 */
    private static final String STATUS_DRAFT = "DRAFT";
    /** 任务状态: 运行中 */
    private static final String STATUS_RUNNING = "RUNNING";
    /** 任务状态: 已暂停 */
    private static final String STATUS_PAUSED = "PAUSED";
    /** 任务状态: 已完成 */
    private static final String STATUS_COMPLETED = "COMPLETED";

    /** 任务类型: 自动发圈 (企微场景下无直接平台 SDK 发布能力, 仅记录告警) */
    private static final String TYPE_AUTO_POST = "AUTO_POST";

    /** 账号登录态: 已登录 (账号分配到任务时要求为此状态) */
    private static final String ACCOUNT_LOGIN_STATE_LOGIN = "LOGIN";

    /** 营销活动数据仓库 */
    private final ScrmCampaignRepository campaignRepository;
    /** 活动账号数据仓库 */
    private final ScrmCampaignAccountRepository campaignAccountRepository;
    /** 活动模板数据仓库 */
    private final ScrmCampaignTemplateRepository templateRepository;
    /** 账号数据访问层, 用于账号分配时校验账号存在性 / 平台一致性 / 登录态 */
    private final ScrmAccountRepository accountRepository;
    /** 营销任务执行引擎扩展点, 用于提交执行任务与触发设备编排 */
    private final TaskExecutionService taskExecutionService;

    /**
     * 创建营销任务
     * <p>
     * 默认状态为 DRAFT, 写入归属账号 ID 后持久化。
     * </p>
     *
     * @param dto 任务参数
     * @return 创建后的任务
     * @throws ScrmException 参数非法 / 权限不足
     */
    @Transactional
    public ScrmCampaignDto createCampaign(ScrmCampaignDto dto) throws ScrmException {
        if (dto.getCampaignName() == null || dto.getCampaignName().isBlank()) {
            throw ScrmException.badRequest("任务名称不能为空");
        }
        if (dto.getCampaignType() == null || dto.getCampaignType().isBlank()) {
            throw ScrmException.badRequest("任务类型不能为空");
        }
        if (dto.getPlatformType() == null || dto.getPlatformType().isBlank()) {
            throw ScrmException.badRequest("平台类型不能为空");
        }
        ScrmCampaignEntity entity = new ScrmCampaignEntity();
        entity.setCampaignName(dto.getCampaignName());
        entity.setCampaignType(dto.getCampaignType());
        entity.setPlatformType(dto.getPlatformType());
        entity.setFleetId(dto.getFleetId());
        entity.setStatus(dto.getStatus() != null ? dto.getStatus() : STATUS_DRAFT);
        entity.setCronExpression(dto.getCronExpression());
        entity.setStartTime(dto.getStartTime());
        entity.setEndTime(dto.getEndTime());
        entity.setBehaviorFlowId(dto.getBehaviorFlowId());
        entity = campaignRepository.save(entity);
        log.info("创建 SCRM 营销任务: id={}, campaignName={}, campaignType={}, platformType={}",
                entity.getId(), entity.getCampaignName(), entity.getCampaignType(), entity.getPlatformType());
        return toDto(entity);
    }

    /**
     * 更新营销任务
     *
     * @param id  任务 ID
     * @param dto 任务参数
     * @return 更新后的任务
     * @throws ScrmException 任务不存在
     */
    @Transactional
    public ScrmCampaignDto updateCampaign(Long id, ScrmCampaignDto dto) throws ScrmException {
        ScrmCampaignEntity entity = findOrThrow(id);
        // 运行中的任务不允许修改关键参数 (campaignType/platformType), 避免与执行侧任务定义不一致
        if (STATUS_RUNNING.equals(entity.getStatus())) {
            if (dto.getCampaignType() != null && !dto.getCampaignType().equals(entity.getCampaignType())) {
                throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                        "任务运行中, 不允许修改任务类型: id=" + id);
            }
            if (dto.getPlatformType() != null && !dto.getPlatformType().equals(entity.getPlatformType())) {
                throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                        "任务运行中, 不允许修改平台类型: id=" + id);
            }
        }
        if (dto.getCampaignName() != null) {
            if (dto.getCampaignName().isBlank()) {
                throw ScrmException.badRequest("任务名称不能为空");
            }
            entity.setCampaignName(dto.getCampaignName());
        }
        if (dto.getCampaignType() != null) entity.setCampaignType(dto.getCampaignType());
        if (dto.getPlatformType() != null) entity.setPlatformType(dto.getPlatformType());
        if (dto.getFleetId() != null) entity.setFleetId(dto.getFleetId());
        if (dto.getCronExpression() != null) entity.setCronExpression(dto.getCronExpression());
        if (dto.getStartTime() != null) entity.setStartTime(dto.getStartTime());
        if (dto.getEndTime() != null) entity.setEndTime(dto.getEndTime());
        entity = campaignRepository.save(entity);
        return toDto(entity);
    }

    /**
     * 查询营销任务
     *
     * @param id 任务 ID
     * @return 任务 DTO
     * @throws ScrmException 任务不存在
     */
    @Transactional(readOnly = true)
    public ScrmCampaignDto getCampaign(Long id) throws ScrmException {
        return toDto(findOrThrow(id));
    }

    /**
     * 启动营销任务
     * <p>
     * 仅 DRAFT / PAUSED 状态可启动。若任务未登记过执行任务 ID 则通过
     * {@link TaskExecutionService#submitCampaign} 提交执行任务; 返回 null (无外部执行引擎)
     * 时仅记录告警并继续将状态置为 RUNNING; 若关联了 fleetId 且执行任务 ID 非空,
     * 则通过 {@link TaskExecutionService#executeCampaign} 触发执行, 失败不阻断启动。
     * AUTO_POST 类型在企微场景下无直接平台 SDK 发布能力, 仅记录告警跳过。
     * </p>
     *
     * @param id 任务 ID
     * @return 更新后的任务
     * @throws ScrmException 任务不存在 / 状态非法
     */
    @Transactional
    public ScrmCampaignDto startCampaign(Long id) throws ScrmException {
        ScrmCampaignEntity entity = findOrThrow(id);
        if (!STATUS_DRAFT.equals(entity.getStatus()) && !STATUS_PAUSED.equals(entity.getStatus())) {
            throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                    "任务状态非法, 仅 DRAFT / PAUSED 可启动: currentStatus=" + entity.getStatus());
        }

        // 已存在执行任务 ID 时复用, 否则通过扩展点提交执行任务
        Long executionTaskId = entity.getBehaviorFlowId();
        if (executionTaskId == null) {
            executionTaskId = taskExecutionService.submitCampaign(entity);
            if (executionTaskId == null) {
                log.warn("无外部执行引擎, 任务已登记待执行: campaignId={}", id);
            } else {
                entity.setBehaviorFlowId(executionTaskId);
                log.info("执行任务已登记: campaignId={}, executionTaskId={}", id, executionTaskId);
            }
        }

        // 关联 fleetId 时触发执行 (失败不阻断启动, 行为流已登记, 可手动重试或定时调度触发)
        if (entity.getFleetId() != null && executionTaskId != null) {
            boolean dispatched = false;
            try {
                dispatched = taskExecutionService.executeCampaign(executionTaskId, entity.getFleetId());
            } catch (Exception e) {
                log.warn("执行任务触发异常 (任务仍置 RUNNING, 可手动重试): campaignId={}, executionTaskId={}",
                        id, executionTaskId, e);
            }
            if (!dispatched) {
                log.warn("执行任务未成功派发 (无外部执行引擎或 fleetId 无效): campaignId={}, executionTaskId={}, fleetId={}",
                        id, executionTaskId, entity.getFleetId());
            }
        } else {
            log.info("任务未关联 fleetId 或无执行任务 ID, 跳过执行派发 (仅登记): campaignId={}", id);
        }

        // AUTO_POST 类型在企微场景下无直接平台 SDK 发布能力, 记录告警跳过
        if (TYPE_AUTO_POST.equals(entity.getCampaignType())) {
            log.warn("AUTO_POST 任务当前未接入执行引擎, 跳过内容发布: campaignId={}", id);
        }

        entity.setStatus(STATUS_RUNNING);
        entity = campaignRepository.save(entity);
        log.info("营销任务已启动: id={}, executionTaskId={}, fleetId={}",
                id, entity.getBehaviorFlowId(), entity.getFleetId());
        return toDto(entity);
    }

    /**
     * 暂停营销任务 (状态置 PAUSED)
     *
     * @param id 任务 ID
     * @return 更新后的任务
     * @throws ScrmException 任务不存在 / 状态非法
     */
    @Transactional
    public ScrmCampaignDto pauseCampaign(Long id) throws ScrmException {
        ScrmCampaignEntity entity = findOrThrow(id);
        if (!STATUS_RUNNING.equals(entity.getStatus())) {
            throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                    "任务状态非法, 仅 RUNNING 可暂停: currentStatus=" + entity.getStatus());
        }
        entity.setStatus(STATUS_PAUSED);
        entity = campaignRepository.save(entity);
        log.info("营销任务已暂停: id={}", id);
        return toDto(entity);
    }

    /**
     * 恢复营销任务 (状态置 RUNNING)
     *
     * @param id 任务 ID
     * @return 更新后的任务
     * @throws ScrmException 任务不存在 / 状态非法
     */
    @Transactional
    public ScrmCampaignDto resumeCampaign(Long id) throws ScrmException {
        ScrmCampaignEntity entity = findOrThrow(id);
        if (!STATUS_PAUSED.equals(entity.getStatus())) {
            throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                    "任务状态非法, 仅 PAUSED 可恢复: currentStatus=" + entity.getStatus());
        }
        entity.setStatus(STATUS_RUNNING);
        entity = campaignRepository.save(entity);
        log.info("营销任务已恢复: id={}", id);
        return toDto(entity);
    }

    /**
     * 停止营销任务 (状态置 COMPLETED)
     * <p>
     * 仅 RUNNING / PAUSED 状态可停止; DRAFT 状态的任务未启动, 直接返回;
     * COMPLETED 状态幂等返回。
     * </p>
     * <p>
     * 注意: 当前实现仅修改业务侧状态, 执行侧可能仍在运行。
     * 如需停止执行侧任务, 需通过执行引擎的取消接口或等待执行自然结束。
     * </p>
     *
     * @param id 任务 ID
     * @return 更新后的任务
     * @throws ScrmException 任务不存在 / 状态非法
     */
    @Transactional
    public ScrmCampaignDto stopCampaign(Long id) throws ScrmException {
        ScrmCampaignEntity entity = findOrThrow(id);
        String currentStatus = entity.getStatus();
        // DRAFT 未启动, 无需停止
        if (STATUS_DRAFT.equals(currentStatus)) {
            return toDto(entity);
        }
        // COMPLETED 幂等返回
        if (STATUS_COMPLETED.equals(currentStatus)) {
            return toDto(entity);
        }
        // 仅 RUNNING / PAUSED 可停止
        if (!STATUS_RUNNING.equals(currentStatus) && !STATUS_PAUSED.equals(currentStatus)) {
            throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                    "任务状态非法, 仅 RUNNING / PAUSED 可停止: currentStatus=" + currentStatus);
        }
        entity.setStatus(STATUS_COMPLETED);
        entity = campaignRepository.save(entity);
        log.info("营销任务已停止: id={}, executionTaskId={} (执行侧可能仍在运行, 需手动确认)",
                id, entity.getBehaviorFlowId());
        return toDto(entity);
    }

    /**
     * 删除营销任务
     * <p>
     * 运行中的任务不允许直接删除 (需先停止), 避免执行侧任务仍在运行时业务侧记录被清除。
     * 删除时级联清理 scrm_campaign_account 关联记录, 执行日志保留用于审计追溯。
     * </p>
     * <p>
     * 注意: 若任务已关联执行任务 ID, 删除后执行侧的任务记录将变为悬空引用,
     * 当前实现仅记录告警日志, 不自动删除执行侧任务 (避免误删可能被其他任务引用的任务)。
     * </p>
     *
     * @param id 任务 ID
     * @throws ScrmException 任务不存在或任务正在运行中
     */
    @Transactional
    public void deleteCampaign(Long id) throws ScrmException {
        ScrmCampaignEntity entity = findOrThrow(id);
        if (STATUS_RUNNING.equals(entity.getStatus())) {
            throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                    "任务正在运行中, 请先停止后再删除: id=" + id);
        }
        // 告警: 关联执行任务的悬空引用
        if (entity.getBehaviorFlowId() != null) {
            log.warn("删除任务关联的执行任务将变为悬空引用, 需手动清理: campaignId={}, executionTaskId={}",
                    id, entity.getBehaviorFlowId());
        }
        // 级联清理: 删除任务与账号的关联记录
        campaignAccountRepository.deleteByCampaignId(id);
        // 删除任务主记录 (执行日志保留用于审计)
        campaignRepository.delete(entity);
        log.info("营销任务已删除: id={}, name={}", id, entity.getCampaignName());
    }

    /**
     * 批量分配账号到营销任务
     * <p>
     * 写入 scrm_campaign_account 关联表, 已存在的 accountId 跳过 (幂等)。
     * 分配前执行三项业务校验, 保证任务与账号的可用性:
     * <ul>
     *   <li>账号存在性: accountId 必须存在于 scrm_account 表</li>
     *   <li>平台一致性: 账号 platformType 必须与任务 platformType 一致 (任务 platformType 为空时跳过)</li>
     *   <li>登录态校验: 账号 loginState 必须为 LOGIN, 避免分配未登录账号导致执行失败</li>
     * </ul>
     * 单个账号校验失败跳过并记录告警, 不阻断其他账号分配。
     * </p>
     *
     * @param campaignId 任务 ID
     * @param accountIds 账号 ID 列表
     * @return 当前任务关联的账号 ID 列表 (含历史已分配)
     * @throws ScrmException 任务不存在 / accountIds 为空
     */
    @Transactional
    public List<Long> assignAccounts(Long campaignId, List<Long> accountIds) throws ScrmException {
        ScrmCampaignEntity campaign = findOrThrow(campaignId);
        if (accountIds == null || accountIds.isEmpty()) {
            throw ScrmException.badRequest("账号 ID 列表不能为空");
        }
        List<Long> existing = campaignAccountRepository.findByCampaignId(campaignId).stream()
                .map(ScrmCampaignAccountEntity::getAccountId)
                .collect(Collectors.toList());
        int added = 0;
        for (Long accountId : accountIds) {
            if (accountId == null) {
                continue;
            }
            if (existing.contains(accountId)) {
                log.debug("账号已分配到任务, 跳过: campaignId={}, accountId={}", campaignId, accountId);
                continue;
            }
            // 校验账号存在性 + 数据隔离: 账号必须归属当前账号
            ScrmAccountEntity account = accountRepository.findById(accountId).orElse(null);
            if (account == null) {
                log.warn("账号不存在, 跳过分配: campaignId={}, accountId={}", campaignId, accountId);
                continue;
            }

            // 校验平台一致性 (任务 platformType 为空时跳过, 兼容旧数据)
            if (campaign.getPlatformType() != null && !campaign.getPlatformType().isBlank() && account.getPlatformType() != null && !campaign.getPlatformType().equalsIgnoreCase(account.getPlatformType())) {
                log.warn("账号平台与任务平台不一致, 跳过分配: campaignId={}, taskPlatform={}, accountPlatform={}, accountId={}",
                        campaignId, campaign.getPlatformType(), account.getPlatformType(), accountId);
                continue;
            }
            // 校验登录态 (LOGIN 才允许分配, 避免执行时未登录导致失败)
            if (!ACCOUNT_LOGIN_STATE_LOGIN.equals(account.getLoginState())) {
                log.warn("账号未登录, 跳过分配: campaignId={}, accountId={}, loginState={}",
                        campaignId, accountId, account.getLoginState());
                continue;
            }
            ScrmCampaignAccountEntity rel = new ScrmCampaignAccountEntity();
            rel.setCampaignId(campaignId);
            rel.setAccountId(accountId);
            campaignAccountRepository.save(rel);
            existing.add(accountId);
            added++;
        }
        log.info("分配账号到任务: campaignId={}, requested={}, added={}, total={}",
                campaignId, accountIds.size(), added, existing.size());
        return existing;
    }

    /**
     * 批量移除营销任务的账号分配
     * <p>
     * 删除 scrm_campaign_account 关联记录, 不存在的 accountId 静默跳过 (幂等)。
     * 运行中的任务允许移除账号 (执行侧由行为流自行处理在线账号),
     * 但已开始的执行不会中断。
     * </p>
     *
     * @param campaignId 任务 ID
     * @param accountIds 账号 ID 列表
     * @return 移除后任务剩余的账号 ID 列表
     * @throws ScrmException 任务不存在 / accountIds 为空
     */
    @Transactional
    public List<Long> unassignAccounts(Long campaignId, List<Long> accountIds) throws ScrmException {
        findOrThrow(campaignId);
        if (accountIds == null || accountIds.isEmpty()) {
            throw ScrmException.badRequest("账号 ID 列表不能为空");
        }
        campaignAccountRepository.deleteByCampaignIdAndAccountIdIn(campaignId, accountIds);
        List<Long> remaining = campaignAccountRepository.findByCampaignId(campaignId).stream()
                .map(ScrmCampaignAccountEntity::getAccountId)
                .collect(Collectors.toList());
        log.info("移除任务账号分配: campaignId={}, requested={}, remaining={}",
                campaignId, accountIds.size(), remaining.size());
        return remaining;
    }

    /**
     * 查询营销任务已分配的账号 ID 列表
     *
     * @param campaignId 任务 ID
     * @return 账号 ID 列表 (任务不存在时返回空列表)
     * @throws ScrmException 任务不存在
     */
    @Transactional(readOnly = true)
    public List<Long> getAssignedAccountIds(Long campaignId) throws ScrmException {
        findOrThrow(campaignId);
        return campaignAccountRepository.findByCampaignId(campaignId).stream()
                .map(ScrmCampaignAccountEntity::getAccountId)
                .collect(Collectors.toList());
    }

    /**
     * 分页查询营销任务, 支持按状态、平台类型、任务类型与关键词过滤
     * <p>
     * 使用 JPA Specification 在数据库层完成过滤, 避免全表加载。
     * </p>
     *
     * @param status       任务状态过滤 (可空)
     * @param platformType 平台类型过滤 (可空)
     * @param campaignType 任务类型过滤 (可空)
     * @param keyword      关键词过滤, 匹配任务名称 (可空)
     * @param page         页码 (从 0 开始)
     * @param size         每页大小
     * @return 任务分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmCampaignDto> listCampaigns(String status, String platformType,
                                                String campaignType, String keyword,
                                                int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createTime"));
        Specification<ScrmCampaignEntity> spec = buildCampaignSpec(status, platformType, campaignType, keyword);
        Page<ScrmCampaignEntity> entities = campaignRepository.findAll(spec, pageable);
        return entities.map(this::toDto);
    }

    /**
     * 构建营销任务查询条件 Specification
     * <p>
     * 数据隔离: 始终按当前用户可见账号范围过滤。
     * </p>
     */
    private Specification<ScrmCampaignEntity> buildCampaignSpec(String status, String platformType,
                                                                 String campaignType, String keyword) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            // 数据隔离: 始终按当前用户可见账号范围过滤
            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(cb.lower(root.get("status")), status.toLowerCase()));
            }
            if (platformType != null && !platformType.isBlank()) {
                predicates.add(cb.equal(cb.lower(root.get("platformType")), platformType.toLowerCase()));
            }
            if (campaignType != null && !campaignType.isBlank()) {
                predicates.add(cb.equal(cb.lower(root.get("campaignType")), campaignType.toLowerCase()));
            }
            if (keyword != null && !keyword.isBlank()) {
                String kw = "%" + keyword.toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("campaignName")), kw),
                        cb.like(cb.lower(root.get("campaignType")), kw)
                ));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * 创建 SOP 模板
     *
     * @param dto 模板参数
     * @return 创建后的模板
     */
    @Transactional
    public ScrmCampaignTemplateDto createTemplate(ScrmCampaignTemplateDto dto) {
        ScrmCampaignTemplateEntity entity = new ScrmCampaignTemplateEntity();
        entity.setTemplateName(dto.getTemplateName());
        entity.setCampaignType(dto.getCampaignType());
        entity.setPlatformType(dto.getPlatformType());
        entity.setTemplateContent(dto.getTemplateContent());
        entity.setDescription(dto.getDescription());
        entity = templateRepository.save(entity);
        log.info("创建 SOP 模板: id={}, templateName={}, campaignType={}",
                entity.getId(), entity.getTemplateName(), entity.getCampaignType());
        return toTemplateDto(entity);
    }

    /**
     * 查询 SOP 模板列表, 支持按任务类型与平台类型过滤
     *
     * @param campaignType 任务类型过滤 (可空)
     * @param platformType 平台类型过滤 (可空)
     * @return 模板列表
     */
    @Transactional(readOnly = true)
    public List<ScrmCampaignTemplateDto> listTemplates(String campaignType, String platformType) {
        Specification<ScrmCampaignTemplateEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            // 数据隔离: 始终按当前用户可见账号范围过滤
            if (campaignType != null && !campaignType.isBlank()) {
                predicates.add(cb.equal(cb.lower(root.get("campaignType")), campaignType.toLowerCase()));
            }
            if (platformType != null && !platformType.isBlank()) {
                predicates.add(cb.equal(cb.lower(root.get("platformType")), platformType.toLowerCase()));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return templateRepository.findAll(spec).stream()
                .map(this::toTemplateDto)
                .collect(Collectors.toList());
    }

    // ============================================================
    // 内部工具方法
    // ============================================================

    /**
     * 按主键查询任务, 不存在抛异常
     */
    private ScrmCampaignEntity findOrThrow(Long id) throws ScrmException {
        ScrmCampaignEntity entity = campaignRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "营销任务不存在: id=" + id));
        // 数据隔离: 校验任务归属当前账号, 防止按 ID 越权访问

        return entity;
    }

    /**
     * 任务实体转 DTO
     */
    private ScrmCampaignDto toDto(ScrmCampaignEntity entity) {
        ScrmCampaignDto dto = new ScrmCampaignDto();
        dto.setId(entity.getId());
        dto.setCampaignName(entity.getCampaignName());
        dto.setCampaignType(entity.getCampaignType());
        dto.setPlatformType(entity.getPlatformType());
        dto.setFleetId(entity.getFleetId());
        dto.setStatus(entity.getStatus());
        dto.setCronExpression(entity.getCronExpression());
        dto.setStartTime(entity.getStartTime());
        dto.setEndTime(entity.getEndTime());
        dto.setBehaviorFlowId(entity.getBehaviorFlowId());
        dto.setCreateTime(entity.getCreateTime());
        dto.setUpdateTime(entity.getUpdateTime());
        dto.setVersion(entity.getVersion());
        return dto;
    }

    /**
     * 模板实体转 DTO
     */
    private ScrmCampaignTemplateDto toTemplateDto(ScrmCampaignTemplateEntity entity) {
        ScrmCampaignTemplateDto dto = new ScrmCampaignTemplateDto();
        dto.setId(entity.getId());
        dto.setTemplateName(entity.getTemplateName());
        dto.setCampaignType(entity.getCampaignType());
        dto.setPlatformType(entity.getPlatformType());
        dto.setTemplateContent(entity.getTemplateContent());
        dto.setDescription(entity.getDescription());
        dto.setCreateTime(entity.getCreateTime());
        dto.setUpdateTime(entity.getUpdateTime());
        dto.setVersion(entity.getVersion());
        return dto;
    }
}
