/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCustomerJourneyController.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hiylo.scrm.dto.ScrmCustomerJourneyDto;
import org.hiylo.scrm.dto.ScrmJourneyEnrollDto;
import org.hiylo.scrm.dto.ScrmJourneyEnrollmentDto;
import org.hiylo.scrm.dto.ScrmJourneyProgressLogDto;
import org.hiylo.scrm.dto.ScrmJourneyStepDto;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.rbac.annotation.RateLimit;
import org.hiylo.scrm.rbac.annotation.RequirePermission;
import org.hiylo.scrm.service.ScrmCustomerJourneyService;
import org.hiylo.scrm.vo.JourneyStatsVo;
import org.hiylo.scrm.vo.JourneyStepStatsVo;
import org.hiylo.scrm.common.OperationResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * SCRM 营销 SOP 客户旅程控制器
 * <p>
 * 提供客户旅程定义 / 步骤编排 / 客户入营 / 步骤执行 / 进度跟踪与统计接口。
 * 面向客户关系的 SOP 编排, 区别于设备行为流。
 * 权限由 gateway-server 统一鉴权, 此处通过 {@link RequirePermission} 声明资源与动作元数据。
 * </p>
 *
 * @author Hsi Chu
 */
@Slf4j
@RestController
@RequestMapping("/scrm/customer-journeys")
@RequiredArgsConstructor
public class ScrmCustomerJourneyController {

    /** 客户旅程服务 */
    private final ScrmCustomerJourneyService customerJourneyService;

    // ============================================================
    // 旅程管理
    // ============================================================

    /**
     * 创建客户旅程
     *
     * @param dto 旅程参数
     * @return 创建后的旅程
     */
    @RequirePermission(resource = "scrm_customer_journey", action = "create")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping
    public OperationResponse<ScrmCustomerJourneyDto> createJourney(@Valid @RequestBody ScrmCustomerJourneyDto dto)
            throws ScrmException {
        return OperationResponse.build(customerJourneyService.createJourney(dto));
    }

    /**
     * 更新客户旅程
     *
     * @param id  旅程 ID
     * @param dto 旅程参数
     * @return 更新后的旅程
     */
    @RequirePermission(resource = "scrm_customer_journey", action = "update")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PutMapping("/{id}")
    public OperationResponse<ScrmCustomerJourneyDto> updateJourney(@PathVariable Long id,
                                                                     @RequestBody ScrmCustomerJourneyDto dto)
            throws ScrmException {
        return OperationResponse.build(customerJourneyService.updateJourney(id, dto));
    }

    /**
     * 删除客户旅程
     *
     * @param id 旅程 ID
     * @return 空响应
     * @throws ScrmException 旅程不存在 / 权限不足
     */
    @RequirePermission(resource = "scrm_customer_journey", action = "delete")
    @DeleteMapping("/{id}")
    public OperationResponse<Void> deleteJourney(@PathVariable Long id) throws ScrmException {
        customerJourneyService.deleteJourney(id);
        return OperationResponse.build();
    }

    /**
     * 查询客户旅程详情
     *
     * @param id 旅程 ID
     * @return 旅程详情
     * @throws ScrmException 旅程不存在 / 权限不足
     */
    @RequirePermission(resource = "scrm_customer_journey", action = "read")
    @GetMapping("/{id}")
    public OperationResponse<ScrmCustomerJourneyDto> getJourney(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(customerJourneyService.getJourney(id));
    }

    /**
     * 分页查询客户旅程, 支持按状态与关键词过滤
     *
     * @param status  状态过滤 (可选)
     * @param keyword 关键词过滤, 匹配旅程名称 (可选)
     * @param page    页码 (从 0 开始, 默认 0)
     * @param size    每页大小 (默认 20)
     * @return 旅程分页结果
     */
    @RequirePermission(resource = "scrm_customer_journey", action = "read")
    @GetMapping("/list")
    public OperationResponse<Page<ScrmCustomerJourneyDto>> listJourneys(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return OperationResponse.build(customerJourneyService.listJourneys(status, keyword, pageable));
    }

    /**
     * 发布客户旅程
     *
     * @param id 旅程 ID
     * @return 更新后的旅程
     * @throws ScrmException 旅程不存在 / 状态非法
     */
    @RequirePermission(resource = "scrm_customer_journey", action = "publish")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/{id}/publish")
    public OperationResponse<ScrmCustomerJourneyDto> publishJourney(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(customerJourneyService.publishJourney(id));
    }

    /**
     * 暂停客户旅程
     *
     * @param id 旅程 ID
     * @return 更新后的旅程
     * @throws ScrmException 旅程不存在 / 状态非法
     */
    @RequirePermission(resource = "scrm_customer_journey", action = "update")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/{id}/pause")
    public OperationResponse<ScrmCustomerJourneyDto> pauseJourney(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(customerJourneyService.pauseJourney(id));
    }

    /**
     * 归档客户旅程
     *
     * @param id 旅程 ID
     * @return 更新后的旅程
     * @throws ScrmException 旅程不存在 / 状态非法
     */
    @RequirePermission(resource = "scrm_customer_journey", action = "update")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/{id}/archive")
    public OperationResponse<ScrmCustomerJourneyDto> archiveJourney(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(customerJourneyService.archiveJourney(id));
    }

    /**
     * 复制客户旅程 (含步骤定义)
     *
     * @param id 源旅程 ID
     * @return 复制后的新旅程
     * @throws ScrmException 旅程不存在 / 权限不足
     */
    @RequirePermission(resource = "scrm_customer_journey", action = "create")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/{id}/copy")
    public OperationResponse<ScrmCustomerJourneyDto> copyJourney(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(customerJourneyService.copyJourney(id));
    }

    // ============================================================
    // 步骤管理
    // ============================================================

    /**
     * 新增旅程步骤
     *
     * @param journeyId 旅程 ID
     * @param dto       步骤参数
     * @return 创建后的步骤
     */
    @RequirePermission(resource = "scrm_customer_journey", action = "update")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/{journeyId}/steps")
    public OperationResponse<ScrmJourneyStepDto> addStep(@PathVariable Long journeyId,
                                                          @Valid @RequestBody ScrmJourneyStepDto dto)
            throws ScrmException {
        return OperationResponse.build(customerJourneyService.addStep(journeyId, dto));
    }

    /**
     * 查询旅程的全部步骤 (按 stepOrder 升序)
     *
     * @param journeyId 旅程 ID
     * @return 步骤列表
     */
    @RequirePermission(resource = "scrm_customer_journey", action = "read")
    @GetMapping("/{journeyId}/steps")
    public OperationResponse<List<ScrmJourneyStepDto>> listSteps(@PathVariable Long journeyId)
            throws ScrmException {
        return OperationResponse.build(customerJourneyService.listSteps(journeyId));
    }

    /**
     * 更新旅程步骤
     *
     * @param id  步骤 ID
     * @param dto 步骤参数
     * @return 更新后的步骤
     */
    @RequirePermission(resource = "scrm_customer_journey", action = "update")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PutMapping("/steps/{id}")
    public OperationResponse<ScrmJourneyStepDto> updateStep(@PathVariable Long id,
                                                              @RequestBody ScrmJourneyStepDto dto)
            throws ScrmException {
        return OperationResponse.build(customerJourneyService.updateStep(id, dto));
    }

    /**
     * 删除旅程步骤
     *
     * @param id 步骤 ID
     * @return 空响应
     * @throws ScrmException 步骤不存在 / 权限不足
     */
    @RequirePermission(resource = "scrm_customer_journey", action = "update")
    @DeleteMapping("/steps/{id}")
    public OperationResponse<Void> deleteStep(@PathVariable Long id) throws ScrmException {
        customerJourneyService.deleteStep(id);
        return OperationResponse.build();
    }

    /**
     * 批量重排旅程步骤顺序
     *
     * @param journeyId 旅程 ID
     * @param stepIds   步骤 ID 列表 (按新顺序排列)
     * @return 重排后的步骤列表
     */
    @RequirePermission(resource = "scrm_customer_journey", action = "update")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/{journeyId}/steps/reorder")
    public OperationResponse<List<ScrmJourneyStepDto>> reorderSteps(@PathVariable Long journeyId,
                                                                     @RequestBody List<Long> stepIds)
            throws ScrmException {
        return OperationResponse.build(customerJourneyService.reorderSteps(journeyId, stepIds));
    }

    // ============================================================
    // 入营管理
    // ============================================================

    /**
     * 客户入旅程
     *
     * @param dto 入营请求
     * @return 创建后的入营记录 (入口步骤已执行)
     */
    @RequirePermission(resource = "scrm_customer_journey", action = "enroll")
    @RateLimit(capacity = 60, refillTokens = 60, refillPeriodSeconds = 60)
    @PostMapping("/enroll")
    public OperationResponse<ScrmJourneyEnrollmentDto> enroll(@Valid @RequestBody ScrmJourneyEnrollDto dto)
            throws ScrmException {
        return OperationResponse.build(customerJourneyService.enroll(dto));
    }

    /**
     * 批量入旅程
     *
     * @param journeyId   旅程 ID
     * @param customerIds 客户 ID 列表
     * @param source      入营来源: EVENT / MANUAL / API (默认 MANUAL)
     * @return 成功入营的客户数
     */
    @RequirePermission(resource = "scrm_customer_journey", action = "enroll")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/batch-enroll")
    public OperationResponse<Integer> batchEnroll(@RequestParam Long journeyId,
                                                   @RequestBody List<Long> customerIds,
                                                   @RequestParam(defaultValue = "MANUAL") String source)
            throws ScrmException {
        return OperationResponse.build(customerJourneyService.batchEnroll(journeyId, customerIds, source));
    }

    /**
     * 分页查询入营记录, 支持按旅程 / 状态 / 客户过滤
     *
     * @param journeyId  旅程 ID 过滤 (可选)
     * @param status     入营状态过滤 (可选)
     * @param customerId 客户 ID 过滤 (可选)
     * @param page       页码 (从 0 开始, 默认 0)
     * @param size       每页大小 (默认 20)
     * @return 入营记录分页结果
     */
    @RequirePermission(resource = "scrm_customer_journey", action = "read")
    @GetMapping("/enrollments/list")
    public OperationResponse<Page<ScrmJourneyEnrollmentDto>> listEnrollments(
            @RequestParam(required = false) Long journeyId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long customerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return OperationResponse.build(customerJourneyService.listEnrollments(journeyId, status, customerId, pageable));
    }

    /**
     * 查询入营记录详情
     *
     * @param id 入营记录 ID
     * @return 入营记录详情
     * @throws ScrmException 入营记录不存在 / 权限不足
     */
    @RequirePermission(resource = "scrm_customer_journey", action = "read")
    @GetMapping("/enrollments/{id}")
    public OperationResponse<ScrmJourneyEnrollmentDto> getEnrollment(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(customerJourneyService.getEnrollment(id));
    }

    /**
     * 退出旅程
     *
     * @param id     入营记录 ID
     * @param reason 退出原因 (可选)
     * @return 更新后的入营记录
     */
    @RequirePermission(resource = "scrm_customer_journey", action = "update")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/enrollments/{id}/exit")
    public OperationResponse<ScrmJourneyEnrollmentDto> exitEnrollment(@PathVariable Long id,
                                                                       @RequestParam(required = false) String reason)
            throws ScrmException {
        return OperationResponse.build(customerJourneyService.exitEnrollment(id, reason));
    }

    // ============================================================
    // 步骤执行
    // ============================================================

    /**
     * 处理所有待执行的 WAIT 到期步骤 (定时任务用)
     *
     * @return 处理的入营记录数
     */
    @RequirePermission(resource = "scrm_customer_journey", action = "execute")
    @RateLimit(capacity = 10, refillTokens = 10, refillPeriodSeconds = 60)
    @PostMapping("/process")
    public OperationResponse<Integer> processPendingSteps() {
        return OperationResponse.build(customerJourneyService.processPendingSteps());
    }

    // ============================================================
    // 统计与进度
    // ============================================================

    /**
     * 旅程统计: 入旅程数 / 完成数 / 退出数 / 转化率 / 各步骤通过率
     *
     * @param journeyId 旅程 ID
     * @return 旅程统计结果
     */
    @RequirePermission(resource = "scrm_customer_journey", action = "read")
    @GetMapping("/{journeyId}/stats")
    public OperationResponse<JourneyStatsVo> getJourneyStats(@PathVariable Long journeyId)
            throws ScrmException {
        return OperationResponse.build(customerJourneyService.getJourneyStats(journeyId));
    }

    /**
     * 各步骤统计: 进入数 / 成功 / 失败 / 跳过 / 等待 / 通过率
     *
     * @param journeyId 旅程 ID
     * @return 步骤统计列表
     */
    @RequirePermission(resource = "scrm_customer_journey", action = "read")
    @GetMapping("/{journeyId}/step-stats")
    public OperationResponse<List<JourneyStepStatsVo>> getStepStats(@PathVariable Long journeyId)
            throws ScrmException {
        return OperationResponse.build(customerJourneyService.getStepStats(journeyId));
    }

    /**
     * 查询入营记录的进度日志 (按执行时间升序)
     *
     * @param enrollmentId 入营记录 ID
     * @return 进度日志列表
     */
    @RequirePermission(resource = "scrm_customer_journey", action = "read")
    @GetMapping("/enrollments/{enrollmentId}/progress")
    public OperationResponse<List<ScrmJourneyProgressLogDto>> getProgressLog(@PathVariable Long enrollmentId)
            throws ScrmException {
        return OperationResponse.build(customerJourneyService.getProgressLog(enrollmentId));
    }
}
