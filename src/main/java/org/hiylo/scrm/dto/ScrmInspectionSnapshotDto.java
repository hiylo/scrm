/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm
 * File : ScrmInspectionSnapshotDto.java
 * Date : 2026/10/23 09:20:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 工商税务巡检结果快照 DTO。
 * <p>
 * 只读展示用: 快照的哈希、是否变化、归一化状态与明细 JSON; 明细中证件号码等
 * 敏感字段已在服务层按项目红线脱敏。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmInspectionSnapshotDto {

    /** 快照 ID */
    private Long id;

    /** 巡检任务 ID */
    private Long taskId;

    /** 巡检维度: BUSINESS / TAX */
    private String dimension;

    /** 统一社会信用代码 */
    private String creditCode;

    /** 企业名称 */
    private String enterpriseName;

    /** 状态哈希（SHA-256） */
    private String stateHash;

    /** 是否最新快照 */
    private Boolean isLatest;

    /** 巡检时间 */
    private LocalDateTime inspectAt;

    /** 数据来源: QIXIN / QCC / GD_TAX / ZJ_TAX / MANUAL */
    private String source;

    /** 归一化状态 */
    private String status;

    /** 明细 JSON（脱敏后） */
    private String detailJson;

    /** 是否相对上一快照发生变化 */
    private Boolean changed;

    /** 错误信息 */
    private String errorMessage;

    /** 创建时间 */
    private LocalDateTime createTime;
}