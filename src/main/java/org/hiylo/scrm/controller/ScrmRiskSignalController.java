/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmRiskSignalController.java
 * Date : 2026/07/29 21:19:51
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.config.UserContext;
import org.hiylo.scrm.entity.ScrmRiskSignalEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.rbac.annotation.RequirePermission;
import org.hiylo.scrm.repository.ScrmRiskSignalRepository;
import org.hiylo.scrm.common.OperationResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * SCRM 风控信号查询控制器。
 * <p>
 * 风控信号由 scrm-server 命中风控规则后回调写入, 此处提供只读查询能力,
 * 供前端"风控信号"页面展示历史触发记录与态势感知。
 *
 * @author Hsi Chu
 */
@RestController
@RequestMapping("/scrm/risk-signals")
@RequiredArgsConstructor
@Slf4j
public class ScrmRiskSignalController {

    /** 风控信号数据访问层 */
    private final ScrmRiskSignalRepository riskSignalRepository;

    /**
     * 分页查询风控信号列表, 支持风险等级 + 信号类型 + 账号 ID + 时间范围组合筛选,
     * 默认按触发时间倒序。所有筛选条件可同时生效 (AND 关系), 任一为空则忽略该条件。
     * 使用 JPA Specification 动态构建查询, 避免 JPQL (? IS NULL) 模式在 PostgreSQL
     * 中的参数类型推断问题。
     *
     * @param page       页码 (从 0 开始)
     * @param size       每页条数
     * @param riskLevel  风险等级筛选 (可空): LOW / MEDIUM / HIGH / CRITICAL
     * @param signalType 信号类型筛选 (可空, 模糊匹配)
     * @param accountId  账号 ID 筛选 (可空)
     * @param status    状态筛选 (可空)
     * @param startTime  触发时间起始 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @param endTime    触发时间截止 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @return 风控信号分页数据
     */
    @RequirePermission(resource = "scrm_risk_signal", action = "read")
    @GetMapping
    public OperationResponse<Page<ScrmRiskSignalEntity>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String riskLevel,
            @RequestParam(required = false) String signalType,
            @RequestParam(required = false) Long accountId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime) {
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "triggeredAt"));
        // 规范化空字符串为 null
        String normalizedRiskLevel = (riskLevel != null && riskLevel.isEmpty()) ? null : riskLevel;
        String normalizedSignalType = (signalType != null && signalType.isEmpty()) ? null : signalType;
        String normalizedStatus = (status != null && status.isEmpty()) ? null : status;

        // 使用 Specification 动态构建多条件查询, 避免 PostgreSQL 参数类型推断问题
        Specification<ScrmRiskSignalEntity> spec = (root, query, cb) -> {
            var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
            // 数据隔离 (必填条件)
            if (normalizedRiskLevel != null) {
                predicates.add(cb.equal(root.get("riskLevel"), normalizedRiskLevel));
            }
            if (normalizedSignalType != null) {
                predicates.add(cb.like(root.get("signalType"), "%" + normalizedSignalType + "%"));
            }
            if (accountId != null) {
                predicates.add(cb.equal(root.get("accountId"), accountId));
            }
            if (normalizedStatus != null) {
                predicates.add(cb.equal(root.get("status"), normalizedStatus));
            }
            if (startTime != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("triggeredAt"), startTime));
            }
            if (endTime != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("triggeredAt"), endTime));
            }
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
        Page<ScrmRiskSignalEntity> result = riskSignalRepository.findAll(spec, pageable);
        return OperationResponse.build(result);
    }

    /**
     * 获取单条风控信号详情。
     *
     * @param id 信号 ID
     * @return 风控信号实体
     * @throws ScrmException 信号不存在时抛出
     */
    @RequirePermission(resource = "scrm_risk_signal", action = "read")
    @GetMapping("/{id}")
    public OperationResponse<ScrmRiskSignalEntity> get(@PathVariable Long id) throws ScrmException {
        return riskSignalRepository.findById(id)
                .map(OperationResponse::build)
                .orElseThrow(() -> new ScrmException("NOT_FOUND", "风控信号不存在: " + id));
    }

    /**
     * 标记风控信号为已处理。
     * <p>
     * 运营人员确认风险已处理后, 将状态更新为 RESOLVED, 记录处理人与处理备注。
     * 已处理/已忽略的信号不允许重复处理。
     * </p>
     *
     * @param id     信号 ID
     * @param body   请求体, 可包含 remark 字段 (处理备注)
     * @return 更新后的风控信号
     * @throws ScrmException 信号不存在或已处理
     */
    @RequirePermission(resource = "scrm_risk_signal", action = "update")
    @PutMapping("/{id}/resolve")
    public OperationResponse<ScrmRiskSignalEntity> resolve(
            @PathVariable Long id,
            @RequestParam(required = false) Map<String, String> body) throws ScrmException {
        ScrmRiskSignalEntity entity = riskSignalRepository.findById(id)
                .orElseThrow(() -> new ScrmException("NOT_FOUND", "风控信号不存在: " + id));
        if ("RESOLVED".equals(entity.getStatus()) || "IGNORED".equals(entity.getStatus())) {
            throw new ScrmException("CONFLICT", "信号已处理, 不允许重复操作");
        }
        String remark = body != null ? body.get("remark") : null;
        entity.setStatus("RESOLVED");
        entity.setResolvedAt(LocalDateTime.now());
        entity.setResolvedBy(UserContext.getUsername());
        entity.setResolveRemark(remark);
        ScrmRiskSignalEntity saved = riskSignalRepository.save(entity);
        log.info("风控信号已标记为已处理: id={}, operator={}, remark={}", id, UserContext.getUsername(), remark);
        return OperationResponse.build(saved);
    }

    /**
     * 标记风控信号为已忽略。
     * <p>
     * 运营人员判断风险无需处理后, 将状态更新为 IGNORED, 记录处理人与忽略原因。
     * 已处理/已忽略的信号不允许重复操作。
     * </p>
     *
     * @param id     信号 ID
     * @param body   请求体, 可包含 remark 字段 (忽略原因)
     * @return 更新后的风控信号
     * @throws ScrmException 信号不存在或已处理
     */
    @RequirePermission(resource = "scrm_risk_signal", action = "update")
    @PutMapping("/{id}/ignore")
    public OperationResponse<ScrmRiskSignalEntity> ignore(
            @PathVariable Long id,
            @RequestParam(required = false) Map<String, String> body) throws ScrmException {
        ScrmRiskSignalEntity entity = riskSignalRepository.findById(id)
                .orElseThrow(() -> new ScrmException("NOT_FOUND", "风控信号不存在: " + id));
        if ("RESOLVED".equals(entity.getStatus()) || "IGNORED".equals(entity.getStatus())) {
            throw new ScrmException("CONFLICT", "信号已处理, 不允许重复操作");
        }
        String remark = body != null ? body.get("remark") : null;
        entity.setStatus("IGNORED");
        entity.setResolvedAt(LocalDateTime.now());
        entity.setResolvedBy(UserContext.getUsername());
        entity.setResolveRemark(remark);
        ScrmRiskSignalEntity saved = riskSignalRepository.save(entity);
        log.info("风控信号已标记为已忽略: id={}, operator={}, remark={}", id, UserContext.getUsername(), remark);
        return OperationResponse.build(saved);
    }
}
