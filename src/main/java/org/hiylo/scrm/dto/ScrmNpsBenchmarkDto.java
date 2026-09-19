/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmNpsBenchmarkDto.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * SCRM NPS 基准 DTO。
 * <p>
 * 对应 {@code ScrmNpsBenchmarkEntity} 的业务字段, 生成 / 查询接口入参与返回。periodType
 * 标注周期类型 (MONTHLY/QUARTERLY/YEARLY); periodStart / periodEnd 标注周期范围;
 * npsScore 取值范围 -100 到 100。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmNpsBenchmarkDto {

    /** 主键 ID (查询返回) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 周期类型: MONTHLY / QUARTERLY / YEARLY */
    @NotBlank(message = "周期类型不能为空")
    @Pattern(regexp = "MONTHLY|QUARTERLY|YEARLY",
            message = "周期类型仅支持 MONTHLY/QUARTERLY/YEARLY")
    private String periodType;

    /** 周期开始日期 */
    @NotNull(message = "周期开始日期不能为空")
    private LocalDate periodStart;

    /** 周期结束日期 */
    @NotNull(message = "周期结束日期不能为空")
    private LocalDate periodEnd;

    /** 总回复数 (查询返回) */
    private Integer totalResponses;

    /** 推荐者数 (9-10, 查询返回) */
    private Integer promoters;

    /** 被动者数 (7-8, 查询返回) */
    private Integer passives;

    /** 贬损者数 (0-6, 查询返回) */
    private Integer detractors;

    /** NPS 得分 -100 到 100 (查询返回) */
    private Integer npsScore;

    /** 推荐者占比 (查询返回) */
    private Double promoterPercent;

    /** 被动者占比 (查询返回) */
    private Double passivePercent;

    /** 贬损者占比 (查询返回) */
    private Double detractorPercent;

    /** 平均 CSAT 得分 (查询返回) */
    private Double avgCsatScore;

    /** 平均 CES 得分 (查询返回) */
    private Double avgCesScore;

    /** 回复率 (查询返回) */
    private Double responseRate;

    /** 行业基准 (可空) */
    @Size(max = 100, message = "行业基准长度不能超过 100")
    private String benchmarkIndustry;

    /** 行业 NPS 基准 (可空) */
    private Integer benchmarkScore;

    /** 生成时间 (查询返回) */
    private LocalDateTime generatedAt;

    /** 创建时间 (查询返回) */
    private LocalDateTime createTime;

    /** 更新时间 (查询返回) */
    private LocalDateTime updateTime;

    /** 乐观锁版本号 (查询返回) */
    private Long version;
}
