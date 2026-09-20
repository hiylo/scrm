/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : StorageException.java
 * Date : 2026/09/18 11:24:39
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.storage;

/**
 * 对象存储操作异常
 * <p>
 * {@link ObjectStorage} 各实现把底层 SDK 异常统一包装为本异常抛出,
 * 避免 SDK 类型渗透到业务层; 业务层再按需转换为业务异常。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
public class StorageException extends RuntimeException {

    /** 序列化版本标识 */
    private static final long serialVersionUID = 1L;

    /**
     * 构造存储异常
     *
     * @param message 错误描述
     */
    public StorageException(String message) {
        super(message);
    }

    /**
     * 构造存储异常
     *
     * @param message 错误描述
     * @param cause   底层异常
     */
    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
