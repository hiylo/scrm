/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmSegmentHistoryDto.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * SCRM 客户分群历史快照 DTO。
 * <p>
 * 对应 {@code ScrmSegmentHistoryEntity} 的业务字段, 记录分群历史快照接口入参 / 返回。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
public class ScrmSegmentHistoryDto {

    /** 主键 ID (更新时必填) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 分群 ID */
    @NotNull(message = "分群 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long segmentId;

    /** 快照日期 */
    @NotNull(message = "快照日期不能为空")
    private LocalDate snapshotDate;

    /** 当日成员数 */
    @NotNull(message = "成员数不能为空")
    private Integer memberCount;

    /** 新增成员数 (默认 0) */
    private Integer addedCount;

    /** 流失成员数 (默认 0) */
    private Integer removedCount;

    /** 平均订单数 (默认 0) */
    private Double avgOrderCount;

    /** 平均消费金额 (默认 0) */
    private Double avgTotalAmount;

    /** 平均互动分 (默认 0) */
    private Double avgEngagementScore;

    /** 等级分布 JSON (可空) */
    private String topLevels;

    /** 标签分布 JSON (可空) */
    private String topTags;

    /** 计算时间 (可空, 未填则取当前时间) */
    private LocalDateTime calculatedAt;

    /** 创建时间 (查询返回) */
    private LocalDateTime createTime;

    /** 更新时间 (查询返回) */
    private LocalDateTime updateTime;

    /** 乐观锁版本号 (查询返回) */
    private Long version;
}
