/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmSalesCustomerVo.java
 * Date : 2026/10/22 10:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 销售端客户视图对象。
 * <p>
 * 聚合客户基础信息 + 联系方式（一个客户可多个号码），供销售 App
 * 「我的客户」列表与详情页直接渲染；号码用于点号拨打与通讯录同步。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScrmSalesCustomerVo {

    /** 归属对象 ID（CUSTOMER 时为客户 ID，PUBLIC_SEA 时为线索 ID） */
    private Long id;

    /** 归属类型：CUSTOMER(正式客户) / PUBLIC_SEA(公海线索) */
    private String ownerType;

    /** 客户昵称 */
    private String nickname;

    /** 生命周期: NEW/PROSPECT/ACTIVE/DORMANT/CHURNED/CONVERTED */
    private String lifecycle;

    /** 客户备注（业务员私有） */
    private String remark;

    /** 下次跟进时间 */
    private String nextFollowUpAt;

    /** 主号码（isPrimary=true 的号码, 无则取第一个） */
    private String primaryPhone;

    /** 全部联系方式 */
    private List<Contact> contacts;

    /**
     * 联系方式子项
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Contact {
        /** 号码 */
        private String phone;
        /** 号码类型: MOBILE / LANDLINE / OTHER */
        private String phoneType;
        /** 是否主号码 */
        private Boolean isPrimary;
        /** 备注 */
        private String remark;
    }
}
