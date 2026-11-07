/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm-finance
 * File : ScrmTaxHolidayController.java
 * Date : 2026/10/27 11:30:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hiylo.scrm.common.OperationResponse;
import org.hiylo.scrm.dto.ScrmTaxHolidayDto;
import org.hiylo.scrm.dto.ScrmTaxWorkdayOverrideDto;
import org.hiylo.scrm.entity.ScrmTaxHolidayEntity;
import org.hiylo.scrm.entity.ScrmTaxWorkdayOverrideEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.rbac.annotation.RequirePermission;
import org.hiylo.scrm.repository.ScrmTaxHolidayRepository;
import org.hiylo.scrm.repository.ScrmTaxWorkdayOverrideRepository;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 税务假期维护控制器。
 * <p>
 * 申报截止日遇到法定节假日要顺延，假期数据每年由国务院公告，因此必须可由运维自助维护，
 * 不能写死在代码里。本控制器提供假期与调休补班日的增删改查，以及「某年数据是否齐备」的自检。
 * </p>
 * <p>
 * <b>为什么需要自检</b>：假期数据缺失时，顺延逻辑会静默退化为「只跳周末」——
 * 不报错、但节假日顺延不生效，导致截止日算错。这种静默失效必须能被主动发现，
 * 因此 {@code /coverage} 端点按年检查数据条数与是否有明显空档。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Slf4j
@RestController
@RequestMapping("/scrm/tax-holidays")
@RequiredArgsConstructor
public class ScrmTaxHolidayController {

    /** 一个完整年度至少应有的法定假期数（元旦/春节/清明/劳动/端午/中秋/国庆） */
    private static final int MIN_HOLIDAYS_PER_YEAR = 7;

    /** 税务假期仓储 */
    private final ScrmTaxHolidayRepository holidayRepository;

    /** 调休上班日仓储 */
    private final ScrmTaxWorkdayOverrideRepository workdayOverrideRepository;

    /** 假期 AI 抽取编排服务 */
    private final org.hiylo.scrm.service.finance.tax.ScrmTaxHolidayAiService aiService;

    /** 草稿仓储 */
    private final org.hiylo.scrm.repository.ScrmTaxHolidayDraftRepository draftRepository;

    // ============================================================
    // 假期
    // ============================================================

    /**
     * 按年份查询假期。
     *
     * @param year 年份（可空，空则查全部）
     * @return 假期列表（按起始日升序）
     */
    @RequirePermission(resource = "scrm_tax_holiday", action = "read")
    @GetMapping
    public OperationResponse<List<ScrmTaxHolidayEntity>> list(@RequestParam(required = false) Integer year) {
        List<ScrmTaxHolidayEntity> list = year == null
                ? holidayRepository.findAll(Sort.by("startDate"))
                : holidayRepository.findByHolidayYearAndIsActiveTrueOrderByStartDateAsc(year);
        return OperationResponse.build(list);
    }

    /**
     * 新增假期。
     *
     * @param dto 假期信息
     * @return 创建后的假期
     * @throws ScrmException 日期区间非法
     */
    @RequirePermission(resource = "scrm_tax_holiday", action = "create")
    @PostMapping
    public OperationResponse<ScrmTaxHolidayEntity> create(@RequestBody ScrmTaxHolidayDto dto)
            throws ScrmException {
        validateRange(dto);
        ScrmTaxHolidayEntity entity = new ScrmTaxHolidayEntity();
        applyFields(entity, dto);
        entity = holidayRepository.save(entity);
        log.info("[税务假期] 新增: id={}, name={}, {}~{}",
                entity.getId(), entity.getHolidayName(), entity.getStartDate(), entity.getEndDate());
        return OperationResponse.build(entity);
    }

    /**
     * 更新假期。
     *
     * @param id  假期 ID
     * @param dto 假期信息
     * @return 更新后的假期
     * @throws ScrmException 假期不存在 / 日期区间非法
     */
    @RequirePermission(resource = "scrm_tax_holiday", action = "update")
    @PutMapping("/{id}")
    public OperationResponse<ScrmTaxHolidayEntity> update(@PathVariable Long id,
                                                          @RequestBody ScrmTaxHolidayDto dto)
            throws ScrmException {
        ScrmTaxHolidayEntity entity = holidayRepository.findById(id)
                .orElseThrow(() -> ScrmException.notFound("假期不存在: id=" + id));
        validateRange(dto);
        applyFields(entity, dto);
        entity = holidayRepository.save(entity);
        log.info("[税务假期] 更新: id={}, name={}, {}~{}",
                entity.getId(), entity.getHolidayName(), entity.getStartDate(), entity.getEndDate());
        return OperationResponse.build(entity);
    }

    /**
     * 删除假期。
     * <p>
     * 删除会让该年份的顺延失效（退化为只跳周末），因此这里直接物理删除并在日志留痕，
     * 由页面提示用户谨慎操作；不引入软删除是因为「停用」与「删除」对顺延的效果相同，
     * 多一层状态只会让数据核对变复杂。
     * </p>
     *
     * @param id 假期 ID
     * @return 空响应
     * @throws ScrmException 假期不存在
     */
    @RequirePermission(resource = "scrm_tax_holiday", action = "delete")
    @DeleteMapping("/{id}")
    public OperationResponse<Void> delete(@PathVariable Long id) throws ScrmException {
        ScrmTaxHolidayEntity entity = holidayRepository.findById(id)
                .orElseThrow(() -> ScrmException.notFound("假期不存在: id=" + id));
        holidayRepository.delete(entity);
        log.warn("[税务假期] 删除: id={}, name={}, {}~{}（该期间顺延将失效）",
                id, entity.getHolidayName(), entity.getStartDate(), entity.getEndDate());
        return OperationResponse.build(null);
    }

    // ============================================================
    // 调休上班日
    // ============================================================

    /**
     * 查询调休上班日。
     *
     * @param year 年份（可空，空则查全部）
     * @return 补班日列表（按日期升序）
     */
    @RequirePermission(resource = "scrm_tax_holiday", action = "read")
    @GetMapping("/workdays")
    public OperationResponse<List<ScrmTaxWorkdayOverrideEntity>> listWorkdays(
            @RequestParam(required = false) Integer year) {
        List<ScrmTaxWorkdayOverrideEntity> all =
                workdayOverrideRepository.findAll(Sort.by("workDate"));
        if (year == null) {
            return OperationResponse.build(all);
        }
        List<ScrmTaxWorkdayOverrideEntity> filtered = new ArrayList<>();
        for (ScrmTaxWorkdayOverrideEntity item : all) {
            if (item.getWorkDate() != null && item.getWorkDate().getYear() == year) {
                filtered.add(item);
            }
        }
        return OperationResponse.build(filtered);
    }

    /**
     * 新增调休上班日。
     *
     * @param dto 补班信息
     * @return 创建后的记录
     * @throws ScrmException 该日期已存在
     */
    @RequirePermission(resource = "scrm_tax_holiday", action = "create")
    @PostMapping("/workdays")
    public OperationResponse<ScrmTaxWorkdayOverrideEntity> createWorkday(
            @RequestBody ScrmTaxWorkdayOverrideDto dto) throws ScrmException {
        if (dto.getWorkDate() == null) {
            throw ScrmException.badRequest("上班日期不能为空");
        }
        // 数据库有唯一约束，这里先查一次给出可读错误
        boolean exists = workdayOverrideRepository.findAll().stream()
                .anyMatch(item -> dto.getWorkDate().equals(item.getWorkDate()));
        if (exists) {
            throw ScrmException.conflict("该日期已存在于调休上班日: " + dto.getWorkDate());
        }
        ScrmTaxWorkdayOverrideEntity entity = new ScrmTaxWorkdayOverrideEntity();
        entity.setWorkDate(dto.getWorkDate());
        entity.setRemark(dto.getRemark());
        entity = workdayOverrideRepository.save(entity);
        log.info("[税务假期] 新增调休上班日: id={}, date={}", entity.getId(), entity.getWorkDate());
        return OperationResponse.build(entity);
    }

    /**
     * 删除调休上班日。
     *
     * @param id 记录 ID
     * @return 空响应
     * @throws ScrmException 记录不存在
     */
    @RequirePermission(resource = "scrm_tax_holiday", action = "delete")
    @DeleteMapping("/workdays/{id}")
    public OperationResponse<Void> deleteWorkday(@PathVariable Long id) throws ScrmException {
        ScrmTaxWorkdayOverrideEntity entity = workdayOverrideRepository.findById(id)
                .orElseThrow(() -> ScrmException.notFound("调休上班日不存在: id=" + id));
        workdayOverrideRepository.delete(entity);
        log.warn("[税务假期] 删除调休上班日: id={}, date={}", id, entity.getWorkDate());
        return OperationResponse.build(null);
    }

    // ============================================================
    // AI 抽取（草稿制，人工确认后才生效）
    // ============================================================

    /**
     * 触发一次 AI 抽取指定年份假期。
     * <p>
     * 抓取公告 → 模型结构化 → 落草稿。产出<b>不会</b>直接进入生效表，
     * 必须人工核对后调用 {@code /drafts/{id}/apply}。
     * </p>
     *
     * @param year 目标年份（可空，默认次年）
     * @return 生成结果摘要
     */
    @RequirePermission(resource = "scrm_tax_holiday", action = "update")
    @PostMapping("/ai/extract")
    public OperationResponse<Map<String, Object>> aiExtract(@RequestParam(required = false) Integer year) {
        int targetYear = year != null ? year : LocalDate.now().getYear() + 1;
        var draft = aiService.autoExtract(targetYear);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("year", targetYear);
        if (draft == null) {
            result.put("generated", false);
            String reason = aiService.getLastFailureReason();
            result.put("message", "未生成草稿：" + (reason != null ? reason : "未知原因")
                    + "。不会用模型凭空生成数据，请处理后重试或手工录入。");
            return OperationResponse.build(result);
        }
        result.put("generated", true);
        result.put("draftId", String.valueOf(draft.getId()));
        result.put("message", "草稿已生成，请核对后采纳");
        return OperationResponse.build(result);
    }

    /**
     * 查询草稿列表。
     *
     * @param year   年份（可空）
     * @param status 状态（可空，默认 PENDING）
     * @return 草稿列表
     */
    @RequirePermission(resource = "scrm_tax_holiday", action = "read")
    @GetMapping("/drafts")
    public OperationResponse<List<org.hiylo.scrm.entity.ScrmTaxHolidayDraftEntity>> listDrafts(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) String status) {
        String targetStatus = status != null && !status.isBlank()
                ? status : org.hiylo.scrm.entity.ScrmTaxHolidayDraftEntity.STATUS_PENDING;
        List<org.hiylo.scrm.entity.ScrmTaxHolidayDraftEntity> list = year == null
                ? draftRepository.findByDraftStatusOrderByCreateTimeDesc(targetStatus)
                : draftRepository.findByHolidayYearAndDraftStatus(year, targetStatus);
        return OperationResponse.build(list);
    }

    /**
     * 采纳草稿：写入生效表。
     * <p>
     * <b>这会清空该年份现有假期再写入</b>，避免 AI 结果与手工数据叠加导致区间并集算错。
     * </p>
     *
     * @param id 草稿 ID
     * @return 写入结果
     * @throws ScrmException 草稿不存在 / 已处理 / 内容非法
     */
    @RequirePermission(resource = "scrm_tax_holiday", action = "update")
    @PostMapping("/drafts/{id}/apply")
    public OperationResponse<Map<String, Object>> applyDraft(@PathVariable Long id) throws ScrmException {
        String operator = org.hiylo.scrm.config.UserContext.getUsername();
        return OperationResponse.build(aiService.applyDraft(id, operator));
    }

    /**
     * 丢弃草稿。
     *
     * @param id     草稿 ID
     * @param remark 备注（可空）
     * @return 空响应
     * @throws ScrmException 草稿不存在 / 已处理
     */
    @RequirePermission(resource = "scrm_tax_holiday", action = "update")
    @PostMapping("/drafts/{id}/reject")
    public OperationResponse<Void> rejectDraft(@PathVariable Long id,
                                               @RequestParam(required = false) String remark)
            throws ScrmException {
        String operator = org.hiylo.scrm.config.UserContext.getUsername();
        aiService.rejectDraft(id, operator, remark);
        return OperationResponse.build(null);
    }

    // ============================================================
    // 数据完整性自检
    // ============================================================

    /**
     * 检查指定年份的假期数据是否齐备。
     * <p>
     * 假期缺失时顺延会<b>静默失效</b>（只跳周末、不报错），因此需要一个主动检查入口，
     * 让运维在年底能确认下一年数据是否已录入。
     * </p>
     *
     * @param year 年份（可空，默认当年）
     * @return 检查结果：假期条数、是否齐备、缺项提示
     */
    @RequirePermission(resource = "scrm_tax_holiday", action = "read")
    @GetMapping("/coverage")
    public OperationResponse<Map<String, Object>> coverage(@RequestParam(required = false) Integer year) {
        int targetYear = year != null ? year : LocalDate.now().getYear();
        List<ScrmTaxHolidayEntity> holidays =
                holidayRepository.findByHolidayYearAndIsActiveTrueOrderByStartDateAsc(targetYear);
        long workdayCount = workdayOverrideRepository.findActiveBetween(
                LocalDate.of(targetYear, 1, 1), LocalDate.of(targetYear, 12, 31)).size();

        List<String> warnings = new ArrayList<>();
        if (holidays.isEmpty()) {
            warnings.add(targetYear + " 年没有任何假期数据，申报截止日顺延将退化为只跳周末");
        } else if (holidays.size() < MIN_HOLIDAYS_PER_YEAR) {
            warnings.add(targetYear + " 年仅有 " + holidays.size() + " 个假期，"
                    + "通常应有 " + MIN_HOLIDAYS_PER_YEAR + " 个（元旦/春节/清明/劳动/端午/中秋/国庆），请核对");
        }
        if (holidays.isEmpty() && workdayCount == 0) {
            warnings.add("调休上班日也为空，若当年有调休安排需一并录入");
        }

        // 语义交叉校验: 补班日「本该休息、改为上班」，因此不能落在假期区间内。
        // 只查条数查不出「日期写错但条数仍对」的笔误（例如 2026 年国庆补班日曾误录为
        // 9/19 而非 9/20，条数始终是 5，条数检查完全无感），必须做区间比对。
        warnings.addAll(findWorkdayHolidayConflicts(targetYear, holidays));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("year", targetYear);
        result.put("holidayCount", holidays.size());
        result.put("workdayOverrideCount", workdayCount);
        result.put("complete", warnings.isEmpty());
        result.put("warnings", warnings);
        return OperationResponse.build(result);
    }

    // ============================================================
    // 内部方法
    // ============================================================

    /**
     * 找出「补班日落在假期区间内」的自相矛盾数据。
     * <p>
     * 调休补班日的语义是「本该放假、改为上班」，因此不可能与法定假期区间重叠。
     * 一旦重叠，说明假期区间或补班日期至少有一个录错——这类错误只查条数发现不了
     * （漏一天、多一天、日期写错，条数都可能不变），必须逐日比对。
     * </p>
     *
     * @param year     目标年份
     * @param holidays 该年份已生效的假期
     * @return 冲突提示列表（无冲突时为空）
     */
    private List<String> findWorkdayHolidayConflicts(int year, List<ScrmTaxHolidayEntity> holidays) {
        List<String> conflicts = new ArrayList<>();
        if (holidays.isEmpty()) {
            return conflicts;
        }
        List<ScrmTaxWorkdayOverrideEntity> overrides = workdayOverrideRepository.findActiveBetween(
                LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31));
        for (ScrmTaxWorkdayOverrideEntity override : overrides) {
            LocalDate workDate = override.getWorkDate();
            if (workDate == null) {
                continue;
            }
            for (ScrmTaxHolidayEntity holiday : holidays) {
                if (holiday.getStartDate() == null || holiday.getEndDate() == null) {
                    continue;
                }
                if (!workDate.isBefore(holiday.getStartDate()) && !workDate.isAfter(holiday.getEndDate())) {
                    conflicts.add("调休上班日 " + workDate + " 与假期「" + holiday.getHolidayName()
                            + "」(" + holiday.getStartDate() + "~" + holiday.getEndDate()
                            + ") 重叠，二者必有一处录错，请对照国务院公告核对");
                    break;
                }
            }
        }
        return conflicts;
    }

    /**
     * 校验假期日期区间。
     *
     * @param dto 假期信息
     * @throws ScrmException 起止日期为空或结束早于开始
     */
    private void validateRange(ScrmTaxHolidayDto dto) throws ScrmException {
        if (dto.getStartDate() == null || dto.getEndDate() == null) {
            throw ScrmException.badRequest("假期的起止日期不能为空");
        }
        if (dto.getEndDate().isBefore(dto.getStartDate())) {
            throw ScrmException.badRequest("假期结束日期不能早于开始日期");
        }
    }

    /**
     * 把入参写入实体。
     *
     * @param entity 目标实体
     * @param dto    入参
     */
    private void applyFields(ScrmTaxHolidayEntity entity, ScrmTaxHolidayDto dto) {
        entity.setHolidayName(dto.getHolidayName().trim());
        entity.setStartDate(dto.getStartDate());
        entity.setEndDate(dto.getEndDate());
        // 年份不传时按起始日推导，避免与区间不一致
        entity.setHolidayYear(dto.getHolidayYear() != null
                ? dto.getHolidayYear() : dto.getStartDate().getYear());
        entity.setHolidayKind(dto.getHolidayKind() != null && !dto.getHolidayKind().isBlank()
                ? dto.getHolidayKind() : ScrmTaxHolidayEntity.KIND_STATUTORY);
        entity.setRemark(dto.getRemark());
    }
}
