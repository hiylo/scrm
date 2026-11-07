/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm
 * File : ScrmRecordingRepository.java
 * Date : 2026/10/22 10:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmRecordingEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 通话录音数据访问层。
 *
 * @author Hsi Chu
 * @since V1.0
 */
public interface ScrmRecordingRepository extends JpaRepository<ScrmRecordingEntity, Long> {

    /**
     * 按文件内容哈希查询（重复上传幂等）。
     *
     * @param fileHash SHA-256
     * @return 录音记录（可能为空）
     */
    Optional<ScrmRecordingEntity> findByFileHash(String fileHash);

    /**
     * 按通话记录查询录音。
     *
     * @param callRecordId 通话记录 ID
     * @return 录音列表
     */
    List<ScrmRecordingEntity> findByCallRecordId(Long callRecordId);

    /**
     * 按用户分页查询录音。
     *
     * @param userId   用户 ID
     * @param pageable 分页参数
     * @return 录音分页
     */
    Page<ScrmRecordingEntity> findByUserIdOrderByCreateTimeDesc(String userId, Pageable pageable);

    /**
     * 统计用户时间区间内已上传录音数（覆盖率口径）。
     *
     * @param userId 用户 ID
     * @param from   起点
     * @param to     终点
     * @return 录音数
     */
    long countByUserIdAndCreateTimeBetween(String userId, LocalDateTime from, LocalDateTime to);
}
