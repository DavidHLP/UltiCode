package com.ulticode.modules.problem.service.impl;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression guard for the problem category filter emitted by
 * {@code DefaultProblemProjection.buildProblemQueryWrapper}.
 *
 * <p>Top-level categories (algorithms/database/shell/concurrency) are stored as
 * {@code problem_tags} rows with slug {@code 'problem-category-<value>'}. The
 * service resolves the bare category value sent by the frontend (e.g.
 * {@code "algorithms"}) via:
 * <pre>
 *   id IN (SELECT ptr.problem_id FROM problem_tag_relations ptr
 *          JOIN problem_tags pt ON ptr.tag_id = pt.id
 *          WHERE pt.slug = CONCAT('problem-category-', {0}))
 * </pre>
 * Previously the filter matched {@code tag_id} directly against the bare value
 * ({@code 'algorithms'}), which never equalled the seeded tag id
 * ({@code 'tag-algorithms'}), so every non-{@code "all"} category pill returned
 * an empty list on the live site (verified: {@code GET /problems?category=algorithms}
 * returned {@code total=0}).
 *
 * <p>This IT replays the exact SQL the service emits against a real MySQL 8.0
 * (mirrors the lightweight Testcontainers pattern of
 * {@code TestCaseSoftDeleteFilterIT} / {@code RejudgeConcurrencyIT} — no Spring
 * context). The {@code "all"} / {@code null} short-circuit lives in the service's
 * {@code if} guard, not in SQL, so it is covered by the end-to-end curl check
 * rather than here.
 *
 * @author ulticode
 */
@Testcontainers
@DisplayName("Problem category filter — slug resolution (IT)")
class ProblemCategoryFilterIT {

    @Container
    static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>("mysql:8.0")
                    .withDatabaseName("ulticode_test")
                    .withUsername("test")
                    .withPassword("test");

    private DataSource dataSource;

    @BeforeAll
    static void startContainer() {
        assertThat(MYSQL.isRunning()).isTrue();
    }

    @BeforeEach
    void setUp() throws Exception {
        dataSource = new DriverManagerDataSource(
                MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());

        try (var conn = dataSource.getConnection();
             var stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS problem_tag_relations");
            stmt.execute("DROP TABLE IF EXISTS problem_tags");
            stmt.execute("""
                    CREATE TABLE problem_tags (
                      id varchar(40) NOT NULL,
                      label varchar(120) NOT NULL,
                      slug varchar(120) DEFAULT NULL,
                      color varchar(20) DEFAULT NULL,
                      description text,
                      usage_count int NOT NULL DEFAULT '0',
                      created_at datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
                      updated_at datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
                      PRIMARY KEY (id),
                      UNIQUE KEY problem_tags_slug_key (slug)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """);
            stmt.execute("""
                    CREATE TABLE problem_tag_relations (
                      problem_id bigint NOT NULL,
                      tag_id varchar(40) NOT NULL,
                      PRIMARY KEY (problem_id, tag_id),
                      KEY problem_tag_relations_tag_id_fkey (tag_id)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """);

            // Two category tags (namespaced slug) + one fine-grained tag (bare slug).
            stmt.execute("INSERT INTO problem_tags (id, label, slug) VALUES " +
                    "('tag-algorithms', '算法', 'problem-category-algorithms'), " +
                    "('tag-database',   '数据库', 'problem-category-database'), " +
                    "('tag-array',      '数组', 'array')");
            // Problems 1,2 -> algorithms category; problem 3 -> only the array tag (no category).
            stmt.execute("INSERT INTO problem_tag_relations (problem_id, tag_id) VALUES " +
                    "(1, 'tag-algorithms'), " +
                    "(2, 'tag-algorithms'), " +
                    "(3, 'tag-array')");
        }
    }

    /**
     * Replays the service's category filter SQL and returns the matched problem ids.
     */
    private List<Long> problemIdsForCategory(String category) throws Exception {
        // Mirrors DefaultProblemProjection.buildProblemQueryWrapper category branch.
        String sql = "SELECT ptr.problem_id FROM problem_tag_relations ptr " +
                "JOIN problem_tags pt ON ptr.tag_id = pt.id " +
                "WHERE pt.slug = CONCAT('problem-category-', ?)";
        List<Long> ids = new ArrayList<>();
        try (var conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, category.toLowerCase());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ids.add(rs.getLong("problem_id"));
                }
            }
        }
        return ids;
    }

    /**
     * Replays the service's fine-grained tag filter (label / slug / id OR-match).
     */
    private List<Long> problemIdsForTag(String tag) throws Exception {
        // Mirrors DefaultProblemProjection.buildProblemQueryWrapper tag branch.
        String sql = "SELECT ptr.problem_id FROM problem_tag_relations ptr " +
                "LEFT JOIN problem_tags pt ON ptr.tag_id = pt.id " +
                "WHERE pt.label = ? OR pt.slug = ? OR pt.id = ?";
        List<Long> ids = new ArrayList<>();
        try (var conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tag);
            ps.setString(2, tag);
            ps.setString(3, tag);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ids.add(rs.getLong("problem_id"));
                }
            }
        }
        return ids;
    }

    @Test
    @DisplayName("category=algorithms resolves via problem-category-algorithms slug → hits tagged problems")
    void algorithmsResolvesViaSlug() throws Exception {
        assertThat(problemIdsForCategory("algorithms"))
                .containsExactlyInAnyOrder(1L, 2L);
    }

    @Test
    @DisplayName("category=database returns empty (data gap, not a SQL regression)")
    void databaseEmptyIsDataGapNotBug() throws Exception {
        assertThat(problemIdsForCategory("database")).isEmpty();
    }

    @Test
    @DisplayName("category namespace isolates from fine-grained tags (array-only problem 3 not matched)")
    void categoryNamespaceIsolatesFromFineGrainedTags() throws Exception {
        // problem 3 carries only the bare-slug 'array' tag; the category slug
        // 'problem-category-algorithms' must NOT match it.
        assertThat(problemIdsForCategory("algorithms")).doesNotContain(3L);
    }

    @Test
    @DisplayName("fine-grained tag filter (tag=array via label/slug/id) unaffected by category fix")
    void fineGrainedTagStillWorks() throws Exception {
        assertThat(problemIdsForTag("array")).containsExactly(3L);
    }
}
