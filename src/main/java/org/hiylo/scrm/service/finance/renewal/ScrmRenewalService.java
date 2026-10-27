/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm-finance
 * File : ScrmRenewalService.java
 * Date : 2026/10/23 12:30:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service.finance.renewal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.config.ScrmRenewalProperties;
import org.hiylo.scrm.dto.ScrmCareTaskDto;
import org.hiylo.scrm.dto.ScrmFollowUpTaskDto;
import org.hiylo.scrm.dto.ScrmNotificationSendDto;
import org.hiylo.scrm.dto.ScrmRenewalTaskDto;
import org.hiylo.scrm.entity.ScrmChurnRuleEntity;
import org.hiylo.scrm.entity.ScrmChurnWarningEntity;
import org.hiylo.scrm.entity.ScrmContractEntity;
import org.hiylo.scrm.entity.ScrmCustomerEntity;
import org.hiylo.scrm.entity.ScrmRenewalTaskEntity;
import org.hiylo.scrm.entity.ScrmTaxDeclarationRecordEntity;
import org.hiylo.scrm.repository.ScrmTaxDeclarationRecordRepository;
import org.hiylo.scrm.service.finance.tax.ScrmTaxDeclarationService;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.service.CompanyResolver;
import org.hiylo.scrm.repository.ScrmChurnRuleRepository;
import org.hiylo.scrm.repository.ScrmChurnWarningRepository;
import org.hiylo.scrm.repository.ScrmContractRepository;
import org.hiylo.scrm.repository.ScrmConversationRepository;
import org.hiylo.scrm.repository.ScrmCustomerRepository;
import org.hiylo.scrm.repository.ScrmFeedbackRepository;
import org.hiylo.scrm.repository.ScrmInvoiceRepository;
import org.hiylo.scrm.repository.ScrmNotificationRepository;
import org.hiylo.scrm.repository.ScrmRenewalTaskRepository;
import org.hiylo.scrm.service.ScrmCustomerCareTaskService;
import org.hiylo.scrm.service.ScrmFollowUpService;
import org.hiylo.scrm.service.ScrmNotificationCenterService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 服务到期续费 + 流失预警服务（续费流失域核心, T08B）。
 * <p>
 * 职责: 到期扫描（每日 08:00, ACTIVE 合同 endDate ∈ [今天+3, 今天+30] 且
 * autoRenew=false 且无未完成任务）→ 幂等生成续费任务（每合同仅一条待办）→
 * 30/7/3 三级提醒（NotificationCenter 推销售, 未跟进时升级提醒销售+主管）→
 * 续费结果录入（RENEWED/LOST/CANCELLED, 写回合同 renewalOfId/renewedToId）→
 * 到期未续自动转 OVERDUE 并触发流失评分。流失评分最小实现（复用 ChurnWarning）:
 * 三输入（近 14 天对话频次 + 服务停用信号[无开票] + 负面反馈[差评/投诉工单]）
 * 加权 0-100 分, 评级 HIGH≥75 / MEDIUM≥50 / LOW, HIGH/MEDIUM 自动生成挽回事宜
 * （ChurnRecovery 语义由 {@code ScrmChurnWarningService.createRecovery} 承接,
 * 本服务生成跟进任务 + APP 提醒）。
 * </p>
 * <p>
 * 推送幂等: 同一任务同一提醒节点只推一次（relatedType=RENEWAL_TASK +
 * relatedId=任务ID+节点）。折扣/赠期审批已接入 Approval 引擎:
 * {@code renewalOption != STANDARD} 时任务置 APPROVAL_PENDING 并自动发起审批实例
 * （见 {@code RenewalApprovalGateway}），审批通过/驳回由
 * {@code ScrmRenewalApprovalListener} 回调本服务回填或退回。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScrmRenewalService {

    // ==================== 状态常量 ====================

    /** 任务状态: 待处理 */
    public static final String STATUS_PENDING = "PENDING";
    /** 任务状态: 审批中 */
    public static final String STATUS_APPROVAL_PENDING = "APPROVAL_PENDING";
    /** 任务状态: 已审批通过 */
    public static final String STATUS_APPROVED = "APPROVED";
    /** 任务状态: 跟进中 */
    public static final String STATUS_IN_PROGRESS = "IN_PROGRESS";
    /** 任务状态: 已续约 */
    public static final String STATUS_RENEWED = "RENEWED";
    /** 任务状态: 已流失（放弃/到期未续） */
    public static final String STATUS_LOST = "LOST";
    /** 任务状态: 已逾期（到期后仍未处理） */
    public static final String STATUS_OVERDUE = "OVERDUE";
    /** 任务状态: 已取消（合同终止） */
    public static final String STATUS_CANCELLED = "CANCELLED";

    /** 续费选项: 标准价 */
    public static final String OPTION_STANDARD = "STANDARD";
    /** 续费选项: 折扣 */
    public static final String OPTION_DISCOUNT = "DISCOUNT";
    /** 续费选项: 赠期 */
    public static final String OPTION_GRACE = "GRACE";
    /** 续费选项: 放弃 */
    public static final String OPTION_CANCEL = "CANCEL";

    // ==================== 推送常量 ====================

    /** 推送关联类型: 续费任务（幂等键） */
    private static final String RELATED_TYPE_RENEWAL_TASK = "RENEWAL_TASK";

    /** 推送发送者 ID */
    private static final String SENDER_ID = "renewal-system";

    /** 跟进类型: 续费跟进（FollowUpTask taskType 需在既有合法集合内, 用 OTHER 兜底） */
    private static final String FOLLOWUP_TASK_TYPE = "OTHER";

    /** 关怀类型: 续费关怀（CareTask careType 需在既有合法集合内, 用 CUSTOM 兜底） */
    private static final String CARE_TYPE_RENEWAL = "CUSTOM";

    // ==================== 依赖 ====================

    /** 合同数据访问层 */
    private final ScrmContractRepository contractRepository;

    /** 续费任务数据访问层 */
    private final ScrmRenewalTaskRepository renewalTaskRepository;

    /** 客户数据访问层（客户名称补全） */
    private final ScrmCustomerRepository customerRepository;

    /** 公司主体归属解析器（业务数据自动挂公司） */
    private final CompanyResolver companyResolver;

    /** 财税数据范围（会计只看到自己负责主体的续费任务） */
    private final org.hiylo.scrm.service.finance.FinanceDataScope financeDataScope;

    /** 通知中心门面（站内信推送） */
    private final ScrmNotificationCenterService notificationCenterService;

    /** 通知记录数据访问层（推送幂等去重） */
    private final ScrmNotificationRepository notificationRepository;

    /** 跟进任务服务（挂跟进待办） */
    private final ScrmFollowUpService followUpService;

    /** 关怀任务服务（挂关怀待办） */
    private final ScrmCustomerCareTaskService careTaskService;

    /** 流失预警仓库（写 ChurnWarning） */
    private final ScrmChurnWarningRepository churnWarningRepository;

    /** 流失规则仓库（读取财税流失规则） */
    private final ScrmChurnRuleRepository churnRuleRepository;

    /** 会话仓库（对话频次因子） */
    private final ScrmConversationRepository conversationRepository;

    /** 发票仓库（服务停用信号因子） */
    private final ScrmInvoiceRepository invoiceRepository;

    /** 反馈仓库（负面反馈因子） */
    private final ScrmFeedbackRepository feedbackRepository;

    /** 续费配置 */
    private final ScrmRenewalProperties properties;

    /** 税务申报记录仓储（申报停滞流失因子） */
    private final ScrmTaxDeclarationRecordRepository taxDeclarationRecordRepository;

    /**
     * 续费审批网关（可空：审批域未装配时续费仍可正常工作，
     * 只是非标准续费需人工在审批中心补建）。
     * 用 ObjectProvider 而非直接注入，避免与审批域形成构造期循环依赖。
     */
    private final org.springframework.beans.factory.ObjectProvider<RenewalApprovalGateway>
            renewalApprovalGatewayProvider;

    // ============================================================
    // 到期扫描 + 任务生成（调度入口, 每日 08:00）
    // ============================================================

    /**
     * 到期扫描并生成续费任务（幂等: 同一合同仅一条待办）。
     * <p>扫描 ACTIVE 合同（endDate ∈ [今天+3, 今天+30], autoRenew=false,
     * 无未完成任务）, 每合同生成一条 {@code ScrmRenewalTask}, 生成即挂一条跟进
     * 任务 + 推送销售第一提醒（30 天）。</p>
     *
     * @return 生成任务数
     */
    @Transactional
    public int scanAndGenerateTasks() {
        LocalDate today = LocalDate.now();
        LocalDate from = today.plusDays(3);
        LocalDate to = today.plusDays(30);
        List<ScrmContractEntity> contracts = contractRepository
                .findByStatusInAndEndDateLessThanEqual(List.of("ACTIVE"), to).stream()
                .filter(c -> !c.getEndDate().isBefore(from))
                .filter(c -> !Boolean.TRUE.equals(c.getAutoRenew()))
                .collect(Collectors.toList());
        int created = 0;
        for (ScrmContractEntity contract : contracts) {
            try {
                if (createTaskIfMissing(contract)) {
                    created++;
                }
            } catch (Exception e) {
                log.warn("[续费-扫描] 续费任务生成失败, 跳过: contractId={}, err={}",
                        contract.getId(), e.getMessage());
            }
        }
        log.info("[续费-扫描] 到期扫描完成: contracts={}, created={}", contracts.size(), created);
        return created;
    }

    /**
     * 到期未续转 OVERDUE 并触发流失评分（调度入口, 与扫描同频）。
     * <p>到期日已过且仍 PENDING/APPROVAL_PENDING 的任务 → OVERDUE; 到期后
     * lostAfterDays 天仍 OVERDUE → LOST + 生命周期保持待人工确认 + 触发流失评分
     * （近 14 天对话 + 无开票 + 负面反馈三输入）。</p>
     *
     * @return 转 OVERDUE 数
     */
    @Transactional
    public int markExpiredTasks() {
        LocalDate today = LocalDate.now();
        int marked = 0;
        for (String status : List.of(STATUS_PENDING, STATUS_APPROVAL_PENDING, STATUS_APPROVED)) {
            for (ScrmRenewalTaskEntity task : renewalTaskRepository.findByStatus(status)) {
                if (task.getPlannedRenewalDate() != null
                        && task.getPlannedRenewalDate().isBefore(today)) {
                    task.setStatus(STATUS_OVERDUE);
                    renewalTaskRepository.save(task);
                    marked++;
                    if (task.getPlannedRenewalDate().plusDays(properties.getLostAfterDays())
                            .isBefore(today)) {
                        markLost(task);
                    }
                }
            }
        }
        log.info("[续费-逾期] 到期未续任务处理完成: marked={}", marked);
        return marked;
    }

    // ============================================================
    // 提醒节奏（调度入口, 每日 09:00）
    // ============================================================

    /**
     * 三级提醒推送（30/7/3 天, 未跟进时升级提醒）。
     * <p>对 PENDING/OVERDUE 任务按到期剩余天数匹配节奏: 30 天首推（任务生成即推）,
     * 7 天二次提醒（未跟进）, 3 天升级提醒（通知销售+主管）。幂等: 同一任务同一节点
     * 只推一次。</p>
     *
     * @return 推送数
     */
    @Transactional
    public int pushReminders() {
        LocalDate today = LocalDate.now();
        int pushed = 0;
        for (ScrmRenewalTaskEntity task : renewalTaskRepository.findAll()) {
            if (!isActive(task.getStatus())) {
                continue;
            }
            if (task.getPlannedRenewalDate() == null) {
                continue;
            }
            long daysLeft = java.time.temporal.ChronoUnit.DAYS.between(today, task.getPlannedRenewalDate());
            for (int day : parseRemindDays()) {
                if (daysLeft == day) {
                    String node = "remind-d" + day;
                    String desc = day == 30 ? "首推" : (day == 7 ? "二次提醒" : "升级提醒");
                    if (pushOnce(task, node, desc, String.valueOf(daysLeft), day <= 3 ? 2 : 1)) {
                        pushed++;
                    }
                }
            }
            // 到期当天（未处理）再推一次销售
            if (daysLeft == 0 && pushOnce(task, "due-today", "今日到期", "0", 2)) {
                pushed++;
            }
        }
        log.info("[续费-提醒] 提醒推送完成: pushed={}", pushed);
        return pushed;
    }

    // ============================================================
    // 任务管理（CRUD + 结果录入）
    // ============================================================

    /**
     * 手动创建续费任务（MANUAL 来源）。
     *
     * @param dto 任务参数
     * @return 创建后的任务
     * @throws ScrmException 参数非法 / 合同不存在 / 已有待办
     */
    @Transactional
    public ScrmRenewalTaskEntity createTask(ScrmRenewalTaskDto dto) throws ScrmException {
        if (dto == null || dto.getContractId() == null) {
            throw ScrmException.badRequest("任务参数与合同 ID 不能为空");
        }
        ScrmContractEntity contract = contractRepository.findById(dto.getContractId())
                .orElseThrow(() -> ScrmException.notFound("合同不存在: id=" + dto.getContractId()));
        if (renewalTaskRepository.findFirstPendingByContractId(contract.getId()).isPresent()) {
            throw ScrmException.conflict("该合同已有未完成的续费任务");
        }
        ScrmRenewalTaskEntity entity = new ScrmRenewalTaskEntity();
        entity.setTaskNo(generateTaskNo());
        entity.setContractId(contract.getId());
        entity.setContractNo(contract.getContractNo());
        entity.setContractName(contract.getContractName());
        entity.setCustomerId(contract.getCustomerId());
        entity.setCustomerName(contract.getCustomerName());
        // 业务数据自动挂公司（合同联系人已归属公司时自动带上）
        entity.setCompanyId(companyResolver.resolveByCustomerId(contract.getCustomerId()));
        entity.setSalesPersonId(contract.getSalesPersonId());
        entity.setSalesPersonName(contract.getSalesPersonName());
        entity.setPlannedRenewalDate(dto.getPlannedRenewalDate() != null
                ? dto.getPlannedRenewalDate() : contract.getEndDate());
        entity.setRemindDaysBefore(dto.getRemindDaysBefore() != null
                ? dto.getRemindDaysBefore() : 30);
        applyOption(entity, dto.getRenewalOption());
        // 折扣/赠期是「申请值」：必须在创建时就落库，否则审批人看不到要批的是什么。
        // 此前只允许在 approve() 里回填，导致申请与审批信息脱节。
        entity.setDiscountPct(dto.getDiscountPct());
        entity.setGraceMonths(dto.getGraceMonths());
        entity.setRenewalAmount(dto.getRenewalAmount());
        entity.setSource(dto.getSource() != null ? dto.getSource() : "MANUAL");
        if (entity.getStatus() == null) {
            entity.setStatus(STATUS_PENDING);
        }
        entity.setRemindCount(0);
        entity.setNotes(dto.getNotes());
        entity.setCreatedBy(operator());
        entity = renewalTaskRepository.save(entity);
        log.info("[续费-任务] 创建: id={}, contractId={}, option={}",
                entity.getId(), entity.getContractId(), entity.getRenewalOption());
        // 非标准续费（折扣/赠期）需审批：保存后自动发起审批实例并回写关联 ID。
        // 审批域未装配或没配 RENEWAL 流程时返回 null，不影响任务创建。
        if (Boolean.TRUE.equals(entity.getApprovalRequired())) {
            attachApproval(entity);
        }
        return entity;
    }

    /**
     * 续费结果录入（RENEWED 写回合同续约关联 / LOST 触发流失 / CANCELLED）。
     *
     * @param id     任务 ID
     * @param option 续费选项
     * @param amount 续约价（RENEWED 时回填）
     * @param notes  备注
     * @return 更新后的任务
     * @throws ScrmException 任务不存在 / 状态不允许
     */
    @Transactional
    public ScrmRenewalTaskEntity recordResult(Long id, String option, java.math.BigDecimal amount,
                                              String notes) throws ScrmException {
        ScrmRenewalTaskEntity task = findTaskOrThrow(id);
        if (!isActive(task.getStatus())) {
            throw ScrmException.conflict("任务已终态, 不可录入结果: id=" + id + ", status=" + task.getStatus());
        }
        if (option == null || option.isBlank()) {
            throw ScrmException.badRequest("续费结果不能为空");
        }
        if (STATUS_RENEWED.equals(option)) {
            task.setStatus(STATUS_RENEWED);
            task.setRenewalAmount(amount != null ? amount : task.getRenewalAmount());
            // 写回合同续约关联（若存在续约合同由合同域维护, 此处仅标记）
            ScrmContractEntity contract = contractRepository.findById(task.getContractId()).orElse(null);
            if (contract != null && !Boolean.TRUE.equals(contract.getAutoRenew())) {
                log.info("[续费-结果] 续约成功, 待合同域建新合同后写回 renewedToId: taskId={}, contractId={}",
                        task.getId(), task.getContractId());
            }
        } else if (STATUS_LOST.equals(option)) {
            task.setStatus(STATUS_LOST);
            triggerChurnScan(task.getCustomerId());
        } else if (STATUS_CANCELLED.equals(option)) {
            task.setStatus(STATUS_CANCELLED);
        } else {
            throw ScrmException.badRequest("续费结果仅支持 RENEWED / LOST / CANCELLED");
        }
        if (notes != null) {
            task.setNotes(notes);
        }
        task = renewalTaskRepository.save(task);
        log.info("[续费-结果] 录入结果: id={}, option={}", id, option);
        return task;
    }

    /**
     * 审批通过回填（Approval 引擎接线: 折扣/赠期审批通过后调用）。
     *
     * @param id           任务 ID
     * @param discountPct  折扣比例
     * @param graceMonths  赠期月数
     * @param amount       续约价
     * @param approvalInstanceId 审批实例 ID
     * @return 更新后的任务
     * @throws ScrmException 任务不存在 / 状态不允许
     */
    @Transactional
    public ScrmRenewalTaskEntity approve(Long id, java.math.BigDecimal discountPct,
                                         Integer graceMonths, java.math.BigDecimal amount,
                                         Long approvalInstanceId) throws ScrmException {
        ScrmRenewalTaskEntity task = findTaskOrThrow(id);
        if (!STATUS_APPROVAL_PENDING.equals(task.getStatus())) {
            throw ScrmException.conflict("仅 APPROVAL_PENDING 状态可审批通过: id=" + id
                    + ", status=" + task.getStatus());
        }
        if (discountPct != null) {
            task.setDiscountPct(discountPct);
        }
        if (graceMonths != null) {
            task.setGraceMonths(graceMonths);
        }
        if (amount != null) {
            task.setRenewalAmount(amount);
        }
        task.setApprovalInstanceId(approvalInstanceId);
        // 审批已完成，复位「需审批」标记，避免已批准任务仍被当作待审批拦截
        task.setApprovalRequired(false);
        task.setStatus(STATUS_APPROVED);
        task = renewalTaskRepository.save(task);
        log.info("[续费-审批] 审批通过: id={}, amount={}, discount={}, grace={}",
                id, amount, discountPct, graceMonths);
        return task;
    }

    /**
     * 查询任务详情。
     *
     * @param id 任务 ID
     * @return 任务实体
     * @throws ScrmException 任务不存在
     */
    public ScrmRenewalTaskEntity getTask(Long id) throws ScrmException {
        return findTaskOrThrow(id);
    }

    /**
     * 分页查询续费任务。
     * <p>
     * 支持状态、客户、公司主体三个过滤维度。公司维度是代账场景的主查询路径——
     * 按联系人查会随对接人变更而丢失历史，按主体查才是稳定的业务口径。
     * </p>
     *
     * @param status     状态过滤（可空）
     * @param customerId 客户过滤（可空）
     * @param companyId  公司主体过滤（可空）
     * @param pageable   分页参数
     * @return 任务分页结果
     */
    public Page<ScrmRenewalTaskEntity> listTasks(String status, Long customerId, Long companyId,
                                                  Pageable pageable) {
        // 财税数据范围：会计只能看到自己负责主体的续费任务
        List<Long> accessibleCompanyIds = financeDataScope.resolveAccessibleCompanyIds();
        return renewalTaskRepository.findAll((root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            if (accessibleCompanyIds != null) {
                if (accessibleCompanyIds.isEmpty()) {
                    predicates.add(cb.isFalse(cb.literal(true)));
                } else {
                    predicates.add(root.get("companyId").in(accessibleCompanyIds));
                }
            }
            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (customerId != null) {
                predicates.add(cb.equal(root.get("customerId"), customerId));
            }
            if (companyId != null) {
                predicates.add(cb.equal(root.get("companyId"), companyId));
            }
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        }, pageable);
    }

    // ============================================================
    // 流失评分最小实现（复用 ChurnWarning）
    // ============================================================

    /**
     * 流失评分最小实现（T08B §3）: 三输入加权 0-100 分, 写 ChurnWarning。
     * <p>输入: ① 近 14 天会话消息数=0 → 满分（30% 权重, 对话频次）;
     * ② 无开票记录（近 60 天发票数=0, 服务停用信号, 35% 权重）;
     * ③ 负面反馈（差评 rating≤2 / 近 30 天负面关键词, 20% 权重）;
     * ④ 生命周期异常（到期未续任务 OVERDUE, 15% 权重, 首期由本服务标记）。
     * 评级: ≥75 HIGH / ≥50 MEDIUM / 其余 LOW。HIGH/MEDIUM 生成跟进任务 + APP 提醒;
     * 冷却复用 ChurnWarning ACTIVE 去重（同客户已有 ACTIVE 预警不重复建）。
     * 申报停滞（T08A ≥1 期逾期）二期接入。</p>
     *
     * @param customerId 客户 ID
     * @return 评分结果（null 表示不命中）
     */
    @Transactional
    public ScrmChurnWarningEntity scanChurn(Long customerId) {
        if (customerId == null) {
            return null;
        }
        try {
            Optional<ScrmChurnWarningEntity> active = churnWarningRepository
                    .findFirstByCustomerIdAndStatusAndDetectedAtAfterOrderByDetectedAtDesc(
                            customerId, "ACTIVE", LocalDateTime.now().minusDays(7));
            if (active.isPresent()) {
                return null; // 冷却期, 不重复打扰
            }
            double score = computeChurnScore(customerId);
            if (score < properties.getMediumRiskThreshold()) {
                log.debug("[续费-流失] 风险分低于 MEDIUM 阈值, 仅观察: customerId={}, score={}",
                        customerId, score);
                return null;
            }
            ScrmCustomerEntity customer = customerRepository.findById(customerId).orElse(null);
            String level = score >= properties.getHighRiskThreshold() ? "HIGH" : "MEDIUM";
            ScrmChurnRuleEntity rule = churnRuleRepository.findByEnabledTrueOrderByPriorityAsc()
                    .stream().findFirst().orElse(null);
            ScrmChurnWarningEntity warning = new ScrmChurnWarningEntity();
            warning.setCustomerId(customerId);
            warning.setCustomerName(customer != null ? customer.getNickname() : "客户#" + customerId);
            warning.setRuleId(rule != null ? rule.getId() : -1L);
            warning.setRuleName(rule != null ? rule.getRuleName() : "财税服务流失评分(v1)");
            warning.setRiskLevel(level);
            warning.setRiskScore(score);
            warning.setRiskFactors(buildRiskFactors(customerId));
            warning.setStatus("ACTIVE");
            warning.setActionType("NOTIFY_ASSIGNEE");
            warning.setDetectedAt(LocalDateTime.now());
            warning.setAssigneeId(resolveSalesOf(customerId));
            warning = churnWarningRepository.save(warning);
            // HIGH/MEDIUM 生成跟进任务 + APP 提醒
            createChurnFollowUp(warning);
            log.info("[续费-流失] 流失预警创建: customerId={}, level={}, score={}",
                    customerId, level, score);
            return warning;
        } catch (Exception e) {
            log.warn("[续费-流失] 流失评分失败 (不影响主流程): customerId={}, err={}",
                    customerId, e.getMessage());
            return null;
        }
    }

    /**
     * 计算流失风险分（五输入加权）。
     * <p>
     * 权重设计依据（合计必须为 1.0）：
     * <ul>
     *   <li><b>无开票 30%</b>：服务实质停用的最强信号，但存在季节性淡季误报，故不给最高；
     *       原为 35%，本次让出 5 个点给申报停滞。</li>
     *   <li><b>对话频次 25%</b>：沟通意愿，噪声较大（客户可能改用其他渠道）；
     *       原为 30%，让出 5 个点。</li>
     *   <li><b>负面反馈 20%</b>：满意度直接信号，维持不变。</li>
     *   <li><b>申报停滞 15%</b>：代账业务的硬信号——客户不再让你报税基本等于流失，
     *       比"聊得少"更能反映真实去留；权重低于无开票是因为它往往与无开票同向，
     *       给太高会造成重复计分。</li>
     *   <li><b>续费异常 10%</b>：与申报停滞高度相关（都是"该办的事没办"），
     *       故从原 15% 下调，避免两个同向因子叠加放大。</li>
     * </ul>
     * </p>
     *
     * @param customerId 客户 ID
     * @return 0-100 分
     */
    private double computeChurnScore(Long customerId) {
        double conversationFactor = conversationFactor(customerId);
        double stopFactor = serviceStopFactor(customerId);
        double negativeFactor = negativeFeedbackFactor(customerId);
        double lifecycleFactor = lifecycleFactor(customerId);
        double taxStagnationFactor = taxStagnationFactor(customerId);
        // 因子为 0-1 值, 加权后放大到 0-100 分制（与 ChurnWarning.riskScore 口径一致）
        double score = (conversationFactor * 0.25 + stopFactor * 0.30
                + negativeFactor * 0.20 + lifecycleFactor * 0.10
                + taxStagnationFactor * 0.15) * 100;
        return Math.min(100.0, Math.max(0.0, score));
    }

    /**
     * 申报停滞因子（税务申报超期未办 → 流失信号）。
     * <p>
     * 分档依据"逾期期数"，而不是单期逾期：代账客户偶尔漏一期是操作失误，
     * 连续或多次逾期才说明服务关系实质上已经松动。
     * </p>
     * <ul>
     *   <li>无逾期 → 0.1（基线，不把正常客户推到风险区）；</li>
     *   <li>1 期逾期 → 0.6；</li>
     *   <li>≥2 期逾期 → 1.0。</li>
     * </ul>
     *
     * @param customerId 客户 ID
     * @return 0-1 因子
     */
    private double taxStagnationFactor(Long customerId) {
        try {
            List<ScrmTaxDeclarationRecordEntity> records =
                    taxDeclarationRecordRepository.findByCustomerIdOrderByPeriodStartAsc(customerId);
            long overdue = records.stream()
                    .filter(r -> ScrmTaxDeclarationService.STATUS_OVERDUE.equals(r.getDeclarationStatus()))
                    .count();
            if (overdue == 0) {
                return 0.1;
            }
            return overdue == 1 ? 0.6 : 1.0;
        } catch (Exception e) {
            // 因子计算失败按中性值处理，不影响整体评分产出
            log.warn("[续费-流失] 申报停滞因子计算失败: customerId={}, err={}",
                    customerId, e.getMessage());
            return 0.5;
        }
    }

    /**
     * 对话频次因子（近 14 天会话消息数: 0 → 1.0, [1,3) → 0.6, [3,10) → 0.3, ≥10 → 0.1）。
     *
     * @param customerId 客户 ID
     * @return 0-1 因子
     */
    private double conversationFactor(Long customerId) {
        try {
            List<org.hiylo.scrm.entity.ScrmConversationEntity> conversations =
                    conversationRepository.findByCustomerId(customerId);
            long recent = conversations.stream()
                    .filter(c -> c.getLastMessageAt() != null
                            && c.getLastMessageAt().isAfter(LocalDateTime.now().minusDays(14)))
                    .count();
            if (recent == 0) {
                return 1.0;
            }
            if (recent < 3) {
                return 0.6;
            }
            if (recent < 10) {
                return 0.3;
            }
            return 0.1;
        } catch (Exception e) {
            return 0.5;
        }
    }

    /**
     * 服务停用因子（近 60 天无发票开票记录 → 1.0, 否则 0.2）。
     *
     * @param customerId 客户 ID
     * @return 0-1 因子
     */
    private double serviceStopFactor(Long customerId) {
        try {
            long count = invoiceRepository.count((root, query, cb) -> cb.and(
                    cb.equal(root.get("customerId"), customerId),
                    cb.greaterThan(root.get("invoiceDate"), LocalDate.now().minusDays(60))));
            return count == 0 ? 1.0 : 0.2;
        } catch (Exception e) {
            return 0.5;
        }
    }

    /**
     * 负面反馈因子（近 30 天差评反馈 rating≤2 或投诉工单 → 1.0, 否则 0.1）。
     *
     * @param customerId 客户 ID
     * @return 0-1 因子
     */
    private double negativeFeedbackFactor(Long customerId) {
        try {
            List<org.hiylo.scrm.entity.ScrmFeedbackEntity> feedbacks =
                    feedbackRepository.findAll((root, query, cb) -> cb.and(
                            cb.equal(root.get("customerId"), customerId),
                            cb.greaterThanOrEqualTo(root.get("createTime"),
                                    LocalDateTime.now().minusDays(30))));
            boolean negative = feedbacks.stream()
                    .anyMatch(f -> f.getRating() != null && f.getRating() <= 2
                            || "COMPLAINT".equals(f.getFeedbackType())
                            || "SERVICE_COMPLAINT".equals(f.getCategory()));
            return negative ? 1.0 : 0.1;
        } catch (Exception e) {
            return 0.5;
        }
    }

    /**
     * 生命周期异常因子（到期未续任务 OVERDUE/LOST → 1.0, 否则 0.1）。
     *
     * @param customerId 客户 ID
     * @return 0-1 因子
     */
    private double lifecycleFactor(Long customerId) {
        try {
            List<ScrmRenewalTaskEntity> tasks = renewalTaskRepository.findAll().stream()
                    .filter(t -> customerId.equals(t.getCustomerId()))
                    .filter(t -> STATUS_OVERDUE.equals(t.getStatus()) || STATUS_LOST.equals(t.getStatus()))
                    .collect(Collectors.toList());
            return tasks.isEmpty() ? 0.1 : 1.0;
        } catch (Exception e) {
            return 0.5;
        }
    }

    /**
     * 构建风险因子明细 JSON（[{factor, value, detail}]）。
     *
     * @param customerId 客户 ID
     * @return JSON 字符串
     */
    private String buildRiskFactors(Long customerId) {
        List<Map<String, Object>> factors = new ArrayList<>();
        factors.add(factor("conversation_frequency", conversationFactor(customerId),
                "近 14 天对话频次"));
        factors.add(factor("service_stop_signal", serviceStopFactor(customerId),
                "近 60 天无开票记录（服务停用信号）"));
        factors.add(factor("negative_feedback", negativeFeedbackFactor(customerId),
                "近 30 天差评/投诉反馈"));
        factors.add(factor("lifecycle_abnormal", lifecycleFactor(customerId),
                "续费到期未处理（OVERDUE/LOST）"));
        factors.add(factor("tax_declaration_stagnation", taxStagnationFactor(customerId),
                "税务申报逾期未办（≥1 期）"));
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(factors);
        } catch (Exception e) {
            return "[]";
        }
    }

    /**
     * 构造风险因子条目。
     *
     * @param name   因子名
     * @param value  因子值（0-1）
     * @param detail 描述
     * @return 因子 Map
     */
    private Map<String, Object> factor(String name, double value, String detail) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("factor", name);
        map.put("value", Math.round(value * 100.0) / 100.0);
        map.put("detail", detail + "（因子值 " + Math.round(value * 100.0) / 100.0 + "）");
        return map;
    }

    // ============================================================
    // 内部工具
    // ============================================================

    /**
     * 幂等创建任务。
     *
     * @param contract 合同
     * @return 是否新建
     */
    private boolean createTaskIfMissing(ScrmContractEntity contract) {
        if (renewalTaskRepository.findFirstPendingByContractId(contract.getId()).isPresent()) {
            return false;
        }
        ScrmRenewalTaskEntity entity = new ScrmRenewalTaskEntity();
        entity.setTaskNo(generateTaskNo());
        entity.setContractId(contract.getId());
        entity.setContractNo(contract.getContractNo());
        entity.setContractName(contract.getContractName());
        entity.setCustomerId(contract.getCustomerId());
        entity.setCustomerName(contract.getCustomerName());
        // 业务数据自动挂公司（合同联系人已归属公司时自动带上）
        entity.setCompanyId(companyResolver.resolveByCustomerId(contract.getCustomerId()));
        entity.setSalesPersonId(contract.getSalesPersonId());
        entity.setSalesPersonName(contract.getSalesPersonName());
        entity.setPlannedRenewalDate(contract.getEndDate());
        entity.setRemindDaysBefore(30);
        entity.setRenewalOption(OPTION_STANDARD);
        entity.setApprovalRequired(false);
        entity.setStatus(STATUS_PENDING);
        entity.setSource("SYSTEM_SCHEDULE");
        entity.setRemindCount(0);
        entity.setCreatedBy("renewal-system");
        renewalTaskRepository.save(entity);
        // 挂一条跟进任务（销售待办页可见）
        createFollowUpTask(entity);
        return true;
    }

    /**
     * 挂跟进任务（复用 ScrmFollowUpService, taskType=OTHER 兜底既有合法集合）。
     *
     * @param task 续费任务
     */
    private void createFollowUpTask(ScrmRenewalTaskEntity task) {
        try {
            ScrmFollowUpTaskDto dto = new ScrmFollowUpTaskDto();
            dto.setCustomerId(task.getCustomerId());
            dto.setCustomerName(task.getCustomerName());
            dto.setAssigneeId(task.getSalesPersonId() != null ? task.getSalesPersonId() : "admin");
            dto.setAssigneeName(task.getSalesPersonName() != null ? task.getSalesPersonName() : "销售");
            dto.setTaskType(FOLLOWUP_TASK_TYPE);
            dto.setTitle("续费跟进: " + (task.getContractName() != null ? task.getContractName()
                    : "合同#" + task.getContractId()));
            dto.setContent("合同 " + task.getContractNo() + " 于 " + task.getPlannedRenewalDate()
                    + " 到期, 请与客户确认续费意向并录入结果。");
            dto.setPlannedAt(LocalDateTime.now().plusDays(1));
            dto.setPriority("HIGH");
            followUpService.createTask(dto);
        } catch (Exception e) {
            log.warn("[续费-任务] 跟进任务挂载失败 (不影响主流程): taskId={}, err={}",
                    task.getId(), e.getMessage());
        }
    }

    /**
     * 创建流失挽回事宜（跟进任务 + APP 提醒）。
     *
     * @param warning 流失预警
     */
    private void createChurnFollowUp(ScrmChurnWarningEntity warning) {
        try {
            ScrmFollowUpTaskDto dto = new ScrmFollowUpTaskDto();
            dto.setCustomerId(warning.getCustomerId());
            dto.setCustomerName(warning.getCustomerName());
            dto.setAssigneeId(warning.getAssigneeId() != null ? warning.getAssigneeId() : "admin");
            dto.setAssigneeName("销售");
            dto.setTaskType(FOLLOWUP_TASK_TYPE);
            dto.setTitle("流失风险跟进: " + warning.getCustomerName());
            dto.setContent("流失风险分 " + warning.getRiskScore() + "（" + warning.getRiskLevel()
                    + "）, 请与客户沟通续费/服务感知, 挽回动作完成后在预警上标记处理。");
            dto.setPlannedAt(LocalDateTime.now().plusDays(1));
            dto.setPriority(warning.getRiskLevel());
            followUpService.createTask(dto);
            // APP 提醒销售（幂等: 同预警同节点只推一次）
            pushChurnNotice(warning);
        } catch (Exception e) {
            log.warn("[续费-流失] 挽回事宜创建失败 (不影响主流程): warningId={}, err={}",
                    warning.getId(), e.getMessage());
        }
    }

    /**
     * 流失预警 APP 提醒（复用续费任务模板, relatedId=预警 ID）。
     *
     * @param warning 流失预警
     */
    private void pushChurnNotice(ScrmChurnWarningEntity warning) {
        try {
            ScrmNotificationSendDto dto = new ScrmNotificationSendDto();
            dto.setTemplateCode("renewal-task-remind");
            dto.setRecipientType("USER");
            dto.setRecipients(List.of(warning.getAssigneeId() != null ? warning.getAssigneeId() : "admin"));
            dto.setSenderId(SENDER_ID);
            dto.setSenderName("续费系统");
            dto.setPriority(2);
            dto.setRelatedType("CHURN_WARNING");
            dto.setRelatedId(String.valueOf(warning.getId()));
            Map<String, String> variables = new LinkedHashMap<>();
            variables.put("customerName", warning.getCustomerName());
            variables.put("contractName", "流失风险 " + warning.getRiskLevel());
            variables.put("contractNo", "");
            variables.put("endDate", String.valueOf(LocalDate.now()));
            variables.put("daysLeft", "流失风险分 " + Math.round(warning.getRiskScore()));
            variables.put("statusLabel", "请尽快跟进挽回");
            dto.setVariables(variables);
            notificationCenterService.sendNotification(dto);
        } catch (Exception e) {
            log.warn("[续费-流失] 流失预警推送失败 (不影响主流程): warningId={}, err={}",
                    warning.getId(), e.getMessage());
        }
    }

    /**
     * 标记流失（OVERDUE 超期 → LOST + 触发流失评分）。
     *
     * @param task 逾期任务
     */
    private void markLost(ScrmRenewalTaskEntity task) {
        task.setStatus(STATUS_LOST);
        renewalTaskRepository.save(task);
        log.info("[续费-流失] 到期未续转流失: taskId={}, customerId={}",
                task.getId(), task.getCustomerId());
        triggerChurnScan(task.getCustomerId());
    }

    /**
     * 触发流失评分（异步由调度器调用, 本处同步执行最小实现）。
     *
     * @param customerId 客户 ID
     */
    private void triggerChurnScan(Long customerId) {
        scanChurn(customerId);
    }

    /**
     * 为非标准续费挂审批实例。
     * <p>
     * 失败只告警不抛出——续费任务已经落库，让创建整体失败反而更糟。
     * </p>
     *
     * @param entity 续费任务（须已保存，有 id）
     */
    private void attachApproval(ScrmRenewalTaskEntity entity) {
        RenewalApprovalGateway gateway = renewalApprovalGatewayProvider.getIfAvailable();
        if (gateway == null) {
            log.warn("[续费-审批] 审批网关未装配, 需人工在审批中心补建: taskId={}", entity.getId());
            return;
        }
        Long instanceId = gateway.startApproval(entity);
        if (instanceId != null) {
            entity.setApprovalInstanceId(instanceId);
            renewalTaskRepository.save(entity);
        }
    }

    /**
     * 非标准续费（折扣/赠期）审批被驳回时的回退处理。
     * <p>
     * 驳回不是终止：折扣/赠期方案被否，续费本身往往还要谈，
     * 因此退回 {@code PENDING} 让销售改方案重提，而不是置成 LOST/CANCELLED。
     * 同时清掉待审批的折扣与赠期，避免被驳回的方案残留在任务上误导后续跟进。
     * </p>
     *
     * @param taskId 续费任务 ID
     * @param reason 驳回原因（记入备注，可空）
     * @return 更新后的任务；任务不存在或状态已不是待审批时返回 null（回调场景不抛异常）
     */
    @Transactional
    public ScrmRenewalTaskEntity rejectByApproval(Long taskId, String reason) {
        ScrmRenewalTaskEntity task = renewalTaskRepository.findById(taskId).orElse(null);
        if (task == null) {
            log.warn("[续费-审批] 驳回回调时任务不存在: taskId={}", taskId);
            return null;
        }
        if (!STATUS_APPROVAL_PENDING.equals(task.getStatus())) {
            // 幂等：可能已被人工处理过，重复回调不覆盖既有状态
            log.info("[续费-审批] 驳回回调忽略（状态非待审批）: taskId={}, status={}",
                    taskId, task.getStatus());
            return task;
        }
        task.setStatus(STATUS_PENDING);
        task.setDiscountPct(null);
        task.setGraceMonths(null);
        task.setApprovalRequired(false);
        if (reason != null && !reason.isBlank()) {
            String note = "[审批驳回] " + reason.trim();
            task.setNotes(task.getNotes() == null || task.getNotes().isBlank()
                    ? note : task.getNotes() + "\n" + note);
        }
        task = renewalTaskRepository.save(task);
        log.info("[续费-审批] 驳回回退: taskId={}, reason={}", taskId, reason);
        return task;
    }

    /**
     * 查询续费任务（回调侧按 ID 取用，不做数据范围过滤——审批回调是系统行为）。
     *
     * @param taskId 任务 ID
     * @return 任务，不存在返回 null
     */
    @Transactional(readOnly = true)
    public ScrmRenewalTaskEntity findByIdForApproval(Long taskId) {
        return renewalTaskRepository.findById(taskId).orElse(null);
    }

    /**
     * 应用续费选项（STANDARD 不需审批; DISCOUNT/GRACE 需审批）。
     *
     * @param entity 任务实体
     * @param option 续费选项
     */
    private void applyOption(ScrmRenewalTaskEntity entity, String option) {
        String opt = option != null ? option : OPTION_STANDARD;
        entity.setRenewalOption(opt);
        entity.setApprovalRequired(!OPTION_STANDARD.equals(opt));
        if (!OPTION_STANDARD.equals(opt) && !OPTION_CANCEL.equals(opt)) {
            entity.setStatus(STATUS_APPROVAL_PENDING);
        }
    }

    /**
     * 任务状态是否在办。
     *
     * @param status 状态
     * @return true 在办
     */
    private boolean isActive(String status) {
        return STATUS_PENDING.equals(status) || STATUS_APPROVAL_PENDING.equals(status)
                || STATUS_APPROVED.equals(status) || STATUS_IN_PROGRESS.equals(status)
                || STATUS_OVERDUE.equals(status);
    }

    /**
     * 提醒单条（幂等）。
     *
     * @param task   任务
     * @param node   节点键
     * @param desc   描述
     * @param days   天数文本
     * @param priority 优先级
     * @return 是否推送
     */
    private boolean pushOnce(ScrmRenewalTaskEntity task, String node, String desc,
                             String days, int priority) {
        String relatedId = task.getId() + ":" + node;
        try {
            if (notificationRepository.existsByRelatedTypeAndRelatedIdAndTemplateCode(
                    RELATED_TYPE_RENEWAL_TASK, relatedId, properties.getRemindTemplateCode())) {
                return false;
            }
            ScrmNotificationSendDto dto = new ScrmNotificationSendDto();
            dto.setTemplateCode(properties.getRemindTemplateCode());
            dto.setRecipientType("USER");
            List<String> recipients = new ArrayList<>();
            // 代账口径优先级：负责会计 > 销售 > 配置默认收件人。
            // 会计是实际办续费的人，销售只是签单人；未指派会计时保持原行为不变。
            Long accountantId = companyResolver.resolveAccountantId(task.getCompanyId());
            if (accountantId != null) {
                recipients.add(String.valueOf(accountantId));
            } else if (task.getSalesPersonId() != null && !task.getSalesPersonId().isBlank()) {
                recipients.add(task.getSalesPersonId());
            } else {
                recipients.add(properties.getNotifyRecipient());
            }
            if (priority >= 2 && !recipients.contains(properties.getNotifyRecipient())) {
                recipients.add(properties.getNotifyRecipient()); // 升级提醒通知销售+主管
            }
            dto.setRecipients(recipients);
            dto.setSenderId(SENDER_ID);
            dto.setSenderName("续费系统");
            dto.setPriority(priority);
            dto.setRelatedType(RELATED_TYPE_RENEWAL_TASK);
            dto.setRelatedId(relatedId);
            Map<String, String> variables = new LinkedHashMap<>();
            variables.put("customerName", task.getCustomerName() != null
                    ? task.getCustomerName() : "客户#" + task.getCustomerId());
            variables.put("contractName", task.getContractName() != null ? task.getContractName() : "");
            variables.put("contractNo", task.getContractNo() != null ? task.getContractNo() : "");
            variables.put("endDate", String.valueOf(task.getPlannedRenewalDate()));
            variables.put("daysLeft", days);
            variables.put("statusLabel", desc + (task.getStatus() != null ? "（" + task.getStatus() + "）" : ""));
            dto.setVariables(variables);
            notificationCenterService.sendNotification(dto);
            task.setLastRemindedAt(LocalDateTime.now());
            task.setRemindCount((task.getRemindCount() == null ? 0 : task.getRemindCount()) + 1);
            renewalTaskRepository.save(task);
            return true;
        } catch (Exception e) {
            log.warn("[续费-提醒] 推送失败 (不影响主流程): taskId={}, node={}, err={}",
                    task.getId(), node, e.getMessage());
            return false;
        }
    }

    /**
     * 解析提醒节奏（逗号分隔天数, 去重降序）。
     *
     * @return 提醒天数列表
     */
    private List<Integer> parseRemindDays() {
        String raw = properties.getRemindDaysBefore() != null
                ? properties.getRemindDaysBefore() : "30,7,3";
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .map(Integer::valueOf)
                .distinct()
                .sorted((a, b) -> Integer.compare(b, a))
                .collect(Collectors.toList());
    }

    /**
     * 生成任务编号 RN-YYYYMMDD-序号。
     *
     * @return 任务编号
     */
    private String generateTaskNo() {
        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        long count = renewalTaskRepository.count();
        return "RN-" + date + "-" + String.format("%04d", (count % 10000) + 1);
    }

    /**
     * 查询任务, 不存在抛 404。
     *
     * @param id 任务 ID
     * @return 任务实体
     * @throws ScrmException 任务不存在
     */
    private ScrmRenewalTaskEntity findTaskOrThrow(Long id) throws ScrmException {
        if (id == null) {
            throw ScrmException.badRequest("任务 ID 不能为空");
        }
        return renewalTaskRepository.findById(id)
                .orElseThrow(() -> ScrmException.notFound("续费任务不存在: id=" + id));
    }

    /**
     * 解析客户对应销售（客户归属账号兜底）。
     *
     * @param customerId 客户 ID
     * @return 销售 ID
     */
    private String resolveSalesOf(Long customerId) {
        try {
            return customerRepository.findById(customerId)
                    .map(c -> c.getOwnerAccountId() != null ? String.valueOf(c.getOwnerAccountId()) : null)
                    .orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 当前操作人。
     *
     * @return 操作人 ID, 缺省 system
     */
    private String operator() {
        String userId = org.hiylo.scrm.config.UserContext.getUserId();
        return userId != null && !userId.isBlank() ? userId : "system";
    }
}
