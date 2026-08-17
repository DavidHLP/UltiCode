package com.ulticode.modules.admin.service.impl;

import com.ulticode.admin.error.AdminErrorCode;
import com.ulticode.common.command.ActorDelegation;
import com.ulticode.app.api.command.CreateProblemListCommand;
import com.ulticode.app.api.command.DeleteProblemListCommand;
import com.ulticode.app.api.command.ReplaceListProblemsCommand;
import com.ulticode.app.api.command.UpdateBannerCommand;
import com.ulticode.app.api.command.UpdateBasicInfoCommand;
import com.ulticode.app.api.command.UpdateProblemListCommand;
import com.ulticode.app.api.command.UpdateVisibilityCommand;
import com.ulticode.app.api.dto.ProblemListDetailDTO;
import com.ulticode.app.api.dto.ProblemListSummaryDTO;
import com.ulticode.app.api.service.ProblemListAdministrationService;
import com.ulticode.app.api.service.ProblemListChainReadPort;
import com.ulticode.common.annotation.Audited;
import com.ulticode.common.audit.AuditVocabulary;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.response.PageResult;
import com.ulticode.common.rpc.RpcResult;
import com.ulticode.common.tracing.IdMetadata;
import com.ulticode.common.tracing.TraceMetadata;
import com.ulticode.common.util.AuditContext;
import com.ulticode.common.util.TraceIdUtil;
import com.ulticode.modules.admin.dto.AdminProblemListQueryDTO;
import com.ulticode.modules.admin.dto.CreateProblemListRequest;
import com.ulticode.modules.admin.dto.UpdateBannerRequest;
import com.ulticode.modules.admin.dto.UpdateBasicInfoRequest;
import com.ulticode.modules.admin.dto.UpdateProblemListRequest;
import com.ulticode.modules.admin.dto.UpdateProblemsRequest;
import com.ulticode.modules.admin.dto.UpdateVisibilityRequest;
import com.ulticode.modules.admin.projection.AdminProblemListProjection;
import com.ulticode.modules.admin.service.AdminProblemListService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Implementation of {@link com.ulticode.modules.admin.service.AdminProblemListService}.
 *
 * <p>ADMIN-005 (P7-RELOCATE-PROBLEMLIST-001): the Admin BFF no longer
 * imports the App-private problem-list module. Reads delegate to the
 * admin projection (backed by {@code ProblemListSearchReadPort} /
 * {@code ProblemListChainReadPort} Dubbo providers); writes issue
 * {@code WriteCommand}s against the {@code ProblemListAdministrationService}
 * Dubbo provider carrying commandId / idempotency / actor / trace
 * metadata and map {@link RpcResult} failures onto admin error codes.
 * No local transaction wraps a remote call.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminProblemListServiceImpl implements AdminProblemListService {

    private final ProblemListAdministrationService problemListAdministrationService;
    private final ProblemListChainReadPort problemListChainReadPort;
    private final AdminProblemListProjection adminProblemListProjection;

    @Override
    public PageResult<ProblemListSummaryDTO> getProblemLists(AdminProblemListQueryDTO query) {
        // Page-walked read + filter-wrapper + entity→VO projection live in
        // AdminProblemListProjection.findAdminLists; the admin service owns
        // only audit context around the call, not the page-assembly mechanics.
        return adminProblemListProjection.findAdminLists(query);
    }

    @Override
    public ProblemListDetailDTO getProblemList(String id) {
        // Intent-level admin detail read: the projection owns the remote
        // chain read (404 on missing) + admin-detail shaping.
        return adminProblemListProjection.getAdminListDetail(id);
    }

    @Override
    public ProblemListSummaryDTO createProblemList(CreateProblemListRequest dto, String authorId) {
        return createProblemList(dto, authorId, null);
    }

    public ProblemListSummaryDTO createProblemList(
            CreateProblemListRequest dto, String authorId, String requestedKey) {
        IdMetadata idempotency = idempotency(requestedKey);
        RpcResult<ProblemListSummaryDTO> result = problemListAdministrationService.createProblemList(
                new CreateProblemListCommand(
                        commandId("create", idempotency), idempotency,
                        new ActorDelegation("ADMIN", authorId, authorId, "admin create problem list"),
                        currentTrace(),
                        dto.getName(), dto.getDescription(), dto.getIsPublic(),
                        dto.getBannerTag(), dto.getBannerIcon(), dto.getBannerTheme(), dto.getBannerOrder()));
        return requireSuccess(result);
    }

    @Override
    @Audited(action = AuditVocabulary.UPDATE_PROBLEM_LIST, entityType = AuditVocabulary.ENTITY_PROBLEM_LIST, userIdFrom = "userId")
    public ProblemListSummaryDTO updateProblemList(
            String id, UpdateProblemListRequest dto, String userId) {
        return updateProblemList(id, dto, userId, null);
    }

    @Audited(action = AuditVocabulary.UPDATE_PROBLEM_LIST, entityType = AuditVocabulary.ENTITY_PROBLEM_LIST, userIdFrom = "userId")
    public ProblemListSummaryDTO updateProblemList(
            String id, UpdateProblemListRequest dto, String userId, String requestedKey) {
        ProblemListSummaryDTO old = preflightList(id, requestedKey);
        if (old != null) {
            AuditContext.setOldValues(oldSnapshot(old));
        }

        IdMetadata idempotency = idempotency(requestedKey);
        RpcResult<ProblemListSummaryDTO> result = problemListAdministrationService.updateProblemList(
                new UpdateProblemListCommand(
                        commandId("update", idempotency), idempotency,
                        new ActorDelegation("ADMIN", userId, userId, "admin update problem list"),
                        currentTrace(), id,
                        dto.getName(), dto.getDescription(), dto.getIsPublic(), dto.getIsFeatured(),
                        dto.getBannerTag(), dto.getBannerIcon(), dto.getBannerTheme(), dto.getBannerOrder()));
        ProblemListSummaryDTO vo = requireSuccess(result);
        AuditContext.setNewValues(newSnapshot(vo));
        return vo;
    }

    @Override
    @Audited(action = AuditVocabulary.DELETE_PROBLEM_LIST, entityType = AuditVocabulary.ENTITY_PROBLEM_LIST, userIdFrom = "userId", entityIdFrom = "id")
    public void deleteProblemList(String id, String userId) {
        deleteProblemList(id, userId, null);
    }

    @Audited(action = AuditVocabulary.DELETE_PROBLEM_LIST, entityType = AuditVocabulary.ENTITY_PROBLEM_LIST, userIdFrom = "userId", entityIdFrom = "id")
    public void deleteProblemList(String id, String userId, String requestedKey) {
        ProblemListSummaryDTO old = preflightList(id, requestedKey);
        if (old != null) {
            AuditContext.setOldValues(deleteSnapshot(old));
        }

        IdMetadata idempotency = idempotency(requestedKey);
        RpcResult<Void> result = problemListAdministrationService.deleteProblemList(
                new DeleteProblemListCommand(
                        commandId("delete", idempotency), idempotency,
                        new ActorDelegation("ADMIN", userId, userId, "admin delete problem list"),
                        currentTrace(), id));
        requireSuccess(result);
    }

    @Override
    @Audited(action = AuditVocabulary.UPDATE_PROBLEM_LIST, entityType = AuditVocabulary.ENTITY_PROBLEM_LIST, userIdFrom = "userId", entityIdFrom = "id")
    public void updateListProblems(String id, UpdateProblemsRequest dto, String userId) {
        updateListProblems(id, dto, userId, null);
    }

    @Audited(action = AuditVocabulary.UPDATE_PROBLEM_LIST, entityType = AuditVocabulary.ENTITY_PROBLEM_LIST, userIdFrom = "userId", entityIdFrom = "id")
    public void updateListProblems(
            String id, UpdateProblemsRequest dto, String userId, String requestedKey) {
        preflightList(id, requestedKey);
        if (dto.getProblems() == null) {
            throw new BusinessException(AdminErrorCode.VALIDATION_FAILED, "Problems list is required");
        }

        List<ReplaceListProblemsCommand.ProblemEntry> entries = new ArrayList<>(dto.getProblems().size());
        for (UpdateProblemsRequest.ProblemEntry entry : dto.getProblems()) {
            if (entry == null) {
                throw new BusinessException(AdminErrorCode.VALIDATION_FAILED, "Problem entry is required");
            }
            entries.add(new ReplaceListProblemsCommand.ProblemEntry(entry.getProblemId(), entry.getSortOrder()));
        }
        IdMetadata idempotency = idempotency(requestedKey);
        RpcResult<Void> result = problemListAdministrationService.replaceListProblems(
                new ReplaceListProblemsCommand(
                        commandId("replace-problems", idempotency), idempotency,
                        new ActorDelegation("ADMIN", userId, userId, "admin replace list problems"),
                        currentTrace(), id, entries));
        requireSuccess(result);
        AuditContext.setNewValues(Map.of("updatedProblems", dto.getProblems().size()));
    }

    @Override
    @Audited(action = AuditVocabulary.UPDATE_PROBLEM_LIST, entityType = AuditVocabulary.ENTITY_PROBLEM_LIST, userIdFrom = "userId")
    public ProblemListSummaryDTO updateBasicInfo(
            String id, String userId, UpdateBasicInfoRequest dto) {
        return updateBasicInfo(id, userId, dto, null);
    }

    @Audited(action = AuditVocabulary.UPDATE_PROBLEM_LIST, entityType = AuditVocabulary.ENTITY_PROBLEM_LIST, userIdFrom = "userId")
    public ProblemListSummaryDTO updateBasicInfo(
            String id, String userId, UpdateBasicInfoRequest dto, String requestedKey) {
        ProblemListSummaryDTO old = preflightList(id, requestedKey);
        if (old != null) {
            Map<String, Object> oldValues = new HashMap<>();
            oldValues.put("name", old.getName() != null ? old.getName() : "");
            oldValues.put("description", old.getDescription() != null ? old.getDescription() : "");
            AuditContext.setOldValues(oldValues);
        }

        IdMetadata idempotency = idempotency(requestedKey);
        RpcResult<ProblemListSummaryDTO> result = problemListAdministrationService.updateBasicInfo(
                new UpdateBasicInfoCommand(
                        commandId("basic-info", idempotency), idempotency,
                        new ActorDelegation("ADMIN", userId, userId, "admin update basic info"),
                        currentTrace(), id, dto.getName(), dto.getDescription()));
        ProblemListSummaryDTO vo = requireSuccess(result);

        Map<String, Object> newValues = new HashMap<>();
        newValues.put("name", vo.getName() != null ? vo.getName() : "");
        newValues.put("description", vo.getDescription() != null ? vo.getDescription() : "");
        AuditContext.setNewValues(newValues);
        return vo;
    }

    @Override
    @Audited(action = AuditVocabulary.UPDATE_PROBLEM_LIST, entityType = AuditVocabulary.ENTITY_PROBLEM_LIST, userIdFrom = "userId")
    public ProblemListSummaryDTO updateVisibility(
            String id, String userId, UpdateVisibilityRequest dto) {
        return updateVisibility(id, userId, dto, null);
    }

    @Audited(action = AuditVocabulary.UPDATE_PROBLEM_LIST, entityType = AuditVocabulary.ENTITY_PROBLEM_LIST, userIdFrom = "userId")
    public ProblemListSummaryDTO updateVisibility(
            String id, String userId, UpdateVisibilityRequest dto, String requestedKey) {
        ProblemListSummaryDTO old = preflightList(id, requestedKey);
        if (old != null) {
            Map<String, Object> oldValues = new HashMap<>();
            oldValues.put("isPublic", old.getIsPublic() != null ? old.getIsPublic() : false);
            oldValues.put("isFeatured", old.getIsFeatured() != null ? old.getIsFeatured() : false);
            AuditContext.setOldValues(oldValues);
        }

        IdMetadata idempotency = idempotency(requestedKey);
        RpcResult<ProblemListSummaryDTO> result = problemListAdministrationService.updateVisibility(
                new UpdateVisibilityCommand(
                        commandId("visibility", idempotency), idempotency,
                        new ActorDelegation("ADMIN", userId, userId, "admin update visibility"),
                        currentTrace(), id, dto.getIsPublic(), dto.getIsFeatured()));
        ProblemListSummaryDTO vo = requireSuccess(result);

        Map<String, Object> newValues = new HashMap<>();
        newValues.put("isPublic", vo.getIsPublic() != null ? vo.getIsPublic() : false);
        newValues.put("isFeatured", vo.getIsFeatured() != null ? vo.getIsFeatured() : false);
        AuditContext.setNewValues(newValues);
        return vo;
    }

    @Override
    @Audited(action = AuditVocabulary.UPDATE_PROBLEM_LIST, entityType = AuditVocabulary.ENTITY_PROBLEM_LIST, userIdFrom = "userId")
    public ProblemListSummaryDTO updateBanner(
            String id, String userId, UpdateBannerRequest dto) {
        return updateBanner(id, userId, dto, null);
    }

    @Audited(action = AuditVocabulary.UPDATE_PROBLEM_LIST, entityType = AuditVocabulary.ENTITY_PROBLEM_LIST, userIdFrom = "userId")
    public ProblemListSummaryDTO updateBanner(
            String id, String userId, UpdateBannerRequest dto, String requestedKey) {
        ProblemListSummaryDTO old = preflightList(id, requestedKey);
        if (old != null) {
            Map<String, Object> oldValues = new HashMap<>();
            oldValues.put("bannerTag", old.getBannerTag() != null ? old.getBannerTag() : "");
            oldValues.put("bannerIcon", old.getBannerIcon() != null ? old.getBannerIcon() : "");
            oldValues.put("bannerTheme", old.getBannerTheme() != null ? old.getBannerTheme() : "");
            oldValues.put("bannerOrder", old.getBannerOrder() != null ? old.getBannerOrder() : 0);
            AuditContext.setOldValues(oldValues);
        }

        IdMetadata idempotency = idempotency(requestedKey);
        RpcResult<ProblemListSummaryDTO> result = problemListAdministrationService.updateBanner(
                new UpdateBannerCommand(
                        commandId("banner", idempotency), idempotency,
                        new ActorDelegation("ADMIN", userId, userId, "admin update banner"),
                        currentTrace(), id,
                        dto.getBannerTag(), dto.getBannerIcon(), dto.getBannerTheme(), dto.getBannerOrder()));
        ProblemListSummaryDTO vo = requireSuccess(result);

        Map<String, Object> newValues = new HashMap<>();
        newValues.put("bannerTag", vo.getBannerTag() != null ? vo.getBannerTag() : "");
        newValues.put("bannerIcon", vo.getBannerIcon() != null ? vo.getBannerIcon() : "");
        newValues.put("bannerTheme", vo.getBannerTheme() != null ? vo.getBannerTheme() : "");
        newValues.put("bannerOrder", vo.getBannerOrder() != null ? vo.getBannerOrder() : 0);
        AuditContext.setNewValues(newValues);
        return vo;
    }

    // ── helpers ────────────────────────────────────────────────

    /**
     * Propagate the current request trace id onto every write command,
     * mirroring {@code UserManagementServiceImpl.currentTrace()} /
     * {@code AdminUserProfileAdapter.trace()}: use the request-scoped
     * {@code TraceIdUtil.current()} value, falling back to a fresh
     * {@code t-<uuid>} so the RPC envelope never carries a null traceId.
     */
    private static TraceMetadata currentTrace() {
        String reqId = TraceIdUtil.current();
        if (reqId == null || reqId.isBlank()) {
            reqId = "t-" + UUID.randomUUID();
        }
        return new TraceMetadata(reqId, null, null, null);
    }

    /**
     * Load the pre-state summary via the remote chain read; maps a
     * missing list to the admin 404 error, preserving the legacy
     * {@code findEntityById} failure semantics.
     */
    private ProblemListSummaryDTO requireList(String id) {
        ProblemListSummaryDTO list = problemListChainReadPort.findSummary(id);
        if (list == null) {
            throw new BusinessException(AdminErrorCode.PROBLEM_LIST_NOT_FOUND);
        }
        return list;
    }

    private ProblemListSummaryDTO preflightList(String id, String requestedKey) {
        try {
            return requireList(id);
        } catch (BusinessException e) {
            if (requestedKey != null
                    && !requestedKey.isBlank()
                    && e.getErrorCode() == AdminErrorCode.PROBLEM_LIST_NOT_FOUND) {
                return null;
            }
            throw e;
        }
    }

    private static <T> T requireSuccess(RpcResult<T> result) {
        if (result.success()) {
            return result.data();
        }
        var err = result.error();
        if (err == null) {
            throw new BusinessException(AdminErrorCode.UNKNOWN_ERROR, "RPC failed without error payload");
        }
        if (err.code() == 40401) {
            throw new BusinessException(AdminErrorCode.PROBLEM_LIST_NOT_FOUND, err.message());
        }
        if (err.code() == 30001) {
            throw new BusinessException(AdminErrorCode.PROBLEM_NOT_FOUND, err.message());
        }
        if (err.code() == 90004) {
            throw new BusinessException(AdminErrorCode.PROBLEM_LIST_PROBLEM_DUPLICATE, err.message());
        }
        if (err.code() == 40300) {
            throw new BusinessException(AdminErrorCode.FORBIDDEN, err.message());
        }
        if (err.code() == 40901 || err.code() == 40902 || err.code() == 40903) {
            throw new BusinessException(AdminErrorCode.CONFLICT, err.message());
        }
        if (err.code() == 40000 || err.code() == 49999) {
            throw new BusinessException(AdminErrorCode.VALIDATION_FAILED, err.message());
        }
        throw new BusinessException(AdminErrorCode.UNKNOWN_ERROR, err.message());
    }

    private static IdMetadata idempotency(String requestedKey) {
        if (requestedKey == null || requestedKey.isBlank()) {
            return IdMetadata.mint();
        }
        String key = requestedKey.trim();
        if (key.length() > 120) {
            throw new BusinessException(AdminErrorCode.VALIDATION_FAILED,
                    "Idempotency-Key must not exceed 120 characters");
        }
        return IdMetadata.of(key, null);
    }

    private static String commandId(String operation, IdMetadata idempotency) {
        return UUID.nameUUIDFromBytes(
                ("admin-problem-list:" + operation + ":" + idempotency.idempotencyKey())
                        .getBytes(StandardCharsets.UTF_8))
                .toString();
    }

    private static Map<String, Object> oldSnapshot(ProblemListSummaryDTO list) {
        Map<String, Object> values = new HashMap<>();
        values.put("name", list.getName() != null ? list.getName() : "");
        values.put("description", list.getDescription() != null ? list.getDescription() : "");
        values.put("isPublic", list.getIsPublic() != null ? list.getIsPublic() : false);
        values.put("isFeatured", list.getIsFeatured() != null ? list.getIsFeatured() : false);
        values.put("bannerTag", list.getBannerTag() != null ? list.getBannerTag() : "");
        values.put("bannerIcon", list.getBannerIcon() != null ? list.getBannerIcon() : "");
        values.put("bannerOrder", list.getBannerOrder() != null ? list.getBannerOrder() : 0);
        return values;
    }

    private static Map<String, Object> newSnapshot(ProblemListSummaryDTO vo) {
        Map<String, Object> values = new HashMap<>();
        values.put("name", vo.getName() != null ? vo.getName() : "");
        values.put("description", vo.getDescription() != null ? vo.getDescription() : "");
        values.put("isPublic", vo.getIsPublic() != null ? vo.getIsPublic() : false);
        values.put("isFeatured", vo.getIsFeatured() != null ? vo.getIsFeatured() : false);
        values.put("bannerTag", vo.getBannerTag() != null ? vo.getBannerTag() : "");
        values.put("bannerIcon", vo.getBannerIcon() != null ? vo.getBannerIcon() : "");
        values.put("bannerTheme", vo.getBannerTheme() != null ? vo.getBannerTheme() : "");
        values.put("bannerOrder", vo.getBannerOrder() != null ? vo.getBannerOrder() : 0);
        return values;
    }

    private static Map<String, Object> deleteSnapshot(ProblemListSummaryDTO list) {
        Map<String, Object> values = new HashMap<>();
        values.put("name", list.getName() != null ? list.getName() : "");
        values.put("authorId", list.getAuthorId() != null ? list.getAuthorId() : "");
        return values;
    }
}
