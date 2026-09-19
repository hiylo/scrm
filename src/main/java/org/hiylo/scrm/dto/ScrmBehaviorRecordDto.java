/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmBehaviorRecordDto.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Version : V1.0
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
 * SCRM 客户行为记录 DTO。
 * <p>
 * {@code recordBehavior} / {@code batchRecord} 接口入参, 承载行为事件的核心字段与可选的
 * 上下文信息 (页面/设备/UTM/会话等)。behaviorTime 缺省时由服务端填充当前时间, 支持补录。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
public class ScrmBehaviorRecordDto {

    /** 客户 ID */
    @NotNull(message = "客户 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long customerId;

    /** 客户名称 (可空, 用于冗余存储) */
    @Size(max = 200, message = "客户名称长度不能超过 200")
    private String customerName;

    /** 关联账号 ID (可空) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long accountId;

    /** 行为类型: PAGE_VIEW/CLICK/SCROLL/SEARCH/FORM_SUBMIT/VIDEO_PLAY/VIDEO_COMPLETE/SHARE/FAVORITE/COMMENT/PURCHASE/ADD_TO_CART/REMOVE_FROM_CART/CHECKOUT/PAYMENT/LOGIN/LOGOUT/DOWNLOAD/UPLOAD/CALL/MESSAGE_SEND/MESSAGE_READ/APPOINTMENT/CANCEL/REFUND/REVIEW */
    @NotBlank(message = "行为类型不能为空")
    @Size(max = 50, message = "行为类型长度不能超过 50")
    private String behaviorType;

/** 触点: WEBSITE/APP/WECHAT_OFFICIAL/WECHAT_MINI/WORK_WECHAT/DOUYIN/KUAISHOU/XIAOHONGSHU/STORE/PHONE/EMAIL/SMS/OTHER
         * */
    @NotBlank(message = "触点不能为空")
    @Size(max = 50, message = "触点长度不能超过 50")
    private String touchpoint;

    /** 行为发生时间 (可空, 缺省由服务端填充当前时间, 支持补录) */
    private LocalDateTime behaviorTime;

    /** 页面 URL (可空) */
    @Size(max = 500, message = "页面 URL 长度不能超过 500")
    private String pageUrl;

    /** 页面标题 (可空) */
    @Size(max = 200, message = "页面标题长度不能超过 200")
    private String pageTitle;

    /** 来源 (可空) */
    @Size(max = 500, message = "来源长度不能超过 500")
    private String referrer;

    /** 停留时长 (秒, 可空) */
    private Integer durationSeconds;

    /** 设备类型: MOBILE/PC/TABLET/TV/OTHER (可空) */
    @Size(max = 30, message = "设备类型长度不能超过 30")
    private String deviceType;

    /** 操作系统 (可空) */
    @Size(max = 50, message = "操作系统长度不能超过 50")
    private String os;

    /** 浏览器 (可空) */
    @Size(max = 100, message = "浏览器长度不能超过 100")
    private String browser;

    /** 应用版本 (可空) */
    @Size(max = 50, message = "应用版本长度不能超过 50")
    private String appVersion;

    /** 客户端 IP (可空) */
    @Size(max = 100, message = "IP 长度不能超过 100")
    private String ip;

    /** 地理位置 (可空) */
    @Size(max = 200, message = "地理位置长度不能超过 200")
    private String location;

    /** 会话 ID (可空, 用于聚合行为路径) */
    @Size(max = 200, message = "会话 ID 长度不能超过 200")
    private String sessionId;

    /** JSON 附加数据 (可空): {productId, searchKeyword, formFields, ...} */
    private String metadata;

    /** UTM 来源 (可空) */
    @Size(max = 100, message = "UTM 来源长度不能超过 100")
    private String utmSource;

    /** UTM 媒介 (可空) */
    @Size(max = 100, message = "UTM 媒介长度不能超过 100")
    private String utmMedium;

    /** UTM 活动 (可空) */
    @Size(max = 200, message = "UTM 活动长度不能超过 200")
    private String utmCampaign;

    /** UTM 内容 (可空) */
    @Size(max = 200, message = "UTM 内容长度不能超过 200")
    private String utmContent;

    /** UTM 关键词 (可空) */
    @Size(max = 200, message = "UTM 关键词长度不能超过 200")
    private String utmTerm;

    /** 转化价值 (可空, 缺省 0) */
    private Double conversionValue;

    /** 是否转化行为 (可空, 缺省 false) */
    private Boolean isConversion;

    /** 漏斗阶段: AWARENESS/INTEREST/DESIRE/ACTION/RETENTION (可空) */
    @Size(max = 30, message = "漏斗阶段长度不能超过 30")
    private String funnelStage;
}
