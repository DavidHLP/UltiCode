package com.ulticode.modules.admin.projection;

import com.ulticode.app.api.dto.ContestAdminDTO;
import com.ulticode.common.response.PageResult;
import com.ulticode.modules.admin.dto.AdminContestQueryDTO;
import com.ulticode.modules.admin.dto.AdminContestVO;

/**
 * Read-side deep module for the admin contest management surface &mdash; owns
 * every owner-DTO-to-{@code AdminContestVO} projection rule, the paginated
 * list-query builder, the single-detail read and the contest-slug generator
 * that previously lived inline on
 * {@link com.ulticode.modules.admin.service.AdminContestService}.
 *
 * <p>Lifted out per ADR-0011 (admin projection series): a Stage 3 deepening
 * matching the {@code AdminSubmissionProjection} /
 * {@code AdminUserProjection} / {@code AdminForumProjection} /
 * {@code AdminSolutionProjection} shape. Before the extraction:
 * <ul>
 *   <li>The 494-line {@code AdminContestServiceImpl} mixed the write state
 *       machine (create / update / soft-delete / start / end /
 *       announcement CRUD / problem-add) with read-side concerns.</li>
 *   <li>{@code toAdminVO(ContestAdminDTO)} lived inside the service and was called by
 *       both reads and write paths &mdash; so every shape tweak had to land in
 *       the same file as the write state machine.</li>
 *   <li>{@code generateSlug(String)} (URL-friendly contest slug) sat next to
 *       the announcement CRUD &mdash; with no obvious locality.</li>
 *   <li>The owner-contract read that powers the {@code problemCount} field on
 *       the VO leaked into the orchestration service.</li>
 * </ul>
 *
 * <p>After the deepening:
 * <ul>
 *   <li>{@link com.ulticode.modules.admin.service.AdminContestService} remains
 *       the read facade, while {@code ContestCutoverService} owns the write
 *       commands and their App-owner contract. This projection owns the
 *       admin read shape and its {@code problemCount} enrichment.</li>
 *   <li>Future admins or port-driven consumers depend on this projection for
 *       reads &mdash; mirroring the AdminSubmissionProjection /
 *       -ModerationProjection pattern documented in the deep-modules index of
 *       {@code backend-spring/AGENTS.md}.</li>
 * </ul>
 *
 * <p>The owner-contract read for {@code problemCount} lives behind this seam;
 * the orchestration service no longer reaches into App-private mappers.
 *
 * @author ulticode
 * @see com.ulticode.modules.admin.projection.AdminSubmissionProjection
 * @see com.ulticode.modules.admin.projection.ProblemListProjection
 */
public interface AdminContestProjection {

    /**
     * Get a paginated list of contests with filters (search by title or slug,
     * contest type, status) and sorting (title / startTime / createdAt /
     * updatedAt). The resulting {@code AdminContestVO}s carry the
     * problem-count enrichment already computed.
     *
     * @param query query parameters including filters, pagination, and sorting
     * @return paginated result of admin contest VOs (list-view shape)
     */
    PageResult<AdminContestVO> getContests(AdminContestQueryDTO query);

    /**
     * Get a single contest by ID with the admin-list shape ({@code problemCount}
     * included).
     *
     * @param id contest ID
     * @return admin contest VO
     * @throws com.ulticode.common.exception.BusinessException with
     *         {@link com.ulticode.admin.error.AdminErrorCode#CONTEST_NOT_FOUND}
     *         when the contest does not exist
     */
    AdminContestVO getContest(String id);

    /**
     * Project an owner {@link ContestAdminDTO} to the admin-side
     * {@link AdminContestVO} shape, including the derived
     * {@code problemCount} field. The implementation may use the owner read
     * contract for that derived field. Used by both projection read paths and write paths
     * that return the resulting contest as a VO.
     *
     * @param contest source owner DTO (may be {@code null})
     * @return projected admin contest VO, or {@code null} when the input is
     *         {@code null}
     */
    AdminContestVO toAdminVO(ContestAdminDTO contest);

    /**
     * Generate a URL-friendly slug from a contest title. Falls back to a
     * random {@code contest-<8 hex>} identifier when the sanitised string is
     * shorter than three characters or the title is {@code null}/blank,
     * matching the original implementation carried by
     * {@code AdminContestServiceImpl}.
     *
     * @param title human-readable contest title
     * @return URL-friendly slug suitable for use as the contest's
     *         {@code slug} column
     */
    String generateSlug(String title);
}
