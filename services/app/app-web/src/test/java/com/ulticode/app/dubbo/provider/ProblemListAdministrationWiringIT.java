package com.ulticode.app.dubbo.provider;

import static org.assertj.core.api.Assertions.assertThat;

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ulticode.app.api.command.ActorDelegation;
import com.ulticode.app.api.command.DeleteProblemListCommand;
import com.ulticode.app.api.service.ProblemExistencePort;
import com.ulticode.app.config.MybatisPlusConfig;
import com.ulticode.app.idempotency.entity.AppCommandReceiptEntity;
import com.ulticode.app.idempotency.mapper.AppCommandReceiptMapper;
import com.ulticode.app.idempotency.CommandReceiptExecutor;
import com.ulticode.common.tracing.IdMetadata;
import com.ulticode.common.tracing.TraceMetadata;
import com.ulticode.modules.problemlist.entity.ProblemList;
import com.ulticode.modules.problemlist.mapper.ProblemListMapper;
import com.ulticode.modules.problemlist.projection.ProblemListProjection;
import com.ulticode.modules.problemlist.service.impl.ProblemListServiceImpl;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.transaction.TransactionAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

/**
 * Real provider evidence for the ProblemList owner transaction and receipt replay.
 */
@SpringBootTest(
        classes = {
                ProblemListAdministrationProvider.class,
                ProblemListServiceImpl.class,
                ProblemListAdministrationWiringTestBeans.class,
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
        "com.ulticode.modules.problemlist.mapper",
        "com.ulticode.app.idempotency.mapper"
})
@Testcontainers
class ProblemListAdministrationWiringIT {

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
    private ProblemListAdministrationProvider provider;

    @Autowired
    private ProblemListMapper problemListMapper;

    @Autowired
    private AppCommandReceiptMapper receiptMapper;

    @MockBean
    private ProblemListProjection problemListProjection;

    @MockBean
    private ProblemExistencePort problemExistencePort;

    @Test
    void keyedDeleteReplaysAfterOwnerRowHasBeenDeleted() {
        String listId = UUID.randomUUID().toString();
        ProblemList list = new ProblemList();
        list.setId(listId);
        list.setName("Provider wiring list");
        list.setDescription("receipt replay");
        list.setAuthorId("admin-1");
        list.setIsPublic(true);
        list.setIsFeatured(false);
        list.setBannerOrder(0);
        list.setVersion(1);
        list.setCreatedAt(LocalDateTime.now());
        list.setUpdatedAt(LocalDateTime.now());
        problemListMapper.insert(list);

        DeleteProblemListCommand command = new DeleteProblemListCommand(
                UUID.randomUUID().toString(),
                IdMetadata.of("problem-list-delete-it", "client-fingerprint"),
                new ActorDelegation("ADMIN", "admin-1", "admin-1", "real wiring IT"),
                new TraceMetadata("trace-problem-list-it", null, null, null),
                listId);

        var first = provider.deleteProblemList(command);
        assertThat(first.success()).isTrue();
        assertThat(problemListMapper.selectById(listId)).isNull();

        var replay = provider.deleteProblemList(command);
        assertThat(replay.success()).isTrue();
        assertThat(replay.idempotencyKey()).isEqualTo("problem-list-delete-it");
        assertThat(problemListMapper.selectById(listId)).isNull();

        AppCommandReceiptEntity receipt = receiptMapper.findByReceiptKey(
                "ProblemListAdministrationService", "deleteProblemList", "problem-list-delete-it");
        assertThat(receipt).isNotNull();
        assertThat(receipt.getStatus()).isEqualTo("SUCCESS");
        assertThat(receipt.getActorId()).isEqualTo("admin-1");
        assertThat(receipt.getTraceId()).isEqualTo("trace-problem-list-it");
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
class ProblemListAdministrationWiringTestBeans {

    @Bean
    Clock clock() {
        return Clock.fixed(Instant.parse("2026-08-11T12:00:00Z"), ZoneOffset.UTC);
    }

    @Bean
    CommandReceiptExecutor commandReceiptExecutor(
            AppCommandReceiptMapper receiptMapper,
            ObjectMapper objectMapper,
            Clock clock) {
        return new CommandReceiptExecutor(receiptMapper, objectMapper, clock);
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
