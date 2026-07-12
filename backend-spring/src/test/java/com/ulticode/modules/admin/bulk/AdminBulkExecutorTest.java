package com.ulticode.modules.admin.bulk;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the {@link AdminBulkExecutor} loop. The executor is the
 * single test surface for every admin bulk-action call site, so the
 * per-item isolation, counting, logging and existence-guard contract live
 * here.
 *
 * @author ulticode
 */
class AdminBulkExecutorTest {

    private final AdminBulkExecutor executor = new AdminBulkExecutor();

    @Test
    void allSucceedCountsEveryItem() {
        List<String> ids = List.of("a", "b", "c");
        AtomicInteger calls = new AtomicInteger();

        AdminBulkExecutor.Run run = executor.run(
            ids, "delete", id -> calls.incrementAndGet(), id -> true);

        assertThat(run.total()).isEqualTo(3);
        assertThat(run.successful()).isEqualTo(3);
        assertThat(run.failed()).isZero();
        assertThat(calls.get()).isEqualTo(3);
        assertThat(run.items()).allMatch(AdminBulkExecutor.ItemOutcome::isSuccess);
    }

    @Test
    void perItemFailureIsolatesAndContinues() {
        List<String> ids = List.of("ok1", "boom", "ok2");

        AdminBulkExecutor.Run run = executor.run(
            ids, "delete",
            id -> {
                if ("boom".equals(id)) {
                    throw new IllegalStateException("kaboom");
                }
            },
            id -> true);

        assertThat(run.successful()).isEqualTo(2);
        assertThat(run.failed()).isEqualTo(1);
        AdminBulkExecutor.ItemOutcome failure = run.items().get(1);
        assertThat(failure).isInstanceOf(AdminBulkExecutor.Failure.class);
        assertThat(failure.id()).isEqualTo("boom");
        AdminBulkExecutor.Failure f = (AdminBulkExecutor.Failure) failure;
        assertThat(f.error()).isEqualTo("kaboom");
    }

    @Test
    void existenceGuardShortCircuitsWithoutRunningAction() {
        List<String> ids = List.of("present", "missing");
        List<String> actionsRun = new ArrayList<>();

        AdminBulkExecutor.Run run = executor.run(
            ids, "publish", actionsRun::add, "present"::equals);

        assertThat(run.successful()).isEqualTo(1);
        assertThat(run.failed()).isEqualTo(1);
        assertThat(run.items().get(1)).isInstanceOf(AdminBulkExecutor.NotFound.class);
        assertThat(actionsRun).containsExactly("present");
    }

    @Test
    void emptyInputProducesEmptyRun() {
        AdminBulkExecutor.Run run = executor.run(
            List.of(), "delete", id -> { }, id -> true);

        assertThat(run.total()).isZero();
        assertThat(run.successful()).isZero();
        assertThat(run.failed()).isZero();
        assertThat(run.items()).isEmpty();
    }
}
