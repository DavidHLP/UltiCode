package com.ulticode.modules.learningplan;

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.ulticode.common.error.BaseErrorCode;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.modules.learningplan.config.LearningPlanConfiguration;
import com.ulticode.modules.learningplan.dto.LearningPlanVO;
import com.ulticode.modules.learningplan.dto.SaveLearningPlanDTO;
import com.ulticode.modules.learningplan.entity.LearningPlan;
import com.ulticode.modules.learningplan.mapper.LearningPlanMapper;
import com.ulticode.modules.learningplan.port.LearningPlanAccessPort;
import com.ulticode.modules.learningplan.service.LearningPlanService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.autoconfigure.transaction.TransactionAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * Real-MySQL integration proof for the App learning-plan write path.
 *
 * <p>Loads only the learning-plan domain service/configuration, its mapper,
 * the DataSource/MyBatis-Plus/Jackson auto-configuration and a {@link Clock} —
 * not the full {@code @SpringBootApplication} scan. Cross-owner RPC is replaced
 * by a mock {@link LearningPlanAccessPort}, so the test exercises the real
 * InnoDB transaction: the {@code (user_id, idempotency_key)} unique fence, the
 * {@code SELECT ... FOR UPDATE} current read that closes the repeatable-read
 * snapshot gap, replay of an identical payload, {@code 40900} on a reused key
 * with a different payload, and owner-scoped reads.
 *
 * <p><b>Not executed in this session</b> — the remote runner is unavailable; run
 * with {@code ./mvnw -pl app/app-web -am -Dtest='LearningPlan*IT' -Dsurefire.failIfNoSpecifiedTests=false test -B}.
 */
@SpringBootTest(
        classes = {
                LearningPlanConfiguration.class,
                LearningPlanMapper.class,
                LearningPlanWriteIT.ClockTestConfig.class,
                DataSourceAutoConfiguration.class,
                DataSourceTransactionManagerAutoConfiguration.class,
                TransactionAutoConfiguration.class,
                JdbcTemplateAutoConfiguration.class,
                MybatisPlusAutoConfiguration.class,
                JacksonAutoConfiguration.class
        },
        properties = {
                "spring.flyway.enabled=false",
                "spring.jpa.hibernate.ddl-auto=none"
        }
)
@MapperScan("com.ulticode.modules.learningplan.mapper")
@Testcontainers
@DisplayName("LearningPlanWriteIT — real MySQL idempotency + owner scoping")
class LearningPlanWriteIT {

    private static final String MIGRATION = "V20261001120000__Create_App_Learning_Plans.sql";

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("ulticode_learning_plan_it")
            .withUsername("test")
            .withPassword("test")
            .withCopyFileToContainer(
                    MountableFile.forHostPath(migrationPath()), "/docker-entrypoint-initdb.d/" + MIGRATION);

    @TestConfiguration
    static class ClockTestConfig {
        @Bean
        Clock clock() {
            return Clock.systemUTC();
        }
    }

    private static Path migrationPath() {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        while (current != null) {
            Path candidate = current.resolve("init-db/migrations/app/" + MIGRATION);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IllegalStateException(MIGRATION + " not found from user.dir=" + System.getProperty("user.dir"));
    }

    @DynamicPropertySource
    static void configureDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", MYSQL::getDriverClassName);
    }

    @Autowired
    private LearningPlanService learningPlanService;

    @Autowired
    private LearningPlanMapper learningPlanMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private LearningPlanAccessPort learningPlanAccessPort;

    private static SaveLearningPlanDTO dto(String title, String content) {
        SaveLearningPlanDTO dto = new SaveLearningPlanDTO();
        dto.setSourceSubmissionId(UUID.randomUUID().toString());
        dto.setDraftVersion(1);
        dto.setTitle(title);
        dto.setContent(content);
        return dto;
    }

    private long rowCount(String userId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM learning_plans WHERE user_id = ?", Long.class, userId);
        return count == null ? -1 : count;
    }

    @Test
    @DisplayName("first save persists exactly one row that reads back owner-scoped")
    void firstSavePersistsOneRow() {
        String userId = UUID.randomUUID().toString();
        String key = UUID.randomUUID().toString();
        SaveLearningPlanDTO dto = dto("Two pointers", "Mirror the indices.");

        LearningPlanVO vo = learningPlanService.save(userId, key, dto);

        assertThat(vo.getId()).isNotNull();
        assertThat(vo.getSourceSubmissionId()).isEqualTo(dto.getSourceSubmissionId());
        assertThat(vo.getCreatedAt()).isNotNull();
        assertThat(rowCount(userId)).isEqualTo(1);

        LearningPlan stored = learningPlanMapper.selectByIdAndUser(vo.getId(), userId);
        assertThat(stored).isNotNull();
        assertThat(stored.getIdempotencyKey()).isEqualTo(key);
        assertThat(stored.getTitle()).isEqualTo("Two pointers");
    }

    @Test
    @DisplayName("replaying the same key and payload returns the same row and does not write twice")
    void replayReturnsSameRow() {
        String userId = UUID.randomUUID().toString();
        String key = UUID.randomUUID().toString();
        SaveLearningPlanDTO dto = dto("Sliding window", "Grow then shrink.");

        LearningPlanVO first = learningPlanService.save(userId, key, dto);
        LearningPlanVO replay = learningPlanService.save(userId, key, dto);

        assertThat(replay.getId()).isEqualTo(first.getId());
        assertThat(replay.getCreatedAt()).isEqualTo(first.getCreatedAt());
        assertThat(rowCount(userId)).isEqualTo(1);
    }

    @Test
    @DisplayName("reusing the key with a different payload returns 40900 and keeps the original row")
    void reusedKeyWithDifferentPayloadConflicts() {
        String userId = UUID.randomUUID().toString();
        String key = UUID.randomUUID().toString();

        LearningPlanVO first = learningPlanService.save(userId, key, dto("Original", "Original body"));

        BusinessException exception = catchThrowableOfType(
                () -> learningPlanService.save(userId, key, dto("Different", "Different body")),
                BusinessException.class);

        assertThat(exception).isNotNull();
        assertThat(exception.getCode()).isEqualTo(BaseErrorCode.CONFLICT.code());
        assertThat(exception.getMessage()).isEqualTo("idempotency_payload_mismatch");
        assertThat(rowCount(userId)).isEqualTo(1);
        assertThat(learningPlanMapper.selectByIdAndUser(first.getId(), userId).getTitle())
                .isEqualTo("Original");
    }

    @Test
    @DisplayName("reads are owner scoped: another user cannot see the row by id or by key")
    void readsAreOwnerScoped() {
        String owner = UUID.randomUUID().toString();
        String stranger = UUID.randomUUID().toString();
        String key = UUID.randomUUID().toString();

        LearningPlanVO vo = learningPlanService.save(owner, key, dto("Owner plan", "Body"));

        assertThat(learningPlanService.get(owner, vo.getId()).getId()).isEqualTo(vo.getId());
        assertThat(learningPlanService.getByKey(owner, key).getId()).isEqualTo(vo.getId());

        assertThat(catchThrowableOfType(
                () -> learningPlanService.get(stranger, vo.getId()), BusinessException.class).getCode())
                .isEqualTo(BaseErrorCode.NOT_FOUND.code());
        assertThat(catchThrowableOfType(
                () -> learningPlanService.getByKey(stranger, key), BusinessException.class).getCode())
                .isEqualTo(BaseErrorCode.NOT_FOUND.code());
    }

    @Test
    @DisplayName("concurrent identical replays collapse to one row with the same plan id")
    void concurrentIdenticalReplaysProduceOneRow() throws Exception {
        String userId = UUID.randomUUID().toString();
        String key = UUID.randomUUID().toString();
        SaveLearningPlanDTO dto = dto("Concurrent", "Same payload");

        List<Callable<LearningPlanVO>> tasks = List.of(
                () -> learningPlanService.save(userId, key, dto),
                () -> learningPlanService.save(userId, key, dto));
        List<LearningPlanVO> results = runConcurrently(tasks);

        assertThat(results.get(0).getId()).isEqualTo(results.get(1).getId());
        assertThat(rowCount(userId)).isEqualTo(1);
    }

    @Test
    @DisplayName("concurrent different payloads: one wins, the other gets 40900, one row total")
    void concurrentDifferentPayloadsConflict() throws Exception {
        String userId = UUID.randomUUID().toString();
        String key = UUID.randomUUID().toString();

        List<Callable<String>> tasks = List.of(
                () -> saveOutcome(userId, key, dto("First", "First body")),
                () -> saveOutcome(userId, key, dto("Second", "Second body")));
        List<String> results = runConcurrently(tasks);

        assertThat(results).filteredOn(result -> result.startsWith("OK:")).hasSize(1);
        assertThat(results).filteredOn(result -> result.startsWith("ERR:")).hasSize(1);
        assertThat(results).filteredOn(result -> result.startsWith("ERR:"))
                .allSatisfy(result -> assertThat(result).isEqualTo("ERR:" + BaseErrorCode.CONFLICT.code()));
        assertThat(rowCount(userId)).isEqualTo(1);
    }

    private String saveOutcome(String userId, String key, SaveLearningPlanDTO dto) {
        try {
            return "OK:" + learningPlanService.save(userId, key, dto).getId();
        } catch (BusinessException exception) {
            return "ERR:" + exception.getCode();
        }
    }

    private static <T> List<T> runConcurrently(List<Callable<T>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<T>> futures = new ArrayList<>();
            for (Callable<T> task : tasks) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return task.call();
                }));
            }
            start.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get(30, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }
}
