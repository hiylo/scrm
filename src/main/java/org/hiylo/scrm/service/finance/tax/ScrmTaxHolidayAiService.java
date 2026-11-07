/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm-finance
 * File : ScrmTaxHolidayAiService.java
 * Date : 2026/10/27 15:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service.finance.tax;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hiylo.scrm.entity.ScrmTaxHolidayDraftEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.repository.ScrmTaxHolidayDraftRepository;
import java.util.LinkedHashMap;
import java.time.LocalDate;
import org.springframework.transaction.annotation.Transactional;
import org.hiylo.scrm.entity.ScrmTaxWorkdayOverrideEntity;
import org.hiylo.scrm.entity.ScrmTaxHolidayEntity;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 假期 AI 抽取编排服务。
 * <p>
 * 把「抓取公告 → 交给 AI 结构化 → 落草稿」串起来：
 * 模型不能联网，抓取由本服务完成（访问国务院办公厅节假日安排页面），
 * 抓到正文后交给 {@link ScrmTaxHolidayExtractService} 做结构化抽取。
 * </p>
 * <p>
 * <b>抓取失败不生成草稿</b>：宁可没有数据，也不要模型凭空编造——
 * 编造的假期会让申报截止日直接算错。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScrmTaxHolidayAiService {

    /**
     * 国务院政策文件库 JSON 检索接口。
     * <p>
     * 不能用 {@code sousuo.www.gov.cn/sousuo/search.shtml} 那个搜索页：它是 JS 渲染的壳，
     * 服务端抓回来只有导航骨架（实测 67KB 却搜不到任何公告标题），正文为空。
     * 该 JSON 接口直接返回结果列表，可拿到公告标题与 URL。
     * </p>
     */
    private static final String GOV_SEARCH_API =
            "https://sousuo.www.gov.cn/search-gov/data?t=zhengcelibrary_gw"
                    + "&q=%E8%8A%82%E5%81%87%E6%97%A5%E5%AE%89%E6%8E%92&p=1&n=20&searchfield=title";

    /** 抓取超时（毫秒） */
    private static final int FETCH_TIMEOUT_MS = 15_000;

    /** 正文送给模型的最大长度 */
    private static final int MAX_TEXT = 6000;

    /** 草稿数据访问层 */
    private final ScrmTaxHolidayDraftRepository draftRepository;

    /** AI 抽取服务（解析 + 落草稿） */
    private final ScrmTaxHolidayExtractService extractService;

    /** JSON 处理（供人工录入草稿序列化） */
    private final ObjectMapper objectMapper;

    /** 抓取超时（毫秒）：公告站点响应慢不应拖垮调度 */
    private static final int FETCH_CONNECT_TIMEOUT_MS = 8_000;

    /** 抓取读取超时（毫秒） */
    private static final int FETCH_READ_TIMEOUT_MS = 8_000;

    /** 通知中心（次年数据提醒） */
    private final org.hiylo.scrm.service.ScrmNotificationCenterService notificationCenterService;

    /** 提醒收件人（默认 admin，可配置） */
    @org.springframework.beans.factory.annotation.Value("${scrm.tax-calendar.notify-recipient:admin}")
    private String notifyRecipient;

    /** 最近一次自动抽取的失败原因（供接口层回显真实原因） */
    private volatile String lastFailureReason;

    /** 最近一次抓到的公告 URL（作为草稿来源，供人工核对溯源） */
    private volatile String lastAnnouncementUrl;

    /** 一个完整年度至少应有的法定假期数 */
    private static final int MIN_HOLIDAYS_PER_YEAR = 7;

    /** 生效假期仓储（采纳时写入） */
    private final org.hiylo.scrm.repository.ScrmTaxHolidayRepository holidayRepository;

    /** 调休上班日仓储（采纳时写入） */
    private final org.hiylo.scrm.repository.ScrmTaxWorkdayOverrideRepository workdayOverrideRepository;

    /**
     * 自动抽取指定年份假期：抓取公告 → AI 结构化 → 落草稿。
     * <p>
     * 任何环节失败都返回 null 并记日志，不抛异常——调度任务不应因外部站点波动而失败重试到爆。
     * </p>
     *
     * @param year 目标年份
     * @return 生成的草稿，失败返回 null
     */
    public ScrmTaxHolidayDraftEntity autoExtract(int year) {
        lastFailureReason = null;
        lastAnnouncementUrl = null;
        String text;
        try {
            text = fetchAnnouncementText(year);
        } catch (RuntimeException e) {
            log.warn("[假期-AI] 抓取公告失败, 不生成草稿(避免模型编造): year={}, err={}",
                    year, e.getMessage());
            lastFailureReason = "公告抓取失败：" + e.getMessage();
            return null;
        }
        if (text == null || text.isBlank()) {
            log.warn("[假期-AI] 公告正文为空, 不生成草稿: year={}", year);
            lastFailureReason = "公告正文为空（当年安排可能尚未发布）";
            return null;
        }
        try {
            return extractService.extract(year,
                    "国务院办公厅 " + year + " 年部分节假日安排", lastAnnouncementUrl, text);
        } catch (ScrmException e) {
            log.warn("[假期-AI] 抽取失败: year={}, err={}", year, e.getMessage());
            lastFailureReason = "AI 抽取失败：" + e.getMessage();
            notifyAiFailure(year, e.getMessage());
            return null;
        } catch (RuntimeException e) {
            log.error("[假期-AI] 抽取异常: year={}", year, e);
            lastFailureReason = "AI 抽取异常：" + e.getMessage();
            notifyAiFailure(year, e.getMessage());
            return null;
        }
    }

    /**
     * 最近一次 {@link #autoExtract(int)} 未生成草稿的原因。
     * <p>供接口层如实回显，避免把「抓取失败」「AI 未启用」「重试耗尽」混成一句猜测性文案。</p>
     *
     * @return 失败原因；成功时为 null
     */
    public String getLastFailureReason() {
        return lastFailureReason;
    }

    /**
     * AI 抽取彻底失败（重试已耗尽）时通知人工介入。
     * <p>
     * 只在真正需要人处理时打扰人：抓取失败、数据已齐备、AI 正常产出草稿都不发通知。
     * 通知失败只记日志，不影响调度任务本身的返回。
     * </p>
     *
     * @param year  目标年份
     * @param reason 失败原因
     */
    private void notifyAiFailure(int year, String reason) {
        try {
            org.hiylo.scrm.dto.ScrmNotificationSendDto dto =
                    new org.hiylo.scrm.dto.ScrmNotificationSendDto();
            dto.setTemplateCode("tax-holiday-year-remind");
            dto.setRecipients(List.of(notifyRecipient));
            Map<String, String> vars = new LinkedHashMap<>();
            vars.put("year", String.valueOf(year));
            dto.setVariables(vars);
            notificationCenterService.sendNotification(dto);
            log.warn("[假期-AI] 已通知人工介入: year={}, reason={}", year, reason);
        } catch (RuntimeException e) {
            log.error("[假期-AI] 转人工通知发送失败: year={}", year, e);
        }
    }

    /**
     * 手工录入草稿（AI 不可用时的兜底，效果等同人工确认）。
     *
     * @param year      年份
     * @param holidays  假期明细 [{name,startDate,endDate}]
     * @param workdays  调休上班日 [{workDate,remark}]
     * @return 生成的草稿
     * @throws ScrmException 参数非法
     */
    public ScrmTaxHolidayDraftEntity createManualDraft(int year, List<Map<String, Object>> holidays,
                                                      List<Map<String, Object>> workdays) throws ScrmException {
        if (holidays == null || holidays.isEmpty()) {
            throw ScrmException.badRequest("假期明细不能为空");
        }
        ScrmTaxHolidayDraftEntity draft = new ScrmTaxHolidayDraftEntity();
        draft.setHolidayYear(year);
        draft.setSourceTitle("手工录入");
        draft.setHolidaysJson(toJson(holidays));
        draft.setWorkdaysJson(toJson(workdays == null ? List.of() : workdays));
        draft.setExtractMode(ScrmTaxHolidayDraftEntity.MODE_MANUAL);
        draft.setDraftStatus(ScrmTaxHolidayDraftEntity.STATUS_PENDING);
        return draftRepository.save(draft);
    }

    /**
     * 检查指定年份假期数据是否齐备，不齐备则发提醒。
     * <p>
     * 由调度每年 11 月触发：国务院办公厅通常此时发布次年安排，
     * 「没录入」必须主动通知——否则顺延静默失效，截止日算错也没人知道。
     * </p>
     *
     * @param year 目标年份
     * @return 是否发出了提醒
     */
    public boolean remindIfIncomplete(int year) {
        long count = holidayRepository.findByHolidayYearAndIsActiveTrueOrderByStartDateAsc(year).size();
        if (count >= MIN_HOLIDAYS_PER_YEAR) {
            log.info("[假期-提醒] {} 年假期数据已齐备({} 个), 无需提醒", year, count);
            return false;
        }
        try {
            org.hiylo.scrm.dto.ScrmNotificationSendDto dto =
                    new org.hiylo.scrm.dto.ScrmNotificationSendDto();
            dto.setTemplateCode("tax-holiday-year-remind");
            dto.setRecipients(List.of(notifyRecipient));
            // 模板占位符统一 {year}（与 tax-deadline-remind 等既有模板一致）；
            // 不能写 ${year}——Flyway 会把它当迁移占位符解析
            Map<String, String> vars = new LinkedHashMap<>();
            vars.put("year", String.valueOf(year));
            dto.setVariables(vars);
            notificationCenterService.sendNotification(dto);
            log.warn("[假期-提醒] {} 年假期仅 {} 个(应≥{}), 已发送提醒给 {}",
                    year, count, MIN_HOLIDAYS_PER_YEAR, notifyRecipient);
            return true;
        } catch (RuntimeException e) {
            // 提醒失败不影响主流程，但必须留日志——静默失效正是要避免的
            log.error("[假期-提醒] 发送提醒失败: year={}", year, e);
            return false;
        }
    }

    /**
     * 抓取公告正文。
     * <p>
     * 国务院办公厅的节假日安排公告 URL 逐年不同，直接猜 URL 不可靠，
     * 因此这里抓检索结果页并取正文；抽不到有效内容时返回 null，
     * 由调用方决定不生成草稿（而不是让模型凭空生成）。
     * </p>
     *
     * @param year 目标年份
     * @return 正文纯文本，失败返回 null
     */
    private String fetchAnnouncementText(int year) {
        // 容器未提供 RestClient.Builder，按固定超时自行构建（与 AiChatClient 一致的做法）
        RestClient client = RestClient.builder()
                .requestFactory(fetchRequestFactory())
                .build();
        String articleUrl = findAnnouncementUrl(client, year);
        lastAnnouncementUrl = articleUrl;
        if (articleUrl == null) {
            // 公告尚未发布属于正常情况（次年安排通常前一年 11 月才发），不算故障
            log.info("[假期-AI] 未检索到 {} 年节假日安排公告，视为尚未发布", year);
            return null;
        }
        String html = client.get()
                .uri(java.net.URI.create(articleUrl))
                .accept(MediaType.TEXT_HTML)
                .retrieve()
                .body(String.class);
        if (html == null || html.isBlank()) {
            return null;
        }
        String text = stripHtml(html);
        if (text.length() > MAX_TEXT) {
            text = text.substring(0, MAX_TEXT);
        }
        log.info("[假期-AI] 公告抓取成功: year={}, url={}, 正文长度={}", year, articleUrl, text.length());
        return text;
    }

    /**
     * 在检索结果中按年份精确匹配公告 URL。
     * <p>
     * 只认标题含「国务院办公厅关于{年份}年部分节假日安排的通知」的条目，
     * 避免抓到相邻年份的安排（模型看不到其他年份，出错也无从发现）。
     * </p>
     *
     * @param client 已配置超时的客户端
     * @param year   目标年份
     * @return 公告 URL；未找到返回 null
     */
    private String findAnnouncementUrl(RestClient client, int year) {
        try {
            // 必须用 uri(URI) 而不是 uri(String)：后者按 URI 模板处理，
            // 会把查询串里的 %E8%8A%82 再编码一次，服务端收到乱码查询词 → 返回 0 条结果
            String json = client.get()
                    .uri(java.net.URI.create(GOV_SEARCH_API))
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(String.class);
            if (json == null || json.isBlank()) {
                return null;
            }
            com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(json);
            String expected = year + "年部分节假日安排";
            for (com.fasterxml.jackson.databind.JsonNode item : extractResultNodes(root)) {
                String title = item.path("title").asText("");
                // 检索结果标题带 <em> 高亮标签，先去掉再比对
                title = title.replaceAll("<[^>]+>", "");
                String url = item.path("url").asText(null);
                if (url == null || url.isBlank()) {
                    url = item.path("link").asText(null);
                }
                if (url != null && title.contains(expected)) {
                    return url;
                }
            }
            return null;
        } catch (Exception e) {
            // 检索失败按抓取失败处理，由上层决定不生成草稿（绝不让模型凭空编造）
            throw new IllegalStateException("检索国务院公告失败: " + e.getMessage(), e);
        }
    }

    /**
     * 从检索接口响应中取出结果条目列表。
     * <p>接口结构可能调整，这里不绑定固定路径，递归找出同时含 {@code title} 与 {@code url} 的对象。</p>
     *
     * @param node 响应根节点
     * @return 结果条目列表
     */
    private List<com.fasterxml.jackson.databind.JsonNode> extractResultNodes(
            com.fasterxml.jackson.databind.JsonNode node) {
        List<com.fasterxml.jackson.databind.JsonNode> result = new ArrayList<>();
        collectResultNodes(node, result);
        return result;
    }

    /** 递归收集含 title + url 的节点 */
    private void collectResultNodes(com.fasterxml.jackson.databind.JsonNode node,
                                    List<com.fasterxml.jackson.databind.JsonNode> out) {
        if (node == null) {
            return;
        }
        if (node.isObject()) {
            if (node.hasNonNull("title") && (node.hasNonNull("url") || node.hasNonNull("link"))) {
                out.add(node);
            }
            node.elements().forEachRemaining(child -> collectResultNodes(child, out));
        } else if (node.isArray()) {
            node.elements().forEachRemaining(child -> collectResultNodes(child, out));
        }
    }

    /**
     * 构造带超时的请求工厂。
     * <p>公告站点偶发无响应，必须设超时，否则调度线程会被长期占用。</p>
     *
     * @return 请求工厂
     */
    private org.springframework.http.client.ClientHttpRequestFactory fetchRequestFactory() {
        org.springframework.http.client.SimpleClientHttpRequestFactory factory =
                new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(FETCH_CONNECT_TIMEOUT_MS);
        factory.setReadTimeout(FETCH_READ_TIMEOUT_MS);
        return factory;
    }

    /**
     * 剥离 HTML 标签，保留纯文本。
     *
     * @param html 原始 HTML
     * @return 纯文本
     */
    private String stripHtml(String html) {
        return html.replaceAll("(?is)<script.*?</script>", " ")
                .replaceAll("(?is)<style.*?</style>", " ")
                .replaceAll("(?s)<[^>]+>", " ")
                .replaceAll("&nbsp;", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    /**
     * 对象转 JSON。
     *
     * @param value 待序列化对象
     * @return JSON 字符串
     */
    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "[]";
        }
    }

    /**
     * 采纳草稿：把草稿里的假期与补班日<b>真正写入生效表</b>，并把草稿置为 APPLIED。
     * <p>
     * 这是<b>唯一</b>让 AI 结果生效的入口，且必须由人工触发。
     * 同一年份若已有假期数据，先清空再写入——否则 AI 与手工数据会叠加，
     * 出现同一假期两段区间的情况，截止日顺延会按并集算错。
     * </p>
     *
     * @param draftId  草稿 ID
     * @param operator 操作人
     * @return 采纳结果：写入的假期数 + 补班日数
     * @throws ScrmException 草稿不存在 / 已被处理 / 内容非法
     */
    @Transactional
    public Map<String, Object> applyDraft(Long draftId, String operator) throws ScrmException {
        ScrmTaxHolidayDraftEntity draft = draftRepository.findById(draftId)
                .orElseThrow(() -> ScrmException.notFound("草稿不存在: id=" + draftId));
        if (!ScrmTaxHolidayDraftEntity.STATUS_PENDING.equals(draft.getDraftStatus())) {
            throw ScrmException.conflict("该草稿已处理: status=" + draft.getDraftStatus());
        }
        int year = draft.getHolidayYear();
        List<Map<String, Object>> holidays = parseArray(draft.getHolidaysJson());
        if (holidays.isEmpty()) {
            throw ScrmException.badRequest("草稿中没有假期数据，无法采纳");
        }

        // 清空该年旧数据，避免 AI 结果与历史手工数据叠加
        List<ScrmTaxHolidayEntity> oldHolidays =
                holidayRepository.findByHolidayYearAndIsActiveTrueOrderByStartDateAsc(year);
        holidayRepository.deleteAll(oldHolidays);

        int written = 0;
        for (Map<String, Object> item : holidays) {
            LocalDate start = parseDate(item.get("startDate"));
            LocalDate end = parseDate(item.get("endDate"));
            if (start == null || end == null || end.isBefore(start)) {
                log.warn("[假期-AI] 跳过非法假期区间: draftId={}, item={}", draftId, item);
                continue;
            }
            ScrmTaxHolidayEntity entity = new ScrmTaxHolidayEntity();
            entity.setHolidayName(String.valueOf(item.getOrDefault("name", "未命名")));
            entity.setHolidayYear(year);
            entity.setStartDate(start);
            entity.setEndDate(end);
            entity.setHolidayKind(ScrmTaxHolidayEntity.KIND_STATUTORY);
            entity.setRemark("由草稿 " + draftId + " 采纳");
            holidayRepository.save(entity);
            written++;
        }

        // 统计口径: 草稿中「最终在库中存在」的补班日数（新增 + 已存在），
        // 只数新增会让返回值误导运维——补班日多半早已录入，新增数为 0 时
        // 看上去像「一条都没写」，实际数据是完整的
        int workdayTotal = 0;
        int workdayAdded = 0;
        for (Map<String, Object> item : parseArray(draft.getWorkdaysJson())) {
            LocalDate date = parseDate(item.get("workDate"));
            if (date == null) {
                continue;
            }
            workdayTotal++;
            // 唯一约束在 work_date 上，重复的跳过而不是让整批失败
            boolean exists = workdayOverrideRepository.existsByWorkDateAndIsActiveTrue(date);
            if (exists) {
                continue;
            }
            ScrmTaxWorkdayOverrideEntity entity = new ScrmTaxWorkdayOverrideEntity();
            entity.setWorkDate(date);
            entity.setRemark(String.valueOf(item.getOrDefault("remark", "调休上班")));
            workdayOverrideRepository.save(entity);
            workdayAdded++;
        }

        draft.setDraftStatus(ScrmTaxHolidayDraftEntity.STATUS_APPLIED);
        draft.setReviewedBy(operator);
        draft.setReviewedAt(LocalDateTime.now());
        draftRepository.save(draft);
        log.info("[假期-AI] 草稿已采纳: draftId={}, year={}, holidays={}, workdays={}(新增 {}), operator={}",
                draftId, year, written, workdayTotal, workdayAdded, operator);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("year", year);
        result.put("holidayCount", written);
        // 草稿中等价于「在库中存在的补班日总数」，便于与公告逐条核对
        result.put("workdayCount", workdayTotal);
        result.put("workdayAdded", workdayAdded);
        return result;
    }

    /**
     * 解析草稿里的 JSON 数组。
     *
     * @param json JSON 字符串
     * @return 元素列表，非法时返回空列表
     */
    private List<Map<String, Object>> parseArray(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<>() { });
        } catch (Exception e) {
            log.warn("[假期-AI] 草稿 JSON 解析失败: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 解析日期字符串。
     *
     * @param value 原始值
     * @return 日期，非法返回 null
     */
    private LocalDate parseDate(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return LocalDate.parse(String.valueOf(value).trim());
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * 丢弃草稿。
     *
     * @param draftId 草稿 ID
     * @param operator 操作人
     * @param remark   备注
     * @throws ScrmException 草稿不存在 / 已被处理
     */
    public void rejectDraft(Long draftId, String operator, String remark) throws ScrmException {
        ScrmTaxHolidayDraftEntity draft = draftRepository.findById(draftId)
                .orElseThrow(() -> ScrmException.notFound("草稿不存在: id=" + draftId));
        if (!ScrmTaxHolidayDraftEntity.STATUS_PENDING.equals(draft.getDraftStatus())) {
            throw ScrmException.conflict("该草稿已处理: status=" + draft.getDraftStatus());
        }
        draft.setDraftStatus(ScrmTaxHolidayDraftEntity.STATUS_REJECTED);
        draft.setReviewedBy(operator);
        draft.setReviewedAt(LocalDateTime.now());
        draft.setReviewRemark(remark);
        draftRepository.save(draft);
    }
}
