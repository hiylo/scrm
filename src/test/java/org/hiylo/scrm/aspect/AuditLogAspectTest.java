/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : AuditLogAspectTest.java
 * Date : 2026/10/24 09:30:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.aspect;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.hiylo.scrm.service.ScrmAuditLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link AuditLogAspect} 参数排除逻辑单元测试。
 * <p>
 * <b>背景</b>：审计切面会把方法入参序列化为 JSON 落库。原先只拦截
 * {@code instanceof MultipartFile}，漏掉了 {@code List<MultipartFile>} 与
 * {@code MultipartFile[]}（运行时类型是 ArrayList / 数组），导致
 * {@code objectMapper.valueToTree} 去序列化 {@code MultipartFile.inputStream}
 * 抛 {@code IllegalArgumentException}，整个业务请求被审计切面打成 500。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@DisplayName("审计切面参数字典排除")
class AuditLogAspectTest {

    private AuditLogAspect aspect;

    @BeforeEach
    void setUp() {
        aspect = new AuditLogAspect(Mockito.mock(ObjectMapper.class),
                Mockito.mock(ScrmAuditLogService.class));
    }

    @Test
    @DisplayName("单个 MultipartFile 被排除")
    void excludesSingleMultipartFile() {
        MultipartFile file = new MockMultipartFile("file", "a.md", "text/markdown", "x".getBytes());

        assertThat(aspect.isExcludedArg(file)).isTrue();
    }

    @Test
    @DisplayName("List<MultipartFile> 被排除（原缺陷：运行时是 ArrayList，判断漏过）")
    void excludesMultipartFileList() {
        List<MultipartFile> files = List.of(
                new MockMultipartFile("files", "a.md", "text/markdown", "x".getBytes()),
                new MockMultipartFile("files", "b.md", "text/markdown", "y".getBytes()));

        assertThat(aspect.isExcludedArg(files)).isTrue();
    }

    @Test
    @DisplayName("MultipartFile[] 被排除")
    void excludesMultipartFileArray() {
        MultipartFile[] files = {new MockMultipartFile("files", "a.md", "text/markdown", "x".getBytes())};

        assertThat(aspect.isExcludedArg(files)).isTrue();
    }

    @Test
    @DisplayName("普通集合/数组/DTO 不被排除（仍要进审计）")
    void keepsSerializableArgs() {
        assertThat(aspect.isExcludedArg(List.of(1L, 2L))).isFalse();
        assertThat(aspect.isExcludedArg(new Object[]{"a", 1})).isFalse();
        assertThat(aspect.isExcludedArg("plain")).isFalse();
        assertThat(aspect.isExcludedArg(42)).isFalse();
    }
}
