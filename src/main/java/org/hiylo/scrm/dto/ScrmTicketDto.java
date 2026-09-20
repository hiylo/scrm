/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmTicketDto.java
 * Date : 2026/08/04 08:40:58
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
 * SCRM 客户工单 DTO。
 * <p>
 * 用于工单创建、更新、查询返回。创建时必填标题、客户 ID、类别; 优先级、状态、来源缺省
 * 由服务端补全 (MEDIUM / OPEN / CUSTOMER)。更新时字段非空才覆盖。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
public class ScrmTicketDto {

    /** 主键 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 工单编号 (创建时由服务端生成, 入参忽略) */
    private String ticketNo;

    /** 工单标题 */
    @NotBlank(message = "工单标题不能为空")
    @Size(max = 200, message = "工单标题长度不能超过 200")
    private String title;

    /** 工单描述 (可空) */
    private String description;

    /** 客户 ID */
    @NotNull(message = "客户 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long customerId;

    /** 客户名称 (可空) */
    @Size(max = 200, message = "客户名称长度不能超过 200")
    private String customerName;

    /** 关联账号 ID (可空) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long accountId;

    /** 工单类别: PRODUCT_ISSUE / SERVICE_COMPLAINT / REFUND / EXCHANGE / TECHNICAL / DELIVERY / BILLING / OTHER */
    @NotBlank(message = "工单类别不能为空")
    @Size(max = 50, message = "工单类别长度不能超过 50")
    private String category;

    /** 优先级: URGENT / HIGH / MEDIUM / LOW */
    @Size(max = 10, message = "优先级长度不能超过 10")
    private String priority;

    /** 状态: OPEN / IN_PROGRESS / RESOLVED / CLOSED / REOPENED / CANCELLED */
    @Size(max = 20, message = "状态长度不能超过 20")
    private String status;

    /** 来源: CUSTOMER / AGENT / SYSTEM / PHONE / EMAIL / CHAT */
    @Size(max = 30, message = "来源长度不能超过 30")
    private String source;

    /** 处理人 ID (可空) */
    @Size(max = 100, message = "处理人 ID 长度不能超过 100")
    private String assigneeId;

    /** 处理人名称 (可空) */
    @Size(max = 100, message = "处理人名称长度不能超过 100")
    private String assigneeName;

    /** 处理团队 ID (可空) */
    @Size(max = 100, message = "处理团队 ID 长度不能超过 100")
    private String teamId;

    /** 关联订单 ID (可空) */
    @Size(max = 100, message = "关联订单 ID 长度不能超过 100")
    private String relatedOrderId;

    /** 关联产品 ID (可空) */
    @Size(max = 100, message = "关联产品 ID 长度不能超过 100")
    private String relatedProductId;

    /** SLA 到期时间 (可空) */
    private LocalDateTime slaDueAt;

    /** 首次响应时间 (可空) */
    private LocalDateTime firstResponseAt;

    /** 解决时间 (可空) */
    private LocalDateTime resolvedAt;

    /** 关闭时间 (可空) */
    private LocalDateTime closedAt;

    /** 解决时长 (分钟, 可空) */
    private Integer resolutionTimeMinutes;

    /** 满意度评分 (1-5, 可空) */
    private Integer satisfactionScore;

    /** 满意度评价内容 (可空) */
    @Size(max = 500, message = "满意度评价长度不能超过 500")
    private String satisfactionComment;

    /** 标签 (可空, 逗号分隔) */
    @Size(max = 500, message = "标签长度不能超过 500")
    private String tags;

    /** 创建人 (可空) */
    @Size(max = 100, message = "创建人长度不能超过 100")
    private String createdBy;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 乐观锁版本号 */
    private Long version;
}
