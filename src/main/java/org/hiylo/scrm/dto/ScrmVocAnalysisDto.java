/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmVocAnalysisDto.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * SCRM VoC 声音分析 DTO。
 * <p>
 * 用于手动分析单条声音: 显式指定情感、分类、关联主题与关键词, 并可附分析摘要。
 * 服务端按入参更新声音的对应字段, 并刷新关联主题统计与 lastAnalysisAt。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
public class ScrmVocAnalysisDto {

    /** 声音 ID */
    @NotNull(message = "声音 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long voiceId;

    /** 情感: POSITIVE/NEUTRAL/NEGATIVE/MIXED (可空, 为空时由服务端按内容自动分析) */
    private String sentiment;

    /** 分类 (可空, 为空时由服务端按内容自动匹配) */
    private String category;

    /** 关联主题 ID 列表 (可空, 为空时不修改关联) */
    private List<Long> topicIds;

    /** 关键词列表 (可空, 服务端会与自动提取的关键词合并去重) */
    private List<String> keywords;

    /** 分析摘要 (可空, 写入声音的 tags 字段并刷新 analyzedAt) */
    private String summary;
}
