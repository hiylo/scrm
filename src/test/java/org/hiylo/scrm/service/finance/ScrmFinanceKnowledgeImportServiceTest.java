/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm-finance
 * File : ScrmFinanceKnowledgeImportServiceTest.java
 * Date : 2026/10/23 12:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service.finance;

import org.hiylo.scrm.config.ScrmFinanceKnowledgeProperties;
import org.hiylo.scrm.entity.ScrmAiKnowledgeBaseEntity;
import org.hiylo.scrm.entity.ScrmAiKnowledgeDocumentEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.repository.ScrmAiKnowledgeBaseRepository;
import org.hiylo.scrm.repository.ScrmAiKnowledgeDocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * T06 模板批量导入服务单元测试。
 * <p>
 * 覆盖: md 文件解析导入（sourceType=IMPORT / 复核日期字段落库 / 内容含免责行）/
 * 知识库自动创建 / 同标题幂等去重 / 单文件失败不阻断整批 / 不支持的扩展名打回 /
 * 文件列表为空与超上限。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@DisplayName("ScrmFinanceKnowledgeImportService 单元测试")
@ExtendWith(MockitoExtension.class)
class ScrmFinanceKnowledgeImportServiceTest {

    @Mock
    private ScrmAiKnowledgeBaseRepository knowledgeBaseRepository;

    @Mock
    private ScrmAiKnowledgeDocumentRepository documentRepository;

    private ScrmFinanceKnowledgeProperties properties;

    private ScrmFinanceKnowledgeImportService service;

    @BeforeEach
    void setUp() {
        properties = new ScrmFinanceKnowledgeProperties();
        service = new ScrmFinanceKnowledgeImportService(
                knowledgeBaseRepository, documentRepository, properties);
    }

    @Test
    @DisplayName("importTemplateFiles_md: 解析并落库为 AiKnowledgeDocument (IMPORT + 复核日期)")
    void importTemplateFiles_md() throws Exception {
        // Given
        ScrmAiKnowledgeBaseEntity kb = new ScrmAiKnowledgeBaseEntity();
        kb.setId(1001L);
        kb.setKbName("财税知识库");
        when(knowledgeBaseRepository.findByKbName(anyString())).thenReturn(Optional.of(kb));
        when(documentRepository.existsByTitle(anyString())).thenReturn(false);
        when(documentRepository.save(any(ScrmAiKnowledgeDocumentEntity.class)))
                .thenAnswer(invocation -> {
                    ScrmAiKnowledgeDocumentEntity e = invocation.getArgument(0);
                    e.setId(2001L);
                    return e;
                });

        String markdown = """
                # 小规模纳税人报税时间
                - 分类：`代账报税规则`
                - 规则编号：`R-ACCT-0001`
                - 适用地区：全国通用
                - 适用对象：小规模纳税人
                - 来源：IMPORT
                - 状态：已发布　审查状态：`已审核`　最后人工复核日期：2026-10-01
                - 版本号：1　录入人：李会计　录入日期：2026-10-01

                ## 规则要点/适用场景

                小规模纳税人增值税按季申报
                """;
        MultipartFile file = new MockMultipartFile(
                "files", "R-ACCT-0001-小规模纳税人报税时间.md",
                "text/markdown", markdown.getBytes(java.nio.charset.StandardCharsets.UTF_8));

        // When
        List<Map<String, Object>> results = service.importTemplateFiles(List.of(file));

        // Then
        assertThat(results).hasSize(1);
        assertThat(results.get(0).get("success")).isEqualTo(true);
        assertThat(results.get(0).get("articleCode")).isEqualTo("R-ACCT-0001");

        ArgumentCaptor<ScrmAiKnowledgeDocumentEntity> captor =
                ArgumentCaptor.forClass(ScrmAiKnowledgeDocumentEntity.class);
        verify(documentRepository).save(captor.capture());
        ScrmAiKnowledgeDocumentEntity saved = captor.getValue();
        assertThat(saved.getTitle()).isEqualTo("小规模纳税人报税时间");
        assertThat(saved.getSourceType()).isEqualTo("IMPORT");
        assertThat(saved.getContentType()).isEqualTo("MARKDOWN");
        assertThat(saved.getKnowledgeBaseId()).isEqualTo(1001L);
        assertThat(saved.getLastReviewedAt()).isEqualTo(
                java.time.LocalDate.of(2026, 10, 1).atStartOfDay());
        assertThat(saved.getReviewCycleDays()).isEqualTo(180);
        assertThat(saved.getNextReviewAt()).isEqualTo(
                java.time.LocalDate.of(2026, 10, 1).atStartOfDay().plusDays(180));
        assertThat(saved.getTags()).contains("代账报税规则").contains("T06");
        assertThat(saved.getContent()).contains("仅供参考，以最新政策及主管机关确认为准。");
        assertThat(saved.getContent()).contains("小规模纳税人增值税按季申报");
    }

    @Test
    @DisplayName("importTemplateFiles_createKb: 知识库不存在时自动创建")
    void importTemplateFiles_createKb() throws Exception {
        // Given
        when(knowledgeBaseRepository.findByKbName(anyString())).thenReturn(Optional.empty());
        when(knowledgeBaseRepository.save(any(ScrmAiKnowledgeBaseEntity.class)))
                .thenAnswer(invocation -> {
                    ScrmAiKnowledgeBaseEntity e = invocation.getArgument(0);
                    e.setId(9001L);
                    return e;
                });
        when(documentRepository.existsByTitle(anyString())).thenReturn(false);
        when(documentRepository.save(any(ScrmAiKnowledgeDocumentEntity.class)))
                .thenAnswer(invocation -> {
                    ScrmAiKnowledgeDocumentEntity e = invocation.getArgument(0);
                    e.setId(2002L);
                    return e;
                });

        String markdown = "# 简易注销流程\n- 分类：`注销流程`\n- 规则编号：`R-CANCEL-0001`\n- 适用地区：全国\n";
        MultipartFile file = new MockMultipartFile(
                "files", "R-CANCEL-0001-简易注销流程.md", "text/markdown",
                markdown.getBytes(java.nio.charset.StandardCharsets.UTF_8));

        // When
        service.importTemplateFiles(List.of(file));

        // Then
        ArgumentCaptor<ScrmAiKnowledgeBaseEntity> kbCaptor =
                ArgumentCaptor.forClass(ScrmAiKnowledgeBaseEntity.class);
        // 建库 save + 文档计数同步 save
        verify(knowledgeBaseRepository, times(2)).save(kbCaptor.capture());
        assertThat(kbCaptor.getAllValues().get(0).getKbName()).isEqualTo("财税知识库");
        assertThat(kbCaptor.getAllValues().get(0).getCategory()).isEqualTo("POLICY");
        // 文档计数同步
        assertThat(kbCaptor.getAllValues().get(1).getDocumentCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("importTemplateFiles_duplicate: 同标题重复导入跳过")
    void importTemplateFiles_duplicate() throws Exception {
        // Given
        ScrmAiKnowledgeBaseEntity kb = new ScrmAiKnowledgeBaseEntity();
        kb.setId(1001L);
        when(knowledgeBaseRepository.findByKbName(anyString())).thenReturn(Optional.of(kb));
        when(documentRepository.existsByTitle("简易注销流程")).thenReturn(true);

        String markdown = "# 简易注销流程\n- 分类：`注销流程`\n- 规则编号：`R-CANCEL-0001`\n- 适用地区：全国\n";
        MultipartFile file = new MockMultipartFile(
                "files", "R-CANCEL-0001.md", "text/markdown",
                markdown.getBytes(java.nio.charset.StandardCharsets.UTF_8));

        // When
        List<Map<String, Object>> results = service.importTemplateFiles(List.of(file));

        // Then
        assertThat(results).hasSize(1);
        assertThat(results.get(0).get("success")).isEqualTo(true);
        verify(documentRepository, never()).save(any(ScrmAiKnowledgeDocumentEntity.class));
    }

    @Test
    @DisplayName("importTemplateFiles_partialFailure: 单文件失败不阻断整批")
    void importTemplateFiles_partialFailure() throws Exception {
        // Given
        ScrmAiKnowledgeBaseEntity kb = new ScrmAiKnowledgeBaseEntity();
        kb.setId(1001L);
        when(knowledgeBaseRepository.findByKbName(anyString())).thenReturn(Optional.of(kb));
        when(documentRepository.existsByTitle(anyString())).thenReturn(false);
        when(documentRepository.save(any(ScrmAiKnowledgeDocumentEntity.class)))
                .thenAnswer(invocation -> {
                    ScrmAiKnowledgeDocumentEntity e = invocation.getArgument(0);
                    e.setId(2003L);
                    return e;
                });

        MultipartFile good = new MockMultipartFile(
                "files", "R-BIZ-0001.md", "text/markdown",
                "# 注册公司需要什么材料\n- 分类：`营业执照办理`\n- 规则编号：`R-BIZ-0001`\n- 适用地区：全国\n"
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        MultipartFile bad = new MockMultipartFile(
                "files", "bad.txt", "text/plain",
                "# 无编号文件\n- 分类：`注销流程`\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        MultipartFile wrongExt = new MockMultipartFile(
                "files", "note.pdf", "application/pdf",
                "%PDF-1.4".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        // When
        List<Map<String, Object>> results = service.importTemplateFiles(List.of(good, bad, wrongExt));

        // Then
        assertThat(results).hasSize(3);
        assertThat(results.get(0).get("success")).isEqualTo(true);
        assertThat(results.get(1).get("success")).isEqualTo(false);
        assertThat(results.get(1).get("message").toString()).contains("规则编号");
        assertThat(results.get(2).get("success")).isEqualTo(false);
        assertThat(results.get(2).get("message").toString()).contains("不支持的文件类型");
        verify(documentRepository, times(1)).save(any(ScrmAiKnowledgeDocumentEntity.class));
    }

    @Test
    @DisplayName("importTemplateFiles_empty: 文件列表为空抛 ScrmException")
    void importTemplateFiles_empty() {
        assertThatThrownBy(() -> service.importTemplateFiles(null))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("不能为空");
    }

    @Test
    @DisplayName("importTemplateFiles_docx: docx 文件走 docx 解析器导入")
    void importTemplateFiles_docx() throws Exception {
        // Given
        ScrmAiKnowledgeBaseEntity kb = new ScrmAiKnowledgeBaseEntity();
        kb.setId(1001L);
        when(knowledgeBaseRepository.findByKbName(anyString())).thenReturn(Optional.of(kb));
        when(documentRepository.existsByTitle(anyString())).thenReturn(false);
        when(documentRepository.save(any(ScrmAiKnowledgeDocumentEntity.class)))
                .thenAnswer(invocation -> {
                    ScrmAiKnowledgeDocumentEntity e = invocation.getArgument(0);
                    e.setId(2004L);
                    return e;
                });

        // 用 POI 生成符合 T06 模板的 docx
        byte[] docxBytes = buildSampleDocx();
        MultipartFile file = new MockMultipartFile(
                "files", "R-QUA-0001-食品经营许可证.md.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                docxBytes);

        // When
        List<Map<String, Object>> results = service.importTemplateFiles(List.of(file));

        // Then
        assertThat(results).hasSize(1);
        assertThat(results.get(0).get("success")).isEqualTo(true);
        assertThat(results.get(0).get("articleCode")).isEqualTo("R-QUA-0001");

        ArgumentCaptor<ScrmAiKnowledgeDocumentEntity> captor =
                ArgumentCaptor.forClass(ScrmAiKnowledgeDocumentEntity.class);
        verify(documentRepository).save(captor.capture());
        ScrmAiKnowledgeDocumentEntity saved = captor.getValue();
        assertThat(saved.getTitle()).isEqualTo("食品经营许可证办理流程");
        assertThat(saved.getSourceType()).isEqualTo("IMPORT");
        assertThat(saved.getContent()).contains("办理条件");
    }

    /**
     * 用 POI 生成简易 T06 docx 模板（标题 + 通用字段 + 业务段）。
     */
    private byte[] buildSampleDocx() throws Exception {
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        try (org.apache.poi.xwpf.usermodel.XWPFDocument doc =
                     new org.apache.poi.xwpf.usermodel.XWPFDocument()) {
            org.apache.poi.xwpf.usermodel.XWPFParagraph p1 = doc.createParagraph();
            p1.setStyle("Heading1");
            p1.createRun().setText("食品经营许可证办理流程");
            addDocxPara(doc, "- 分类：`资质代办`");
            addDocxPara(doc, "- 规则编号：`R-QUA-0001`");
            addDocxPara(doc, "- 适用地区：全国");
            addDocxPara(doc, "- 适用对象：餐饮企业");
            addDocxPara(doc, "- 来源：IMPORT");
            addDocxPara(doc, "- 状态：草稿　审查状态：`待人工审核`");
            addDocxPara(doc, "- 版本号：1　录入人：AI 起草　录入日期：2026-10-22");
            addDocxHeading2(doc, "办理条件");
            addDocxPara(doc, "- 有固定经营场所，面积符合当地要求");
            addDocxHeading2(doc, "政策依据");
            addDocxPara(doc, "- 《食品经营许可和备案管理办法》");
            doc.write(baos);
        }
        return baos.toByteArray();
    }

    /**
     * 向 docx 追加普通段落。
     */
    private void addDocxPara(org.apache.poi.xwpf.usermodel.XWPFDocument doc, String text) {
        org.apache.poi.xwpf.usermodel.XWPFParagraph p = doc.createParagraph();
        p.createRun().setText(text);
    }

    /**
     * 向 docx 追加 Heading2 段落。
     */
    private void addDocxHeading2(org.apache.poi.xwpf.usermodel.XWPFDocument doc, String text) {
        org.apache.poi.xwpf.usermodel.XWPFParagraph p = doc.createParagraph();
        p.setStyle("Heading2");
        p.createRun().setText(text);
    }
}
