/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmRfmAnalysisDto.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * SCRM RFM 分析结果 DTO。
 *
 * @author Hsi Chu
 */
@Data
public class ScrmRfmAnalysisDto {

    /** 主键 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 客户 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long customerId;

    /** 客户名称 */
    private String customerName;

    /** 使用的 RFM 配置 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long configId;

    /** 最近互动/消费距今天数 */
    private Integer recencyDays;

    /** 消费/互动次数 */
    private Integer frequency;

    /** 消费/互动总金额 */
    private Double monetary;

    /** R 评分 (1-5) */
    private Integer rScore;

    /** F 评分 (1-5) */
    private Integer fScore;

    /** M 评分 (1-5) */
    private Integer mScore;

    /** RFM 分群编码 (如 "111") */
    private String rfmSegment;

    /** 分群名称 (如 "重要价值客户") */
    private String segmentName;

    /** 分群大类 */
    private String segmentCategory;

    /** 综合价值分 (0-100) */
    private Double valueScore;

    /** 计算时间 */
    private LocalDateTime calculatedAt;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 乐观锁版本号 */
    private Long version;
}
