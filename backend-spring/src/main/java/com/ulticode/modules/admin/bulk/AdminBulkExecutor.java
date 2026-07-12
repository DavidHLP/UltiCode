package com.ulticode.modules.admin.bulk;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Deep module that owns the admin bulk-action loop.
 *
 * <p>Five admin write services (forum / comment / solution / problem /
 * submission-batch) previously each re-implemented the same per-item
 * try/catch scaffold: iterate ids, dispatch one action, catch
 * {@link RuntimeException}, shape a per-item result, count success/failure,
 * log. The shapes diverged (aggregated {@code dto.BulkActionResult} for
 * forum/comment, a nested record list for solution, {@code BulkProblemResultDTO}
 * for problem), so tests could not share an expectation and the rollback /
 * error-shaping policy quietly drifted per service.
 *
 * <p>This executor absorbs the loop, the try/catch boundary, the counting,
 * the logging, and an optional pre-batch existence guard behind one
 * canonical outcome type ({@link ItemOutcome}). Each service collapses to
 * a thin action switch that builds the per-item outcome; the executor does
 * everything else. New bulk actions add one {@link Consumer} rather than a
 * fresh loop.
 *
 * <p><strong>Contract preserved per service:</strong>
 * <ul>
 *   <li>A {@link RuntimeException} thrown by the action is recorded as a
 *       failure on that item; the loop continues to the next id (no
 *       whole-batch abort). This matches the historical per-item isolation
 *       of every call site.</li>
 *   <li>The optional {@code existenceGuard} short-circuits an id to a
 *       not-found outcome <em>before</em> the action runs, preserving the
 *       solution service's single-batched pre-check (BUG-Q4 perf fix).</li>
 * </ul>
 *
 * <p>The executor is intentionally agnostic of the action vocabulary
 * (delete / pin / publish / &hellip;) &mdash; that switch stays in the
 * caller, where the domain lives. Only the loop machinery is concentrated
 * here.
 *
 * @author ulticode
 */
@Slf4j
@Component
public final class AdminBulkExecutor {

    /**
     * Run {@code action} against every id, isolating failures per item.
     *
     * @param ids         the ids to act on (must be non-null)
     * @param actionLabel human-readable action label for log lines
     * @param action      the per-id action; may throw {@link RuntimeException}
     * @return the aggregated run with per-item outcomes and counts
     */
    public Run run(List<String> ids, String actionLabel, Consumer<String> action) {
        return run(ids, actionLabel, action, null);
    }

    /**
     * Run {@code action} against every id with an optional pre-batch
     * existence guard.
     *
     * <p>When {@code existenceGuard} is non-null and reports an id as
     * absent, that id is recorded as a not-found outcome and the action is
     * skipped &mdash; matching the solution service's BUG-Q4 single-query
     * pre-check without forcing every caller to pay for it.
     *
     * @param ids             the ids to act on (must be non-null)
     * @param actionLabel     human-readable action label for log lines
     * @param action          the per-id action; may throw {@link RuntimeException}
     * @param existenceGuard  optional predicate returning {@code true} when
     *                        the id exists; {@code null} disables the guard
     * @return the aggregated run with per-item outcomes and counts
     */
    public Run run(List<String> ids, String actionLabel, Consumer<String> action,
                   Predicate<String> existenceGuard) {
        Run run = new Run(ids.size());
        for (String id : ids) {
            if (existenceGuard != null && !existenceGuard.test(id)) {
                run.add(ItemOutcome.notFound(id));
                continue;
            }
            try {
                action.accept(id);
                run.add(ItemOutcome.success(id));
            } catch (RuntimeException e) {
                log.error("Failed to perform action {} on {}", actionLabel, id, e);
                run.add(ItemOutcome.failure(id, e.getMessage()));
            }
        }
        return run;
    }

    /**
     * Canonical per-item outcome of one bulk action.
     *
     * <p>One type for every call site &mdash; the thing tests can share an
     * expectation against regardless of which response DTO the service
     * ultimately shapes.
     */
    public record ItemOutcome(String id, boolean success, boolean notFound, String error) {

        /** Outcome for a successful action. */
        public static ItemOutcome success(String id) {
            return new ItemOutcome(id, true, false, null);
        }

        /** Outcome for a failed action, carrying the error message. */
        public static ItemOutcome failure(String id, String error) {
            return new ItemOutcome(id, false, false, error);
        }

        /** Outcome for an id that failed the existence guard. */
        public static ItemOutcome notFound(String id) {
            return new ItemOutcome(id, false, true, "Not found");
        }
    }

    /**
     * Aggregated result of one bulk run.
     */
    public static final class Run {

        private final int total;
        private final List<ItemOutcome> items;
        private int successful;
        private int failed;

        private Run(int total) {
            this.total = total;
            this.items = new ArrayList<>(total);
            this.successful = 0;
            this.failed = 0;
        }

        private void add(ItemOutcome outcome) {
            items.add(outcome);
            if (outcome.success()) {
                successful++;
            } else {
                failed++;
            }
        }

        /** Total number of ids submitted to the run. */
        public int total() {
            return total;
        }

        /** Number of ids whose action succeeded. */
        public int successful() {
            return successful;
        }

        /** Number of ids that failed (including not-found). */
        public int failed() {
            return failed;
        }

        /** Per-item outcomes, in input order. */
        public List<ItemOutcome> items() {
            return items;
        }
    }
}
