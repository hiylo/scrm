/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm
 * File : ScrmCustomerContactEntity.java
 * Date : 2026/10/22 10:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import org.hibernate.annotations.GenericGenerator;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * SCRM 客户联系方式实体。
 * <p>
 * 一个客户可拥有多个联系方式（手机 / 固话 / 其他），{@code isPrimary}
 * 标记默认拨打号码（同一客户仅允许一个主号码，由服务层保证）。
 * 该表供销售端 App 实现「保存到系统通讯录 + 去重 + 点号拨打」。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_customer_contact", schema = "scrm", indexes = {
        @Index(name = "idx_customer_contact_customer", columnList = "customer_id"),
        @Index(name = "idx_customer_contact_phone", columnList = "phone"),
        @Index(name = "uk_customer_contact_owner_phone", columnList = "owner_type,customer_id,phone", unique = true)
})
@Data
public class ScrmCustomerContactEntity {

    // ==================== 公共字段 ====================

    /** 主键 ID（Snowflake 雪花算法生成） */
    @Id
    @GeneratedValue(generator = "snowflake")
    @GenericGenerator(name = "snowflake", strategy = "org.hiylo.scrm.config.SnowflakeIdGenerator")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 创建时间 */
    @Column(name = "create_time", nullable = false, updatable = false)
    private LocalDateTime createTime;

    /** 更新时间 */
    @Column(name = "update_time", nullable = false)
    private LocalDateTime updateTime;

    /** 乐观锁版本号（并发更新保护, 后写入者触发 OptimisticLockException） */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    /**
     * 持久化前回调: 自动填充创建/更新时间与版本号初值
     */
    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createTime = now;
        updateTime = now;
        if (version == null) {
            version = 0L;
        }
    }

    /**
     * 更新前回调: 自动刷新更新时间
     */
    @PreUpdate
    protected void onUpdate() {
        updateTime = LocalDateTime.now();
    }

    // ==================== 业务字段 ====================

    /**
     * 归属类型：CUSTOMER(正式客户) / PUBLIC_SEA(公海线索)。
     * <p>
     * 销售 App 的「我的客户」以公海线索为主（assigned_to = 当前销售），
     * 联系方式表因此需要区分归属类型，避免两类 ID 空间冲突。
     * </p>
     */
    @Column(name = "owner_type", nullable = false, length = 20)
    private String ownerType;

    /**
     * 归属对象 ID：
     * ownerType=CUSTOMER 时引用 scrm_customer.id；
     * ownerType=PUBLIC_SEA 时引用 scrm_public_sea_customer.id。
     */
    @Column(name = "customer_id", nullable = false)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long customerId;

    /** 联系电话（已规范化, 去除空格/横线/国码后存储） */
    @Column(name = "phone", nullable = false, length = 32)
    private String phone;

    /** 号码类型: MOBILE(手机) / LANDLINE(固话) / OTHER(其他) */
    @Column(name = "phone_type", nullable = false, length = 20)
    private String phoneType;

    /** 是否主号码: 同一客户仅允许一个 true */
    @Column(name = "is_primary", nullable = false)
    private Boolean isPrimary;

    /** 备注（可选） */
    @Column(name = "remark", length = 200)
    private String remark;
}
