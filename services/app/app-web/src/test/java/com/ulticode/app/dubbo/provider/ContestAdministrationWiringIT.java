package com.ulticode.app.dubbo.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ulticode.common.command.ActorDelegation;
import com.ulticode.app.api.command.CreateContestCommand;
import com.ulticode.app.api.dto.ContestAdminViewDTO;
import com.ulticode.app.idempotency.entity.AppCommandReceiptEntity;
import com.ulticode.app.idempotency.mapper.AppCommandReceiptMapper;
import com.ulticode.common.tracing.IdMetadata;
import com.ulticode.common.tracing.TraceMetadata;
import com.ulticode.common.uuid.UuidGenerator;
import com.ulticode.modules.contest.entity.Contest;
import com.ulticode.modules.contest.entity.ContestProblem;
import com.ulticode.modules.contest.mapper.ContestMapper;
import com.ulticode.modules.contest.mapper.ContestProblemMapper;
import com.ulticode.modules.contest.port.ContestOwnerPort;
import com.ulticode.modules.contest.port.DefaultContestOwnerPort;
import com.ulticode.modules.contest.port.adapter.DefaultContestAdminReadAdapter;
import com.ulticode.modules.contest.service.ContestLifecycleService;
import com.ulticode.app.config.MybatisPlusConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.transaction.TransactionAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

/**
 * Real provider contract evidence: Dubbo implementation, owner port and
 * production MyBatis mappers run against MySQL. Only lifecycle side effects
 * outside contest creation are mocked.
 */
@SpringBootTest(
        classes = {
                ContestAdministrationProvider.class,
                DefaultContestOwnerPort.class,
                DefaultContestAdminReadAdapter.class,
                ContestAdministrationWiringTestBeans.class,
                DataSourceAutoConfiguration.class,
                DataSourceTransactionManagerAutoConfiguration.class,
                TransactionAutoConfiguration.class,
                MybatisPlusAutoConfiguration.class,
                JacksonAutoConfiguration.class
        },
        properties = {
                "spring.flyway.enabled=false",
                "spring.jpa.hibernate.ddl-auto=none"
        })
@MapperScan({
        "com.ulticode.modules.contest.mapper",
        "com.ulticode.app.idempotency.mapper"
})
@Testcontainers
@DisplayName("Contest administration provider production wiring")
class ContestAdministrationWiringIT {

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("ulticode")
            .withUsername("test")
            .withPassword("test")
            .withCopyFileToContainer(
                    MountableFile.forHostPath(migrationPath("V20260602_120000__Create_All_Tables.sql")),
                    "/docker-entrypoint-initdb.d/001-base.sql")
            .withCopyFileToContainer(
                    MountableFile.forHostPath(migrationPath("app/V20260801000000__Create_App_Command_Receipt.sql")),
                    "/docker-entrypoint-initdb.d/002-app-command-receipt.sql");

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", MYSQL::getDriverClassName);
    }

    @Autowired
    private ContestAdministrationProvider provider;

    @Autowired
    private ContestMapper contestMapper;

    @Autowired
    private ContestProblemMapper contestProblemMapper;

    @Autowired
    private AppCommandReceiptMapper receiptMapper;

    @MockBean
    private ContestLifecycleService contestLifecycleService;

    @MockBean
    private UuidGenerator uuidGenerator;

    @BeforeEach
    void configureUuidGenerator() {
        when(uuidGenerator.newId()).thenAnswer(invocation -> UUID.randomUUID().toString());
    }

    @Test
    @DisplayName("full create command reaches owner mappers and records receipt")
    void createCommandUsesRealOwnerGraph() {
        long startEpochMs = Instant.parse("2026-08-11T12:00:00Z").toEpochMilli();
        CreateContestCommand command = new CreateContestCommand(
                UUID.randomUUID().toString(),
                IdMetadata.of("provider-create-key", "client-fingerprint"),
                new ActorDelegation("ADMIN", "admin-1", null, "contract IT"),
                new TraceMetadata("trace-provider", null, null, null),
                "provider-wiring",
                "Provider Wiring Contest",
                "creator-1",
                "ICPC",
                "SCORE",
                null,
                "created through the owner seam",
                startEpochMs,
                90,
                50,
                true,
                true,
                List.of(101L),
                List.of(new com.ulticode.app.api.dto.ContestProblemInputDTO(101L, 250)));

        var result = provider.createContest(command);

        assertThat(result.success()).isTrue();
        ContestAdminViewDTO view = result.data();
        assertThat(view).isNotNull();
        assertThat(view.title()).isEqualTo("Provider Wiring Contest");
        assertThat(view.status()).isEqualTo("UPCOMING");

        Contest contest = contestMapper.selectById(view.contestId());
        assertThat(contest.getSlug()).isEqualTo("provider-wiring");
        assertThat(contest.getDescription()).isEqualTo("created through the owner seam");
        assertThat(contest.getCreatedBy()).isEqualTo("creator-1");
        assertThat(contest.getContestType()).isEqualTo("ICPC");
        assertThat(contest.getScoringMode()).isEqualTo("SCORE");
        assertThat(contest.getDurationMinutes()).isEqualTo(90);
        assertThat(contest.getMaxParticipants()).isEqualTo(50);
        assertThat(contest.getIsVisible()).isTrue();

        var replay = provider.createContest(command);
        assertThat(replay.success()).isTrue();
        assertThat(replay.data()).isEqualTo(view);
        assertThat(contestMapper.selectCount(null)).isEqualTo(1);

        List<ContestProblem> problems = contestProblemMapper.findByContestId(view.contestId());
        assertThat(problems).singleElement().satisfies(problem -> {
            assertThat(problem.getId()).isNotBlank();
            assertThat(problem.getProblemId()).isEqualTo(101L);
            assertThat(problem.getScore()).isEqualTo(250);
            assertThat(problem.getCreatedAt()).isNotNull();
            assertThat(problem.getUpdatedAt()).isNotNull();
        });

        AppCommandReceiptEntity receipt = receiptMapper.findByReceiptKey(
                "ContestAdministrationService", "createContest", "provider-create-key");
        assertThat(receipt).isNotNull();
        assertThat(receipt.getCommandId()).isEqualTo(command.commandId());
        assertThat(receipt.getActorType()).isEqualTo("ADMIN");
        assertThat(receipt.getActorId()).isEqualTo("admin-1");
        assertThat(receipt.getTraceId()).isEqualTo("trace-provider");
    }

    private static Path migrationPath(String filename) {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        while (current != null) {
            Path candidate = current.resolve("init-db/migrations").resolve(filename);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Migration not found: " + filename);
    }
}

@TestConfiguration(proxyBeanMethods = false)
class ContestAdministrationWiringTestBeans {

    @Bean
    Clock clock() {
        return Clock.fixed(Instant.parse("2026-08-10T12:00:00Z"), ZoneOffset.UTC);
    }

    @Bean
    MetaObjectHandler metaObjectHandler() {
        return new MybatisPlusConfig.AutoFillMetaObjectHandler();
    }

    @Bean
    ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}
