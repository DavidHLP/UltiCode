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
 * the logging, and the per-id existence check behind one canonical
 * outcome type ({@link ItemOutcome}). Each service collapses to a thin
 * action switch that builds the per-item outcome; the executor does
 * everything else. New bulk actions add one {@link Consumer} rather than
 * a fresh loop.
 *
 * <p><strong>Contract preserved per service:</strong>
 * <ul>
 *   <li>A {@link RuntimeException} thrown by the action is recorded as a
 *       {@link ItemOutcome.Failure} on that item; the loop continues to
 *       the next id (no whole-batch abort). This matches the historical
 *       per-item isolation of every call site.</li>
 *   <li>The {@code existenceGuard} is mandatory. Callers that have not
 *       pre-fetched an existing-ids set pass {@code id -> true}; the
 *       solution service passes its single-batched pre-check (BUG-Q4
 *       perf fix) so non-existent ids short-circuit to a
 *       {@link ItemOutcome.NotFound} without running the action.</li>
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
     * @param ids             the ids to act on (must be non-null)
     * @param actionLabel     human-readable action label for log lines
     * @param action          the per-id action; may throw {@link RuntimeException}
     * @param existenceGuard  predicate returning {@code true} when the id
     *                        exists; pass {@code id -> true} when no
     *                        pre-check is needed
     * @return the aggregated run with per-item outcomes and counts
     */
    public Run run(List<String> ids, String actionLabel, Consumer<String> action,
                   Predicate<String> existenceGuard) {
        Run run = new Run(ids.size());
        for (String id : ids) {
            if (!existenceGuard.test(id)) {
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
     * Canonical per-item outcome of one bulk action. Sealed hierarchy
     * expresses the three terminal states the loop can record; the
     * previous record-with-booleans shape leaked the "what does a
     * not-found look like" question into every call site.
     */
    public sealed interface ItemOutcome permits Success, NotFound, Failure {

        /** The id this outcome describes. */
        String id();

        /** Whether the action succeeded. */
        default boolean isSuccess() {
            return this instanceof Success;
        }

        /**
         * Error message for a failed outcome, or {@code null} for
         * success / not-found. Callers shaping DTOs use this to avoid
         * the instanceof-cascade on every item.
         */
        default String errorOrNull() {
            return this instanceof Failure f ? f.error() : null;
        }

        /** Outcome for a successful action. */
        static ItemOutcome success(String id) {
            return new Success(id);
        }

        /** Outcome for an id that failed the existence guard. */
        static ItemOutcome notFound(String id) {
            return new NotFound(id);
        }

        /** Outcome for a failed action, carrying the error message. */
        static ItemOutcome failure(String id, String error) {
            return new Failure(id, error);
        }
    }

    /** Successful per-item outcome. */
    public record Success(String id) implements ItemOutcome { }

    /** Per-item outcome for an id that the existence guard rejected. */
    public record NotFound(String id) implements ItemOutcome { }

    /** Per-item outcome for an action that threw. */
    public record Failure(String id, String error) implements ItemOutcome { }

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
            if (outcome instanceof Success) {
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
