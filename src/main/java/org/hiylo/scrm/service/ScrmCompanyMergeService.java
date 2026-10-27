/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCompanyMergeService.java
 * Date : 2026/10/23 21:30:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hiylo.scrm.config.UserContext;
import org.hiylo.scrm.dto.ScrmCustomerDto;
import org.hiylo.scrm.entity.ScrmCompanyEntity;
import org.hiylo.scrm.entity.ScrmCustomerEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.repository.ScrmCompanyRepository;
import org.hiylo.scrm.repository.ScrmCustomerRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 存量联系人归并服务（把「未归属公司」的联系人高效挂到公司主体下）。
 * <p>
 * <b>为什么不做「自动归并」</b>：实测存量 1269 条联系人里手机号 0 条、身份标识 0 条、
 * 证照识别 0 条、会话里也提不到企业名——自动匹配没有可靠信号，硬猜只会造出错误归属，
 * 而错误的主体归属比不归属更糟（报税/巡检/续约都会挂错主体）。故本服务的定位是
 * <b>「系统给候选 + 人工确认」</b>：
 * </p>
 * <ul>
 *   <li><b>待归并清单</b>：列出 company_id 为空的联系人，附带可用信号摘要
 *       （是否有手机号、昵称是否含企业特征词），供人工快速筛</li>
 *   <li><b>候选建议</b>：按「公司名称关键词 ↔ 联系人昵称」做保守匹配，
 *       命中才给候选并说明理由；无命中就明确返回空，不编造</li>
 *   <li><b>批量挂靠</b>：一次把多个联系人挂到同一公司（1269 条逐条挂不现实）</li>
 * </ul>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScrmCompanyMergeService {

    /**
     * 昵称里出现即视为「可能含企业信息」的特征词。
     * <p>仅用于给人工提示信号强度，<b>绝不作为归属依据</b>。词表来自存量数据实际昵称
     * （如「A定昌电子-罗贤东（物联网主板）」），覆盖行业词 + 组织形式词。</p>
     */
    private static final List<String> COMPANY_HINTS = List.of(
            // 组织形式
            "公司", "有限", "个体", "工作室", "商行", "经营部", "集团", "厂", "中心", "分店",
            // 财税服务相关
            "财税", "财务", "会计", "代账", "记账",
            // 常见行业词
            "科技", "电子", "商贸", "贸易", "实业", "咨询", "建材", "装饰", "物流",
            "餐饮", "食品", "机械", "五金", "服饰", "纺织", "化工", "医药", "教育", "文化");

    /** 公司名称里不宜作为匹配关键词的通用词（避免「有限公司」匹配到所有公司） */
    private static final Set<String> NAME_STOPWORDS = Set.of(
            "上海", "北京", "深圳", "广州", "杭州", "有限公司", "有限责任公司", "公司",
            "企业", "个体", "工商户", "商贸", "科技", "咨询", "服务", "工作室");

    /** 电话/证件中提取企业名时的最小关键词长度（低于此长度不做匹配，避免噪音） */
    private static final int MIN_KEYWORD_LENGTH = 2;

    /** 企业名常见前导地名（匹配前先剥掉，避免「上海XX」匹配不到昵称里的「XX」） */
    private static final List<String> CITY_PREFIXES = List.of(
            "上海", "北京", "深圳", "广州", "杭州", "南京", "苏州", "成都", "武汉", "西安",
            "重庆", "天津", "青岛", "宁波", "厦门", "长沙", "郑州", "济南", "合肥", "福州",
            "东莞", "佛山", "无锡", "常州", "温州", "嘉兴", "绍兴", "金华", "台州", "珠海",
            "中山", "惠州", "昆明", "贵阳", "南昌", "太原", "石家庄", "沈阳", "大连", "哈尔滨",
            "长春", "兰州", "海口", "南宁", "三亚", "乌鲁木齐", "呼和浩特", "银川", "西宁", "拉萨");

    /** 组织形式后缀（匹配前先剥掉） */
    private static final List<String> ORG_SUFFIXES = List.of(
            "有限责任公司", "股份有限公司", "有限公司", "股份公司", "公司",
            "工作室", "经营部", "商行", "分店", "集团");

    /** 公司主体数据访问层 */
    private final ScrmCompanyRepository companyRepository;

    /** 联系人数据访问层 */
    private final ScrmCustomerRepository customerRepository;

    /** 数据权限服务（与既有客户列表使用同一套账号可见范围） */
    private final DataScopeService dataScopeService;

    /**
     * 待归并清单：列出尚未归属公司的联系人，附可用信号摘要。
     *
     * @param keyword 昵称关键词（可空）
     * @param page    页码（0 起）
     * @param size    每页条数
     * @return 分页结果（每项含联系人 + 信号摘要）
     */
    @Transactional(readOnly = true)
    public Page<Map<String, Object>> listUnlinked(String keyword, int page, int size) {
        // 与既有客户列表口径一致：按「数据权限可见账号集合」过滤，而不是直接用登录用户 ID
        // （scrm_customer.owner_account_id 指向 scrm_account.id，与登录用户 ID 是两个空间）
        List<Long> accessibleAccountIds = dataScopeService.getAccessibleAccountIds(
                dataScopeService.getCurrentUserId(),
                dataScopeService.getCurrentRole(),
                dataScopeService.getCurrentDepartmentId());
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.max(1, size),
                Sort.by(Sort.Direction.DESC, "createTime"));
        String kw = (keyword == null || keyword.isBlank()) ? null : "%" + keyword.trim() + "%";
        Page<ScrmCustomerEntity> pageData = customerRepository.findAll((root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isNull(root.get("companyId")));
            if (accessibleAccountIds != null) {
                if (accessibleAccountIds.isEmpty()) {
                    predicates.add(cb.isFalse(cb.literal(true)));
                } else {
                    predicates.add(root.get("ownerAccountId").in(accessibleAccountIds));
                }
            }
            if (kw != null) {
                predicates.add(cb.like(root.get("nickname"), kw));
            }
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        }, pageable);

        return pageData.map(entity -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("customerId", entity.getId());
            item.put("nickname", entity.getNickname());
            item.put("phone", entity.getPhone());
            item.put("platformType", entity.getPlatformType());
            item.put("platformCustomerUid", entity.getPlatformCustomerUid());
            item.put("sourceChannel", entity.getSourceChannel());
            item.put("createTime", entity.getCreateTime());
            // 信号摘要：告诉人工「这个人有什么线索可用于判断归属」
            item.put("signals", collectSignals(entity));
            return item;
        });
    }

    /**
     * 为公司推荐候选联系人（保守匹配：公司名称关键词 出现在 联系人昵称 中）。
     * <p>
     * 只返回有明确命中的候选并给出命中关键词；无命中返回空列表——绝不编造候选。
     * </p>
     *
     * @param companyId 公司主体 ID
     * @param size      最多返回条数
     * @return 候选列表（含命中理由）
     * @throws ScrmException 公司主体不存在
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> suggestContacts(Long companyId, int size) throws ScrmException {
        ScrmCompanyEntity company = companyRepository.findById(companyId)
                .orElseThrow(() -> ScrmException.notFound("公司主体不存在: " + companyId));
        List<String> keywords = extractKeywords(company.getCompanyName());
        if (keywords.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        Set<Long> seen = new LinkedHashSet<>();
        for (String kw : keywords) {
            List<ScrmCustomerEntity> hits = customerRepository
                    .findByOwnerAccountIdAndNicknameLike(company.getOwnerAccountId(), "%" + kw + "%");
            for (ScrmCustomerEntity customer : hits) {
                if (customer.getCompanyId() != null || !seen.add(customer.getId())) {
                    continue;
                }
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("customerId", customer.getId());
                item.put("nickname", customer.getNickname());
                item.put("phone", customer.getPhone());
                item.put("platformType", customer.getPlatformType());
                item.put("matchedKeyword", kw);
                item.put("reason", "昵称包含公司名关键词「" + kw + "」");
                result.add(item);
                if (result.size() >= Math.max(1, size)) {
                    return result;
                }
            }
        }
        return result;
    }

    /**
     * 为联系人推荐候选公司（反向：从联系人昵称里提取关键词，匹配公司名）。
     *
     * @param customerId 联系人 ID
     * @param size       最多返回条数
     * @return 候选公司列表（含命中理由）
     * @throws ScrmException 联系人不存在
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> suggestCompanies(Long customerId, int size) throws ScrmException {
        ScrmCustomerEntity customer = customerRepository.findById(customerId)
                .orElseThrow(() -> ScrmException.notFound("联系人不存在: " + customerId));
        List<String> keywords = extractKeywords(customer.getNickname());
        if (keywords.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        Set<Long> seen = new LinkedHashSet<>();
        for (String kw : keywords) {
            List<ScrmCompanyEntity> hits = companyRepository
                    .findByOwnerAccountIdAndCompanyNameContainingIgnoreCase(
                            customer.getOwnerAccountId(), kw);
            for (ScrmCompanyEntity company : hits) {
                if (!seen.add(company.getId())) {
                    continue;
                }
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("companyId", company.getId());
                item.put("companyName", company.getCompanyName());
                item.put("creditCode", company.getCreditCode());
                item.put("serviceStatus", company.getServiceStatus());
                item.put("matchedKeyword", kw);
                item.put("reason", "公司名包含「" + kw + "」");
                result.add(item);
                if (result.size() >= Math.max(1, size)) {
                    return result;
                }
            }
        }
        return result;
    }

    /**
     * 批量挂靠：把多个联系人一次性挂到同一公司（单事务，任一失败整体回滚）。
     *
     * @param companyId   公司主体 ID
     * @param customerIds 联系人 ID 列表
     * @param contactRole 公司内角色（可空；为空时保留各联系人原角色）
     * @return 挂靠结果（成功数 + 跳过原因明细）
     * @throws ScrmException 公司不存在 / 入参为空
     */
    @Transactional
    public Map<String, Object> bulkBind(Long companyId, List<Long> customerIds, String contactRole)
            throws ScrmException {
        if (customerIds == null || customerIds.isEmpty()) {
            throw ScrmException.badRequest("联系人 ID 列表不能为空");
        }
        ScrmCompanyEntity company = companyRepository.findById(companyId)
                .orElseThrow(() -> ScrmException.notFound("公司主体不存在: " + companyId));

        int bound = 0;
        List<Map<String, Object>> skipped = new ArrayList<>();
        for (Long customerId : customerIds) {
            if (customerId == null) {
                continue;
            }
            ScrmCustomerEntity customer = customerRepository.findById(customerId).orElse(null);
            if (customer == null) {
                skipped.add(skipReason(customerId, "联系人不存在"));
                continue;
            }
            if (!company.getOwnerAccountId().equals(customer.getOwnerAccountId())) {
                skipped.add(skipReason(customerId, "归属账号不一致"));
                continue;
            }
            if (companyId.equals(customer.getCompanyId())) {
                skipped.add(skipReason(customerId, "已归属该公司"));
                continue;
            }
            customer.setCompanyId(companyId);
            if (contactRole != null && !contactRole.isBlank()) {
                customer.setContactRole(contactRole);
            }
            customerRepository.save(customer);
            bound++;
        }
        log.info("[公司归并] 批量挂靠: companyId={}, 请求={}, 成功={}, 跳过={}",
                companyId, customerIds.size(), bound, skipped.size());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("companyId", companyId);
        result.put("requested", customerIds.size());
        result.put("bound", bound);
        result.put("skipped", skipped);
        return result;
    }

    // ============================================================
    // 内部工具
    // ============================================================

    /**
     * 汇总联系人可用于判断归属的信号（供人工参考，不作为自动归属依据）。
     *
     * @param entity 联系人
     * @return 信号列表
     */
    private List<String> collectSignals(ScrmCustomerEntity entity) {
        List<String> signals = new ArrayList<>();
        if (entity.getPhone() != null && !entity.getPhone().isBlank()) {
            signals.add("有手机号");
        }
        String nickname = entity.getNickname();
        if (nickname != null) {
            for (String hint : COMPANY_HINTS) {
                if (nickname.contains(hint)) {
                    signals.add("昵称含企业特征词「" + hint + "」");
                    break;
                }
            }
        } else {
            signals.add("无昵称");
        }
        if (entity.getSourceChannel() != null && !entity.getSourceChannel().isBlank()) {
            signals.add("获客渠道：" + entity.getSourceChannel());
        }
        if (signals.isEmpty()) {
            signals.add("无可用于判断归属的信号（需人工凭沟通记录判断或暂缓）");
        }
        return signals;
    }

    /**
     * 从名称中提取可用于匹配的关键词（去掉城市/通用后缀等噪音）。
     *
     * @param name 公司名或昵称
     * @return 关键词列表（可能为空）
     */
    private List<String> extractKeywords(String name) {
        if (name == null || name.isBlank()) {
            return List.of();
        }
        List<String> keywords = new ArrayList<>();
        // 1) 去掉括号注释后按分隔符切分
        String cleaned = name.replaceAll("[（(].*?[)）]", " ").trim();
        for (String part : cleaned.split("[\\s\\-—_·.,，、/|]+")) {
            String kw = part.trim();
            if (kw.isEmpty()) {
                continue;
            }
            // 2) 剥掉微信昵称常见的字母排序前缀（「A定昌电子」→「定昌电子」），
            //    否则「A定昌电子」匹配不到公司名里的「定昌电子」
            kw = kw.replaceAll("^[A-Za-z]+", "").trim();
            if (kw.isEmpty()) {
                continue;
            }
            // 3) 剥掉前导城市/地域词（「上海定昌电子」→「定昌电子」），
            //    否则「上海XX」永远匹配不到昵称里的「XX」
            boolean stripped = true;
            while (stripped) {
                stripped = false;
                for (String city : CITY_PREFIXES) {
                    if (kw.length() > city.length() && kw.startsWith(city)) {
                        kw = kw.substring(city.length());
                        stripped = true;
                        break;
                    }
                }
            }
            // 4) 剥掉组织形式后缀（「定昌电子有限公司」→「定昌电子」）
            for (String suffix : ORG_SUFFIXES) {
                if (kw.length() > suffix.length() && kw.endsWith(suffix)) {
                    kw = kw.substring(0, kw.length() - suffix.length());
                    break;
                }
            }
            kw = kw.trim();
            if (kw.length() < MIN_KEYWORD_LENGTH || NAME_STOPWORDS.contains(kw)) {
                continue;
            }
            keywords.add(kw);
            // 5) 长词再补一个前 2 字核心词（中文品牌词通常在前部，如「定昌电子」→「定昌」）
            if (kw.length() >= 4) {
                String head = kw.substring(0, 2);
                if (!NAME_STOPWORDS.contains(head)) {
                    keywords.add(head);
                }
            }
        }
        // 去重保序
        return new ArrayList<>(new LinkedHashSet<>(keywords));
    }

    /**
     * 构造跳过原因条目。
     *
     * @param customerId 联系人 ID
     * @param reason     原因
     * @return 条目
     */
    private Map<String, Object> skipReason(Long customerId, String reason) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("customerId", customerId);
        item.put("reason", reason);
        return item;
    }

    /**
     * 从登录上下文解析归属账号 ID。
     *
     * @return 账号 ID（无法解析返回 null）
     */
    private Long parseUserId() {
        String userId = UserContext.getUserId();
        if (userId == null || userId.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(userId);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
