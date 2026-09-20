/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmMessageTemplateVersionDto.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * SCRM 消息模板版本 DTO。
 * <p>
 * 用于 {@code createVersion} 接口传参, 指定模板 ID 与变更说明。版本内容快照由服务从
 * 当前模板内容自动拷贝, 调用方无需重复传入 content 等字段。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
public class ScrmMessageTemplateVersionDto {

    /** 所属模板 ID */
    @NotNull(message = "模板 ID 不能为空")
    private Long templateId;

    /** 变更说明（可空） */
    @Size(max = 500, message = "变更说明长度不能超过 500")
    private String changeLog;

    /** 创建人（可空） */
    @Size(max = 100, message = "创建人长度不能超过 100")
    private String createdBy;
}
