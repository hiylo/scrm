/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : WeworkExternalContactDto.java
 * Date : 2026/09/19 21:20:11
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.integration.wework.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 企业微信外部联系人 DTO
 * <p>
 * 对应企业微信「外部联系人」接口返回的客户信息，包含客户名称、头像、
 * 所属企业、添加时间、添加人等字段。同时承载外部联系人列表查询与详情查询结果。
 * </p>
 *
 * @author Hsi Chu
 * @since 1.0.0
 */
@Data
@NoArgsConstructor
public class WeworkExternalContactDto {

    /** 是否成功 */
    private Boolean success;

    /** 错误码 */
    private String errorCode;

    /** 错误信息 */
    private String errorMessage;

    /** 外部联系人userid */
    private String externalUserId;

    /** 外部联系人名称 */
    private String name;

    /** 外部联系人头像URL */
    private String avatar;

    /** 外部联系人所在企业名称 */
    private String corpName;

    /** 外部联系人所在企业全称 */
    private String corpFullName;

    /** 客户类型：1=微信用户，2=企业微信用户 */
    private Integer type;

    /** 性别：0=未知，1=男，2=女 */
    private Integer gender;

    /** 添加客户的成员userid */
    private String ownerUserId;

    /** 添加时间 */
    private LocalDateTime addTime;

    /** 备注 */
    private String remark;

    /** 描述 */
    private String description;

    /** 标签列表 */
    private List<String> tags;

    /**
     * 获取外部联系人的标签列表
     *
     * @return 标签列表
     */
    public List<String> getTags() {
        return tags == null ? null : new ArrayList<>(tags);
    }

    /**
     * 设置外部联系人的标签列表
     *
     * @param tags 标签列表
     */
    public void setTags(List<String> tags) {
        this.tags = tags == null ? null : new ArrayList<>(tags);
    }

    /**
     * 构造成功响应
     *
     * @param externalUserId 外部联系人userid
     * @param name           名称
     * @return 成功响应
     */
    public static WeworkExternalContactDto success(String externalUserId, String name) {
        WeworkExternalContactDto dto = new WeworkExternalContactDto();
        dto.setSuccess(true);
        dto.setExternalUserId(externalUserId);
        dto.setName(name);
        return dto;
    }

    /**
     * 构造失败响应
     *
     * @param errorCode    错误码
     * @param errorMessage 错误信息
     * @return 失败响应
     */
    public static WeworkExternalContactDto failure(String errorCode, String errorMessage) {
        WeworkExternalContactDto dto = new WeworkExternalContactDto();
        dto.setSuccess(false);
        dto.setErrorCode(errorCode);
        dto.setErrorMessage(errorMessage);
        return dto;
    }

    /**
     * 检查是否成功
     *
     * @return 是否成功
     */
    public boolean isSuccess() {
        return Boolean.TRUE.equals(success);
    }
}
