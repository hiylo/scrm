/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm-finance
 * File : ScrmVoucherAttachmentRepository.java
 * Date : 2026/11/10 16:05:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmVoucherAttachmentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * 凭证原始单据数据访问层（V28）。
 *
 * @author Hsi Chu
 * @since V1.0
 */
public interface ScrmVoucherAttachmentRepository
        extends JpaRepository<ScrmVoucherAttachmentEntity, Long> {

    /**
     * 取某张凭证的全部单据（按上传时间倒序，最新的在最上面）。
     *
     * @param voucherId 凭证 ID
     * @return 单据列表
     */
    List<ScrmVoucherAttachmentEntity> findByVoucherIdOrderByUploadedAtDesc(Long voucherId);

    /**
     * 批量统计多张凭证各自的单据数（列表页一次 group by 出全页，避免 N+1）。
     * <p>
     * 返回数组形如 {@code [voucherId, count]}，调用方自行组成 Map。
     * </p>
     *
     * @param voucherIds 凭证 ID 集合（调用方保证非空，空集合会生成非法 SQL）
     * @return 每张凭证的单据数
     */
    @Query("select a.voucherId, count(a) from ScrmVoucherAttachmentEntity a "
            + "where a.voucherId in :voucherIds group by a.voucherId")
    List<Object[]> countGroupByVoucherIds(@Param("voucherIds") List<Long> voucherIds);

    /**
     * 统计某张凭证的单据数（详情/校验用）。
     *
     * @param voucherId 凭证 ID
     * @return 单据数
     */
    long countByVoucherId(Long voucherId);

    /**
     * 删除某张凭证的全部单据行（凭证物理删除时级联清理）。
     *
     * @param voucherId 凭证 ID
     * @return 删除行数
     */
    @Modifying
    @Query("delete from ScrmVoucherAttachmentEntity a where a.voucherId = :voucherId")
    int deleteByVoucherId(@Param("voucherId") Long voucherId);

    /**
     * 取某张凭证的全部对象 key（删凭证时要逐个删对象存储里的文件）。
     *
     * @param voucherId 凭证 ID
     * @return objectKey 列表
     */
    @Query("select a.objectKey from ScrmVoucherAttachmentEntity a where a.voucherId = :voucherId")
    List<String> findObjectKeysByVoucherId(@Param("voucherId") Long voucherId);
}
