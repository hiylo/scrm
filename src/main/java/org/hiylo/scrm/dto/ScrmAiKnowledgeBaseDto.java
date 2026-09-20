/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmAiKnowledgeBaseDto.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * SCRM AI 知识库 DTO。
 * <p>
 * 对应 {@code ScrmAiKnowledgeBaseEntity} 的业务字段, 创建/更新接口入参。
 * category 仅支持 PRODUCT/FAQ/POLICY/SCRIPT/PROCESS。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmAiKnowledgeBaseDto {

    /** 主键 ID (更新时必填) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 知识库名称 */
    @NotBlank(message = "知识库名称不能为空")
    @Size(max = 200, message = "知识库名称长度不能超过 200")
    private String kbName;

    /** 描述 */
    @Size(max = 500, message = "描述长度不能超过 500")
    private String description;

    /** 知识库分类: PRODUCT / FAQ / POLICY / SCRIPT / PROCESS */
    @Pattern(regexp = "PRODUCT|FAQ|POLICY|SCRIPT|PROCESS",
            message = "知识库分类仅支持 PRODUCT/FAQ/POLICY/SCRIPT/PROCESS")
    private String category;

    /** 是否启用 (创建时可选, 默认 true) */
    private Boolean enabled;

    /** 创建人 */
    @Size(max = 100, message = "创建人长度不能超过 100")
    private String createdBy;

    /** 文档数量 (查询返回) */
    private Integer documentCount;

    /** 创建时间 (查询返回) */
    private LocalDateTime createTime;

    /** 更新时间 (查询返回) */
    private LocalDateTime updateTime;

    /** 乐观锁版本号 (查询返回) */
    private Long version;
}
