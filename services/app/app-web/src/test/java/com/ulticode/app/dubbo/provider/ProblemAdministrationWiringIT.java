package com.ulticode.app.dubbo.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.ulticode.app.api.command.DeleteProblemCommand;
import com.ulticode.app.api.command.PublishProblemCommand;
import com.ulticode.app.api.error.AppErrorCode;
import com.ulticode.app.api.service.ProblemAdministrationService;
import com.ulticode.app.api.service.SolutionReadPort;
import com.ulticode.app.config.AppClockConfig;
import com.ulticode.app.config.AppCommonUuidGenerator;
import com.ulticode.app.config.AppDomainServiceConfig;
import com.ulticode.app.config.MybatisPlusConfig;
import com.ulticode.app.security.AdminActorAuthorizer;
import com.ulticode.common.auth.CurrentUserProvider;
import com.ulticode.common.command.ActorDelegation;
import com.ulticode.common.tracing.IdMetadata;
import com.ulticode.common.tracing.TraceMetadata;
import com.ulticode.modules.event.outbox.IntegrationEventPublisher;
import com.ulticode.modules.problem.adapter.LegacyProblemVersionAdapter;
import com.ulticode.modules.problem.adapter.LegacyProblemWriteAdapter;
import com.ulticode.modules.problem.dto.CreateProblemDTO;
import com.ulticode.modules.problem.dto.UpdateProblemDTO;
import com.ulticode.modules.problem.port.DefaultProblemDetailPort;
import com.ulticode.modules.problem.port.ProblemInteractionQueryPort;
import com.ulticode.modules.problem.port.ProblemLanguageCatalog;
import com.ulticode.modules.problem.projection.DefaultProblemProjection;
import com.ulticode.modules.problem.service.ProblemIndexRefresher;
import com.ulticode.modules.problem.service.ProblemMutationModule;
import com.ulticode.modules.problem.service.ProblemService;
import com.ulticode.modules.problem.service.impl.ProblemServiceImpl;
import com.ulticode.modules.problem.service.impl.ProblemSnapshotServiceImpl;
import com.ulticode.modules.problem.service.impl.ProblemVersionServiceImpl;
import com.ulticode.modules.search.source.SearchDocumentChangedPublisher;
import com.ulticode.submission.api.service.ProblemSubmissionStatsPort;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jackson2.autoconfigure.Jackson2AutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.JdbcTemplateAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.transaction.autoconfigure.TransactionAutoConfiguration;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

@SpringBootTest(classes = {
        ProblemAdministrationProvider.class, ProblemMutationModule.class, AppDomainServiceConfig.class,
        ProblemServiceImpl.class, DefaultProblemProjection.class,
        LegacyProblemWriteAdapter.class, LegacyProblemVersionAdapter.class, DefaultProblemDetailPort.class,
        ProblemVersionServiceImpl.class, ProblemSnapshotServiceImpl.class,
        ProblemIndexRefresher.class, SearchDocumentChangedPublisher.class, IntegrationEventPublisher.class,
        AppClockConfig.class, AppCommonUuidGenerator.class, ProblemAdministrationWiringIT.TestBeans.class,
        DataSourceAutoConfiguration.class, DataSourceTransactionManagerAutoConfiguration.class,
        JdbcTemplateAutoConfiguration.class, TransactionAutoConfiguration.class,
        MybatisPlusAutoConfiguration.class, Jackson2AutoConfiguration.class
})
@MapperScan({"com.ulticode.modules.problem.mapper", "com.ulticode.modules.event.outbox"})
@MockitoBean(types = {ProblemSubmissionStatsPort.class, SolutionReadPort.class,
        ProblemInteractionQueryPort.class, ProblemLanguageCatalog.class})
@Testcontainers
class ProblemAdministrationWiringIT {

    private static final ActorDelegation ACTOR = new ActorDelegation("ADMIN", "admin-1", null, null);
    private static final TraceMetadata TRACE = new TraceMetadata("problem-mutation-it", null, null, null);

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withCommand("--log-bin-trust-function-creators=1")
            .withCopyFileToContainer(migration("V20260729140300__Create_App_Schema_Tables.sql"),
                    "/docker-entrypoint-initdb.d/000-app-schema.sql")
            .withCopyFileToContainer(migration("V20260823151000__Create_Missing_App_Owner_Tables.sql"),
                    "/docker-entrypoint-initdb.d/001-owner-tables.sql")
            .withCopyFileToContainer(migration("V20260823151200__Converge_App_Tables_With_Runtime_Contracts.sql"),
                    "/docker-entrypoint-initdb.d/002-runtime-contracts.sql")
            .withCopyFileToContainer(migration("V20261027070000__Restore_Server_Generated_Problem_Ids.sql"),
                    "/docker-entrypoint-initdb.d/003-problem-ids.sql");

    @DynamicPropertySource
    static void dataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @Autowired
    private ProblemAdministrationService provider;

    @Autowired
    private ProblemService local;

    @Autowired
    private JdbcTemplate jdbc;

    @MockitoBean
    private AdminActorAuthorizer actorAuthorizer;

    @MockitoBean
    private CurrentUserProvider currentUser;

    @BeforeEach
    void authorizeAdmin() {
        when(actorAuthorizer.isAuthorized(any())).thenReturn(true);
        when(currentUser.getCurrentUserId()).thenReturn("local-admin");
    }

    @Test
    void failedSearchPublicationRollsBackPublishAndReturnsRpcFailure() {
        jdbc.update("""
                INSERT INTO problems (id, slug, title, difficulty, is_published)
                VALUES (42, 'draft', 'Draft', 'Easy', false)
                """);
        rejectSearchOutbox();
        try {
            var result = provider.publishProblem(new PublishProblemCommand(UUID.randomUUID().toString(),
                    IdMetadata.mint(), ACTOR, TRACE, "42", 1L, true, null));

            assertThat(result.success()).isFalse();
            assertThat(result.error().code()).isEqualTo(AppErrorCode.UNEXPECTED_APP_STATE.code());
            assertThat(result.traceId()).isEqualTo(TRACE.traceId());
            assertThat(jdbc.queryForObject("SELECT is_published FROM problems WHERE id=42", Boolean.class)).isFalse();
            assertThat(jdbc.queryForObject("SELECT version FROM problems WHERE id=42", Integer.class)).isEqualTo(1);
            assertThat(outboxCount(42L)).isZero();
        } finally {
            jdbc.execute("DROP TRIGGER reject_search_outbox");
        }
    }

    @Test
    void localWriteRollsBackProblemDetailsAndVersionBeforeRpcDeleteCommitsTombstone() {
        var create = new CreateProblemDTO();
        create.setSlug("atomic-lifecycle");
        create.setDifficulty("Easy");
        create.setTitle("Original title");
        create.setSummary("Original summary");
        create.setContent("Original statement");
        var created = local.createProblem(create);
        long id = created.getId();
        assertThat(created.getPublishedBy()).isEqualTo("local-admin");
        assertThat(outboxCount(id)).isEqualTo(1);
        assertThat(local.getProblemById(id).getTitle()).isEqualTo("Original title");

        var rejected = new UpdateProblemDTO();
        rejected.setTitle("Rejected title");
        rejected.setSummary("Rejected summary");
        rejectSearchOutbox();
        try {
            assertThatThrownBy(() -> local.updateProblem(id, rejected))
                    .isInstanceOf(DataAccessException.class)
                    .hasRootCauseMessage("Search publication failed");
        } finally {
            jdbc.execute("DROP TRIGGER reject_search_outbox");
        }
        assertThat(local.findById(id)).hasValueSatisfying(problem -> {
            assertThat(problem.getTitle()).isEqualTo("Original title");
            assertThat(problem.getVersion()).isEqualTo(1);
        });
        assertThat(jdbc.queryForObject("SELECT summary FROM problem_details WHERE problem_id=?", String.class, id))
                .isEqualTo("Original summary");
        assertThat(jdbc.queryForList("SELECT version_number FROM problem_versions WHERE problem_id=?", Integer.class, id))
                .containsExactly(1);
        assertThat(outboxCount(id)).isEqualTo(1);

        var partial = new UpdateProblemDTO();
        partial.setTitle("Updated title");
        assertThat(local.updateProblem(id, partial).getTitle()).isEqualTo("Updated title");
        assertThat(local.getProblemById(id).getTitle()).isEqualTo("Updated title");
        assertThat(jdbc.queryForObject("SELECT summary FROM problem_details WHERE problem_id=?", String.class, id))
                .isEqualTo("Original summary");
        assertThat(jdbc.queryForObject("""
                SELECT created_by FROM problem_versions WHERE problem_id=? AND version_number=2
                """, String.class, id)).isEqualTo("local-admin");
        assertThat(outboxCount(id)).isEqualTo(2);

        var staleDelete = provider.deleteProblem(deleteCommand(id, 1L));
        assertThat(staleDelete.success()).isFalse();
        assertThat(staleDelete.error().code()).isEqualTo(AppErrorCode.VERSION_CONFLICT.code());
        assertThat(outboxCount(id)).isEqualTo(2);

        assertThat(provider.deleteProblem(deleteCommand(id, 2L)).success()).isTrue();
        assertThat(local.findById(id)).isEmpty();
        assertThat(jdbc.queryForObject("SELECT version FROM problems WHERE id=?", Integer.class, id)).isEqualTo(3);
        assertThat(outboxCount(id)).isEqualTo(3);
        assertThat(jdbc.queryForObject("""
                SELECT JSON_CONTAINS_PATH(payload, 'one', '$.document')
                FROM integration_outbox
                WHERE aggregate_id=? AND event_type='SearchDocumentChanged'
                  AND JSON_UNQUOTE(JSON_EXTRACT(payload, '$.index'))='problems'
                  AND JSON_UNQUOTE(JSON_EXTRACT(payload, '$.operation'))='DELETE'
                """, Integer.class, Long.toString(id))).isZero();
    }

    private void rejectSearchOutbox() {
        jdbc.execute("""
                CREATE TRIGGER reject_search_outbox AFTER INSERT ON integration_outbox FOR EACH ROW
                SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Search publication failed'
                """);
    }

    private int outboxCount(long problemId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM integration_outbox WHERE aggregate_id=?",
                Integer.class, Long.toString(problemId));
    }

    private static DeleteProblemCommand deleteCommand(long id, long version) {
        return new DeleteProblemCommand(UUID.randomUUID().toString(), IdMetadata.mint(), ACTOR, TRACE,
                Long.toString(id), version, null);
    }

    private static MountableFile migration(String filename) {
        for (Path root = Path.of(System.getProperty("user.dir")).toAbsolutePath(); root != null; root = root.getParent()) {
            Path file = root.resolve("init-db/migrations/app").resolve(filename);
            if (Files.isRegularFile(file)) {
                return MountableFile.forHostPath(file);
            }
        }
        throw new IllegalStateException("App migration not found: " + filename);
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableCaching
    static class TestBeans {
        @Bean
        MetaObjectHandler metaObjectHandler() {
            return new MybatisPlusConfig.AutoFillMetaObjectHandler();
        }

        @Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager("problem");
        }
    }
}
