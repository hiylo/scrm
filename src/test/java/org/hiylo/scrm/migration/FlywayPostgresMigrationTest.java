/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : FlywayPostgresMigrationTest.java
 * Date : 2026/09/19 06:40:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.migration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Flyway 迁移脚本在真实 PostgreSQL 上的回归测试。
 * <p>
 * 现有 1500+ 用例全部跑在 H2 上 (application-test.yml 关闭 Flyway 并用
 * {@code ddl-auto: create-drop} 由 Hibernate 建表), 因此 db/migration 下的 V1/V2
 * 从未被任何自动化测试执行过。V1 使用 RANGE 分区表, V2 使用 pg_trgm 与
 * {@code CREATE INDEX ... ON ONLY} + 逐分区 {@code ATTACH PARTITION} 的分区索引写法,
 * 都是 H2 无法解析的 PostgreSQL 专有语法。本类在 postgres:16-alpine 容器里真实执行
 * 全部迁移脚本并校验结果, 防止"迁移静默不执行 / 分区索引漏挂"这类缺陷再次溜进主干。
 * </p>
 * <p>
 * 表数量断言口径: V1 共 275 条 {@code CREATE TABLE} 且全部位于 {@code scrm} schema,
 * 含 1 张分区父表 (scrm_conversation_message) + 13 个叶子分区 + 261 张普通表。
 * 所以 {@code information_schema} 里 scrm schema 的 BASE TABLE (排除 flyway_schema_history)
 * 为 275; 把 Flyway 自身的 flyway_schema_history 计入后, 非系统 schema 基表总数为 276。
 * 迁移配置与 application.yml 的 spring.flyway 对齐 (locations / schemas / default-schema /
 * baseline-on-migrate), 因此 flyway_schema_history 落在 {@code scrm} 而非 {@code public}。
 * </p>
 * <p>
 * Docker 不可用时整类跳过而非失败: {@code @Testcontainers(disabledWithoutDocker = true)}
 * 在执行条件阶段拦下无 Docker 环境, {@code @BeforeAll} 里的
 * {@link Assumptions#assumeTrue(boolean, String)} 再兜一层并给出可读原因。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Testcontainers(disabledWithoutDocker = true)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Flyway 迁移脚本 PostgreSQL 真实容器回归测试")
class FlywayPostgresMigrationTest {

    /** 迁移脚本位置, 与生产 spring.flyway.locations 一致 */
    private static final String MIGRATION_LOCATION = "classpath:db/migration";

    /** 业务 schema, 与生产 spring.flyway.schemas / default-schema 一致 (flyway_schema_history 也落在该 schema) */
    private static final String APPLICATION_SCHEMA = "scrm";

    /** 与手工验证时一致的镜像 */
    private static final DockerImageName POSTGRES_IMAGE = DockerImageName.parse("postgres:16-alpine");

    /** scrm schema 下的业务表数 (V1 的 275 条 CREATE TABLE, 含分区父表与 13 个叶子分区) */
    private static final int EXPECTED_SCRM_TABLES = 275;

    /** 业务表 + flyway_schema_history 的非系统 schema 基表总数 */
    private static final int EXPECTED_TOTAL_TABLES = 276;

    /** scrm_conversation_message 的月分区 (202607~202706) + default 分区 */
    private static final int EXPECTED_LEAF_PARTITIONS = 13;

    /** V1 在消息分区表上建的 5 个索引 + V2 新增的 trigram 索引 */
    private static final int EXPECTED_INDEXES_PER_LEAF = 6;

    /** 客户表造行数: 需大到让 seq scan 代价高于 GIN 位图扫描, 优化器才会选择 trgm 索引 */
    private static final int CUSTOMER_SEED_ROWS = 20000;

    /** 命中客户 nickname trigram 索引的稀有关键词 */
    private static final String NICKNAME_TOKEN = "qzrarenickname";

    /** 命中消息 content trigram 索引的稀有关键词 */
    private static final String CONTENT_TOKEN = "qzrarecontent";

    /** 消息父表 trigram 父索引 (schema 限定, 便于直接 regclass 转换) */
    private static final String MESSAGE_TRGM_INDEX = "scrm.idx_conversation_message_content_trgm";

    /** 分区后缀匹配, 用于从查询计划里提取被扫描到的叶子分区 */
    private static final Pattern LEAF_PATTERN = Pattern.compile("scrm_conversation_message_(20\\d{4}|default)");

    /** scrm schema 业务表计数 SQL, 排除 Flyway 自身的历史表 */
    private static final String SCRM_TABLE_COUNT_SQL =
            "select count(*) from information_schema.tables where table_schema = 'scrm' "
                    + "and table_type = 'BASE TABLE' and table_name <> 'flyway_schema_history'";

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(POSTGRES_IMAGE)
            .withDatabaseName("scrm_migration")
            .withUsername("postgres")
            .withPassword("postgres");

    /** 一次全量迁移的结果, 供各用例断言 */
    private static MigrateResult migrateResult;

    @BeforeAll
    static void runMigrations() {
        Assumptions.assumeTrue(POSTGRES.isRunning(),
                "Docker 不可用或 postgres:16-alpine 容器未能启动, 跳过 Flyway 迁移回归测试"
                        + " (该用例需要可用的 Docker daemon 才能在真实 PostgreSQL 上执行迁移)");
        migrateResult = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations(MIGRATION_LOCATION)
                .schemas(APPLICATION_SCHEMA)
                .defaultSchema(APPLICATION_SCHEMA)
                .baselineOnMigrate(true)
                .baselineVersion("0")
                .load()
                .migrate();
    }

    @Test
    @Order(1)
    @DisplayName("V1/V2/V3 全部执行成功并写入 flyway_schema_history")
    void flywayRecordsBothMigrationsAsSuccess() throws SQLException {
        assertThat(migrateResult.success).as("Flyway migrate 结果").isTrue();
        assertThat(migrateResult.migrationsExecuted).as("本次执行的迁移数").isEqualTo(3);

        String historySchema = singleValue(
                "select table_schema from information_schema.tables where table_name = 'flyway_schema_history'");
        assertThat(historySchema).as("flyway_schema_history 所在 schema (与 spring.flyway.default-schema 一致)")
                .isEqualTo(APPLICATION_SCHEMA);
        List<String> entries = queryAll("select type || '|' || coalesce(version, '-') || '|' "
                + "|| coalesce(script, '-') || '|' || success from \""
                + historySchema + "\".flyway_schema_history order by installed_rank");
        assertThat(entries).filteredOn(entry -> entry.startsWith("SQL|"))
                .as("V1/V2/V3 三条迁移记录 (type|version|script|success)").containsExactly(
                        "SQL|1|V1__init_schema.sql|true",
                        "SQL|2|V2__add_text_search_indexes.sql|true",
                        "SQL|3|V3__add_account_owner.sql|true");
        // spring.flyway.schemas=scrm 会让 Flyway 自建 schema 并写入一条 SCHEMA 记录, 此时 V1 的
        // CREATE SCHEMA IF NOT EXISTS scrm 退化为空操作
        assertThat(entries).filteredOn(entry -> entry.startsWith("SCHEMA|"))
                .as("Flyway 自建 scrm schema 的记录").hasSize(1);
    }

    @Test
    @Order(2)
    @DisplayName("scrm schema 下 275 张业务表全部创建成功")
    void allBusinessTablesCreated() throws SQLException {
        assertThat(count(SCRM_TABLE_COUNT_SQL)).as("scrm schema 业务表数 (排除 flyway_schema_history)")
                .isEqualTo(EXPECTED_SCRM_TABLES);
        assertThat(count("select count(*) from information_schema.tables where table_type = 'BASE TABLE' "
                + "and table_schema not in ('pg_catalog', 'information_schema')"))
                .as("非系统 schema 基表数 (业务表 + flyway_schema_history)").isEqualTo(EXPECTED_TOTAL_TABLES);
        assertThat(queryAll("select table_name from information_schema.tables where table_schema = 'scrm' "
                + "and table_type = 'BASE TABLE' and table_name in ('scrm_customer', 'scrm_conversation_message', "
                + "'scrm_wework_archive_message', 'scrm_webhook_log') order by table_name"))
                .as("被 V2 索引覆盖的关键表").containsExactly(
                        "scrm_conversation_message", "scrm_customer", "scrm_webhook_log", "scrm_wework_archive_message");
    }

    @Test
    @Order(3)
    @DisplayName("pg_trgm 扩展与 V2 的 trigram 索引全部就位")
    void pgTrgmExtensionAndIndexesInstalled() throws SQLException {
        assertThat(count("select count(*) from pg_extension where extname = 'pg_trgm'"))
                .as("pg_extension 中 pg_trgm 数量").isEqualTo(1);
        assertThat(queryAll("select indexname from pg_indexes where schemaname = 'scrm' "
                + "and indexname like '%trgm%' and tablename in ('scrm_customer', 'scrm_wework_archive_message') "
                + "order by indexname"))
                .as("非分区表上的 trigram 索引").containsExactly(
                        "idx_customer_nickname_trgm", "idx_customer_platform_uid_trgm",
                        "idx_wework_archive_message_content_trgm");
        assertThat(count("select count(*) from pg_indexes where schemaname = 'scrm' "
                + "and indexname = 'idx_webhook_log_create_time'")).as("V2 的 webhook 时间索引数量").isEqualTo(1);
    }

    @Test
    @Order(4)
    @DisplayName("消息分区表的 trigram 父索引 valid 且每个叶子分区均已 ATTACH 索引")
    void everyLeafPartitionHasAttachedTrigramIndex() throws SQLException {
        assertThat(count("select count(*) from pg_class where oid = 'scrm.scrm_conversation_message'::regclass "
                + "and relkind = 'p'")).as("scrm_conversation_message 应为分区父表 (relkind=p)").isEqualTo(1);
        assertThat(queryAll("select am.amname || '|' || x.indisvalid || '|' || x.indisready "
                + "from pg_class i join pg_index x on x.indexrelid = i.oid join pg_am am on am.oid = i.relam "
                + "where i.oid = '" + MESSAGE_TRGM_INDEX + "'::regclass"))
                .as("父索引访问方式与有效性 (am|valid|ready)").containsExactly("gin|true|true");
        assertThat(count("select count(*) from pg_inherits h join pg_class p on p.oid = h.inhparent "
                + "where p.oid = 'scrm.scrm_conversation_message'::regclass"))
                .as("表叶子分区数").isEqualTo(EXPECTED_LEAF_PARTITIONS);
        assertThat(queryAll("select am.amname || '|' || x.indisvalid from pg_inherits h "
                + "join pg_class c on c.oid = h.inhrelid join pg_index x on x.indexrelid = c.oid "
                + "join pg_am am on am.oid = c.relam where h.inhparent = '" + MESSAGE_TRGM_INDEX + "'::regclass "
                + "order by 1"))
                .as("已 ATTACH 到父索引的分区索引 (am|valid)").hasSize(EXPECTED_LEAF_PARTITIONS).containsOnly("gin|true");

        // 逐叶子分区判缺失: 只数索引总数会把 V1 的 65 个索引分区误算进来, 从而漏掉"某分区没挂 trigram 索引"
        assertThat(queryAll("select l.relname from pg_inherits h "
                + "join pg_class p on p.oid = h.inhparent join pg_class l on l.oid = h.inhrelid "
                + "where p.oid = 'scrm.scrm_conversation_message'::regclass and not exists (select 1 "
                + "from pg_inherits ch join pg_index cx on cx.indexrelid = ch.inhrelid "
                + "where ch.inhparent = '" + MESSAGE_TRGM_INDEX + "'::regclass and cx.indrelid = l.oid) "
                + "order by l.relname"))
                .as("存在 trigram 索引但缺失索引的叶子分区").isEmpty();

        assertThat(count("select count(*) from pg_class c join pg_index x on x.indexrelid = c.oid "
                + "join pg_class t on t.oid = x.indrelid where c.relkind = 'i' "
                + "and t.relname like 'scrm_conversation_message\\_%'"))
                .as("消息表索引分区总数 (13 个叶子分区 x 6 个索引)")
                .isEqualTo(EXPECTED_LEAF_PARTITIONS * EXPECTED_INDEXES_PER_LEAF);
    }

    @Test
    @Order(5)
    @DisplayName("lower(nickname) LIKE 查询计划实际使用 idx_customer_nickname_trgm")
    void customerNicknameTrigramIndexIsUsedByPlanner() throws SQLException {
        execute(
                "insert into scrm.scrm_account (id, platform_type, platform_account_uid, display_name, "
                        + "create_time, update_time) values (9001, 'WECOM', 'acct-migration-test', '迁移校验账号', "
                        + "now(), now()) on conflict do nothing",
                "insert into scrm.scrm_customer (id, platform_type, platform_customer_uid, nickname, "
                        + "owner_account_id, create_time, update_time) select g, 'WECOM', 'uid-' || g, "
                        + "'客户Nick' || g, 9001, now(), now() from generate_series(1, " + CUSTOMER_SEED_ROWS + ") g "
                        + "on conflict do nothing",
                "insert into scrm.scrm_customer (id, platform_type, platform_customer_uid, nickname, "
                        + "owner_account_id, create_time, update_time) values (99901, 'WECOM', 'uid-rare-1', "
                        + "'重点客户 QzRareNickName 高价值', 9001, now(), now()), (99902, 'WECOM', 'uid-rare-2', "
                        + "'流失预警 QzRareNickName 客户', 9001, now(), now()) on conflict do nothing",
                "analyze scrm.scrm_customer");

        assertThat(explain("select id from scrm.scrm_customer where lower(nickname) like '%"
                + NICKNAME_TOKEN + "%'")).as("客户昵称检索查询计划").contains("idx_customer_nickname_trgm");
        assertThat(queryAll("select id::text from scrm.scrm_customer where lower(nickname) like '%"
                + NICKNAME_TOKEN + "%' order by id")).as("索引命中后返回的行").containsExactly("99901", "99902");
    }

    @Test
    @Order(6)
    @DisplayName("content ILIKE 跨分区检索使用逐分区 trigram 索引")
    void conversationMessageTrigramIndexUsedOnEveryPartition() throws SQLException {
        seedConversationMessages();

        try (Connection connection = openConnection()) {
            // 小数据量下代价估算仍偏好 seq scan, 关掉它才能验证索引"可用"; 此处校验索引可用性而非优化器选择
            executeOn(connection, "set enable_seqscan = off");

            String plan = explainOn(connection, "select id from scrm.scrm_conversation_message where content ilike '%"
                    + CONTENT_TOKEN + "%'");
            assertThat(partitionsIn(plan)).as("查询计划覆盖的叶子分区").hasSize(EXPECTED_LEAF_PARTITIONS);
            assertThat(countOccurrences(plan, "content_trgm_idx")).as("计划中被使用的分区 trigram 索引次数")
                    .isEqualTo(EXPECTED_LEAF_PARTITIONS);
            assertThat(plan).as("关闭 seqscan 后不应再逐分区全表扫").doesNotContain("Seq Scan on scrm_conversation_message");
            assertThat(queryAllOn(connection, "select id::text from scrm.scrm_conversation_message where content "
                    + "ilike '%" + CONTENT_TOKEN + "%' order by id"))
                    .as("跨 202608 / 202703 / default 三个分区的命中行").containsExactly("1", "2", "3");

            assertThat(partitionsIn(explainOn(connection, "select id from scrm.scrm_conversation_message "
                    + "where sent_at >= '2026-08-01' and sent_at < '2026-09-01' and content ilike '%"
                    + CONTENT_TOKEN + "%'"))).as("带 sent_at 范围条件时的分区裁剪结果").containsExactly("202608");
        }
    }

    /**
     * 给 12 个月分区各灌 20 行、default 分区灌 1 行, 并在 202608 / 202703 / default 各放一行稀有关键词。
     */
    private static void seedConversationMessages() throws SQLException {
        execute(
                "insert into scrm.scrm_account (id, platform_type, platform_account_uid, display_name, "
                        + "create_time, update_time) values (9001, 'WECOM', 'acct-migration-test', '迁移校验账号', "
                        + "now(), now()) on conflict do nothing",
                "insert into scrm.scrm_customer (id, platform_type, platform_customer_uid, nickname, "
                        + "owner_account_id, create_time, update_time) values (1, 'WECOM', 'uid-msg-owner', "
                        + "'会话归属客户', 9001, now(), now()) on conflict do nothing",
                "insert into scrm.scrm_conversation (id, platform_type, account_id, customer_id, conversation_type, "
                        + "create_time, update_time) values (5001, 'WECOM', 9001, 1, 'SINGLE', now(), now()) "
                        + "on conflict do nothing",
                "insert into scrm.scrm_conversation_message (id, message_id, conversation_id, message_type, "
                        + "direction, content, sent_at, create_time, update_time) select 100000 + p.idx * 100 + s.n, "
                        + "'m-' || (p.idx * 100 + s.n), 5001, 'TEXT', 'IN', '普通会话内容 ' || (p.idx * 100 + s.n), "
                        + "p.month_start + (s.n * interval '1 hour'), now(), now() from (values "
                        + "(1, timestamp '2026-07-05 00:00:00'), (2, timestamp '2026-08-05 00:00:00'), "
                        + "(3, timestamp '2026-09-05 00:00:00'), (4, timestamp '2026-10-05 00:00:00'), "
                        + "(5, timestamp '2026-11-05 00:00:00'), (6, timestamp '2026-12-05 00:00:00'), "
                        + "(7, timestamp '2027-01-05 00:00:00'), (8, timestamp '2027-02-05 00:00:00'), "
                        + "(9, timestamp '2027-03-05 00:00:00'), (10, timestamp '2027-04-05 00:00:00'), "
                        + "(11, timestamp '2027-05-05 00:00:00'), (12, timestamp '2027-06-05 00:00:00')) "
                        + "as p(idx, month_start) cross join generate_series(1, 20) as s(n) on conflict do nothing",
                "insert into scrm.scrm_conversation_message (id, message_id, conversation_id, message_type, "
                        + "direction, content, sent_at, create_time, update_time) values (1, 'm-rare-aug', 5001, "
                        + "'TEXT', 'IN', '重要内容 QzRareContent 八月', timestamp '2026-08-20 03:00:00', now(), now()), "
                        + "(2, 'm-rare-mar', 5001, 'TEXT', 'IN', '重要内容 QzRareContent 三月', "
                        + "timestamp '2027-03-15 03:00:00', now(), now()), (3, 'm-rare-old', 5001, 'TEXT', 'IN', "
                        + "'遗留内容 QzRareContent 默认分区', timestamp '2024-01-05 08:00:00', now(), now()) "
                        + "on conflict do nothing",
                "analyze scrm.scrm_conversation_message");
    }

    private static Connection openConnection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private static long count(String sql) throws SQLException {
        try (Connection connection = openConnection(); Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private static String singleValue(String sql) throws SQLException {
        List<String> values = queryAll(sql);
        return values.isEmpty() ? null : values.get(0);
    }

    private static List<String> queryAll(String sql) throws SQLException {
        try (Connection connection = openConnection()) {
            return queryAllOn(connection, sql);
        }
    }

    private static List<String> queryAllOn(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet rs = statement.executeQuery(sql)) {
            List<String> rows = new ArrayList<>();
            while (rs.next()) {
                rows.add(rs.getString(1));
            }
            return rows;
        }
    }

    private static void execute(String... statements) throws SQLException {
        try (Connection connection = openConnection()) {
            for (String statement : statements) {
                executeOn(connection, statement);
            }
        }
    }

    private static void executeOn(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private static String explain(String sql) throws SQLException {
        try (Connection connection = openConnection()) {
            return explainOn(connection, sql);
        }
    }

    private static String explainOn(Connection connection, String sql) throws SQLException {
        StringBuilder plan = new StringBuilder();
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("explain " + sql)) {
            while (rs.next()) {
                plan.append(rs.getString(1)).append('\n');
            }
        }
        return plan.toString();
    }

    /**
     * 提取查询计划里出现过的叶子分区后缀, 用于校验分区覆盖与分区裁剪。
     */
    private static Set<String> partitionsIn(String plan) {
        Set<String> partitions = new LinkedHashSet<>();
        Matcher matcher = LEAF_PATTERN.matcher(plan);
        while (matcher.find()) {
            partitions.add(matcher.group(1));
        }
        return partitions;
    }

    private static int countOccurrences(String text, String needle) {
        int count = 0;
        for (int index = text.indexOf(needle); index >= 0; index = text.indexOf(needle, index + needle.length())) {
            count++;
        }
        return count;
    }
}
