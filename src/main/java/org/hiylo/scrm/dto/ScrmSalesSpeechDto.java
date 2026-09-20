/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmSalesSpeechDto.java
 * Date : 2026/08/05 08:55:12
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
 * SCRM 销售话术库 DTO。
 * <p>
 * 对应 {@code ScrmSalesSpeechEntity} 的业务字段, 创建/更新接口入参。
 * speechType / speechStyle / difficultyLevel 以枚举字符串校验合法性;
 * rating / usageCount / successCount / feedbackCount / positiveFeedback /
 * negativeFeedback / isRecommended / isVerified / versionNo / enabled 缺省时由服务端填充默认值。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmSalesSpeechDto {

    /** 主键 ID (更新时必填) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 场景 ID */
    @NotNull(message = "场景 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long scenarioId;

    /** 场景名称 (冗余, 可空) */
    @Size(max = 200, message = "场景名称长度不能超过 200")
    private String scenarioName;

    /** 话术标题 */
    @NotBlank(message = "话术标题不能为空")
    @Size(max = 200, message = "话术标题长度不能超过 200")
    private String speechTitle;

    /** 话术内容 */
    @NotBlank(message = "话术内容不能为空")
    private String speechContent;

    /** 话术类型: TEXT/SCRIPT/QA/GUIDE/TEMPLATE (默认 TEXT) */
    @Pattern(regexp = "TEXT|SCRIPT|QA|GUIDE|TEMPLATE|",
            message = "话术类型仅支持 TEXT/SCRIPT/QA/GUIDE/TEMPLATE")
    private String speechType;

    /** 话术风格: FORMAL/FRIENDLY/PROFESSIONAL/CASUAL/PERSUASIVE/EMPATHETIC (可空) */
    @Pattern(regexp = "FORMAL|FRIENDLY|PROFESSIONAL|CASUAL|PERSUASIVE|EMPATHETIC|",
            message = "话术风格仅支持 FORMAL/FRIENDLY/PROFESSIONAL/CASUAL/PERSUASIVE/EMPATHETIC")
    private String speechStyle;

    /** 目标受众 (可空) */
    @Size(max = 500, message = "目标受众长度不能超过 500")
    private String targetAudience;

    /** 适用产品 (逗号分隔, 可空) */
    @Size(max = 500, message = "适用产品长度不能超过 500")
    private String applicableProducts;

    /** 适用场景 (逗号分隔, 可空) */
    @Size(max = 500, message = "适用场景长度不能超过 500")
    private String applicableScenes;

    /** 关键词 (逗号分隔, 可空) */
    @Size(max = 500, message = "关键词长度不能超过 500")
    private String keywords;

    /** 可用变量 (逗号分隔, 如 {customerName},{productName},{price}, 可空) */
    @Size(max = 500, message = "可用变量长度不能超过 500")
    private String variables;

    /** 媒体附件 JSON (可空) */
    @Size(max = 1000, message = "媒体附件长度不能超过 1000")
    private String mediaAttachments;

    /** 难度等级: BEGINNER/INTERMEDIATE/ADVANCED/EXPERT (默认 INTERMEDIATE) */
    @Pattern(regexp = "BEGINNER|INTERMEDIATE|ADVANCED|EXPERT|",
            message = "难度等级仅支持 BEGINNER/INTERMEDIATE/ADVANCED/EXPERT")
    private String difficultyLevel;

    /** 预计时长 (秒, 默认 0) */
    private Integer estimatedDuration;

    /** 评分 (查询返回) */
    private Double rating;

    /** 使用次数 (查询返回) */
    private Integer usageCount;

    /** 成功次数 (查询返回) */
    private Integer successCount;

    /** 反馈数 (查询返回) */
    private Integer feedbackCount;

    /** 正面反馈数 (查询返回) */
    private Integer positiveFeedback;

    /** 负面反馈数 (查询返回) */
    private Integer negativeFeedback;

    /** 是否推荐 (默认 FALSE) */
    private Boolean isRecommended;

    /** 是否验证 (默认 FALSE) */
    private Boolean isVerified;

    /** 业务版本号 (默认 1) */
    private Integer versionNo;

    /** 作者 ID (可空) */
    @Size(max = 100, message = "作者 ID 长度不能超过 100")
    private String authorId;

    /** 作者名称 (可空) */
    @Size(max = 100, message = "作者名称长度不能超过 100")
    private String authorName;

    /** 标签 (逗号分隔, 可空) */
    @Size(max = 500, message = "标签长度不能超过 500")
    private String tags;

    /** 是否启用 (默认 TRUE) */
    private Boolean enabled;

    /** 创建人 (可空) */
    @Size(max = 100, message = "创建人长度不能超过 100")
    private String createdBy;

    /** 创建时间 (查询返回) */
    private LocalDateTime createTime;

    /** 更新时间 (查询返回) */
    private LocalDateTime updateTime;

    /** 乐观锁版本号 (查询返回) */
    private Long version;
}
