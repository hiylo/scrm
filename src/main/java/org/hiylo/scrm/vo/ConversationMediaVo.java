/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ConversationMediaVo.java
 * Date : 2026/09/18 11:24:39
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 会话媒体上传结果 VO
 * <p>
 * 承载媒体文件上传后的对象存储定位信息与预签名下载地址,
 * 供前端持久化 objectKey 并直接渲染媒体。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationMediaVo {

    /** 对象存储 key (持久化字段, 格式 scrm/conversation/{yyyyMM}/{uuid}_{fileName}) */
    private String objectKey;

    /** 当前生效的对象存储实现标识 (minio / oss) */
    private String provider;

    /** bucket 名称 */
    private String bucket;

    /** 预签名下载地址 */
    private String url;

    /** 预签名地址有效期 (秒) */
    private Integer expiresInSeconds;

    /** 文件大小 (字节) */
    private Long size;

    /** MIME 类型 */
    private String contentType;
}
