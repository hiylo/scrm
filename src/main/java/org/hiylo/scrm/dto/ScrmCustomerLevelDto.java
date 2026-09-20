/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCustomerLevelDto.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * SCRM 客户等级 DTO。
 * <p>
 * 对应 {@code ScrmCustomerLevelEntity} 的业务字段, 创建/更新接口入参, 校验注解保证必填字段
 * 与取值约束。benefits 为 JSON 字符串, 由前端构建。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
public class ScrmCustomerLevelDto {

    /** 主键 ID (更新时必填) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 等级名称 (如 VIP / 高级会员 / 普通会员 / 潜在客户) */
    @NotBlank(message = "等级名称不能为空")
    @Size(max = 100, message = "等级名称长度不能超过 100")
    private String levelName;

    /** 等级编码 (全局唯一, 如 VIP / SENIOR / NORMAL / POTENTIAL) */
    @NotBlank(message = "等级编码不能为空")
    @Size(max = 50, message = "等级编码长度不能超过 50")
    private String levelCode;

    /** 等级排序 (数字越大等级越高) */
    @NotNull(message = "等级排序不能为空")
    private Integer levelOrder;

    /** 等级描述 */
    @Size(max = 500, message = "等级描述长度不能超过 500")
    private String description;

    /** 等级颜色标识 (前端展示用, 如 #FFD700) */
    @Size(max = 20, message = "颜色标识长度不能超过 20")
    private String color;

    /** 等级图标 (前端展示用, 如 icon-vip) */
    @Size(max = 100, message = "图标长度不能超过 100")
    private String icon;

    /** 等级权益 JSON 列表 */
    private String benefits;

    /** 升级阈值 (累计消费金额达到此值可升级) */
    private Double upgradeThreshold;

    /** 降级阈值 (累计消费金额低于此值降级) */
    private Double downgradeThreshold;

    /** 等级有效天数 (null=永久) */
    private Integer validityDays;

    /** 是否默认等级 (创建时可选, 默认 false) */
    private Boolean isDefault;

    /** 是否启用 (创建时可选, 默认 true) */
    private Boolean enabled;

    /** 创建时间 (查询返回) */
    private LocalDateTime createTime;

    /** 更新时间 (查询返回) */
    private LocalDateTime updateTime;

    /** 乐观锁版本号 (查询返回) */
    private Long version;
}
