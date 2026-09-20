/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmAiKnowledgeDocumentDto.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * SCRM AI 知识库文档 DTO。
 * <p>
 * 对应 {@code ScrmAiKnowledgeDocumentEntity} 的业务字段, 创建/更新接口入参。
 * contentType 仅支持 TEXT/MARKDOWN/JSON, sourceType 仅支持 MANUAL/IMPORT/URL。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
public class ScrmAiKnowledgeDocumentDto {

    /** 主键 ID (更新时必填) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 所属知识库 ID (创建时必填) */
    @NotNull(message = "知识库 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long knowledgeBaseId;

    /** 文档标题 */
    @NotBlank(message = "文档标题不能为空")
    @Size(max = 200, message = "文档标题长度不能超过 200")
    private String title;

    /** 文档内容 */
    @NotBlank(message = "文档内容不能为空")
    private String content;

    /** 内容类型: TEXT / MARKDOWN / JSON (默认 TEXT) */
    @Pattern(regexp = "TEXT|MARKDOWN|JSON", message = "内容类型仅支持 TEXT/MARKDOWN/JSON")
    private String contentType;

    /** 标签 (逗号分隔) */
    @Size(max = 500, message = "标签长度不能超过 500")
    private String tags;

    /** 来源类型: MANUAL / IMPORT / URL (默认 MANUAL) */
    @Pattern(regexp = "MANUAL|IMPORT|URL", message = "来源类型仅支持 MANUAL/IMPORT/URL")
    private String sourceType;

    /** 来源 URL */
    @Size(max = 500, message = "来源 URL 长度不能超过 500")
    private String sourceUrl;

    /** 问答对 (JSON 字符串) */
    private String qaPairs;

    /** 是否启用 (创建时可选, 默认 true) */
    private Boolean enabled;

    /** 创建人 */
    @Size(max = 100, message = "创建人长度不能超过 100")
    private String createdBy;

    /** 浏览次数 (查询返回) */
    private Integer viewCount;

    /** 最近使用时间 (查询返回) */
    private LocalDateTime lastUsedAt;

    /** 创建时间 (查询返回) */
    private LocalDateTime createTime;

    /** 更新时间 (查询返回) */
    private LocalDateTime updateTime;

    /** 乐观锁版本号 (查询返回) */
    private Long version;
}
