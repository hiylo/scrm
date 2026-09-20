/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : WeworkUserDto.java
 * Date : 2026/09/19 21:20:11
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.integration.wework.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 企业微信用户信息 DTO
 * <p>
 * 替代原 {@code org.hiylo.components.platform.dto.UserInfoResponse}，
 * 仅保留企微场景实际使用的字段，字段名与原 UserInfoResponse 保持一致，
 * 确保下游 {@code getNickname()} / {@code getAvatar()} 等调用无需修改。
 * </p>
 *
 * @author Hsi Chu
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WeworkUserDto {

    /** 平台用户ID */
    private String platformUserId;

    /** 平台类型标识(固定为 WEWORK) */
    private String platformType;

    /** 用户昵称 */
    private String nickname;

    /** 用户头像 */
    private String avatar;
}
