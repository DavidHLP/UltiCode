package com.ulticode.app.dubbo.provider;

import com.ulticode.common.command.ActorDelegation;
import com.ulticode.app.api.command.CreateProblemListCommand;
import com.ulticode.app.api.command.DeleteProblemListCommand;
import com.ulticode.app.api.command.ReplaceListProblemsCommand;
import com.ulticode.app.api.command.UpdateBannerCommand;
import com.ulticode.app.api.command.UpdateBasicInfoCommand;
import com.ulticode.app.api.command.UpdateProblemListCommand;
import com.ulticode.app.api.command.UpdateVisibilityCommand;
import com.ulticode.app.api.dto.ProblemListSummaryDTO;
import com.ulticode.app.api.error.AppErrorCode;
import com.ulticode.common.command.WriteCommand;
import com.ulticode.app.api.service.ProblemListAdministrationService;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.app.idempotency.CommandReceiptExecutor;
import com.ulticode.common.rpc.RpcResult;
import com.ulticode.modules.problemlist.dto.CreateProblemListDTO;
import com.ulticode.modules.problemlist.dto.UpdateBannerDTO;
import com.ulticode.modules.problemlist.dto.UpdateBasicInfoDTO;
import com.ulticode.modules.problemlist.dto.UpdateProblemListDTO;
import com.ulticode.modules.problemlist.dto.UpdateProblemListProblemsDTO;
import com.ulticode.modules.problemlist.dto.UpdateVisibilityDTO;
import com.ulticode.modules.problemlist.service.ProblemListAdminService;
import com.ulticode.modules.problemlist.service.ProblemListService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboService;

import java.util.ArrayList;
import java.util.List;

/**
 * Dubbo Provider implementation of {@link ProblemListAdministrationService}
 * in {@code backend-app}.
 *
 * <p>Delegates to the in-module {@link ProblemListService} (create) and
 * {@link ProblemListAdminService} (admin-bypass mutations) for canonical
 * write-side domain logic, translating the RPC command payloads onto the
 * private module DTOs. Failures map onto app-api error codes:
 * missing list &rarr; {@link AppErrorCode#CONTENT_NOT_FOUND}, missing
 * referenced problem &rarr; {@link AppErrorCode#PROBLEM_NOT_FOUND}, duplicate
 * problem &rarr; {@link AppErrorCode#PROBLEM_LIST_PROBLEM_DUPLICATE},
 * validation &rarr; {@link AppErrorCode#BAD_REQUEST}, anything else &rarr;
 * {@link AppErrorCode#UNEXPECTED_APP_STATE}.
 */
@Slf4j
@DubboService(group = "backend-app", version = "1.0.0")
@RequiredArgsConstructor
public class ProblemListAdministrationProvider implements ProblemListAdministrationService {

    private static final String SERVICE = CommandReceiptExecutor.problemListService();

    private final ProblemListService problemListService;
    private final ProblemListAdminService problemListAdminService;
    private final CommandReceiptExecutor receiptExecutor;

    @Override
    public RpcResult<ProblemListSummaryDTO> createProblemList(CreateProblemListCommand command) {
        RpcResult<ProblemListSummaryDTO> rejected = rejectIfNotAdmin(command);
        if (rejected != null) {
            return rejected;
        }
        return execute("createProblemList", command, ProblemListSummaryDTO.class,
                traceId -> create(command, traceId));
    }

    private RpcResult<ProblemListSummaryDTO> create(
            CreateProblemListCommand command, String traceId) {
        log.info("ProblemListAdministrationProvider.createProblemList name={} commandId={} actor={}",
                command.name(), command.commandId(), command.actor().actorId());
        CreateProblemListDTO dto = new CreateProblemListDTO();
        dto.setName(command.name());
        dto.setDescription(command.description());
        dto.setIsPublic(command.isPublic() != null ? command.isPublic() : false);
        dto.setBannerTag(command.bannerTag());
        dto.setBannerIcon(command.bannerIcon());
        dto.setBannerTheme(command.bannerTheme());
        dto.setBannerOrder(command.bannerOrder());
        ProblemListSummaryDTO created =
                toSummaryDTO(problemListService.createList(command.actor().actorId(), dto));
        return RpcResult.success(created, traceId);
    }

    @Override
    public RpcResult<ProblemListSummaryDTO> updateProblemList(UpdateProblemListCommand command) {
        RpcResult<ProblemListSummaryDTO> rejected = rejectIfNotAdmin(command);
        if (rejected != null) {
            return rejected;
        }
        return execute("updateProblemList", command, ProblemListSummaryDTO.class,
                traceId -> update(command, traceId));
    }

    private RpcResult<ProblemListSummaryDTO> update(
            UpdateProblemListCommand command, String traceId) {
        log.info("ProblemListAdministrationProvider.updateProblemList id={} commandId={} actor={}",
                command.listId(), command.commandId(), command.actor().actorId());
        UpdateProblemListDTO dto = new UpdateProblemListDTO();
        dto.setName(command.name());
        dto.setDescription(command.description());
        dto.setIsPublic(command.isPublic());
        dto.setIsFeatured(command.isFeatured());
        dto.setBannerTag(command.bannerTag());
        dto.setBannerIcon(command.bannerIcon());
        dto.setBannerTheme(command.bannerTheme());
        dto.setBannerOrder(command.bannerOrder());
        ProblemListSummaryDTO updated =
                toSummaryDTO(problemListAdminService.adminUpdateProblemList(command.listId(), dto));
        return RpcResult.success(updated, traceId);
    }

    @Override
    public RpcResult<Void> deleteProblemList(DeleteProblemListCommand command) {
        RpcResult<Void> rejected = rejectIfNotAdmin(command);
        if (rejected != null) {
            return rejected;
        }
        return execute("deleteProblemList", command, Void.class,
                traceId -> delete(command, traceId));
    }

    private RpcResult<Void> delete(DeleteProblemListCommand command, String traceId) {
        log.info("ProblemListAdministrationProvider.deleteProblemList id={} commandId={} actor={}",
                command.listId(), command.commandId(), command.actor().actorId());
        problemListAdminService.adminDeleteProblemList(command.listId());
        return RpcResult.success(traceId);
    }

    @Override
    public RpcResult<ProblemListSummaryDTO> updateBasicInfo(UpdateBasicInfoCommand command) {
        RpcResult<ProblemListSummaryDTO> rejected = rejectIfNotAdmin(command);
        if (rejected != null) {
            return rejected;
        }
        return execute("updateBasicInfo", command, ProblemListSummaryDTO.class,
                traceId -> updateBasicInfo(command, traceId));
    }

    private RpcResult<ProblemListSummaryDTO> updateBasicInfo(
            UpdateBasicInfoCommand command, String traceId) {
        log.info("ProblemListAdministrationProvider.updateBasicInfo id={} commandId={} actor={}",
                command.listId(), command.commandId(), command.actor().actorId());
        UpdateBasicInfoDTO dto = new UpdateBasicInfoDTO();
        dto.setName(command.name());
        dto.setDescription(command.description());
        ProblemListSummaryDTO updated =
                toSummaryDTO(problemListAdminService.adminUpdateBasicInfo(command.listId(), dto));
        return RpcResult.success(updated, traceId);
    }

    @Override
    public RpcResult<ProblemListSummaryDTO> updateVisibility(UpdateVisibilityCommand command) {
        RpcResult<ProblemListSummaryDTO> rejected = rejectIfNotAdmin(command);
        if (rejected != null) {
            return rejected;
        }
        return execute("updateVisibility", command, ProblemListSummaryDTO.class,
                traceId -> updateVisibility(command, traceId));
    }

    private RpcResult<ProblemListSummaryDTO> updateVisibility(
            UpdateVisibilityCommand command, String traceId) {
        log.info("ProblemListAdministrationProvider.updateVisibility id={} commandId={} actor={}",
                command.listId(), command.commandId(), command.actor().actorId());
        UpdateVisibilityDTO dto = new UpdateVisibilityDTO();
        dto.setIsPublic(command.isPublic());
        dto.setIsFeatured(command.isFeatured());
        ProblemListSummaryDTO updated =
                toSummaryDTO(problemListAdminService.adminUpdateVisibility(command.listId(), dto));
        return RpcResult.success(updated, traceId);
    }

    @Override
    public RpcResult<ProblemListSummaryDTO> updateBanner(UpdateBannerCommand command) {
        RpcResult<ProblemListSummaryDTO> rejected = rejectIfNotAdmin(command);
        if (rejected != null) {
            return rejected;
        }
        return execute("updateBanner", command, ProblemListSummaryDTO.class,
                traceId -> updateBanner(command, traceId));
    }

    private RpcResult<ProblemListSummaryDTO> updateBanner(
            UpdateBannerCommand command, String traceId) {
        log.info("ProblemListAdministrationProvider.updateBanner id={} commandId={} actor={}",
                command.listId(), command.commandId(), command.actor().actorId());
        UpdateBannerDTO dto = new UpdateBannerDTO();
        dto.setBannerTag(command.bannerTag());
        dto.setBannerIcon(command.bannerIcon());
        dto.setBannerTheme(command.bannerTheme());
        dto.setBannerOrder(command.bannerOrder());
        ProblemListSummaryDTO updated =
                toSummaryDTO(problemListAdminService.adminUpdateBanner(command.listId(), dto));
        return RpcResult.success(updated, traceId);
    }

    @Override
    public RpcResult<Void> replaceListProblems(ReplaceListProblemsCommand command) {
        RpcResult<Void> rejected = rejectIfNotAdmin(command);
        if (rejected != null) {
            return rejected;
        }
        return execute("replaceListProblems", command, Void.class,
                traceId -> replaceListProblems(command, traceId));
    }

    private RpcResult<Void> replaceListProblems(
            ReplaceListProblemsCommand command, String traceId) {
        log.info("ProblemListAdministrationProvider.replaceListProblems id={} problems={} commandId={} actor={}",
                command.listId(), command.problems().size(), command.commandId(), command.actor().actorId());
        UpdateProblemListProblemsDTO dto = new UpdateProblemListProblemsDTO();
        List<UpdateProblemListProblemsDTO.ProblemEntry> entries =
                new ArrayList<>(command.problems().size());
        for (ReplaceListProblemsCommand.ProblemEntry entry : command.problems()) {
            UpdateProblemListProblemsDTO.ProblemEntry e =
                    new UpdateProblemListProblemsDTO.ProblemEntry();
            e.setProblemId(entry.problemId());
            e.setSortOrder(entry.sortOrder());
            entries.add(e);
        }
        dto.setProblems(entries);
        problemListAdminService.adminReplaceListProblems(command.listId(), dto);
        return RpcResult.success(traceId);
    }
    private <T> RpcResult<T> execute(
            String operation,
            WriteCommand command,
            Class<T> resultType,
            java.util.function.Function<String, RpcResult<T>> mutation) {
        try {
            return receiptExecutor.execute(SERVICE, operation, command, resultType, mutation);
        } catch (BusinessException e) {
            return toFailure(e, traceId(command));
        } catch (Exception e) {
            log.error("ProblemListAdministrationProvider.{} unexpected error", operation, e);
            return RpcResult.failure(AppErrorCode.UNEXPECTED_APP_STATE, traceId(command));
        }
    }

    private static <T> RpcResult<T> rejectIfNotAdmin(WriteCommand command) {
        if (command == null) {
            return RpcResult.failure(AppErrorCode.BAD_REQUEST, null);
        }
        ActorDelegation actor = command.actor();
        if (actor == null || actor.actorId() == null || actor.actorId().isBlank()
                || (!"ADMIN".equalsIgnoreCase(actor.actorType())
                && !"SUPER_ADMIN".equalsIgnoreCase(actor.actorType()))) {
            return RpcResult.failure(AppErrorCode.FORBIDDEN, traceId(command));
        }
        return null;
    }

    private static String traceId(WriteCommand command) {
        return command == null || command.trace() == null || command.trace().traceId() == null
                ? null : command.trace().traceId();
    }


    /**
     * Project the feature-side summary VO onto the public wire DTO.
     * Author fields are copied as part of the legacy Admin response shape;
     * the Admin read projection may enrich them when a provider lacks them.
     */
    private static ProblemListSummaryDTO toSummaryDTO(
            com.ulticode.modules.problemlist.dto.ProblemListSummaryVO vo) {
        ProblemListSummaryDTO dto = new ProblemListSummaryDTO();
        dto.setId(vo.getId());
        dto.setName(vo.getName());
        dto.setDescription(vo.getDescription());
        dto.setAuthorId(vo.getAuthorId());
        dto.setAuthorName(vo.getAuthorName());
        dto.setAuthorUsername(vo.getAuthorUsername());
        dto.setIsPublic(vo.getIsPublic());
        dto.setIsFeatured(vo.getIsFeatured());
        dto.setBannerTag(vo.getBannerTag());
        dto.setBannerIcon(vo.getBannerIcon());
        dto.setBannerTheme(vo.getBannerTheme());
        dto.setBannerOrder(vo.getBannerOrder());
        dto.setProblemCount(vo.getProblemCount());
        dto.setCreatedAt(vo.getCreatedAt());
        dto.setUpdatedAt(vo.getUpdatedAt());
        return dto;
    }

    private static <T> RpcResult<T> toFailure(BusinessException e, String traceId) {
        if (e.getErrorCode() == null) {
            return RpcResult.failure(AppErrorCode.UNEXPECTED_APP_STATE, traceId);
        }
        return switch (e.getErrorCode().code()) {
            case 90001 -> RpcResult.failure(AppErrorCode.CONTENT_NOT_FOUND, traceId);
            case 30001 -> RpcResult.failure(AppErrorCode.PROBLEM_NOT_FOUND, traceId);
            case 90002, 90003 -> RpcResult.failure(AppErrorCode.FORBIDDEN, traceId);
            case 90004 -> RpcResult.failure(AppErrorCode.PROBLEM_LIST_PROBLEM_DUPLICATE, traceId);
            case 49999, 40000 -> RpcResult.failure(AppErrorCode.BAD_REQUEST, traceId);
            default -> RpcResult.failure(AppErrorCode.UNEXPECTED_APP_STATE, traceId);
        };
    }
}
