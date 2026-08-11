package com.ulticode.modules.forum.mapper;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.ulticode.modules.forum.entity.ForumPost;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that the admin forum mapper keeps offset pagination stable when
 * rows tie on view count or creation time.
 *
 * <p>Run explicitly with
 * {@code ./mvnw -Dtest='ForumPostPaginationIT'
 * -Dforum.pagination.it.run=true test -B} when Docker is available.
 */
@EnabledIfSystemProperty(named = "forum.pagination.it.run", matches = "true")
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("ForumPostMapper deterministic pagination (IT)")
class ForumPostPaginationIT {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("ulticode_forum_pagination_it")
            .withUsername("test")
            .withPassword("test")
            .withStartupTimeoutSeconds(180);

    private SqlSession session;
    private ForumPostMapper mapper;

    @BeforeEach
    void setUp() throws Exception {
        DataSource dataSource = new DriverManagerDataSource(
                "jdbc:mysql://127.0.0.1:" + MYSQL.getMappedPort(3306)
                        + "/ulticode_forum_pagination_it",
                "test", "test");
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS forum_comments");
            statement.execute("DROP TABLE IF EXISTS forum_posts");
            statement.execute("""
                    CREATE TABLE forum_posts (
                      id varchar(40) NOT NULL,
                      community_id varchar(40),
                      user_id varchar(40),
                      title varchar(255),
                      excerpt text,
                      views int NOT NULL DEFAULT 0,
                      is_pinned tinyint(1) NOT NULL DEFAULT 0,
                      is_locked tinyint(1) NOT NULL DEFAULT 0,
                      is_flagged tinyint(1) NOT NULL DEFAULT 0,
                      flagged_reason varchar(255),
                      flagged_at datetime(3),
                      is_deleted tinyint(1) NOT NULL DEFAULT 0,
                      deleted_at datetime(3),
                      created_at datetime(3) NOT NULL,
                      PRIMARY KEY (id)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """);
            statement.execute("""
                    CREATE TABLE forum_comments (
                      id varchar(40) NOT NULL,
                      post_id varchar(40) NOT NULL,
                      is_deleted tinyint(1) NOT NULL DEFAULT 0,
                      PRIMARY KEY (id)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """);
            insertPost(connection, "post-a", 10);
            insertPost(connection, "post-b", 10);
            insertPost(connection, "post-c", 10);
            insertComment(connection, "comment-b-1", "post-b", false);
            insertComment(connection, "comment-b-2", "post-b", false);
            insertComment(connection, "comment-c-1", "post-c", false);
            insertComment(connection, "comment-c-2", "post-c", false);
            insertComment(connection, "comment-c-deleted", "post-c", true);
        }

        MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();

        factory.setDataSource(dataSource);
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.addMapper(ForumPostMapper.class);
        factory.setConfiguration(configuration);
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        configuration.addInterceptor(interceptor);
        SqlSessionFactory sqlSessionFactory = factory.getObject();
        assertThat(sqlSessionFactory).isNotNull();
        session = sqlSessionFactory.openSession(true);
        mapper = session.getMapper(ForumPostMapper.class);
    }

    @AfterEach
    void tearDown() {
        if (session != null) {
            session.close();
        }
    }

    @Test
    @DisplayName("viewCount pagination uses id as an ascending tie-breaker")
    void viewCountPaginationIsStableAcrossPages() {
        assertThat(pageIds("viewCount", "asc", 1)).containsExactly("post-a");
        assertThat(pageIds("viewCount", "asc", 2)).containsExactly("post-b");
        assertThat(pageIds("viewCount", "asc", 3)).containsExactly("post-c");
    }

    @Test
    @DisplayName("default createdAt pagination uses id as a descending tie-breaker")
    void defaultPaginationIsStableAcrossPages() {
        assertThat(pageIds("createdAt", "desc", 1)).containsExactly("post-c");
        assertThat(pageIds("createdAt", "desc", 2)).containsExactly("post-b");
        assertThat(pageIds("createdAt", "desc", 3)).containsExactly("post-a");
    }
    @Test
    @DisplayName("commentCount pagination uses id as a descending tie-breaker and ignores deleted comments")
    void commentCountPaginationIsStableAcrossPages() {
        assertThat(pageIds("commentCount", "desc", 1)).containsExactly("post-c");
        assertThat(pageIds("commentCount", "desc", 2)).containsExactly("post-b");
        assertThat(pageIds("commentCount", "desc", 3)).containsExactly("post-a");
    }

    private List<String> pageIds(String sortBy, String sortOrder, long pageNumber) {
        List<ForumPost> rows = mapper.selectPageIgnoreDeleted(
                new Page<>(pageNumber, 1),
                null, null, null, null, null, null, false, sortBy, sortOrder);
        return rows.stream().map(ForumPost::getId).toList();
    }

    private static void insertPost(java.sql.Connection connection, String id, int views) throws Exception {
        try (var statement = connection.prepareStatement(
                "INSERT INTO forum_posts "
                        + "(id, community_id, user_id, title, excerpt, views, created_at) "
                        + "VALUES (?, 'community-1', 'user-1', ?, ?, ?, ?)")) {
            statement.setString(1, id);
            statement.setString(2, id);
            statement.setString(3, "excerpt-" + id);
            statement.setInt(4, views);
            statement.setTimestamp(5, Timestamp.valueOf(LocalDateTime.of(2026, 8, 11, 12, 0)));
            statement.executeUpdate();
        }
    }

    private static void insertComment(
            java.sql.Connection connection, String id, String postId, boolean deleted) throws Exception {
        try (var statement = connection.prepareStatement(
                "INSERT INTO forum_comments (id, post_id, is_deleted) VALUES (?, ?, ?)")) {
            statement.setString(1, id);
            statement.setString(2, postId);
            statement.setBoolean(3, deleted);
            statement.executeUpdate();
        }
    }
}
