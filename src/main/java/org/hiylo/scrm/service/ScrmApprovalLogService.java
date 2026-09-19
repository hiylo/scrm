/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmApprovalLogService.java
 * Date : 2026/09/19 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.entity.ScrmApprovalInstanceEntity;
import org.hiylo.scrm.entity.ScrmApprovalLogEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.repository.ScrmApprovalInstanceRepository;
import org.hiylo.scrm.repository.ScrmApprovalLogRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SCRM 审批操作日志服务。
 * <p>
 * 承载操作日志子域: 日志明细查询 / 实例日志分页 / 审批时间线 / 操作人日志。同时托管日志写入
 * (统一入口 recordLog) 与日志顺序号生成, 供实例 / 操作兄弟类以 package 级访问复用。
 * </p>
 *
 * @author Hsi Chu
 * @since 2026-09-19
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmApprovalLogService {

    /** 操作人类型: SYSTEM 系统 (日志缺省值) */
    private static final String OPERATOR_SYSTEM = "SYSTEM";

    /** 审批实例数据访问层 (日志查询前的实例存在性校验) */
    private final ScrmApprovalInstanceRepository instanceRepository;

    /** 操作日志数据访问层 */
    private final ScrmApprovalLogRepository logRepository;

    // ============================================================
    // 操作日志
    // ============================================================

    /**
     * 查询日志详情。
     *
     * @param id 日志 ID
     * @return 日志实体
     * @throws ScrmException 日志不存在
     */
    @Transactional(readOnly = true)
    public ScrmApprovalLogEntity getLog(Long id) throws ScrmException {
        ScrmApprovalLogEntity entity = logRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "审批日志不存在: id=" + id));
        return entity;
    }

    /**
     * 分页查询实例的日志 (按操作顺序升序)。
     *
     * @param instanceId 实例 ID
     * @param pageable  分页参数
     * @return 日志分页结果
     * @throws ScrmException 实例不存在
     */
    @Transactional(readOnly = true)
    public Page<ScrmApprovalLogEntity> listLogs(Long instanceId, Pageable pageable) throws ScrmException {
        findInstanceOrThrow(instanceId);
        return logRepository.findAll((root, query, cb) ->
                cb.and(
                        cb.equal(root.get("instanceId"), instanceId)), pageable);
    }

    /**
     * 获取实例审批时间线 (按操作顺序, 含节点与状态信息)。
     *
     * @param instanceId 实例 ID
     * @return 时间线 Map
     * @throws ScrmException 实例不存在
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getInstanceTimeline(Long instanceId) throws ScrmException {
        ScrmApprovalInstanceEntity instance = findInstanceOrThrow(instanceId);
        List<ScrmApprovalLogEntity> logs = logRepository
                .findByInstanceIdOrderBySequence(instanceId);
        List<Map<String, Object>> timeline = new ArrayList<>();
        for (ScrmApprovalLogEntity logEntry : logs) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("logId", logEntry.getId());
            m.put("nodeId", logEntry.getNodeId());
            m.put("nodeName", logEntry.getNodeName());
            m.put("nodeType", logEntry.getNodeType());
            m.put("actionType", logEntry.getActionType());
            m.put("operatorId", logEntry.getOperatorId());
            m.put("operatorName", logEntry.getOperatorName());
            m.put("operatorType", logEntry.getOperatorType());
            m.put("comment", logEntry.getComment());
            m.put("actionData", logEntry.getActionData());
            m.put("previousNodeId", logEntry.getPreviousNodeId());
            m.put("nextNodeId", logEntry.getNextNodeId());
            m.put("actedAt", logEntry.getActedAt());
            m.put("isAutoAction", logEntry.getIsAutoAction());
            m.put("sequence", logEntry.getSequence());
            timeline.add(m);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("instanceId", instanceId);
        result.put("instanceNo", instance.getInstanceNo());
        result.put("status", instance.getStatus());
        result.put("businessTitle", instance.getBusinessTitle());
        result.put("applicantId", instance.getApplicantId());
        result.put("startedAt", instance.getStartedAt());
        result.put("completedAt", instance.getCompletedAt());
        result.put("durationHours", instance.getDurationHours());
        result.put("timeline", timeline);
        return result;
    }

    /**
     * 分页查询操作人的日志 (按操作时间倒序)。
     *
     * @param operatorId 操作人 ID
     * @param pageable   分页参数
     * @return 日志分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmApprovalLogEntity> getLogsByOperator(String operatorId, Pageable pageable) {
        if (operatorId == null || operatorId.isBlank()) {
            throw ScrmException.badRequest("操作人 ID 不能为空");
        }
        return logRepository.findByOperator(operatorId, pageable);
    }

    // ============================================================
    // 内部工具方法
    // ============================================================

    /**
     * 记录审批操作日志。
     *
     * @param instance       实例实体
     * @param nodeId         节点 ID
     * @param nodeName       节点名称
     * @param nodeType       节点类型
     * @param actionType     操作类型
     * @param operatorId     操作人 ID
     * @param operatorName   操作人名称
     * @param operatorRole   操作人角色
     * @param operatorType   操作人类型
     * @param comment        审批意见
     * @param actionData     操作数据 JSON
     * @param previousNodeId 上一节点 ID
     * @param nextNodeId     下一节点 ID
     * @param isAutoAction   是否自动操作
     */
    void recordLog(ScrmApprovalInstanceEntity instance, String nodeId, String nodeName, String nodeType,
                    String actionType, String operatorId, String operatorName, String operatorRole,
                    String operatorType, String comment, String actionData,
                    String previousNodeId, String nextNodeId, boolean isAutoAction) {
        try {
            ScrmApprovalLogEntity logEntry = new ScrmApprovalLogEntity();
            logEntry.setInstanceId(instance.getId());
            logEntry.setNodeId(nodeId != null ? nodeId : "INIT");
            logEntry.setNodeName(nodeName);
            logEntry.setNodeType(nodeType);
            logEntry.setActionType(actionType);
            logEntry.setOperatorId(operatorId != null ? operatorId : "SYSTEM");
            logEntry.setOperatorName(operatorName);
            logEntry.setOperatorRole(operatorRole);
            logEntry.setOperatorType(operatorType != null ? operatorType : OPERATOR_SYSTEM);
            logEntry.setComment(comment);
            logEntry.setActionData(actionData);
            logEntry.setPreviousNodeId(previousNodeId);
            logEntry.setNextNodeId(nextNodeId);
            logEntry.setActedAt(LocalDateTime.now());
            logEntry.setIsAutoAction(isAutoAction);
            logEntry.setSequence(nextLogSequence(instance.getId()));
            logRepository.save(logEntry);
        } catch (Exception e) {
            log.warn("记录审批日志失败: instanceId={}, actionType={}, err={}",
                    instance.getId(), actionType, e.getMessage());
        }
    }

    /**
     * 计算下一日志的顺序号。
     *
     * @param instanceId 实例 ID
     * @return 下一顺序号
     */
    private int nextLogSequence(Long instanceId) {
        List<ScrmApprovalLogEntity> logs = logRepository
                .findByInstanceIdOrderBySequence(instanceId);
        if (logs.isEmpty()) {
            return 0;
        }
        ScrmApprovalLogEntity last = logs.get(logs.size() - 1);
        return last.getSequence() != null ? last.getSequence() + 1 : logs.size();
    }

    /**
     * 按主键查询实例, 不存在则抛异常。
     *
     * @param id 实例 ID
     * @return 实例实体
     * @throws ScrmException 实例不存在
     */
    private ScrmApprovalInstanceEntity findInstanceOrThrow(Long id) throws ScrmException {
        ScrmApprovalInstanceEntity entity = instanceRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "审批实例不存在: id=" + id));
        return entity;
    }
}