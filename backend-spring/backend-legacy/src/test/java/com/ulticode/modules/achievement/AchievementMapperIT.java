package com.ulticode.modules.achievement;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.ulticode.modules.achievement.entity.Achievement;
import com.ulticode.modules.achievement.mapper.AchievementMapper;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for {@link AchievementMapper} against a real MySQL
 * container. Verifies that:
 *
 * <ol>
 *   <li>{@code @Select("SELECT * FROM achievements WHERE `key` = #{key}")} on
 *       {@link AchievementMapper#findByKey} executes without
 *       {@code SQLSyntaxErrorException} on the MySQL reserved word
 *       {@code key} (CRITICAL #2).</li>
 *   <li>{@link AchievementMapper#findAllActive} (the {@code default} method
 *       that routes through {@code BaseMapper.selectList} and uses
 *       {@code @TableField(value = "`key`")} on
 *       {@link Achievement#key}) executes without
 *       {@code SQLSyntaxErrorException} (CRITICAL #1).</li>
 *   <li>The JSON {@code criteria} column is deserialized as a
 *       {@code Map<String,Object>} by {@code JacksonTypeHandler} — not
 *       {@code null} (Bug #7, found during T3 validation when
 *       {@code getUserAchievements} returned {@code progress=0, target=0}
 *       for all rows).</li>
 * </ol>
 *
 * <p>Mirrors
 * {@code com.ulticode.modules.admin.service.impl.SystemSettingsServiceImplIT}
 * for the Testcontainers MySQL pattern. Pairs with the cheaper
 * reflection-guard {@link AchievementMapperSQLGuardTest} which runs in
 * &lt;1ms during normal Surefire (see review L2).</p>
 *
 * <p>Run with: {@code ./mvnw -Dtest='AchievementMapperIT' test -B}</p>
 */
@Testcontainers
@DisplayName("AchievementMapper (IT) — real MySQL 8.0")
class AchievementMapperIT {

    @Container
    static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>("mysql:8.0")
                    .withDatabaseName("ulticode_test")
                    .withUsername("test")
                    .withPassword("test");

    private static DataSource dataSource;
    private static SqlSessionFactory sqlSessionFactory;

    private AchievementMapper mapper;

    @BeforeAll
    static void startContainer() {
        assertThat(MYSQL.isRunning()).isTrue();
    }

    @BeforeEach
    void setUpSchema() throws Exception {
        dataSource = new DriverManagerDataSource(
                MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());

        try (var conn = dataSource.getConnection();
             var stmt = conn.createStatement()) {
            // Match the prod DDL from
            // init-db/migrations/V20260602_120000__Create_All_Tables.sql.
            stmt.execute("DROP TABLE IF EXISTS achievements");
            stmt.execute("""
                CREATE TABLE achievements (
                  id varchar(36) NOT NULL,
                  `key` varchar(100) NOT NULL,
                  name varchar(200) NOT NULL,
                  description text,
                  icon varchar(500) DEFAULT NULL,
                  category varchar(50) NOT NULL,
                  tier int NOT NULL DEFAULT '1',
                  criteria json DEFAULT NULL,
                  points int NOT NULL DEFAULT '0',
                  is_active tinyint(1) NOT NULL DEFAULT '1',
                  created_at datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
                  updated_at datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
                                       ON UPDATE CURRENT_TIMESTAMP(3),
                  PRIMARY KEY (id),
                  UNIQUE KEY `key` (`key`)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);
            // Seed 2 rows with valid JSON criteria — mirrors the dev seed in
            // docs/achievement-api-test-report-2026-06-11.md §5.
            stmt.execute("""
                INSERT INTO achievements (id, `key`, name, description, category, tier, criteria, points, is_active)
                VALUES
                  ('ach-001', 'first_solved', 'First Solved', 'desc1', 'problems', 1,
                   JSON_OBJECT('type', 'problems_solved', 'target', 1), 10, 1),
                  ('ach-002', 'ten_solved', 'Ten Solved', 'desc2', 'problems', 2,
                   JSON_OBJECT('type', 'problems_solved', 'target', 10), 50, 1)
                """);
        }

        // Build MyBatis-Plus SqlSessionFactory. MyBatis-Plus scans the
        // Achievement entity on mapper registration and auto-registers
        // JacksonTypeHandler for fields with
        // @TableField(typeHandler = JacksonTypeHandler.class).
        MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.addMapper(AchievementMapper.class);
        factory.setConfiguration(configuration);
        factory.setPlugins(new MybatisPlusInterceptor());
        sqlSessionFactory = factory.getObject();
        assertThat(sqlSessionFactory).isNotNull();

        mapper = sqlSessionFactory.openSession().getMapper(AchievementMapper.class);
    }

    @AfterEach
    void tearDown() throws Exception {
        try (var conn = dataSource.getConnection();
             var stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS achievements");
        }
    }

    @Test
    @DisplayName("findByKey: @Select SQL with backticked `key` column runs without syntax error (CRITICAL #2)")
    void findByKey_executesWithoutReservedWordError() {
        Achievement a = mapper.findByKey("first_solved");
        assertThat(a).isNotNull();
        assertThat(a.getKey()).isEqualTo("first_solved");
    }

    @Test
    @DisplayName("findByKey: criteria JSON column is deserialized as Map (Bug #8 typeHandler)")
    void findByKey_criteriaIsDeserializedAsMap() {
        Achievement a = mapper.findByKey("first_solved");
        assertThat(a).isNotNull();
        assertThat(a.getCriteria())
                .as("JacksonTypeHandler must deserialize the JSON `criteria` column as a Map "
                        + "(Bug #8 — previously @Select bypassed @TableField typeHandler even "
                        + "after the CRITICAL #2 backtick fix; now findByKey is a default method "
                        + "that routes through BaseMapper.selectList)")
                .isInstanceOf(Map.class);
        Map<String, Object> criteria = a.getCriteria();
        assertThat(criteria).containsEntry("type", "problems_solved");
        assertThat(criteria).containsEntry("target", 1);
    }

    @Test
    @DisplayName("findAllActive: default method via BaseMapper applies @TableField backticks (CRITICAL #1)")
    void findAllActive_defaultMethodExecutes() {
        List<Achievement> all = mapper.findAllActive();
        assertThat(all).hasSize(2);
        assertThat(all).extracting(Achievement::getKey)
                .containsExactlyInAnyOrder("first_solved", "ten_solved");
    }

    @Test
    @DisplayName("findAllActive: criteria JSON is deserialized as Map for ALL rows (Bug #7)")
    void findAllActive_criteriaIsDeserializedAsMap() {
        List<Achievement> all = mapper.findAllActive();
        assertThat(all).isNotEmpty();
        for (Achievement a : all) {
            assertThat(a.getCriteria())
                    .as("JacksonTypeHandler must be applied to every row in the "
                            + "auto-generated column list (Bug #7 — previously criteria "
                            + "was null because @Select bypassed @TableField typeHandler)")
                    .isInstanceOf(Map.class);
        }
    }

    @Test
    @DisplayName("selectById: base CRUD applies @TableField backticks and JacksonTypeHandler (CRITICAL #1 + Bug #7)")
    void selectById_backticksAndTypeHandler() {
        Achievement a = mapper.selectById("ach-001");
        assertThat(a).isNotNull();
        assertThat(a.getKey()).isEqualTo("first_solved");
        assertThat(a.getCriteria()).isInstanceOf(Map.class);
    }
}
