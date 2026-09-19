/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmJourneyEnrollmentService.java
 * Date : 2026/09/19 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.dto.ScrmJourneyEnrollDto;
import org.hiylo.scrm.dto.ScrmJourneyEnrollmentDto;
import org.hiylo.scrm.entity.ScrmCustomerEntity;
import org.hiylo.scrm.entity.ScrmCustomerJourneyEntity;
import org.hiylo.scrm.entity.ScrmJourneyEnrollmentEntity;
import org.hiylo.scrm.entity.ScrmJourneyStepEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.repository.ScrmCustomerJourneyRepository;
import org.hiylo.scrm.repository.ScrmCustomerRepository;
import org.hiylo.scrm.repository.ScrmJourneyEnrollmentRepository;
import org.hiylo.scrm.repository.ScrmJourneyStepRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.criteria.Predicate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * SCRM 客户旅程入营管理子域服务
 * <p>
 * 负责客户入营全流程 (入营校验 / 批量入营 / 退出) 与入营记录查询。
 * 步骤执行见 {@link ScrmJourneyStepExecutionService} (入营时触发入口步骤),
 * 旅程定义见 {@link ScrmCustomerJourneyDefinitionService}。本服务为
 * {@link ScrmCustomerJourneyService} 门面的子域拆分, 不反向依赖门面。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmJourneyEnrollmentService {

    /** 旅程状态: 已发布 */
    private static final String STATUS_PUBLISHED = "PUBLISHED";

    /** 入营状态: 进行中 */
    private static final String ENROLLMENT_STATUS_ACTIVE = "ACTIVE";
    /** 入营状态: 已退出 */
    private static final String ENROLLMENT_STATUS_EXITED = "EXITED";

    /** 入营来源: 手动 */
    private static final String SOURCE_MANUAL = "MANUAL";

    /** 客户旅程数据仓库 */
    private final ScrmCustomerJourneyRepository journeyRepository;
    /** 旅程加入数据仓库 */
    private final ScrmJourneyEnrollmentRepository enrollmentRepository;
    /** 客户数据仓库 */
    private final ScrmCustomerRepository customerRepository;
    /** 旅程步骤数据仓库 */
    private final ScrmJourneyStepRepository stepRepository;
    /** 步骤执行兄弟服务 (提供共享查询 / 条件解析 / 转化率重算 / DTO 转换) */
    private final ScrmJourneyStepExecutionService stepExecutionService;

    /**
     * 客户入旅程
     * <p>
     * 流程: 校验旅程已发布 → 校验客户存在且归属当前账号 → 检查入旅程条件 →
     * 校验未重复入营 → 创建入营记录 → 执行入口步骤。
     * </p>
     *
     * @param enrollDto 入营请求
     * @return 创建后的入营记录 (入口步骤已执行)
     * @throws ScrmException 旅程未发布 / 客户不存在 / 条件不满足 / 已入营
     */
    @Transactional
    public ScrmJourneyEnrollmentDto enroll(ScrmJourneyEnrollDto enrollDto) throws ScrmException {
        if (enrollDto.getJourneyId() == null) {
            throw ScrmException.badRequest("旅程 ID 不能为空");
        }
        if (enrollDto.getCustomerId() == null) {
            throw ScrmException.badRequest("客户 ID 不能为空");
        }
        if (enrollDto.getSource() == null || enrollDto.getSource().isBlank()) {
            throw ScrmException.badRequest("入营来源不能为空");
        }
        ScrmCustomerJourneyEntity journey = stepExecutionService.findJourneyOrThrow(enrollDto.getJourneyId());
        if (!STATUS_PUBLISHED.equals(journey.getStatus())) {
            throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                    "旅程未发布, 不允许入营: status=" + journey.getStatus());
        }
        // 校验客户存在性 + 数据隔离
        ScrmCustomerEntity customer = customerRepository.findById(enrollDto.getCustomerId())
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "客户不存在: id=" + enrollDto.getCustomerId()));

        // 校验未重复入营 (ACTIVE 状态)
        if (enrollmentRepository.existsByJourneyIdAndCustomerIdAndStatus(
                 enrollDto.getJourneyId(), enrollDto.getCustomerId(),
                ENROLLMENT_STATUS_ACTIVE)) {
            throw new ScrmException(ScrmExceptionConstants.CONFLICT,
                    "客户已入此旅程且仍在进行中, 不允许重复入营: customerId=" + enrollDto.getCustomerId());
        }
        // 检查入旅程条件 (filter.platformType 等)
        checkEnrollmentCondition(journey, customer);
        // 确定入口步骤 (优先 isEntryPoint=true, 否则取 stepOrder 最小者)
        List<ScrmJourneyStepEntity> steps = stepRepository
                .findByJourneyIdOrderByStepOrderAsc(enrollDto.getJourneyId());
        if (steps.isEmpty()) {
            throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                    "旅程未配置任何步骤, 不允许入营: journeyId=" + enrollDto.getJourneyId());
        }
        ScrmJourneyStepEntity entryStep = steps.stream()
                .filter(s -> Boolean.TRUE.equals(s.getIsEntryPoint()))
                .findFirst()
                .orElse(steps.get(0));
        // 创建入营记录
        ScrmJourneyEnrollmentEntity enrollment = new ScrmJourneyEnrollmentEntity();
        enrollment.setJourneyId(enrollDto.getJourneyId());
        enrollment.setCustomerId(enrollDto.getCustomerId());
        enrollment.setCustomerNickname(customer.getNickname());
        enrollment.setCurrentStepId(entryStep.getId());
        enrollment.setEntrySource(enrollDto.getSource());
        enrollment.setStatus(ENROLLMENT_STATUS_ACTIVE);
        enrollment.setEnteredAt(LocalDateTime.now());
        enrollment.setLastStepAt(LocalDateTime.now());
        enrollment.setProgress(0);
        enrollment = enrollmentRepository.save(enrollment);
        // 旅程入营计数 +1
        journey.setEnrolledCount((journey.getEnrolledCount() == null ? 0 : journey.getEnrolledCount()) + 1);
        stepExecutionService.recalcConversionRate(journey);
        journeyRepository.save(journey);
        log.info("客户入旅程: journeyId={}, customerId={}, enrollmentId={}, entryStepId={}",
                enrollDto.getJourneyId(), enrollDto.getCustomerId(),
                enrollment.getId(), entryStep.getId());
        // 执行入口步骤
        stepExecutionService.processStep(enrollment.getId());
        return stepExecutionService.toEnrollmentDto(enrollment);
    }

    /**
     * 批量入旅程
     * <p>
     * 对客户列表逐一执行入营流程, 单个客户失败跳过并记录告警, 不阻断其他客户。
     * </p>
     *
     * @param journeyId   旅程 ID
     * @param customerIds 客户 ID 列表
     * @param source      入营来源: EVENT / MANUAL / API
     * @return 成功入营的客户数
     * @throws ScrmException 旅程不存在
     */
    @Transactional
    public int batchEnroll(Long journeyId, List<Long> customerIds, String source) throws ScrmException {
        stepExecutionService.findJourneyOrThrow(journeyId);
        if (customerIds == null || customerIds.isEmpty()) {
            throw ScrmException.badRequest("客户 ID 列表不能为空");
        }
        String enrollSource = (source == null || source.isBlank()) ? SOURCE_MANUAL : source;
        int success = 0;
        for (Long customerId : customerIds) {
            if (customerId == null) {
                continue;
            }
            try {
                ScrmJourneyEnrollDto dto = new ScrmJourneyEnrollDto();
                dto.setJourneyId(journeyId);
                dto.setCustomerId(customerId);
                dto.setSource(enrollSource);
                enroll(dto);
                success++;
            } catch (ScrmException e) {
                log.warn("批量入营失败, 跳过: journeyId={}, customerId={}, code={}, msg={}",
                        journeyId, customerId, e.getCode(), e.getMessage());
            }
        }
        log.info("批量入旅程完成: journeyId={}, requested={}, success={}",
                journeyId, customerIds.size(), success);
        return success;
    }

    /**
     * 退出旅程
     * <p>
     * 将入营记录状态置 EXITED, 记录退出原因与退出时间, 旅程退出计数 +1。
     * </p>
     *
     * @param enrollmentId 入营记录 ID
     * @param reason       退出原因
     * @return 更新后的入营记录
     * @throws ScrmException 入营记录不存在 / 状态非法
     */
    @Transactional
    public ScrmJourneyEnrollmentDto exitEnrollment(Long enrollmentId, String reason) throws ScrmException {
        ScrmJourneyEnrollmentEntity entity = stepExecutionService.findEnrollmentOrThrow(enrollmentId);
        if (!ENROLLMENT_STATUS_ACTIVE.equals(entity.getStatus())) {
            throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                    "入营状态非法, 仅 ACTIVE 可退出: currentStatus=" + entity.getStatus());
        }
        entity.setStatus(ENROLLMENT_STATUS_EXITED);
        entity.setExitedAt(LocalDateTime.now());
        entity.setExitReason(reason);
        entity = enrollmentRepository.save(entity);
        // 旅程退出计数 +1
        ScrmCustomerJourneyEntity journey = stepExecutionService.findJourneyOrThrow(entity.getJourneyId());
        journey.setExitedCount((journey.getExitedCount() == null ? 0 : journey.getExitedCount()) + 1);
        journeyRepository.save(journey);
        log.info("客户退出旅程: enrollmentId={}, journeyId={}, customerId={}, reason={}",
                enrollmentId, entity.getJourneyId(), entity.getCustomerId(), reason);
        return stepExecutionService.toEnrollmentDto(entity);
    }

    /**
     * 查询入营记录详情
     *
     * @param id 入营记录 ID
     * @return 入营记录 DTO
     * @throws ScrmException 入营记录不存在
     */
    @Transactional(readOnly = true)
    public ScrmJourneyEnrollmentDto getEnrollment(Long id) throws ScrmException {
        return stepExecutionService.toEnrollmentDto(stepExecutionService.findEnrollmentOrThrow(id));
    }

    /**
     * 分页查询入营记录, 支持按旅程 / 状态 / 客户过滤
     *
     * @param journeyId  旅程 ID 过滤 (可空)
     * @param status     入营状态过滤 (可空)
     * @param customerId 客户 ID 过滤 (可空)
     * @param pageable    分页参数
     * @return 入营记录分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmJourneyEnrollmentDto> listEnrollments(Long journeyId, String status,
                                                            Long customerId, Pageable pageable) {
        Specification<ScrmJourneyEnrollmentEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (journeyId != null) {
                predicates.add(cb.equal(root.get("journeyId"), journeyId));
            }
            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(cb.lower(root.get("status")), status.toLowerCase()));
            }
            if (customerId != null) {
                predicates.add(cb.equal(root.get("customerId"), customerId));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        Pageable sorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, "enteredAt"));
        return enrollmentRepository.findAll(spec, sorted)
                .map(e -> stepExecutionService.toEnrollmentDto(e));
    }

    // ============================================================
    // 内部工具方法
    // ============================================================

    /**
     * 检查入旅程条件 (entryCondition 中的 filter.platformType 等)
     * <p>
     * 当前实现: 解析 entryCondition JSON, 若含 filter.platformType 则校验客户平台类型匹配。
     * MANUAL / API 来源视为手动覆盖, 即使旅程 entryType 为 EVENT 也允许入营。
     * </p>
     *
     * @param journey  旅程实体
     * @param customer 客户实体
     * @throws ScrmException 条件不满足
     */
    private void checkEnrollmentCondition(ScrmCustomerJourneyEntity journey,
                                          ScrmCustomerEntity customer) throws ScrmException {
        Map<String, Object> condition = stepExecutionService.parseConfig(journey.getEntryCondition());
        Object filterObj = condition.get("filter");
        if (filterObj instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> filter = (Map<String, Object>) filterObj;
            Object platformType = filter.get("platformType");
            if (platformType != null && !Objects.equals(stepExecutionService.str(platformType), customer.getPlatformType())) {
                throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                        "客户平台类型不匹配入旅程条件: expected=" + platformType
                                + ", actual=" + customer.getPlatformType());
            }
        }
    }
}
