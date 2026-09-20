/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmAttributionTouchpointDto.java
 * Date : 2026/08/05 08:55:12
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
 * SCRM 营销效果归因触点 DTO。
 * <p>
 * 对应 {@code ScrmAttributionTouchpointEntity} 的业务字段, 记录触点接口入参与查询返回。
 * metadata 为 JSON 字符串, 承载附加数据。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
public class ScrmAttributionTouchpointDto {

    /** 主键 ID (更新时必填) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 客户 ID */
    @NotNull(message = "客户 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long customerId;

    /** 客户名称 (可空) */
    @Size(max = 200, message = "客户名称长度不能超过 200")
    private String customerName;

    /** 触点顺序 (从 1 开始, 缺省由服务端按触点时间升序回填) */
    private Integer touchpointOrder;

    /** 触点类型: AD_CLICK/AD_VIEW/EMAIL_OPEN/EMAIL_CLICK/SMS_CLICK/WECHAT_MESSAGE/WEB_VISIT/SEARCH/REFERRAL/SOCIAL_POST/DIRECT/STORE_VISIT/CALL/CONTENT_VIEW */
    @NotBlank(message = "触点类型不能为空")
    @Size(max = 50, message = "触点类型长度不能超过 50")
    private String touchpointType;

    /** 渠道: SEARCH/SOCIAL/EMAIL/SMS/WECHAT/DIRECT/REFERRAL/STORE/AD/OTHER */
    @NotBlank(message = "渠道不能为空")
    @Size(max = 50, message = "渠道长度不能超过 50")
    private String channel;

    /** 营销活动 ID (可空) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long campaignId;

    /** 营销活动名称 (可空) */
    @Size(max = 200, message = "营销活动名称长度不能超过 200")
    private String campaignName;

    /** 关联内容 ID (可空) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long contentId;

    /** 触点时间 */
    @NotNull(message = "触点时间不能为空")
    private LocalDateTime touchpointTime;

    /** 触点价值 (可空, 缺省 0) */
    private Double touchpointValue;

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

    /** 着陆页 (可空) */
    @Size(max = 500, message = "着陆页长度不能超过 500")
    private String landingPage;

    /** 来源 (可空) */
    @Size(max = 500, message = "来源长度不能超过 500")
    private String referrer;

    /** 设备类型 (可空): MOBILE/PC/TABLET/TV/OTHER */
    @Size(max = 30, message = "设备类型长度不能超过 30")
    private String deviceType;

    /** 会话 ID (可空) */
    @Size(max = 200, message = "会话 ID 长度不能超过 200")
    private String sessionId;

    /** JSON 附加数据 (可空) */
    private String metadata;

    /** 是否被归因 (查询返回) */
    private Boolean isAttributed;

    /** 归因权重 (查询返回) */
    private Double attributionWeight;

    /** 归因价值 (查询返回) */
    private Double attributionValue;

    /** 创建时间 (查询返回) */
    private LocalDateTime createTime;

    /** 更新时间 (查询返回) */
    private LocalDateTime updateTime;

    /** 乐观锁版本号 (查询返回) */
    private Long version;
}
