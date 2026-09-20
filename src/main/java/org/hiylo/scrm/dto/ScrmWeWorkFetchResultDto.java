/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmWeWorkFetchResultDto.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

/**
 * 企微会话存档拉取结果 DTO。
 * <p>
 * 描述一次拉取操作的执行结果, 包括拉取前后的游标位置、入库消息数与跳过数。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
public class ScrmWeWorkFetchResultDto {

    /** 存档配置 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long configId;

    /** 配置名称 */
    private String configName;

    /** 拉取前游标 seq */
    private Long beforeCursorSeq;

    /** 拉取后游标 seq */
    private Long afterCursorSeq;

    /** 企微最新 seq */
    private Long lastSeq;

    /** 本次拉取到的消息数 */
    private Integer fetchedCount;

    /** 实际入库的消息数 */
    private Integer savedCount;

    /** 跳过的消息数（重复 seq） */
    private Integer skippedCount;

    /** 失败的消息数 */
    private Integer failedCount;

    /** 拉取是否成功 */
    private Boolean success;

    /** 错误信息（失败时填充） */
    private String errorMessage;
}
