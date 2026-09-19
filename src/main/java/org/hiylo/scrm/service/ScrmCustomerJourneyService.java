/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCustomerJourneyService.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.dto.ScrmCustomerJourneyDto;
import org.hiylo.scrm.dto.ScrmJourneyEnrollDto;
import org.hiylo.scrm.dto.ScrmJourneyEnrollmentDto;
import org.hiylo.scrm.dto.ScrmJourneyProgressLogDto;
import org.hiylo.scrm.dto.ScrmJourneyStepDto;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.vo.JourneyStatsVo;
import org.hiylo.scrm.vo.JourneyStepStatsVo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * SCRM 营销 SOP 客户旅程服务 (门面)
 * <p>
 * 负责客户旅程的全生命周期管理, 包括旅程定义 / 步骤编排 / 客户入营 / 步骤自动执行 /
 * 进度跟踪与统计。区别于设备行为流, 本模块面向客户关系的 SOP 编排。
 * </p>
 * <p>
 * 步骤执行支持七种动作: SEND_MESSAGE (发消息) / WAIT (等待) / CONDITION (条件分支) /
 * ADD_TAG (打标签) / SET_LIFECYCLE (改生命周期) / WEBHOOK (回调通知) / END (结束)。
 * 其中 SEND_MESSAGE (会话消息持久化) / ADD_TAG (标签赋值) / SET_LIFECYCLE (客户字段更新) /
 * WEBHOOK (真实 HTTP 回调) 均为真实执行。
 * </p>
 * <p>
 * 本类为门面, 所有方法委托给子域兄弟服务:
 * {@link ScrmCustomerJourneyDefinitionService} (旅程定义与步骤) /
 * {@link ScrmJourneyEnrollmentService} (入营管理) /
 * {@link ScrmJourneyStepExecutionService} (步骤执行) /
 * {@link ScrmJourneyStatsService} (统计与进度)。
 * </p>
 *
 * @author Hsi Chu
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmCustomerJourneyService {

    /** 旅程定义与步骤编排子域服务 */
    private final ScrmCustomerJourneyDefinitionService journeyDefinitionService;
    /** 旅程入营管理子域服务 */
    private final ScrmJourneyEnrollmentService journeyEnrollmentService;
    /** 旅程步骤执行子域服务 */
    private final ScrmJourneyStepExecutionService stepExecutionService;
    /** 旅程统计与进度子域服务 */
    private final ScrmJourneyStatsService statsService;

    /**
     * 创建客户旅程
     *
     * @param dto 旅程参数
     * @return 创建后的旅程
     * @throws ScrmException 参数非法
     */
    public ScrmCustomerJourneyDto createJourney(ScrmCustomerJourneyDto dto) throws ScrmException {
        return journeyDefinitionService.createJourney(dto);
    }

    /**
     * 更新客户旅程
     *
     * @param id  旅程 ID
     * @param dto 旅程参数
     * @return 更新后的旅程
     * @throws ScrmException 旅程不存在 / 状态非法
     */
    public ScrmCustomerJourneyDto updateJourney(Long id, ScrmCustomerJourneyDto dto) throws ScrmException {
        return journeyDefinitionService.updateJourney(id, dto);
    }

    /**
     * 删除客户旅程
     *
     * @param id 旅程 ID
     * @throws ScrmException 旅程不存在 / 状态非法
     */
    public void deleteJourney(Long id) throws ScrmException {
        journeyDefinitionService.deleteJourney(id);
    }

    /**
     * 查询客户旅程详情
     *
     * @param id 旅程 ID
     * @return 旅程 DTO
     * @throws ScrmException 旅程不存在
     */
    public ScrmCustomerJourneyDto getJourney(Long id) throws ScrmException {
        return journeyDefinitionService.getJourney(id);
    }

    /**
     * 分页查询客户旅程, 支持按状态与关键词过滤
     *
     * @param status  状态过滤 (可空)
     * @param keyword 关键词过滤, 匹配旅程名称 (可空)
     * @param pageable 分页参数
     * @return 旅程分页结果
     */
    public Page<ScrmCustomerJourneyDto> listJourneys(String status, String keyword, Pageable pageable) {
        return journeyDefinitionService.listJourneys(status, keyword, pageable);
    }

    /**
     * 发布客户旅程
     *
     * @param id 旅程 ID
     * @return 更新后的旅程
     * @throws ScrmException 旅程不存在 / 状态非法
     */
    public ScrmCustomerJourneyDto publishJourney(Long id) throws ScrmException {
        return journeyDefinitionService.publishJourney(id);
    }

    /**
     * 暂停客户旅程
     *
     * @param id 旅程 ID
     * @return 更新后的旅程
     * @throws ScrmException 旅程不存在 / 状态非法
     */
    public ScrmCustomerJourneyDto pauseJourney(Long id) throws ScrmException {
        return journeyDefinitionService.pauseJourney(id);
    }

    /**
     * 归档客户旅程
     *
     * @param id 旅程 ID
     * @return 更新后的旅程
     * @throws ScrmException 旅程不存在 / 状态非法
     */
    public ScrmCustomerJourneyDto archiveJourney(Long id) throws ScrmException {
        return journeyDefinitionService.archiveJourney(id);
    }

    /**
     * 复制客户旅程
     *
     * @param id 源旅程 ID
     * @return 复制后的新旅程
     * @throws ScrmException 源旅程不存在
     */
    public ScrmCustomerJourneyDto copyJourney(Long id) throws ScrmException {
        return journeyDefinitionService.copyJourney(id);
    }

    /**
     * 新增旅程步骤
     *
     * @param journeyId 旅程 ID
     * @param dto       步骤参数
     * @return 创建后的步骤
     * @throws ScrmException 旅程不存在 / 参数非法
     */
    public ScrmJourneyStepDto addStep(Long journeyId, ScrmJourneyStepDto dto) throws ScrmException {
        return journeyDefinitionService.addStep(journeyId, dto);
    }

    /**
     * 更新旅程步骤
     *
     * @param id  步骤 ID
     * @param dto 步骤参数
     * @return 更新后的步骤
     * @throws ScrmException 步骤不存在
     */
    public ScrmJourneyStepDto updateStep(Long id, ScrmJourneyStepDto dto) throws ScrmException {
        return journeyDefinitionService.updateStep(id, dto);
    }

    /**
     * 删除旅程步骤
     *
     * @param id 步骤 ID
     * @throws ScrmException 步骤不存在 / 仍被引用
     */
    public void deleteStep(Long id) throws ScrmException {
        journeyDefinitionService.deleteStep(id);
    }

    /**
     * 查询旅程的全部步骤 (按 stepOrder 升序)
     *
     * @param journeyId 旅程 ID
     * @return 步骤列表
     * @throws ScrmException 旅程不存在
     */
    public List<ScrmJourneyStepDto> listSteps(Long journeyId) throws ScrmException {
        return journeyDefinitionService.listSteps(journeyId);
    }

    /**
     * 批量重排旅程步骤顺序
     *
     * @param journeyId 旅程 ID
     * @param stepIds   步骤 ID 列表 (按新顺序排列)
     * @return 重排后的步骤列表
     * @throws ScrmException 旅程不存在 / 步骤不属于此旅程
     */
    public List<ScrmJourneyStepDto> reorderSteps(Long journeyId, List<Long> stepIds) throws ScrmException {
        return journeyDefinitionService.reorderSteps(journeyId, stepIds);
    }

    /**
     * 客户入旅程
     *
     * @param enrollDto 入营请求
     * @return 创建后的入营记录 (入口步骤已执行)
     * @throws ScrmException 旅程未发布 / 客户不存在 / 条件不满足 / 已入营
     */
    public ScrmJourneyEnrollmentDto enroll(ScrmJourneyEnrollDto enrollDto) throws ScrmException {
        return journeyEnrollmentService.enroll(enrollDto);
    }

    /**
     * 批量入旅程
     *
     * @param journeyId   旅程 ID
     * @param customerIds 客户 ID 列表
     * @param source      入营来源: EVENT / MANUAL / API
     * @return 成功入营的客户数
     * @throws ScrmException 旅程不存在
     */
    public int batchEnroll(Long journeyId, List<Long> customerIds, String source) throws ScrmException {
        return journeyEnrollmentService.batchEnroll(journeyId, customerIds, source);
    }

    /**
     * 退出旅程
     *
     * @param enrollmentId 入营记录 ID
     * @param reason       退出原因
     * @return 更新后的入营记录
     * @throws ScrmException 入营记录不存在 / 状态非法
     */
    public ScrmJourneyEnrollmentDto exitEnrollment(Long enrollmentId, String reason) throws ScrmException {
        return journeyEnrollmentService.exitEnrollment(enrollmentId, reason);
    }

    /**
     * 查询入营记录详情
     *
     * @param id 入营记录 ID
     * @return 入营记录 DTO
     * @throws ScrmException 入营记录不存在
     */
    public ScrmJourneyEnrollmentDto getEnrollment(Long id) throws ScrmException {
        return journeyEnrollmentService.getEnrollment(id);
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
    public Page<ScrmJourneyEnrollmentDto> listEnrollments(Long journeyId, String status,
                                                            Long customerId, Pageable pageable) {
        return journeyEnrollmentService.listEnrollments(journeyId, status, customerId, pageable);
    }

    /**
     * 执行入营记录的当前步骤
     *
     * @param enrollmentId 入营记录 ID
     * @return 更新后的入营记录
     * @throws ScrmException 入营记录不存在 / 步骤不存在
     */
    public ScrmJourneyEnrollmentDto processStep(Long enrollmentId) throws ScrmException {
        return stepExecutionService.processStep(enrollmentId);
    }

    /**
     * 处理所有待执行的 WAIT 到期步骤 (定时任务用)
     *
     * @return 处理的入营记录数
     */
    public int processPendingSteps() {
        return stepExecutionService.processPendingSteps();
    }

    /**
     * 旅程统计: 入旅程数 / 完成数 / 退出数 / 转化率 / 当前活跃数 / 各步骤通过率
     *
     * @param journeyId 旅程 ID
     * @return 旅程统计结果
     * @throws ScrmException 旅程不存在
     */
    public JourneyStatsVo getJourneyStats(Long journeyId) throws ScrmException {
        return statsService.getJourneyStats(journeyId);
    }

    /**
     * 各步骤统计: 进入数 / 成功 / 失败 / 跳过 / 等待 / 通过率
     *
     * @param journeyId 旅程 ID
     * @return 步骤统计列表 (按 stepOrder 升序)
     * @throws ScrmException 旅程不存在
     */
    public List<JourneyStepStatsVo> getStepStats(Long journeyId) throws ScrmException {
        return statsService.getStepStats(journeyId);
    }

    /**
     * 查询入营记录的进度日志 (按执行时间升序)
     *
     * @param enrollmentId 入营记录 ID
     * @return 进度日志列表
     * @throws ScrmException 入营记录不存在
     */
    public List<ScrmJourneyProgressLogDto> getProgressLog(Long enrollmentId) throws ScrmException {
        return statsService.getProgressLog(enrollmentId);
    }
}