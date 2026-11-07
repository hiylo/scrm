/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm
 * File : ScrmRecordingUploadResultVo.java
 * Date : 2026/10/22 10:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 录音上传结果 VO。
 * <p>
 * 返回录音记录 ID、对象存储 key 与预签名 URL（供 App 本地回显 / 后端回放）。
 * 重复上传（文件哈希相同）时返回既有记录并标记 {@code duplicated=true}。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScrmRecordingUploadResultVo {

    /** 录音记录 ID */
    private Long id;

    /** 是否重复上传（文件哈希已存在） */
    private Boolean duplicated;

    /** 对象存储 key */
    private String objectKey;

    /** 预签名下载 URL（短时有效） */
    private String url;

    /** 文件大小（字节） */
    private Long fileSize;

    /** 来源: OEM_RECORD / LINE / MANUAL */
    private String source;

    /** 关联通话记录 ID（可为空） */
    private Long callRecordId;

    /** 关联客户 ID（可为空） */
    private Long customerId;

    /** 关联对象类型：CUSTOMER / PUBLIC_SEA（可为空） */
    private String customerType;
}
