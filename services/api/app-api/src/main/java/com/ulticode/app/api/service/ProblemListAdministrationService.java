package com.ulticode.app.api.service;

import com.ulticode.app.api.command.CreateProblemListCommand;
import com.ulticode.app.api.command.DeleteProblemListCommand;
import com.ulticode.app.api.command.ReplaceListProblemsCommand;
import com.ulticode.app.api.command.UpdateBannerCommand;
import com.ulticode.app.api.command.UpdateBasicInfoCommand;
import com.ulticode.app.api.command.UpdateProblemListCommand;
import com.ulticode.app.api.command.UpdateVisibilityCommand;
import com.ulticode.app.api.dto.ProblemListSummaryDTO;
import com.ulticode.common.rpc.RpcResult;

/**
 * App-owned administrative provider for problem-list lifecycle operations.
 *
 * <p>The {@code problem_lists} and {@code problem_list_problem_relations}
 * tables are App-owned per {@code TABLE_OWNERS.md}; the Admin BFF must
 * route writes through this contract so App is the sole writer. The
 * provider delegates to the in-module
 * {@code ProblemListService}/{@code ProblemListAdminService} seams and
 * maps failures onto app-api error codes.
 *
 * <p>This interface is contract-only; no ServiceImpl lives in this
 * module. The provider implementation belongs to {@code backend-app}.
 */
public interface ProblemListAdministrationService {

    /**
     * Create a problem list owned by the command's actor.
     *
     * @param command carries commandId, idempotency, actor, trace, and
     *                the list fields (name required)
     * @return success with the created {@link ProblemListSummaryDTO};
     *         failure with {@code BAD_REQUEST} when name is blank
     */
    RpcResult<ProblemListSummaryDTO> createProblemList(CreateProblemListCommand command);

    /**
     * Partially update a problem list; null command fields are unchanged.
     *
     * @param command carries commandId, idempotency, actor, trace, the
     *                listId, and the new field values
     * @return success with the updated {@link ProblemListSummaryDTO};
     *         failure with {@code CONTENT_NOT_FOUND} when the list is unknown
     */
    RpcResult<ProblemListSummaryDTO> updateProblemList(UpdateProblemListCommand command);

    /**
     * Delete a problem list and all its problem relations.
     *
     * @param command carries commandId, idempotency, actor, trace, and the listId
     * @return success (void); failure with {@code CONTENT_NOT_FOUND} when unknown
     */
    RpcResult<Void> deleteProblemList(DeleteProblemListCommand command);

    /**
     * Update the name/description of a problem list.
     *
     * @param command carries commandId, idempotency, actor, trace, the
     *                listId, and the new name/description
     * @return success with the updated {@link ProblemListSummaryDTO};
     *         failure with {@code CONTENT_NOT_FOUND} when unknown
     */
    RpcResult<ProblemListSummaryDTO> updateBasicInfo(UpdateBasicInfoCommand command);

    /**
     * Update the public/featured flags of a problem list.
     *
     * @param command carries commandId, idempotency, actor, trace, the
     *                listId, and the new flags
     * @return success with the updated {@link ProblemListSummaryDTO};
     *         failure with {@code CONTENT_NOT_FOUND} when unknown
     */
    RpcResult<ProblemListSummaryDTO> updateVisibility(UpdateVisibilityCommand command);

    /**
     * Update the banner settings of a problem list.
     *
     * @param command carries commandId, idempotency, actor, trace, the
     *                listId, and the new banner fields
     * @return success with the updated {@link ProblemListSummaryDTO};
     *         failure with {@code CONTENT_NOT_FOUND} when unknown
     */
    RpcResult<ProblemListSummaryDTO> updateBanner(UpdateBannerCommand command);

    /**
     * Fully replace the problem set of a problem list. An empty problems
     * list clears all relations.
     *
     * @param command carries commandId, idempotency, actor, trace, the
     *                listId, and the new ordered problem entries
     * @return success (void); failure with {@code CONTENT_NOT_FOUND} when
     *         the list is unknown, {@code PROBLEM_NOT_FOUND} when a
     *         referenced Problem is missing, or
     *         {@code PROBLEM_LIST_PROBLEM_DUPLICATE} for duplicate entries
     */
    RpcResult<Void> replaceListProblems(ReplaceListProblemsCommand command);
}
