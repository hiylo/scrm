/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmWorkflowNodeConfigDto.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * SCRM 营销自动化工作流节点配置 DTO。
 * <p>
 * 描述工作流图中单个节点的配置: 节点 ID、节点类型、动作类型与配置 JSON。
 * 用于工作流校验与节点执行时解析节点定义。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmWorkflowNodeConfigDto {

    /** 节点 ID */
    @NotBlank(message = "节点 ID 不能为空")
    @Size(max = 100, message = "节点 ID 长度不能超过 100")
    private String nodeId;

    /** 节点类型: START/END/ACTION/CONDITION/DELAY/LOOP/SWITCH/PARALLEL/WAIT/SUB_WORKFLOW */
    @NotBlank(message = "节点类型不能为空")
    @Pattern(regexp = "START|END|ACTION|CONDITION|DELAY|LOOP|SWITCH|PARALLEL|WAIT|SUB_WORKFLOW",
            message = "节点类型仅支持 START/END/ACTION/CONDITION/DELAY/LOOP/SWITCH/PARALLEL/WAIT/SUB_WORKFLOW")
    private String nodeType;

    /** 动作类型 (可空): SEND_MESSAGE/SEND_EMAIL/SEND_SMS/ADD_TAG/REMOVE_TAG/UPDATE_FIELD/CREATE_TASK/NOTIFY/WEBHOOK/CALL_API/ADD_TO_SEGMENT/REMOVE_FROM_SEGMENT/ASSIGN_OWNER/CREATE_TICKET */
    @Pattern(regexp = "SEND_MESSAGE|SEND_EMAIL|SEND_SMS|ADD_TAG|REMOVE_TAG|UPDATE_FIELD|CREATE_TASK|NOTIFY|WEBHOOK|CALL_API|ADD_TO_SEGMENT|REMOVE_FROM_SEGMENT|ASSIGN_OWNER|CREATE_TICKET|",
            message = "动作类型非法")
    private String actionType;

    /** 节点/动作配置 JSON (可空) */
    private String config;
}
