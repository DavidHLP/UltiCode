package com.ulticode.modules.search.backfill;

import static org.assertj.core.api.Assertions.assertThat;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.ulticode.modules.forum.mapper.ForumPostMapper;
import com.ulticode.modules.forum.port.ForumPostSearchBackfillReadPort;
import com.ulticode.modules.problem.mapper.ProblemMapper;
import com.ulticode.modules.problem.port.ProblemSearchBackfillReadPort;
import com.ulticode.modules.solution.mapper.SolutionMapper;
import com.ulticode.modules.solution.port.SolutionSearchBackfillReadPort;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;
import javax.sql.DataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * SEARCH-003 slice-2 real-MySQL evidence: the shared Flyway chain (including
 * V20260816220000 version columns) applies cleanly and the App-owned content
 * backfill enumeration ports return the correct predicates, document shapes
 * and version semantics against the real schema. User backfill composition is
 * covered by the owner-adapter unit test.
 */
@Testcontainers
@DisplayName("SEARCH-003 backfill enumeration real-MySQL IT")
class SearchBackfillReadPortIT {

    @Container
    private static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("ulticode")
            .withUsername("root")
            .withPassword("root");

    private static DataSource dataSource;
    private static SqlSessionFactory sqlSessionFactory;

    private ProblemSearchBackfillReadPort problemPort;
    private ForumPostSearchBackfillReadPort forumPostPort;
    private SolutionSearchBackfillReadPort solutionPort;

    @BeforeAll
    static void setUpSchema() throws Exception {
        dataSource = new DriverManagerDataSource(
                mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
        Flyway.configure()
                .dataSource(dataSource)
                .locations("filesystem:../../../init-db/migrations/*.sql")
                .load()
                .migrate();

        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(ProblemMapper.class);
        configuration.addMapper(ForumPostMapper.class);
        configuration.addMapper(SolutionMapper.class);
        MybatisSqlSessionFactoryBean factoryBean = new MybatisSqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setConfiguration(configuration);
        sqlSessionFactory = factoryBean.getObject();
    }

    @BeforeEach
    void setUp() throws Exception {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("SET FOREIGN_KEY_CHECKS = 0");
            statement.execute("DELETE FROM solutions");
            statement.execute("DELETE FROM forum_posts");
            statement.execute("DELETE FROM problems");
            statement.execute("DELETE FROM user_profiles");
            statement.execute("DELETE FROM users");
            statement.execute("SET FOREIGN_KEY_CHECKS = 1");
        }
        problemPort = new ProblemSearchBackfillReadPort(
                sqlSessionFactory.openSession().getMapper(ProblemMapper.class));
        forumPostPort = new ForumPostSearchBackfillReadPort(
                sqlSessionFactory.openSession().getMapper(ForumPostMapper.class));
        solutionPort = new SolutionSearchBackfillReadPort(
                sqlSessionFactory.openSession().getMapper(SolutionMapper.class));
    }

    private long millis(String isoDateTime) {
        return SearchBackfillReadPort.toVersionMillis(LocalDateTime.parse(isoDateTime));
    }

    @Test
    @DisplayName("migration applies and version columns exist with ON UPDATE maintenance")
    void migrationAddsVersionColumns() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            var meta = connection.getMetaData();
            for (String table : List.of("users", "forum_posts", "solutions")) {
                boolean found = false;
                try (ResultSet columns = meta.getColumns(null, null, table, "updated_at")) {
                    found = columns.next();
                }
                assertThat(found).as("updated_at on %s", table).isTrue();
            }
        }
        // solutions.updated_at must auto-maintain on UPDATE (ON UPDATE CURRENT_TIMESTAMP(3))
        try (Connection connection = dataSource.getConnection()) {
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO solutions (id, problem_id, user_id, title, content, language, created_at, updated_at) "
                            + "VALUES ('s-1', 1, 'u-1', 'T', 'C', 'java', '2026-08-01 00:00:00.000', '2026-08-01 00:00:00.000')")) {
                insert.executeUpdate();
            }
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("UPDATE solutions SET title = 'T2' WHERE id = 's-1'");
            }
            try (Statement statement = connection.createStatement();
                 ResultSet rs = statement.executeQuery("SELECT updated_at FROM solutions WHERE id = 's-1'")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getTimestamp(1).getTime()).isGreaterThan(millis("2026-08-01T00:00:00"));
            }
        }
    }

    @Test
    @DisplayName("problem enumeration keeps published non-deleted rows with updated_at versions")
    void problemEnumerationPredicatesAndShape() throws Exception {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("""
                    INSERT INTO problems (id, slug, title, difficulty, is_published, is_deleted, updated_at)
                    VALUES (1, 'one', 'One', 'Easy', 1, 0, '2026-08-16 08:00:00.000'),
                           (2, 'two', 'Two', 'Hard', 0, 0, '2026-08-16 09:00:00.000'),
                           (3, 'three', 'Three', 'Medium', 1, 1, '2026-08-16 10:00:00.000')""");
        }

        List<SearchBackfillDocument> rows = problemPort.enumerateForBackfill(0, 100);

        assertThat(rows).hasSize(1);
        SearchBackfillDocument doc = rows.get(0);
        assertThat(doc.documentId()).isEqualTo("1");
        assertThat(doc.versionMillis()).isEqualTo(millis("2026-08-16T08:00:00"));
        assertThat(doc.document())
                .containsEntry("id", 1L)
                .containsEntry("title", "One")
                .containsEntry("slug", "one")
                .containsEntry("difficulty", "Easy");
    }

    @Test
    @DisplayName("forum post enumeration keeps non-deleted rows with updated_at versions")
    void forumPostEnumeration() throws Exception {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("""
                    INSERT INTO forum_posts (id, community_id, user_id, title, tags, excerpt, permalink,
                                             is_deleted, created_at, updated_at)
                    VALUES ('p-1', 'c-1', 'u-1', 'Post', '[]', 'Excerpt', '/p/p-1', 0,
                            '2026-08-16 07:00:00.000', '2026-08-16 08:00:00.000'),
                           ('p-2', 'c-1', 'u-1', 'Gone', '[]', NULL, NULL, 1,
                            '2026-08-16 07:00:00.000', '2026-08-16 09:00:00.000')""");
        }

        List<SearchBackfillDocument> rows = forumPostPort.enumerateForBackfill(0, 100);

        assertThat(rows).hasSize(1);
        SearchBackfillDocument doc = rows.get(0);
        assertThat(doc.documentId()).isEqualTo("p-1");
        assertThat(doc.versionMillis()).isEqualTo(millis("2026-08-16T08:00:00"));
        assertThat(doc.document())
                .containsEntry("id", "p-1")
                .containsEntry("title", "Post")
                .containsEntry("excerpt", "Excerpt")
                .containsEntry("permalink", "/p/p-1");
    }

    @Test
    @DisplayName("solution enumeration keeps published non-deleted rows with updated_at versions")
    void solutionEnumeration() throws Exception {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("""
                    INSERT INTO solutions (id, problem_id, user_id, title, content, language, summary,
                                           is_published, is_deleted, updated_at)
                    VALUES ('s-1', 7, 'u-1', 'Clean', 'C', 'java', 'Sum', 1, 0, '2026-08-16 08:00:00.000'),
                           ('s-2', 7, 'u-1', 'Hidden', 'C', 'java', NULL, 0, 0, '2026-08-16 09:00:00.000'),
                           ('s-3', 7, 'u-1', 'Dead', 'C', 'java', NULL, 1, 1, '2026-08-16 10:00:00.000')""");
        }

        List<SearchBackfillDocument> rows = solutionPort.enumerateForBackfill(0, 100);

        assertThat(rows).hasSize(1);
        SearchBackfillDocument doc = rows.get(0);
        assertThat(doc.documentId()).isEqualTo("s-1");
        assertThat(doc.versionMillis()).isEqualTo(millis("2026-08-16T08:00:00"));
        assertThat(doc.document())
                .containsEntry("id", "s-1")
                .containsEntry("title", "Clean")
                .containsEntry("summary", "Sum")
                .containsEntry("problemId", 7L);
    }


    @Test
    @DisplayName("paging is stable and gapless over the natural key")
    void pagingIsStableAndGapless() throws Exception {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("""
                    INSERT INTO problems (id, slug, title, difficulty, is_published, is_deleted, updated_at)
                    VALUES (1, 'a', 'A', 'Easy', 1, 0, '2026-08-16 08:00:00.000'),
                           (2, 'b', 'B', 'Easy', 1, 0, '2026-08-16 08:00:00.000'),
                           (3, 'c', 'C', 'Easy', 1, 0, '2026-08-16 08:00:00.000')""");
        }

        List<SearchBackfillDocument> page1 = problemPort.enumerateForBackfill(0, 2);
        List<SearchBackfillDocument> page2 = problemPort.enumerateForBackfill(2, 2);

        assertThat(page1).extracting(SearchBackfillDocument::documentId).containsExactly("1", "2");
        assertThat(page2).extracting(SearchBackfillDocument::documentId).containsExactly("3");
    }
}
