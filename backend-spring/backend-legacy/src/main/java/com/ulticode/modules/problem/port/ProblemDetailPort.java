package com.ulticode.modules.problem.port;

import com.ulticode.modules.problem.dto.UpdateProblemDTO;
import com.ulticode.modules.problem.entity.Problem;

/**
 * Write surface for the {@code problem_details} row and its satellite tables
 * ({@code problem_languages}, {@code problem_examples},
 * {@code problem_tag_relations}).
 *
 * <p>Extracted from {@code ProblemServiceImpl.updateProblemDetail}. The problem
 * state-machine owns the {@code problems} row (slug, title, difficulty, publish
 * state) and delegates every detail-satellite mutation here so the 111-LOC
 * field-conditional upsert + delete-and-rebuild logic lives behind a narrow
 * seam instead of inside the service body.
 *
 * <p>Why a separate module and not "a private helper":
 * <ul>
 *   <li><b>Locality</b>: the four satellite writes (detail upsert, language
 *       rebuild, example rebuild, tag rebuild) share the same
 *       delete-then-batch-insert shape and the same
 *       "skip when the DTO section is null" guard. Keeping them next to the
 *       problem state machine made {@code updateProblem} read as a wall of
 *       mapper calls; they are now concentrated here.</li>
 *   <li><b>Leverage</b>: the rebuild-on-update policy is the same one a future
 *       admin detail-write path or an import-with-detail path would need;
 *       sharing inside one module beats duplicating across call sites.</li>
 *   <li><b>Interface is the test surface</b>: the four branches can now be
 *       exercised with mapper mocks directly, without standing up the
 *       {@code ProblemServiceImpl} collaborators ({@code ProblemMapper},
 *       {@code ProblemVersionService}, {@code ProblemProjection}) just to reach
 *       the detail-write path.</li>
 * </ul>
 *
 * <p>Dependency category: <b>in-process</b>. The seam is real because the
 * problem state machine is the only writer today; the default adapter is the
 * only provider. Tests can substitute a fake.
 *
 * <p>The {@code Problem} entity is passed (not just its id) so the adapter can
 * denormalize {@code problem.slug} onto a newly-created {@code ProblemDetail}
 * row — {@code problem_details.slug} is NOT NULL in the schema.
 *
 * @author ulticode
 */
public interface ProblemDetailPort {

    /**
     * Apply a batch of detail-satellite updates for one problem inside a single
     * transaction. Each null section of {@code updateDTO} is a no-op:
     * <ul>
     *   <li>summary / content / constraintsJson / hints → upsert
     *       {@code ProblemDetail} (create-and-denormalize-slug if missing);</li>
     *   <li>languages → delete + rebuild {@code ProblemLanguage} rows from the
     *       template registry, validating each value;</li>
     *   <li>examples → delete + rebuild {@code ProblemExample} rows from the
     *       JSON payload;</li>
     *   <li>tags → delete + rebuild {@code ProblemTagRelation} rows, validating
     *       each label exists in {@code problem_tags}.</li>
     * </ul>
     * No-op (early return) when none of the five sections is present.
     *
     * @param problemId the problem id whose detail satellites to update
     * @param problem   the problem entity (used to denormalize slug onto a new
     *                  {@code ProblemDetail} row); never {@code null}
     * @param updateDTO the update payload; never {@code null}
     */
    void applyDetailUpdate(Long problemId, Problem problem, UpdateProblemDTO updateDTO);
}
