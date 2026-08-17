package com.ulticode.app.dubbo.provider;

import com.ulticode.common.command.ActorDelegation;
import com.ulticode.app.api.command.CreateProblemListCommand;
import com.ulticode.app.api.command.ReplaceListProblemsCommand;
import com.ulticode.app.api.dto.ProblemListSummaryDTO;
import com.ulticode.app.api.error.AppErrorCode;
import com.ulticode.app.idempotency.CommandReceiptExecutor;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.rpc.RpcResult;
import com.ulticode.common.tracing.IdMetadata;
import com.ulticode.common.tracing.TraceMetadata;
import com.ulticode.modules.problemlist.dto.ProblemListSummaryVO;
import com.ulticode.modules.problemlist.service.ProblemListAdminService;
import com.ulticode.modules.problemlist.service.ProblemListService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProblemListAdministrationProviderTest {

    @Mock
    private ProblemListService problemListService;

    @Mock
    private ProblemListAdminService problemListAdminService;

    @Mock
    private CommandReceiptExecutor receiptExecutor;

    private ProblemListAdministrationProvider provider;

    @BeforeEach
    void setUp() {
        provider = new ProblemListAdministrationProvider(
                problemListService, problemListAdminService, receiptExecutor);
    }

    @Test
    void createRoutesThroughDurableReceiptBoundaryWithCommandMetadata() {
        when(receiptExecutor.execute(
                eq(CommandReceiptExecutor.problemListService()),
                eq("createProblemList"),
                any(CreateProblemListCommand.class),
                eq(ProblemListSummaryDTO.class),
                any()))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    Function<String, RpcResult<ProblemListSummaryDTO>> mutation = invocation.getArgument(4);
                    return mutation.apply("t-provider");
                });
        when(problemListService.createList(eq("admin-1"), any())).thenReturn(summary("list-1"));

        RpcResult<ProblemListSummaryDTO> result = provider.createProblemList(new CreateProblemListCommand(
                "cmd-1",
                IdMetadata.of("retry-1", null),
                new ActorDelegation("ADMIN", "admin-1", "admin-1", "create list"),
                new TraceMetadata("t-request", null, null, null),
                "New List", "desc", true, "tag", "icon", "blue", 1));

        assertThat(result.success()).isTrue();
        assertThat(result.data().getAuthorName()).isEqualTo("Author");
        assertThat(result.data().getAuthorUsername()).isEqualTo("author-user");
        assertThat(result.data().getId()).isEqualTo("list-1");

        ArgumentCaptor<CreateProblemListCommand> command =
                ArgumentCaptor.forClass(CreateProblemListCommand.class);
        verify(receiptExecutor).execute(
                eq(CommandReceiptExecutor.problemListService()),
                eq("createProblemList"), command.capture(), eq(ProblemListSummaryDTO.class), any());
        assertThat(command.getValue().idempotency().idempotencyKey()).isEqualTo("retry-1");
        assertThat(command.getValue().actor().actorId()).isEqualTo("admin-1");
        assertThat(command.getValue().trace().traceId()).isEqualTo("t-request");
    }

    @Test
    void mapsOwnerPrivateFailureToForbidden() {
        when(receiptExecutor.execute(
                eq(CommandReceiptExecutor.problemListService()),
                eq("createProblemList"),
                any(CreateProblemListCommand.class),
                eq(ProblemListSummaryDTO.class),
                any()))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    Function<String, RpcResult<ProblemListSummaryDTO>> mutation = invocation.getArgument(4);
                    return mutation.apply("t-provider");
                });
        when(problemListService.createList(eq("admin-1"), any()))
                .thenThrow(new BusinessException(
                        com.ulticode.app.error.ProblemListErrorCode.PROBLEM_LIST_PRIVATE));

        RpcResult<ProblemListSummaryDTO> result = provider.createProblemList(new CreateProblemListCommand(
                "cmd-2",
                IdMetadata.of("retry-2", null),
                new ActorDelegation("ADMIN", "admin-1", "admin-1", "create list"),
                new TraceMetadata("t-request", null, null, null),
                "New List", null, false, null, null, null, null));

        assertThat(result.success()).isFalse();
        assertThat(result.error().code()).isEqualTo(AppErrorCode.FORBIDDEN.code());
    }

    @Test
    void mapsMissingProblemToProblemNotFound() {
        when(receiptExecutor.execute(
                eq(CommandReceiptExecutor.problemListService()),
                eq("createProblemList"),
                any(CreateProblemListCommand.class),
                eq(ProblemListSummaryDTO.class),
                any()))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    Function<String, RpcResult<ProblemListSummaryDTO>> mutation = invocation.getArgument(4);
                    return mutation.apply("t-provider");
                });
        when(problemListService.createList(eq("admin-1"), any()))
                .thenThrow(new BusinessException(com.ulticode.app.error.ProblemErrorCode.PROBLEM_NOT_FOUND));

        RpcResult<ProblemListSummaryDTO> result = provider.createProblemList(new CreateProblemListCommand(
                "cmd-3",
                IdMetadata.of("retry-3", null),
                new ActorDelegation("ADMIN", "admin-1", "admin-1", "create list"),
                new TraceMetadata("t-request", null, null, null),
                "New List", null, false, null, null, null, null));

        assertThat(result.success()).isFalse();
        assertThat(result.error().code()).isEqualTo(AppErrorCode.PROBLEM_NOT_FOUND.code());
    }
    @Test
    void preservesMissingProblemErrorFromOwner() {
        when(receiptExecutor.execute(
                eq(CommandReceiptExecutor.problemListService()),
                eq("replaceListProblems"),
                any(ReplaceListProblemsCommand.class),
                eq(Void.class),
                any()))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    Function<String, RpcResult<Void>> mutation = invocation.getArgument(4);
                    return mutation.apply("t-provider");
                });
        doThrow(new BusinessException(
                com.ulticode.app.error.ProblemErrorCode.PROBLEM_NOT_FOUND))
                .when(problemListAdminService)
                .adminReplaceListProblems(eq("list-1"), any());

        RpcResult<Void> result = provider.replaceListProblems(replaceCommand("missing-problem"));

        assertThat(result.success()).isFalse();
        assertThat(result.error().code()).isEqualTo(AppErrorCode.PROBLEM_NOT_FOUND.code());
    }

    @Test
    void preservesDuplicateProblemErrorFromOwner() {
        when(receiptExecutor.execute(
                eq(CommandReceiptExecutor.problemListService()),
                eq("replaceListProblems"),
                any(ReplaceListProblemsCommand.class),
                eq(Void.class),
                any()))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    Function<String, RpcResult<Void>> mutation = invocation.getArgument(4);
                    return mutation.apply("t-provider");
                });
        doThrow(new BusinessException(
                com.ulticode.app.error.ProblemListErrorCode.PROBLEM_LIST_PROBLEM_DUPLICATE))
                .when(problemListAdminService)
                .adminReplaceListProblems(eq("list-1"), any());

        RpcResult<Void> result = provider.replaceListProblems(replaceCommand("duplicate-problem"));

        assertThat(result.success()).isFalse();
        assertThat(result.error().code())
                .isEqualTo(AppErrorCode.PROBLEM_LIST_PROBLEM_DUPLICATE.code());
    }

    private static ReplaceListProblemsCommand replaceCommand(String key) {
        return new ReplaceListProblemsCommand(
                "cmd-" + key,
                IdMetadata.of(key, null),
                new ActorDelegation("ADMIN", "admin-1", "admin-1", "replace list"),
                new TraceMetadata("t-request", null, null, null),
                "list-1",
                java.util.List.of(new ReplaceListProblemsCommand.ProblemEntry(1L, 0)));
    }


    private static ProblemListSummaryVO summary(String id) {
        ProblemListSummaryVO vo = new ProblemListSummaryVO();
        vo.setId(id);
        vo.setName("List");
        vo.setAuthorName("Author");
        vo.setAuthorUsername("author-user");
        vo.setIsPublic(true);
        return vo;
    }
}
