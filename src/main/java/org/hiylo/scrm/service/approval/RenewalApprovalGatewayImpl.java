/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm-finance
 * File : RenewalApprovalGatewayImpl.java
 * Date : 2026/10/27 08:40:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service.approval;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hiylo.scrm.dto.ScrmApprovalSubmitDto;
import org.hiylo.scrm.entity.ScrmApprovalInstanceEntity;
import org.hiylo.scrm.entity.ScrmRenewalTaskEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.service.ScrmApprovalFlowService;
import org.hiylo.scrm.service.ScrmApprovalInstanceService;
import org.hiylo.scrm.service.finance.renewal.RenewalApprovalGateway;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 续费审批网关实现。
 * <p>
 * 非标准续费（折扣 / 赠期）创建任务后，按 {@code flowType=RENEWAL} 找已激活流程并自动提交审批实例。
 * 审批通过 / 驳回后由 {@code ScrmRenewalApprovalListener} 回调续费状态机。
 * </p>
 * <p>
 * <b>失败不阻断主流程</b>：找不到流程或提交失败只记告警，续费任务本身已落库，
 * 由人工在审批中心补建——这比让会计建不了续费任务更好。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RenewalApprovalGatewayImpl implements RenewalApprovalGateway {

    /** 续费业务类型（与审批引擎 businessType / flowType 白名单一致） */
    public static final String BUSINESS_TYPE_RENEWAL = "RENEWAL";

    /** 审批实例服务 */
    private final ScrmApprovalInstanceService instanceService;

    /** 审批流程服务（按 flowType 查流程） */
    private final ScrmApprovalFlowService flowService;

    /**
     * 为非标准续费发起审批实例。
     *
     * @param task 已落库的续费任务
     * @return 审批实例 ID，未发起时返回 null
     */
    @Override
    public Long startApproval(ScrmRenewalTaskEntity task) {
        if (task == null || task.getId() == null) {
            return null;
        }
        // 幂等：已有实例直接返回，避免重复建单
        if (task.getApprovalInstanceId() != null) {
            return task.getApprovalInstanceId();
        }
        try {
            var flow = flowService.getFlowByBusinessType(BUSINESS_TYPE_RENEWAL);
            ScrmApprovalSubmitDto submit = new ScrmApprovalSubmitDto();
            submit.setFlowId(flow.getId());
            submit.setBusinessType(BUSINESS_TYPE_RENEWAL);
            submit.setBusinessId(String.valueOf(task.getId()));
            submit.setBusinessTitle("续费审批 - " + nullToDash(task.getContractName()));
            submit.setBusinessData(buildBusinessData(task));
            // 申请人取销售负责人，缺省记系统，保证审批链路有归属
            submit.setApplicantId(task.getSalesPersonId() != null
                    ? String.valueOf(task.getSalesPersonId()) : "SYSTEM");
            submit.setApplicantName(task.getSalesPersonName() != null
                    ? task.getSalesPersonName() : "系统");
            ScrmApprovalInstanceEntity instance = instanceService.submit(submit);
            log.info("[续费-审批] 自动发起: taskId={}, instanceId={}, flowId={}",
                    task.getId(), instance.getId(), flow.getId());
            return instance.getId();
        } catch (ScrmException e) {
            // 没配 RENEWAL 流程属于配置缺失，不应让续费任务创建失败
            log.warn("[续费-审批] 自动发起失败, 需人工在审批中补建: taskId={}, reason={}",
                    task.getId(), e.getMessage());
            return null;
        } catch (RuntimeException e) {
            log.error("[续费-审批] 自动发起异常: taskId={}", task.getId(), e);
            return null;
        }
    }

    /**
     * 构造审批业务快照（供审批人判断折扣与赠期是否合理）。
     *
     * @param task 续费任务
     * @return JSON 字符串
     */
    private String buildBusinessData(ScrmRenewalTaskEntity task) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("taskNo", task.getTaskNo());
        data.put("contractNo", task.getContractNo());
        data.put("contractName", task.getContractName());
        data.put("customerName", task.getCustomerName());
        data.put("renewalOption", task.getRenewalOption());
        data.put("discountPct", task.getDiscountPct());
        data.put("graceMonths", task.getGraceMonths());
        data.put("renewalAmount", task.getRenewalAmount());
        data.put("plannedRenewalDate", task.getPlannedRenewalDate());
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(data);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            log.warn("[续费-审批] 业务快照序列化失败: taskId={}", task.getId(), e);
            return null;
        }
    }

    /** 空值转短横线，避免标题出现 "null" */
    private String nullToDash(String value) {
        return value != null && !value.isBlank() ? value : "-";
    }
}
