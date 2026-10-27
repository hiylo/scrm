/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmInspectionSnapshotService.java
 * Date : 2026/10/23 09:30:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.entity.ScrmInspectionSnapshotEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.repository.ScrmInspectionSnapshotRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;

/**
 * 工商税务巡检结果快照服务。
 * <p>
 * 承担「快照入库 + 状态哈希比对」核心语义: 同一企业同一维度只保留最新一条
 * ({@code isLatest=true}), 新快照与最近快照计算 SHA-256 哈希比对,
 * <b>哈希一致 = 无变化不告警; 哈希不一致 = 变化, 置 {@code changed=true} 由告警服务推送;
 * 无旧快照 = 基线入库, 不告警</b>。哈希基于稳定的结构化状态 JSON, 与明细的
 * 展示字段顺序/脱敏无关。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScrmInspectionSnapshotService {

    /** 巡检维度: 工商状态 API */
    public static final String DIM_BUSINESS = "BUSINESS";

    /** 巡检维度: 税务公开公告 */
    public static final String DIM_TAX = "TAX";

    /** 快照数据访问层 */
    private final ScrmInspectionSnapshotRepository snapshotRepository;

    /**
     * 保存一条巡检快照并做变化比对。
     * <p>流程: 取最近快照(最新一条) → 计算新快照哈希 → 哈希一致返回无变化;
     * 不一致则把旧快照置为非最新、新快照标记 changed=true。首次入库（无旧快照）
     * 视作基线, changed=false。</p>
     *
     * @param taskId        巡检任务 ID
     * @param dimension     巡检维度
     * @param creditCode    统一社会信用代码
     * @param enterpriseName 企业名称
     * @param source        数据来源
     * @param status        归一化状态
     * @param detailJson    明细 JSON（已脱敏）
     * @param errorMessage  错误信息（可空）
     * @return 保存后的快照实体（{@code changed} 表明是否变化）
     */
    @Transactional
    public ScrmInspectionSnapshotEntity saveSnapshot(Long taskId, String dimension,
                                                     String creditCode, String enterpriseName,
                                                     String source, String status,
                                                     String detailJson, String errorMessage) {
        String stateHash = sha256(detailJson);
        Optional<ScrmInspectionSnapshotEntity> latest = snapshotRepository
                .findFirstByTaskIdAndDimensionAndIsLatestTrue(taskId, dimension);
        boolean changed = latest.isPresent()
                && !stateHash.equals(latest.get().getStateHash());

        ScrmInspectionSnapshotEntity entity = new ScrmInspectionSnapshotEntity();
        entity.setTaskId(taskId);
        entity.setDimension(dimension);
        entity.setCreditCode(creditCode);
        entity.setEnterpriseName(enterpriseName);
        entity.setStateHash(stateHash);
        entity.setIsLatest(true);
        entity.setInspectAt(LocalDateTime.now());
        entity.setSource(source);
        entity.setStatus(status);
        entity.setDetailJson(detailJson);
        entity.setChanged(changed);
        entity.setErrorMessage(errorMessage);
        if (changed) {
            snapshotRepository.markAllNotLatest(taskId, dimension);
        }
        ScrmInspectionSnapshotEntity saved = snapshotRepository.save(entity);
        log.info("[巡检快照] 入库: taskId={}, dim={}, changed={}, hash={}",
                taskId, dimension, changed, stateHash.substring(0, 12));
        return saved;
    }

    /**
     * 查询某任务某维度最新快照（供告警服务读取详情）。
     *
     * @param taskId    巡检任务 ID
     * @param dimension 巡检维度
     * @return 最新快照（可能为空）
     */
    @Transactional(readOnly = true)
    public Optional<ScrmInspectionSnapshotEntity> latest(Long taskId, String dimension) {
        return snapshotRepository.findFirstByTaskIdAndDimensionAndIsLatestTrue(taskId, dimension);
    }

    /**
     * 计算明细 JSON 的 SHA-256 哈希（状态比对核心）。
     *
     * @param text 明细 JSON（null 视为空串, 保证入参一致）
     * @return 64 位十六进制哈希
     */
    public String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((text == null ? "" : text).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            // JDK 必含 SHA-256, 此分支仅防御性兜底, 抛内部异常由上层记录失败
            throw ScrmException.internal("SHA-256 算法不可用");
        }
    }
}