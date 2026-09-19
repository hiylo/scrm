/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmTouchpointDto.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * SCRM 触点管理 DTO。
 * <p>
 * 用于触点增删改查接口入参与返回。{@code touchpointCode} 为唯一编码,
 * {@code config} 为 JSON 触点配置字符串。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
public class ScrmTouchpointDto {

    /** 主键 ID (更新时必填) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 触点名称 */
    @NotBlank(message = "触点名称不能为空")
    @Size(max = 100, message = "触点名称长度不能超过 100")
    private String touchpointName;

    /** 触点编码 (唯一, 与行为轨迹 touchpoint 字段对齐) */
    @NotBlank(message = "触点编码不能为空")
    @Size(max = 50, message = "触点编码长度不能超过 50")
    private String touchpointCode;

/** 触点类型:
* WEBSITE/APP/WECHAT_OFFICIAL/WECHAT_MINI/WORK_WECHAT/DOUYIN/KUAISHOU/XIAOHONGSHU/STORE/PHONE/EMAIL/SMS/OTHER
          * */
    @NotBlank(message = "触点类型不能为空")
    @Size(max = 50, message = "触点类型长度不能超过 50")
    private String touchpointType;

    /** 触点 URL (可空) */
    @Size(max = 500, message = "触点 URL 长度不能超过 500")
    private String url;

    /** 应用 ID (可空, 如小程序 appId) */
    @Size(max = 200, message = "应用 ID 长度不能超过 200")
    private String appId;

    /** 描述 (可空) */
    @Size(max = 500, message = "描述长度不能超过 500")
    private String description;

    /** 是否启用 (可空, 缺省 true) */
    private Boolean isActive;

    /** JSON 触点配置 (可空) */
    private String config;

    /** 创建人 (可空) */
    @Size(max = 100, message = "创建人长度不能超过 100")
    private String createdBy;

    /** 累计事件数 (查询返回) */
    private Integer totalEvents;

    /** 独立访客数 (查询返回) */
    private Integer uniqueVisitors;

    /** 转化数 (查询返回) */
    private Integer conversionCount;

    /** 最近事件时间 (查询返回) */
    private LocalDateTime lastEventAt;

    /** 创建时间 (查询返回) */
    private LocalDateTime createTime;

    /** 更新时间 (查询返回) */
    private LocalDateTime updateTime;

    /** 乐观锁版本号 (查询返回) */
    private Long version;
}
