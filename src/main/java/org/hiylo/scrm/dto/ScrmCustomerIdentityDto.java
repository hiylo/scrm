/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCustomerIdentityDto.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Version : V1.0
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
 * SCRM 客户身份标识 DTO。
 * <p>
 * 对应 {@code ScrmCustomerIdentityEntity} 的业务字段, 用于新增 / 查询返回。
 * identityType 以 Pattern 校验合法性, source / platform 通过枚举集合校验。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmCustomerIdentityDto {

    /** 主键 ID (查询返回) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 客户 ID */
    @NotNull(message = "客户 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long customerId;

    /** 客户名称 (可空, 冗余字段) */
    @Size(max = 200, message = "客户名称长度不能超过 200")
    private String customerName;

    /** 身份类型: PHONE/EMAIL/WECHAT_OPENID/WECHAT_UNIONID/WORK_WECHAT_EXTERNAL_ID/DOUYIN_OPENID/KUAISHOU_OPENID/XIAOHONGSHU_OPENID/WEIBO_UID/ALIPAY_USERID/QQ_OPENID/ID_CARD/PASSPORT/USERNAME/DEVICE_ID/CUSTOM */
    @NotBlank(message = "身份类型不能为空")
    @Pattern(regexp = "PHONE|EMAIL|WECHAT_OPENID|WECHAT_UNIONID|WORK_WECHAT_EXTERNAL_ID|DOUYIN_OPENID|KUAISHOU_OPENID|XIAOHONGSHU_OPENID|WEIBO_UID|ALIPAY_USERID|QQ_OPENID|ID_CARD|PASSPORT|USERNAME|DEVICE_ID|CUSTOM",
            message = "身份类型非法")
    private String identityType;

    /** 身份值 */
    @NotBlank(message = "身份值不能为空")
    @Size(max = 500, message = "身份值长度不能超过 500")
    private String identityValue;

    /** 平台 (可空): WECHAT/WORK_WECHAT/DOUYIN/KUAISHOU/XIAOHONGSHU/WEIBO/ALIPAY/QQ/WEB/APP */
    @Pattern(regexp = "WECHAT|WORK_WECHAT|DOUYIN|KUAISHOU|XIAOHONGSHU|WEIBO|ALIPAY|QQ|WEB|APP|",
            message = "平台非法")
    private String platform;

    /** 是否主身份 (新增时可空, 默认 FALSE) */
    private Boolean isPrimary;

    /** 是否验证 (新增时可空, 默认 FALSE) */
    private Boolean isVerified;

    /** 来源: REGISTRATION/IMPORT/MERGE/OAUTH/MANUAL/SYSTEM (可空, 默认 MANUAL) */
    @Pattern(regexp = "REGISTRATION|IMPORT|MERGE|OAUTH|MANUAL|SYSTEM|",
            message = "来源非法")
    private String source;

    /** JSON 附加数据 (可空) */
    private String metadata;

    /** 是否活跃 (查询返回) */
    private Boolean isActive;

    /** 创建时间 (查询返回) */
    private LocalDateTime createTime;

    /** 更新时间 (查询返回) */
    private LocalDateTime updateTime;
}
