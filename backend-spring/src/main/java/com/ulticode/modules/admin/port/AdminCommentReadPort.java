package com.ulticode.modules.admin.port;

import java.util.Map;
import java.util.Set;

/**
 * Typed read port the admin module uses to enrich comment views with data
 * that lives behind the user / forum / solution modules.
 *
 * <p>Replaces the direct dependencies {@code AdminCommentServiceImpl} used to
 * have on {@code UserMapper}, {@code ForumPostMapper}, and
 * {@code SolutionMapper}. The admin comment list and detail pages need three
 * kinds of cross-module enrichment — author profile, parent forum-post title,
 * parent solution title — that are none of admin's business to query by raw
 * mapper. This port narrows that surface to three batched read methods; the
 * production adapter
 * ({@link com.ulticode.modules.admin.port.adapter.AdminCommentReadAdapter})
 * hides the three mappers and owns the empty-input short-circuit.
 *
 * <p>Third phase of the AdminReadModel seam (after {@link AdminSubmissionReadPort}
 * and {@link AdminUserStatsReadPort}). Returns typed views rather than raw
 * entities: {@link AuthorSummary} and title strings free
 * {@code AdminCommentServiceImpl} from importing the {@code User} /
 * {@code ForumPost} / {@code Solution} entities and re-implementing null
 * guards — that is the leverage this deep module buys. The deletion test
 * passes: deleting the port would force {@code AdminCommentServiceImpl} back
 * into reaching across to three mappers, three batch-load helpers, and four
 * per-field null guards in the VO assembly.
 *
 * <p>Only the read-side enrichment is owned by this port. Write-side comment
 * moderation (flag / unflag / delete) stays on {@code ForumCommentMapper} and
 * {@code SolutionCommentMapper} directly — those are admin's legitimate CRUD
 * targets, not cross-module leakage.
 *
 * @author ulticode
 */
public interface AdminCommentReadPort {

    /**
     * Minimal author profile needed to render an admin comment row.
     *
     * @param id       the user id
     * @param username the display name
     * @param avatar   the avatar url (may be {@code null})
     */
    record AuthorSummary(String id, String username, String avatar) {}

    /**
     * Batch-load author summaries for the given user ids.
     *
     * @param userIds the user ids to look up
     * @return map keyed by user id; ids with no matching user are absent from
     *         the map (callers coerce the missing case to a {@code null}
     *         author). Empty input returns an empty map without touching the
     *         mapper.
     */
    Map<String, AuthorSummary> findAuthorSummariesByIds(Set<String> userIds);

    /**
     * Batch-load forum-post titles for the given post ids.
     *
     * @param postIds the post ids to look up
     * @return map keyed by post id; a post whose title is null still appears
     *         with a {@code null} value (preserved rather than coerced, so the
     *         caller can distinguish "post missing" from "post has no title").
     *         Empty input returns an empty map without touching the mapper.
     */
    Map<String, String> findForumPostTitlesByIds(Set<String> postIds);

    /**
     * Batch-load solution titles for the given solution ids.
     *
     * @param solutionIds the solution ids to look up
     * @return map keyed by solution id; null titles are preserved (see
     *         {@link #findForumPostTitlesByIds(Set)}). Empty input returns an
     *         empty map without touching the mapper.
     */
    Map<String, String> findSolutionTitlesByIds(Set<String> solutionIds);
}
