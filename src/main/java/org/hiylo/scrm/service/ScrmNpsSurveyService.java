/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmNpsSurveyService.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import lombok.RequiredArgsConstructor;

import org.hiylo.scrm.dto.ScrmSurveyDistributeDto;
import org.hiylo.scrm.dto.ScrmSurveyDto;
import org.hiylo.scrm.dto.ScrmSurveyInvitationDto;
import org.hiylo.scrm.dto.ScrmSurveySubmitDto;
import org.hiylo.scrm.entity.ScrmNpsBenchmarkEntity;
import org.hiylo.scrm.entity.ScrmSurveyEntity;
import org.hiylo.scrm.entity.ScrmSurveyInvitationEntity;
import org.hiylo.scrm.entity.ScrmSurveyResponseEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * SCRM 客户满意度 NPS 调查服务 (门面)。
 * <p>
 * 保留对外全部 public 方法签名, 按子域委托给兄弟服务: 问卷管理
 * ({@link ScrmNpsSurveyManageService})、邀请管理 ({@link ScrmNpsSurveyInvitationService})、
 * 回复管理 ({@link ScrmNpsSurveyResponseService})、基准与统计
 * ({@link ScrmNpsSurveyAnalyticsService})。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Service
@RequiredArgsConstructor
public class ScrmNpsSurveyService {

    /** 问卷管理子域服务 */
    private final ScrmNpsSurveyManageService manageService;

    /** 邀请管理子域服务 */
    private final ScrmNpsSurveyInvitationService invitationService;

    /** 回复管理子域服务 */
    private final ScrmNpsSurveyResponseService responseService;

    /** 基准与统计子域服务 */
    private final ScrmNpsSurveyAnalyticsService analyticsService;

    /**
     * 创建调查问卷。
     *
     * @param dto 问卷参数
     * @return 创建后的问卷
     * @throws ScrmException 参数非法
     */
    public ScrmSurveyEntity createSurvey(ScrmSurveyDto dto) throws ScrmException {
        return manageService.createSurvey(dto);
    }

    /**
     * 更新调查问卷（字段非空才覆盖）。
     *
     * @param id  问卷 ID
     * @param dto 问卷参数
     * @return 更新后的问卷
     * @throws ScrmException 问卷不存在 / 已归档 / 参数非法
     */
    public ScrmSurveyEntity updateSurvey(Long id, ScrmSurveyDto dto) throws ScrmException {
        return manageService.updateSurvey(id, dto);
    }

    /**
     * 删除调查问卷。
     *
     * @param id 问卷 ID
     * @throws ScrmException 问卷不存在 / 状态非法
     */
    public void deleteSurvey(Long id) throws ScrmException {
        manageService.deleteSurvey(id);
    }

    /**
     * 查询问卷详情。
     *
     * @param id 问卷 ID
     * @return 问卷实体
     * @throws ScrmException 问卷不存在
     */
    public ScrmSurveyEntity getSurvey(Long id) throws ScrmException {
        return manageService.getSurvey(id);
    }

    /**
     * 分页查询问卷。
     *
     * @param surveyType 调查类型过滤（可空）
     * @param status     状态过滤（可空）
     * @param keyword    问卷名称关键字模糊匹配（可空）
     * @param pageable   分页参数
     * @return 问卷分页结果 (按 createTime DESC)
     */
    public Page<ScrmSurveyEntity> listSurveys(String surveyType, String status, String keyword, Pageable pageable) {
        return manageService.listSurveys(surveyType, status, keyword, pageable);
    }

    /**
     * 激活问卷。
     *
     * @param id 问卷 ID
     * @return 更新后的问卷
     * @throws ScrmException 问卷不存在 / 状态非法
     */
    public ScrmSurveyEntity activateSurvey(Long id) throws ScrmException {
        return manageService.activateSurvey(id);
    }

    /**
     * 暂停问卷。
     *
     * @param id 问卷 ID
     * @return 更新后的问卷
     * @throws ScrmException 问卷不存在 / 状态非法
     */
    public ScrmSurveyEntity pauseSurvey(Long id) throws ScrmException {
        return manageService.pauseSurvey(id);
    }

    /**
     * 完成问卷。
     *
     * @param id 问卷 ID
     * @return 更新后的问卷
     * @throws ScrmException 问卷不存在 / 状态非法
     */
    public ScrmSurveyEntity completeSurvey(Long id) throws ScrmException {
        return manageService.completeSurvey(id);
    }

    /**
     * 归档问卷。
     *
     * @param id 问卷 ID
     * @return 更新后的问卷
     * @throws ScrmException 问卷不存在
     */
    public ScrmSurveyEntity archiveSurvey(Long id) throws ScrmException {
        return manageService.archiveSurvey(id);
    }

    /**
     * 复制问卷。
     *
     * @param id 源问卷 ID
     * @return 复制后的新问卷
     * @throws ScrmException 源问卷不存在
     */
    public ScrmSurveyEntity copySurvey(Long id) throws ScrmException {
        return manageService.copySurvey(id);
    }

    /**
     * 通过邀请码获取问卷。
     *
     * @param code 邀请码
     * @return 问卷实体
     * @throws ScrmException 邀请不存在 / 问卷不存在
     */
    public ScrmSurveyEntity getSurveyByInvitationCode(String code) throws ScrmException {
        return manageService.getSurveyByInvitationCode(code);
    }

    /**
     * 创建调查邀请。
     *
     * @param dto 邀请参数
     * @return 创建后的邀请
     * @throws ScrmException 参数非法 / 问卷不存在 / 重复创建
     */
    public ScrmSurveyInvitationEntity createInvitation(ScrmSurveyInvitationDto dto) throws ScrmException {
        return invitationService.createInvitation(dto);
    }

    /**
     * 批量创建邀请并模拟发送。
     *
     * @param distributeDto 分发参数
     * @return 分发结果
     * @throws ScrmException 问卷不存在 / 问卷未激活
     */
    public Map<String, Object> batchCreateInvitations(ScrmSurveyDistributeDto distributeDto) throws ScrmException {
        return invitationService.batchCreateInvitations(distributeDto);
    }

    /**
     * 查询邀请详情。
     *
     * @param id 邀请 ID
     * @return 邀请实体
     * @throws ScrmException 邀请不存在
     */
    public ScrmSurveyInvitationEntity getInvitation(Long id) throws ScrmException {
        return invitationService.getInvitation(id);
    }

    /**
     * 按邀请码查询邀请。
     *
     * @param code 邀请码
     * @return 邀请实体
     * @throws ScrmException 邀请不存在
     */
    public ScrmSurveyInvitationEntity getInvitationByCode(String code) throws ScrmException {
        return invitationService.getInvitationByCode(code);
    }

    /**
     * 分页查询邀请。
     *
     * @param surveyId  问卷 ID 过滤（可空）
     * @param status    状态过滤（可空）
     * @param channel   渠道过滤（可空）
     * @param startTime 发送时间起始 (含, 可空)
     * @param endTime   发送时间截止 (含, 可空)
     * @param pageable  分页参数
     * @return 邀请分页结果 (按 createTime DESC)
     */
    public Page<ScrmSurveyInvitationEntity> listInvitations(Long surveyId, String status, String channel,
                                                              LocalDateTime startTime, LocalDateTime endTime,
                                                              Pageable pageable) {
        return invitationService.listInvitations(surveyId, status, channel, startTime, endTime, pageable);
    }

    /**
     * 发送邀请 (模拟实现)。
     *
     * @param id 邀请 ID
     * @return 更新后的邀请
     * @throws ScrmException 邀请不存在 / 状态非法
     */
    public ScrmSurveyInvitationEntity sendInvitation(Long id) throws ScrmException {
        return invitationService.sendInvitation(id);
    }

    /**
     * 发送提醒 (模拟实现)。
     *
     * @param id 邀请 ID
     * @return 更新后的邀请
     * @throws ScrmException 邀请不存在 / 状态非法
     */
    public ScrmSurveyInvitationEntity sendReminder(Long id) throws ScrmException {
        return invitationService.sendReminder(id);
    }

    /**
     * 过期邀请。
     *
     * @param id 邀请 ID
     * @return 更新后的邀请
     * @throws ScrmException 邀请不存在 / 状态非法
     */
    public ScrmSurveyInvitationEntity expireInvitation(Long id) throws ScrmException {
        return invitationService.expireInvitation(id);
    }

    /**
     * 取消邀请。
     *
     * @param id 邀请 ID
     * @return 更新后的邀请
     * @throws ScrmException 邀请不存在 / 状态非法
     */
    public ScrmSurveyInvitationEntity cancelInvitation(Long id) throws ScrmException {
        return invitationService.cancelInvitation(id);
    }

    /**
     * 提交调查回答。
     *
     * @param submitDto 提交参数
     * @return 创建后的回复
     * @throws ScrmException 邀请不存在 / 问卷不存在 / 状态非法 / 回答 JSON 解析失败
     */
    public ScrmSurveyResponseEntity submitResponse(ScrmSurveySubmitDto submitDto) throws ScrmException {
        return responseService.submitResponse(submitDto);
    }

    /**
     * 查询回复详情。
     *
     * @param id 回复 ID
     * @return 回复实体
     * @throws ScrmException 回复不存在
     */
    public ScrmSurveyResponseEntity getResponse(Long id) throws ScrmException {
        return responseService.getResponse(id);
    }

    /**
     * 分页查询回复。
     *
     * @param surveyId         问卷 ID 过滤（可空）
     * @param customerId       客户 ID 过滤（可空）
     * @param npsScore         NPS 分数过滤（可空）
     * @param sentiment        情感过滤（可空）
     * @param followUpRequired 是否需要跟进过滤（可空）
     * @param startTime        提交时间起始 (含, 可空)
     * @param endTime          提交时间截止 (含, 可空)
     * @param pageable         分页参数
     * @return 回复分页结果 (按 submittedAt DESC)
     */
    public Page<ScrmSurveyResponseEntity> listResponses(Long surveyId, Long customerId, Integer npsScore,
                                                          String sentiment, Boolean followUpRequired,
                                                          LocalDateTime startTime, LocalDateTime endTime,
                                                          Pageable pageable) {
        return responseService.listResponses(surveyId, customerId, npsScore, sentiment, followUpRequired,
                startTime, endTime, pageable);
    }

    /**
     * 更新回复跟进状态。
     *
     * @param id          回复 ID
     * @param status      跟进状态
     * @param assigneeId  跟进人 ID (可空)
     * @return 更新后的回复
     * @throws ScrmException 回复不存在 / 跟进状态非法
     */
    public ScrmSurveyResponseEntity updateFollowUpStatus(Long id, String status, String assigneeId)
            throws ScrmException {
        return responseService.updateFollowUpStatus(id, status, assigneeId);
    }

    /**
     * 添加标签。
     *
     * @param id    回复 ID
     * @param tags  待添加标签 (逗号分隔)
     * @return 更新后的回复
     * @throws ScrmException 回复不存在
     */
    public ScrmSurveyResponseEntity addTags(Long id, String tags) throws ScrmException {
        return responseService.addTags(id, tags);
    }

    /**
     * 生成 NPS 基准。
     *
     * @param periodType  周期类型
     * @param startDate   周期开始日期
     * @param endDate     周期结束日期
     * @return 生成 / 更新后的基准
     * @throws ScrmException 参数非法
     */
    public ScrmNpsBenchmarkEntity generateBenchmark(String periodType, LocalDate startDate, LocalDate endDate)
            throws ScrmException {
        return analyticsService.generateBenchmark(periodType, startDate, endDate);
    }

    /**
     * 查询基准详情。
     *
     * @param id 基准 ID
     * @return 基准实体
     * @throws ScrmException 基准不存在
     */
    public ScrmNpsBenchmarkEntity getBenchmark(Long id) throws ScrmException {
        return analyticsService.getBenchmark(id);
    }

    /**
     * 分页查询基准。
     *
     * @param periodType 周期类型过滤（可空）
     * @param startDate  周期开始日期起始 (含, 可空)
     * @param endDate    周期结束日期截止 (含, 可空)
     * @param pageable   分页参数
     * @return 基准分页结果 (按 generatedAt DESC)
     */
    public Page<ScrmNpsBenchmarkEntity> listBenchmarks(String periodType, LocalDate startDate, LocalDate endDate,
                                                         Pageable pageable) {
        return analyticsService.listBenchmarks(periodType, startDate, endDate, pageable);
    }

    /**
     * 查询最新基准。
     *
     * @param periodType 周期类型
     * @return 最新基准, 不存在返回 null
     * @throws ScrmException 周期类型非法
     */
    public ScrmNpsBenchmarkEntity getLatestBenchmark(String periodType) throws ScrmException {
        return analyticsService.getLatestBenchmark(periodType);
    }

    /**
     * 计算 NPS 分数。
     *
     * @param responses 回复列表
     * @return NPS 分数 (-100 到 100)
     */
    public int calculateNps(List<ScrmSurveyResponseEntity> responses) {
        return analyticsService.calculateNps(responses);
    }

    /**
     * 问卷统计。
     *
     * @param surveyId 问卷 ID
     * @return 统计结果 Map
     * @throws ScrmException 问卷不存在
     */
    public Map<String, Object> getSurveyStats(Long surveyId) throws ScrmException {
        return analyticsService.getSurveyStats(surveyId);
    }

    /**
     * 总体统计。
     *
     * @param startTime 提交时间起始 (含, 可空)
     * @param endTime   提交时间截止 (含, 可空)
     * @return 统计结果 Map
     */
    public Map<String, Object> getOverallStats(LocalDateTime startTime, LocalDateTime endTime) {
        return analyticsService.getOverallStats(startTime, endTime);
    }

    /**
     * NPS 趋势。
     *
     * @param months 月数
     * @return 趋势列表
     */
    public List<Map<String, Object>> getNpsTrend(int months) {
        return analyticsService.getNpsTrend(months);
    }

    /**
     * 回复趋势。
     *
     * @param surveyId 问卷 ID (可空)
     * @param days      天数
     * @return 趋势列表
     * @throws ScrmException 问卷不存在 (surveyId 非空时)
     */
    public List<Map<String, Object>> getResponseTrend(Long surveyId, int days) throws ScrmException {
        return analyticsService.getResponseTrend(surveyId, days);
    }

    /**
     * 情感分布。
     *
     * @param surveyId 问卷 ID (可空)
     * @return 情感分布 Map
     * @throws ScrmException 问卷不存在 (surveyId 非空时)
     */
    public Map<String, Long> getSentimentDistribution(Long surveyId) throws ScrmException {
        return analyticsService.getSentimentDistribution(surveyId);
    }

    /**
     * 获取高频反馈。
     *
     * @param surveyId 问卷 ID (可空)
     * @param limit    返回条数
     * @return 高频反馈列表
     * @throws ScrmException 问卷不存在 (surveyId 非空时)
     */
    public List<Map<String, Object>> getTopFeedback(Long surveyId, int limit) throws ScrmException {
        return analyticsService.getTopFeedback(surveyId, limit);
    }
}