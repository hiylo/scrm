/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm-finance
 * File : ScrmTaxHolidayAiServiceTest.java
 * Description : 假期 AI 抽取的安全约束测试（草稿制 / 不编造 / 采纳才入库）
 */

package org.hiylo.scrm.service.finance.tax;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.hiylo.scrm.entity.ScrmTaxHolidayDraftEntity;
import org.hiylo.scrm.entity.ScrmTaxWorkdayOverrideEntity;
import org.hiylo.scrm.entity.ScrmTaxHolidayEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.feign.AiChatClient;
import org.hiylo.scrm.feign.dto.AiChatRequest;
import org.hiylo.scrm.feign.dto.AiChatResponse;
import org.hiylo.scrm.repository.ScrmTaxHolidayDraftRepository;
import org.hiylo.scrm.repository.ScrmTaxHolidayRepository;
import org.hiylo.scrm.repository.ScrmTaxWorkdayOverrideRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 假期 AI 抽取服务测试。
 * <p>
 * 重点验证三条安全约束：
 * <ol>
 *   <li>AI 产出只落草稿，<b>绝不直接写入生效表</b>；</li>
 *   <li>抓取失败或 AI 不可用时<b>不生成草稿</b>，宁缺勿编；</li>
 *   <li>只有人工调用 {@code applyDraft} 才真正入库。</li>
 * </ol>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("假期 AI 抽取：草稿制与安全约束")
class ScrmTaxHolidayAiServiceTest {

    @Mock
    private ScrmTaxHolidayDraftRepository draftRepository;

    @Mock
    private ScrmTaxHolidayRepository holidayRepository;

    @Mock
    private ScrmTaxWorkdayOverrideRepository workdayOverrideRepository;

    @Mock
    private AiChatClient aiChatClient;

    private ScrmTaxHolidayExtractService extractService;

    private ScrmTaxHolidayAiService aiService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        extractService = new ScrmTaxHolidayExtractService(
                objectMapper, aiChatClient, draftRepository);
        aiService = new ScrmTaxHolidayAiService(
                draftRepository, extractService, objectMapper, null,
                holidayRepository, workdayOverrideRepository);
        when(draftRepository.save(any(ScrmTaxHolidayDraftEntity.class)))
                .thenAnswer(inv -> {
                    ScrmTaxHolidayDraftEntity d = inv.getArgument(0);
                    if (d.getId() == null) {
                        d.setId(5001L);
                    }
                    return d;
                });
    }

    /** 构造一个含指定内容的 AI 响应 */
    private AiChatResponse responseWith(String content) {
        AiChatResponse response = new AiChatResponse();
        AiChatResponse.AiChatChoice choice = new AiChatResponse.AiChatChoice();
        AiChatRequest.AiChatMessage msg = new AiChatRequest.AiChatMessage();
        msg.setRole("assistant");
        msg.setContent(content);
        choice.setMessage(msg);
        response.setChoices(List.of(choice));
        return response;
    }

    @Test
    @DisplayName("抽取成功：结果落草稿，且绝不写入生效表")
    void extract_savesDraftOnly() throws Exception {
        String modelJson = "{\"holidays\":[{\"name\":\"国庆节\",\"startDate\":\"2027-10-01\","
                + "\"endDate\":\"2027-10-07\"}],\"workdays\":[{\"workDate\":\"2027-10-09\"}]}";
        when(aiChatClient.chatCompletion(any())).thenReturn(responseWith(modelJson));

        ScrmTaxHolidayDraftEntity draft = extractService.extract(
                2027, "国务院办公厅 2027 年节假日安排", "https://gov.cn/x", "国庆节：10月1日至10月7日放假");

        assertThat(draft.getHolidayYear()).isEqualTo(2027);
        assertThat(draft.getDraftStatus()).isEqualTo(ScrmTaxHolidayDraftEntity.STATUS_PENDING);
        assertThat(draft.getHolidaysJson()).contains("国庆节");
        assertThat(draft.getWorkdaysJson()).contains("2027-10-09");

        // 关键：抽取阶段绝不允许碰生效表
        verify(holidayRepository, never()).save(any());
        verify(workdayOverrideRepository, never()).save(any());
    }

    @Test
    @DisplayName("模型输出带 markdown 代码块时仍能解析")
    void extract_toleratesMarkdownWrapper() throws Exception {
        String wrapped = "```json\n{\"holidays\":[{\"name\":\"元旦\","
                + "\"startDate\":\"2027-01-01\",\"endDate\":\"2027-01-03\"}],\"workdays\":[]}\n```";
        when(aiChatClient.chatCompletion(any())).thenReturn(responseWith(wrapped));

        ScrmTaxHolidayDraftEntity draft = extractService.extract(
                2027, "公告", "https://gov.cn/x", "元旦放假三天");

        assertThat(draft.getHolidaysJson()).contains("元旦");
    }

    @Test
    @DisplayName("AI 未启用（降级响应）时拒绝生成草稿，绝不编造")
    void extract_rejectsWhenAiDisabled() {
        when(aiChatClient.chatCompletion(any())).thenReturn(responseWith(AiChatClient.FALLBACK_CONTENT));

        assertThatThrownBy(() -> extractService.extract(
                2027, "公告", "https://gov.cn/x", "正文"))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("连续 3 次");

        // 降级响应视为通道不可用（可恢复），重试满 3 次才放弃
        verify(aiChatClient, times(3)).chatCompletion(any());
        verify(draftRepository, never()).save(any());
    }

    @Test
    @DisplayName("公告原文为空时直接拒绝：模型无法自行联网抓取")
    void extract_rejectsEmptySource() {
        assertThatThrownBy(() -> extractService.extract(2027, "公告", "https://gov.cn/x", "  "))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("公告原文不能为空");
    }

    @Test
    @DisplayName("模型输出不是 JSON 时拒绝，不用模型自由文本充数")
    void extract_rejectsNonJson() {
        when(aiChatClient.chatCompletion(any())).thenReturn(responseWith("抱歉，我无法确定具体日期。"));

        assertThatThrownBy(() -> extractService.extract(
                2027, "公告", "https://gov.cn/x", "正文"))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("不是合法 JSON");
    }

    @Test
    @DisplayName("采纳草稿才会写入生效表，并清空该年旧数据避免叠加")
    void applyDraft_writesToEffectiveTable() throws Exception {
        ScrmTaxHolidayDraftEntity draft = new ScrmTaxHolidayDraftEntity();
        draft.setId(9001L);
        draft.setHolidayYear(2027);
        draft.setDraftStatus(ScrmTaxHolidayDraftEntity.STATUS_PENDING);
        draft.setHolidaysJson("[{\"name\":\"国庆节\",\"startDate\":\"2027-10-01\",\"endDate\":\"2027-10-07\"}]");
        draft.setWorkdaysJson("[{\"workDate\":\"2027-10-09\",\"remark\":\"调休上班\"}]");
        when(draftRepository.findById(9001L)).thenReturn(Optional.of(draft));

        ScrmTaxHolidayEntity old = new ScrmTaxHolidayEntity();
        old.setId(1L);
        when(holidayRepository.findByHolidayYearAndIsActiveTrueOrderByStartDateAsc(2027))
                .thenReturn(List.of(old));
        when(workdayOverrideRepository.existsByWorkDateAndIsActiveTrue(any())).thenReturn(false);

        var result = aiService.applyDraft(9001L, "admin");

        assertThat(result.get("holidayCount")).isEqualTo(1);
        assertThat(result.get("workdayCount")).isEqualTo(1);
        // 旧数据先清空，避免 AI 结果与手工数据叠加成两段区间
        verify(holidayRepository).deleteAll(List.of(old));
        verify(holidayRepository).save(any(ScrmTaxHolidayEntity.class));
        assertThat(draft.getDraftStatus()).isEqualTo(ScrmTaxHolidayDraftEntity.STATUS_APPLIED);
        assertThat(draft.getReviewedBy()).isEqualTo("admin");
    }

    @Test
    @DisplayName("已处理的草稿不能重复采纳")
    void applyDraft_rejectsAlreadyHandled() {
        ScrmTaxHolidayDraftEntity draft = new ScrmTaxHolidayDraftEntity();
        draft.setId(9002L);
        draft.setDraftStatus(ScrmTaxHolidayDraftEntity.STATUS_APPLIED);
        when(draftRepository.findById(9002L)).thenReturn(Optional.of(draft));

        assertThatThrownBy(() -> aiService.applyDraft(9002L, "admin"))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("已处理");
    }

    @Test
    @DisplayName("草稿中假期区间非法时跳过该条，不影响其余写入")
    void applyDraft_skipsInvalidRange() throws Exception {
        ScrmTaxHolidayDraftEntity draft = new ScrmTaxHolidayDraftEntity();
        draft.setId(9003L);
        draft.setHolidayYear(2027);
        draft.setDraftStatus(ScrmTaxHolidayDraftEntity.STATUS_PENDING);
        // 第二条结束日早于开始日，应被跳过
        draft.setHolidaysJson("[{\"name\":\"元旦\",\"startDate\":\"2027-01-01\",\"endDate\":\"2027-01-03\"},"
                + "{\"name\":\"错误\",\"startDate\":\"2027-05-05\",\"endDate\":\"2027-05-01\"}]");
        draft.setWorkdaysJson("[]");
        when(draftRepository.findById(9003L)).thenReturn(Optional.of(draft));
        when(holidayRepository.findByHolidayYearAndIsActiveTrueOrderByStartDateAsc(2027))
                .thenReturn(List.of());

        var result = aiService.applyDraft(9003L, "admin");

        assertThat(result.get("holidayCount")).isEqualTo(1);
    }

    @Test
    @DisplayName("丢弃草稿后状态为 REJECTED，不写生效表")
    void rejectDraft_marksRejected() throws Exception {
        ScrmTaxHolidayDraftEntity draft = new ScrmTaxHolidayDraftEntity();
        draft.setId(9004L);
        draft.setDraftStatus(ScrmTaxHolidayDraftEntity.STATUS_PENDING);
        when(draftRepository.findById(9004L)).thenReturn(Optional.of(draft));

        aiService.rejectDraft(9004L, "admin", "区间与公告不符");

        assertThat(draft.getDraftStatus()).isEqualTo(ScrmTaxHolidayDraftEntity.STATUS_REJECTED);
        assertThat(draft.getReviewRemark()).isEqualTo("区间与公告不符");
        verify(holidayRepository, never()).save(any());
    }

    @Test
    @DisplayName("抓取失败时不生成草稿：宁可没有数据，也不让模型编造")
    void autoExtract_returnsNullWhenFetchFails() {
        // restClientBuilder 为 null 会直接抛 NPE，被 catch 成「抓取失败」
        ScrmTaxHolidayDraftEntity draft = aiService.autoExtract(2027);

        assertThat(draft).isNull();
        verify(draftRepository, never()).save(any());
    }

    @Test
    @DisplayName("AI 可恢复失败时重试 3 次后才报错（不是一次就转人工）")
    void extract_retriesThreeTimesOnRecoverableFailure() {
        when(aiChatClient.chatCompletion(any())).thenReturn(null);

        assertThatThrownBy(() -> extractService.extract(2027, "公告", "http://x", "正文"))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("连续 3 次");

        verify(aiChatClient, times(3)).chatCompletion(any());
        verify(draftRepository, never()).save(any());
    }

    @Test
    @DisplayName("第 2 次成功则不再重试，正常产出草稿")
    void extract_succeedsOnSecondAttempt() {
        when(aiChatClient.chatCompletion(any()))
                .thenReturn(null)
                .thenReturn(responseWith("{\"holidays\":[{\"name\":\"元旦\","
                        + "\"startDate\":\"2027-01-01\",\"endDate\":\"2027-01-03\"}],"
                        + "\"workdays\":[]}"));

        var draft = extractService.extract(2027, "公告", "http://x", "正文");

        assertThat(draft).isNotNull();
        verify(aiChatClient, times(2)).chatCompletion(any());
    }

    @Test
    @DisplayName("模型返回空数组时拒绝生成草稿：空草稿被采纳会清空该年份假期")
    void extract_rejectsEmptyHolidays() {
        when(aiChatClient.chatCompletion(any()))
                .thenReturn(responseWith("{\"holidays\":[],\"workdays\":[]}"));

        assertThatThrownBy(() -> extractService.extract(2027, "公告", "http://x", "正文"))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("未从公告中抽到任何假期");

        verify(draftRepository, never()).save(any());
    }

    @Test
    @DisplayName("补班日已存在时 workdayCount 仍按草稿总数返回，不能只数新增")
    void applyDraft_countsExistingWorkdays() {
        ScrmTaxHolidayDraftEntity draft = new ScrmTaxHolidayDraftEntity();
        draft.setId(9005L);
        draft.setHolidayYear(2026);
        draft.setDraftStatus(ScrmTaxHolidayDraftEntity.STATUS_PENDING);
        draft.setHolidaysJson("[{\"name\":\"国庆节\",\"startDate\":\"2026-10-01\","
                + "\"endDate\":\"2026-10-07\"}]");
        draft.setWorkdaysJson("[{\"workDate\":\"2026-02-14\"},{\"workDate\":\"2026-09-20\"},"
                + "{\"workDate\":\"2026-10-10\"}]");
        when(draftRepository.findById(9005L)).thenReturn(Optional.of(draft));
        when(holidayRepository.findByHolidayYearAndIsActiveTrueOrderByStartDateAsc(2026))
                .thenReturn(List.of());

        // 三条补班日里只有 10/10 是新的
        java.time.LocalDate already1 = java.time.LocalDate.parse("2026-02-14");
        java.time.LocalDate already2 = java.time.LocalDate.parse("2026-09-20");
        when(workdayOverrideRepository.existsByWorkDateAndIsActiveTrue(any())).thenReturn(false);
        when(workdayOverrideRepository.existsByWorkDateAndIsActiveTrue(already1)).thenReturn(true);
        when(workdayOverrideRepository.existsByWorkDateAndIsActiveTrue(already2)).thenReturn(true);

        var result = aiService.applyDraft(9005L, "admin");

        // 口径修正前的 bug: 这里会返回 1（只数新增），让运维误以为补班日只写了 1 条
        assertThat(result.get("workdayCount")).isEqualTo(3);
        assertThat(result.get("workdayAdded")).isEqualTo(1);
    }

    @Test
    @DisplayName("判重只看生效中的补班日：停用记录占用唯一键但不应阻止写入")
    void applyDraft_ignoresInactiveWorkday() {
        ScrmTaxHolidayDraftEntity draft = new ScrmTaxHolidayDraftEntity();
        draft.setId(9006L);
        draft.setHolidayYear(2026);
        draft.setDraftStatus(ScrmTaxHolidayDraftEntity.STATUS_PENDING);
        draft.setHolidaysJson("[{\"name\":\"国庆节\",\"startDate\":\"2026-10-01\","
                + "\"endDate\":\"2026-10-07\"}]");
        draft.setWorkdaysJson("[{\"workDate\":\"2026-10-10\"}]");
        when(draftRepository.findById(9006L)).thenReturn(Optional.of(draft));
        when(holidayRepository.findByHolidayYearAndIsActiveTrueOrderByStartDateAsc(2026))
                .thenReturn(List.of());
        // 仓储方法带 isActive 条件, 停用记录不会被返回 -> 视为不存在, 应当写入
        when(workdayOverrideRepository.existsByWorkDateAndIsActiveTrue(any())).thenReturn(false);

        var result = aiService.applyDraft(9006L, "admin");

        assertThat(result.get("workdayCount")).isEqualTo(1);
        assertThat(result.get("workdayAdded")).isEqualTo(1);
        verify(workdayOverrideRepository).save(any(ScrmTaxWorkdayOverrideEntity.class));
    }
}
