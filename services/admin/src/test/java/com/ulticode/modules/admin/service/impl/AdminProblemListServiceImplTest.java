package com.ulticode.modules.admin.service.impl;

import com.ulticode.admin.error.AdminErrorCode;
import com.ulticode.app.api.error.AppErrorCode;
import com.ulticode.app.api.command.CreateProblemListCommand;
import com.ulticode.app.api.command.DeleteProblemListCommand;
import com.ulticode.app.api.command.ReplaceListProblemsCommand;
import com.ulticode.app.api.command.UpdateBannerCommand;
import com.ulticode.app.api.command.UpdateBasicInfoCommand;
import com.ulticode.app.api.command.UpdateProblemListCommand;
import com.ulticode.app.api.command.UpdateVisibilityCommand;
import com.ulticode.app.api.command.WriteCommand;
import com.ulticode.app.api.dto.ProblemListDetailDTO;
import com.ulticode.app.api.dto.ProblemListSummaryDTO;
import com.ulticode.app.api.service.ProblemListAdministrationService;
import com.ulticode.app.api.service.ProblemListChainReadPort;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.response.PageResult;
import com.ulticode.common.rpc.RpcResult;
import com.ulticode.common.util.AuditContext;
import com.ulticode.modules.admin.dto.AdminProblemListQueryDTO;
import com.ulticode.modules.admin.dto.CreateProblemListRequest;
import com.ulticode.modules.admin.dto.UpdateBannerRequest;
import com.ulticode.modules.admin.dto.UpdateBasicInfoRequest;
import com.ulticode.modules.admin.dto.UpdateProblemListRequest;
import com.ulticode.modules.admin.dto.UpdateProblemsRequest;
import com.ulticode.modules.admin.dto.UpdateVisibilityRequest;
import com.ulticode.modules.admin.projection.AdminProblemListProjection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AdminProblemListServiceImpl} after the
 * ADMIN-005 rewiring.
 *
 * <p>Verifies the architectural contract:
 * <ul>
 *   <li>Every mutation path issues a {@link WriteCommand} carrying
 *       commandId / idempotency / actor / trace metadata against
 *       {@link ProblemListAdministrationService} and maps the
 *       {@link RpcResult} onto admin error semantics.</li>
 *   <li>Pre-state audit snapshots come from the remote
 *       {@link ProblemListChainReadPort#findSummary} (404 on missing).</li>
 *   <li>{@link AuditContext} receives both old and new value snapshots for
 *       every audited mutation.</li>
 *   <li>Read paths (paginated list + single detail) keep delegating to the
 *       admin projection.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AdminProblemListServiceImpl")
class AdminProblemListServiceImplTest {

    private static final String LIST_ID = "list-001";
    private static final String AUTHOR_ID = "user-author";
    private static final String ADMIN_USER_ID = "admin-001";

    @Mock private ProblemListAdministrationService problemListAdministrationService;
    @Mock private ProblemListChainReadPort problemListChainReadPort;
    @Mock private AdminProblemListProjection adminProblemListProjection;

    private AdminProblemListServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AdminProblemListServiceImpl(
                problemListAdministrationService,
                problemListChainReadPort,
                adminProblemListProjection);
    }

    @AfterEach
    void tearDown() {
        AuditContext.clear();
    }

    private ProblemListSummaryDTO createSummary() {
        ProblemListSummaryDTO dto = new ProblemListSummaryDTO();
        dto.setId(LIST_ID);
        dto.setName("Original Name");
        dto.setDescription("Original Description");
        dto.setAuthorId(AUTHOR_ID);
        dto.setIsPublic(false);
        dto.setIsFeatured(false);
        dto.setBannerTag("original-tag");
        dto.setBannerIcon("original-icon");
        dto.setBannerTheme("original-theme");
        dto.setBannerOrder(1);
        dto.setCreatedAt(LocalDateTime.of(2024, 1, 1, 0, 0));
        dto.setUpdatedAt(LocalDateTime.of(2024, 1, 1, 0, 0));
        return dto;
    }

    private ProblemListSummaryDTO updatedSummary() {
        ProblemListSummaryDTO dto = createSummary();
        dto.setName("Updated Name");
        dto.setDescription("Updated Description");
        dto.setIsPublic(true);
        dto.setIsFeatured(true);
        dto.setBannerTag("updated-tag");
        dto.setBannerTheme("updated-theme");
        dto.setBannerOrder(2);
        return dto;
    }

    // ==================== getProblemLists (read path: projection) ====================

    @Nested
    @DisplayName("getProblemLists()")
    class GetProblemListsTests {

        @Test
        @DisplayName("should delegate the page read to AdminProblemListProjection.findAdminLists")
        void delegatesToProjectionFindAdminLists() {
            AdminProblemListQueryDTO query = new AdminProblemListQueryDTO();
            ProblemListSummaryDTO vo = updatedSummary();
            PageResult<ProblemListSummaryDTO> projectionResult =
                    PageResult.of(List.of(vo), 1L, 1, 10);
            when(adminProblemListProjection.findAdminLists(query)).thenReturn(projectionResult);

            var result = service.getProblemLists(query);

            assertThat(result.getTotal()).isEqualTo(1L);
            assertThat(result.getItems()).containsExactly(vo);
            verify(adminProblemListProjection).findAdminLists(query);
        }

        @Test
        @DisplayName("should return an empty page when the projection finds nothing")
        void emptyPage() {
            AdminProblemListQueryDTO query = new AdminProblemListQueryDTO();
            PageResult<ProblemListSummaryDTO> empty =
                    PageResult.of(Collections.emptyList(), 0L, 1, 10);
            when(adminProblemListProjection.findAdminLists(query)).thenReturn(empty);

            var result = service.getProblemLists(query);

            assertThat(result.getItems()).isEmpty();
            verify(adminProblemListProjection).findAdminLists(query);
        }
    }

    // ==================== getProblemList (read path: projection) ====================

    @Nested
    @DisplayName("getProblemList()")
    class GetProblemListTests {

        @Test
        @DisplayName("should delegate the detail read to AdminProblemListProjection.getAdminListDetail")
        void delegatesToProjectionGetAdminListDetail() {
            ProblemListDetailDTO detail = new ProblemListDetailDTO();
            when(adminProblemListProjection.getAdminListDetail(LIST_ID)).thenReturn(detail);

            ProblemListDetailDTO result = service.getProblemList(LIST_ID);

            assertThat(result).isSameAs(detail);
            verify(adminProblemListProjection).getAdminListDetail(LIST_ID);
            verify(problemListChainReadPort, never()).findSummary(any());
        }

        @Test
        @DisplayName("should surface PROBLEM_LIST_NOT_FOUND when the projection cannot find the list")
        void notFound() {
            when(adminProblemListProjection.getAdminListDetail(LIST_ID))
                    .thenThrow(new BusinessException(AdminErrorCode.PROBLEM_LIST_NOT_FOUND));

            assertThatThrownBy(() -> service.getProblemList(LIST_ID))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(AdminErrorCode.PROBLEM_LIST_NOT_FOUND));
        }
    }

    // ==================== createProblemList (remote command) ====================

    @Nested
    @DisplayName("createProblemList()")
    class CreateProblemListTests {

        @Test
        @DisplayName("should issue a CreateProblemListCommand with write metadata and return the payload")
        void issuesCommandAndMapsResult() {
            CreateProblemListRequest request = new CreateProblemListRequest();
            request.setName("New List");
            request.setDescription("desc");
            request.setIsPublic(true);
            request.setBannerTag("tag");
            request.setBannerTheme("blue");
            request.setBannerOrder(3);

            ProblemListSummaryDTO vo = updatedSummary();
            when(problemListAdministrationService.createProblemList(any(CreateProblemListCommand.class)))
                    .thenReturn(RpcResult.success(vo, "t-1"));

            ProblemListSummaryDTO result = service.createProblemList(request, ADMIN_USER_ID);

            assertThat(result).isSameAs(vo);
            ArgumentCaptor<CreateProblemListCommand> captor =
                    ArgumentCaptor.forClass(CreateProblemListCommand.class);
            verify(problemListAdministrationService).createProblemList(captor.capture());
            CreateProblemListCommand cmd = captor.getValue();
            assertThat(cmd.name()).isEqualTo("New List");
            assertThat(cmd.isPublic()).isTrue();
            assertThat(cmd.commandId()).isNotBlank();
            assertThat(cmd.idempotency().hasKey()).isTrue();
            assertThat(cmd.actor().actorId()).isEqualTo(ADMIN_USER_ID);
            assertThat(cmd.actor().actorType()).isEqualTo("ADMIN");
            assertThat(cmd.trace()).isNotNull();
            // Confirmed review finding: commands must propagate a real
            // request trace id, never TraceMetadata.EMPTY (null traceId).
            assertThat(cmd.trace().hasTraceId()).isTrue();
            assertThat(cmd.trace().traceId()).startsWith("t-");
        }

        @Test
        @DisplayName("should preserve a caller idempotency key and derive a stable command id")
        void preservesProvidedIdempotencyKey() {
            CreateProblemListRequest request = new CreateProblemListRequest();
            request.setName("Retryable List");
            when(problemListAdministrationService.createProblemList(any(CreateProblemListCommand.class)))
                    .thenReturn(RpcResult.success(updatedSummary(), "t-1"));

            service.createProblemList(request, ADMIN_USER_ID, "problem-list-retry-1");
            service.createProblemList(request, ADMIN_USER_ID, "problem-list-retry-1");

            ArgumentCaptor<CreateProblemListCommand> captor =
                    ArgumentCaptor.forClass(CreateProblemListCommand.class);
            verify(problemListAdministrationService, times(2)).createProblemList(captor.capture());
            List<CreateProblemListCommand> commands = captor.getAllValues();
            assertThat(commands).allMatch(command ->
                    command.idempotency().idempotencyKey().equals("problem-list-retry-1"));
            assertThat(commands.get(0).commandId()).isEqualTo(commands.get(1).commandId());
        }

        @Test
        @DisplayName("should map a provider failure onto the admin error code")
        void mapsProviderFailure() {
            CreateProblemListRequest request = new CreateProblemListRequest();
            request.setName("New List");
            when(problemListAdministrationService.createProblemList(any(CreateProblemListCommand.class)))
                    .thenReturn(RpcResult.failure(
                            new com.ulticode.common.rpc.RpcResult.ErrorPayload("app", 40000, "Bad request"),
                            "t-1"));

            assertThatThrownBy(() -> service.createProblemList(request, ADMIN_USER_ID))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(AdminErrorCode.VALIDATION_FAILED));
        }
    }

    // ==================== updateProblemList (pre-state + remote command + audit) ====================

    @Nested
    @DisplayName("updateProblemList()")
    class UpdateProblemListTests {

        @Test
        @DisplayName("should read pre-state, issue command, and capture audit snapshot")
        void issuesCommandAndAudits() {
            when(problemListChainReadPort.findSummary(LIST_ID)).thenReturn(createSummary());
            ProblemListSummaryDTO vo = updatedSummary();
            when(problemListAdministrationService.updateProblemList(any(UpdateProblemListCommand.class)))
                    .thenReturn(RpcResult.success(vo, "t-1"));

            UpdateProblemListRequest request = new UpdateProblemListRequest();
            request.setName("Updated Name");

            ProblemListSummaryDTO result = service.updateProblemList(LIST_ID, request, ADMIN_USER_ID);

            assertThat(result).isSameAs(vo);
            ArgumentCaptor<UpdateProblemListCommand> captor =
                    ArgumentCaptor.forClass(UpdateProblemListCommand.class);
            verify(problemListAdministrationService).updateProblemList(captor.capture());
            assertThat(captor.getValue().listId()).isEqualTo(LIST_ID);
            assertThat(captor.getValue().name()).isEqualTo("Updated Name");
            assertThat(captor.getValue().actor().actorId()).isEqualTo(ADMIN_USER_ID);
            assertThat(captor.getValue().trace().hasTraceId()).isTrue();

            Map<String, Object> oldValues = AuditContext.getOldValues();
            assertThat(oldValues).containsEntry("name", "Original Name");
            assertThat(oldValues).containsEntry("isPublic", false);
            assertThat(oldValues).containsEntry("isFeatured", false);

            Map<String, Object> newValues = AuditContext.getNewValues();
            assertThat(newValues).containsEntry("name", "Updated Name");
            assertThat(newValues).containsEntry("isPublic", true);
            assertThat(newValues).containsEntry("isFeatured", true);
        }

        @Test
        @DisplayName("should throw PROBLEM_LIST_NOT_FOUND when pre-state read returns null")
        void notFoundBubbles() {
            when(problemListChainReadPort.findSummary(LIST_ID)).thenReturn(null);

            UpdateProblemListRequest request = new UpdateProblemListRequest();
            request.setName("X");

            assertThatThrownBy(() -> service.updateProblemList(LIST_ID, request, ADMIN_USER_ID))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(AdminErrorCode.PROBLEM_LIST_NOT_FOUND));
            verify(problemListAdministrationService, never()).updateProblemList(any());
        }

        @Test
        @DisplayName("should let a keyed update reach the provider when the pre-state is already gone")
        void keyedUpdateReachesProviderWhenPreStateMissing() {
            when(problemListChainReadPort.findSummary(LIST_ID)).thenReturn(null);
            ProblemListSummaryDTO vo = updatedSummary();
            when(problemListAdministrationService.updateProblemList(any(UpdateProblemListCommand.class)))
                    .thenReturn(RpcResult.success(vo, "t-1"));

            UpdateProblemListRequest request = new UpdateProblemListRequest();
            request.setName("Updated Name");

            ProblemListSummaryDTO result =
                    service.updateProblemList(LIST_ID, request, ADMIN_USER_ID, "problem-list-update-retry-1");

            assertThat(result).isSameAs(vo);
            verify(problemListAdministrationService).updateProblemList(any(UpdateProblemListCommand.class));
        }

        @Test
        @DisplayName("should map provider CONTENT_NOT_FOUND onto PROBLEM_LIST_NOT_FOUND")
        void mapsProviderNotFound() {
            when(problemListChainReadPort.findSummary(LIST_ID)).thenReturn(createSummary());
            when(problemListAdministrationService.updateProblemList(any(UpdateProblemListCommand.class)))
                    .thenReturn(RpcResult.failure(
                            new com.ulticode.common.rpc.RpcResult.ErrorPayload("app", 40401, "Content not found"),
                            "t-1"));

            UpdateProblemListRequest request = new UpdateProblemListRequest();
            request.setName("X");

            assertThatThrownBy(() -> service.updateProblemList(LIST_ID, request, ADMIN_USER_ID))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(AdminErrorCode.PROBLEM_LIST_NOT_FOUND));
        }
    }

    // ==================== deleteProblemList (pre-state + remote command + audit) ====================

    @Nested
    @DisplayName("deleteProblemList()")
    class DeleteProblemListTests {

        @Test
        @DisplayName("should read pre-state, capture audit snapshot, and issue delete command")
        void issuesCommandAndAudits() {
            when(problemListChainReadPort.findSummary(LIST_ID)).thenReturn(createSummary());
            when(problemListAdministrationService.deleteProblemList(any(DeleteProblemListCommand.class)))
                    .thenReturn(RpcResult.success("t-1"));

            service.deleteProblemList(LIST_ID, ADMIN_USER_ID);

            ArgumentCaptor<DeleteProblemListCommand> captor =
                    ArgumentCaptor.forClass(DeleteProblemListCommand.class);
            verify(problemListAdministrationService).deleteProblemList(captor.capture());
            assertThat(captor.getValue().listId()).isEqualTo(LIST_ID);
            assertThat(captor.getValue().actor().actorId()).isEqualTo(ADMIN_USER_ID);
            assertThat(captor.getValue().trace().hasTraceId()).isTrue();

            Map<String, Object> oldValues = AuditContext.getOldValues();
            assertThat(oldValues).containsEntry("name", "Original Name");
            assertThat(oldValues).containsEntry("authorId", AUTHOR_ID);
        }

        @Test
        @DisplayName("should throw PROBLEM_LIST_NOT_FOUND when pre-state read returns null")
        void notFoundBubbles() {
            when(problemListChainReadPort.findSummary(LIST_ID)).thenReturn(null);

            assertThatThrownBy(() -> service.deleteProblemList(LIST_ID, ADMIN_USER_ID))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(AdminErrorCode.PROBLEM_LIST_NOT_FOUND));
            verify(problemListAdministrationService, never()).deleteProblemList(any());
        }

        @Test
        @DisplayName("should let a keyed delete reach the provider when the pre-state is already gone")
        void keyedDeleteReachesProviderWhenPreStateMissing() {
            when(problemListChainReadPort.findSummary(LIST_ID)).thenReturn(null);
            when(problemListAdministrationService.deleteProblemList(any(DeleteProblemListCommand.class)))
                    .thenReturn(RpcResult.success("t-1"));

            service.deleteProblemList(LIST_ID, ADMIN_USER_ID, "problem-list-delete-retry-1");

            verify(problemListAdministrationService).deleteProblemList(any(DeleteProblemListCommand.class));
        }
    }

    // ==================== updateListProblems (validation + remote command + audit) ====================

    @Nested
    @DisplayName("updateListProblems()")
    class UpdateListProblemsTests {

        @Test
        @DisplayName("should validate, issue replace command, capture count in audit context")
        void issuesReplaceCommandAndAudits() {
            when(problemListChainReadPort.findSummary(LIST_ID)).thenReturn(createSummary());
            when(problemListAdministrationService.replaceListProblems(any(ReplaceListProblemsCommand.class)))
                    .thenReturn(RpcResult.success("t-1"));

            UpdateProblemsRequest dto = new UpdateProblemsRequest();
            UpdateProblemsRequest.ProblemEntry e1 = new UpdateProblemsRequest.ProblemEntry();
            e1.setProblemId(1L);
            e1.setSortOrder(0);
            UpdateProblemsRequest.ProblemEntry e2 = new UpdateProblemsRequest.ProblemEntry();
            e2.setProblemId(2L);
            e2.setSortOrder(1);
            dto.setProblems(List.of(e1, e2));

            service.updateListProblems(LIST_ID, dto, ADMIN_USER_ID);

            ArgumentCaptor<ReplaceListProblemsCommand> captor =
                    ArgumentCaptor.forClass(ReplaceListProblemsCommand.class);
            verify(problemListAdministrationService).replaceListProblems(captor.capture());
            assertThat(captor.getValue().listId()).isEqualTo(LIST_ID);
            assertThat(captor.getValue().problems()).hasSize(2);
            assertThat(captor.getValue().problems().get(0).problemId()).isEqualTo(1L);
            assertThat(captor.getValue().actor().actorId()).isEqualTo(ADMIN_USER_ID);
            assertThat(captor.getValue().trace().hasTraceId()).isTrue();

            Map<String, Object> newValues = AuditContext.getNewValues();
            assertThat(newValues).containsEntry("updatedProblems", 2);
        }

        @Test
        @DisplayName("should reject null problems before issuing the remote command")
        void rejectsNullProblems() {
            when(problemListChainReadPort.findSummary(LIST_ID)).thenReturn(createSummary());

            UpdateProblemsRequest dto = new UpdateProblemsRequest();
            dto.setProblems(null);

            assertThatThrownBy(() -> service.updateListProblems(LIST_ID, dto, ADMIN_USER_ID))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(AdminErrorCode.VALIDATION_FAILED));
            verify(problemListAdministrationService, never()).replaceListProblems(any());
        }
        @Test
        @DisplayName("should preserve missing problem error from the App owner")
        void preservesMissingProblemError() {
            when(problemListChainReadPort.findSummary(LIST_ID)).thenReturn(createSummary());
            when(problemListAdministrationService.replaceListProblems(any(ReplaceListProblemsCommand.class)))
                    .thenReturn(RpcResult.failure(AppErrorCode.PROBLEM_NOT_FOUND, "t-1"));

            UpdateProblemsRequest dto = problems(1L, 0);

            assertThatThrownBy(() -> service.updateListProblems(LIST_ID, dto, ADMIN_USER_ID))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(AdminErrorCode.PROBLEM_NOT_FOUND));
        }

        @Test
        @DisplayName("should preserve duplicate problem error from the App owner")
        void preservesDuplicateProblemError() {
            when(problemListChainReadPort.findSummary(LIST_ID)).thenReturn(createSummary());
            when(problemListAdministrationService.replaceListProblems(any(ReplaceListProblemsCommand.class)))
                    .thenReturn(RpcResult.failure(
                            AppErrorCode.PROBLEM_LIST_PROBLEM_DUPLICATE, "t-1"));

            UpdateProblemsRequest dto = problems(1L, 0);

            assertThatThrownBy(() -> service.updateListProblems(LIST_ID, dto, ADMIN_USER_ID))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(AdminErrorCode.PROBLEM_LIST_PROBLEM_DUPLICATE));
        }

        @Test
        @DisplayName("should reject null problem entries before issuing the remote command")
        void rejectsNullProblemEntry() {
            when(problemListChainReadPort.findSummary(LIST_ID)).thenReturn(createSummary());

            UpdateProblemsRequest dto = new UpdateProblemsRequest();
            dto.setProblems(Collections.singletonList(null));

            assertThatThrownBy(() -> service.updateListProblems(LIST_ID, dto, ADMIN_USER_ID))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(AdminErrorCode.VALIDATION_FAILED));
            verify(problemListAdministrationService, never()).replaceListProblems(any());
        }

        private UpdateProblemsRequest problems(Long problemId, Integer sortOrder) {
            UpdateProblemsRequest.ProblemEntry entry = new UpdateProblemsRequest.ProblemEntry();
            entry.setProblemId(problemId);
            entry.setSortOrder(sortOrder);
            UpdateProblemsRequest dto = new UpdateProblemsRequest();
            dto.setProblems(List.of(entry));
            return dto;
        }

    }

    // ==================== updateBasicInfo (pre-state + remote command + audit) ====================

    @Nested
    @DisplayName("updateBasicInfo()")
    class UpdateBasicInfoTests {

        @Test
        @DisplayName("should capture pre-state, issue command, capture new state in audit context")
        void issuesCommandAndAudits() {
            when(problemListChainReadPort.findSummary(LIST_ID)).thenReturn(createSummary());
            ProblemListSummaryDTO vo = updatedSummary();
            when(problemListAdministrationService.updateBasicInfo(any(UpdateBasicInfoCommand.class)))
                    .thenReturn(RpcResult.success(vo, "t-1"));

            UpdateBasicInfoRequest dto = new UpdateBasicInfoRequest();
            dto.setName("Updated Name");
            dto.setDescription("Updated Description");

            ProblemListSummaryDTO result = service.updateBasicInfo(LIST_ID, ADMIN_USER_ID, dto);

            assertThat(result).isSameAs(vo);
            ArgumentCaptor<UpdateBasicInfoCommand> captor =
                    ArgumentCaptor.forClass(UpdateBasicInfoCommand.class);
            verify(problemListAdministrationService).updateBasicInfo(captor.capture());
            assertThat(captor.getValue().listId()).isEqualTo(LIST_ID);
            assertThat(captor.getValue().name()).isEqualTo("Updated Name");
            assertThat(captor.getValue().trace().hasTraceId()).isTrue();

            Map<String, Object> oldValues = AuditContext.getOldValues();
            assertThat(oldValues).containsEntry("name", "Original Name");
            assertThat(oldValues).containsEntry("description", "Original Description");

            Map<String, Object> newValues = AuditContext.getNewValues();
            assertThat(newValues).containsEntry("name", "Updated Name");
            assertThat(newValues).containsEntry("description", "Updated Description");
        }
    }

    // ==================== updateVisibility (pre-state + remote command + audit) ====================

    @Nested
    @DisplayName("updateVisibility()")
    class UpdateVisibilityTests {

        @Test
        @DisplayName("should capture pre-state, issue command, capture new state in audit context")
        void issuesCommandAndAudits() {
            when(problemListChainReadPort.findSummary(LIST_ID)).thenReturn(createSummary());
            ProblemListSummaryDTO vo = updatedSummary();
            when(problemListAdministrationService.updateVisibility(any(UpdateVisibilityCommand.class)))
                    .thenReturn(RpcResult.success(vo, "t-1"));

            UpdateVisibilityRequest dto = new UpdateVisibilityRequest();
            dto.setIsPublic(true);
            dto.setIsFeatured(true);

            ProblemListSummaryDTO result = service.updateVisibility(LIST_ID, ADMIN_USER_ID, dto);

            assertThat(result).isSameAs(vo);
            ArgumentCaptor<UpdateVisibilityCommand> captor =
                    ArgumentCaptor.forClass(UpdateVisibilityCommand.class);
            verify(problemListAdministrationService).updateVisibility(captor.capture());
            assertThat(captor.getValue().listId()).isEqualTo(LIST_ID);
            assertThat(captor.getValue().isPublic()).isTrue();
            assertThat(captor.getValue().isFeatured()).isTrue();
            assertThat(captor.getValue().trace().hasTraceId()).isTrue();

            Map<String, Object> oldValues = AuditContext.getOldValues();
            assertThat(oldValues).containsEntry("isPublic", false);
            assertThat(oldValues).containsEntry("isFeatured", false);

            Map<String, Object> newValues = AuditContext.getNewValues();
            assertThat(newValues).containsEntry("isPublic", true);
            assertThat(newValues).containsEntry("isFeatured", true);
        }
    }

    // ==================== updateBanner (pre-state + remote command + audit) ====================

    @Nested
    @DisplayName("updateBanner()")
    class UpdateBannerTests {

        @Test
        @DisplayName("should capture pre-state, issue command, capture new state in audit context")
        void issuesCommandAndAudits() {
            when(problemListChainReadPort.findSummary(LIST_ID)).thenReturn(createSummary());
            ProblemListSummaryDTO vo = updatedSummary();
            when(problemListAdministrationService.updateBanner(any(UpdateBannerCommand.class)))
                    .thenReturn(RpcResult.success(vo, "t-1"));

            UpdateBannerRequest dto = new UpdateBannerRequest();
            dto.setBannerTag("updated-tag");
            dto.setBannerTheme("updated-theme");
            dto.setBannerOrder(2);

            ProblemListSummaryDTO result = service.updateBanner(LIST_ID, ADMIN_USER_ID, dto);

            assertThat(result).isSameAs(vo);
            ArgumentCaptor<UpdateBannerCommand> captor =
                    ArgumentCaptor.forClass(UpdateBannerCommand.class);
            verify(problemListAdministrationService).updateBanner(captor.capture());
            assertThat(captor.getValue().listId()).isEqualTo(LIST_ID);
            assertThat(captor.getValue().bannerTag()).isEqualTo("updated-tag");
            assertThat(captor.getValue().trace().hasTraceId()).isTrue();

            Map<String, Object> oldValues = AuditContext.getOldValues();
            assertThat(oldValues).containsEntry("bannerTag", "original-tag");
            assertThat(oldValues).containsEntry("bannerTheme", "original-theme");
            assertThat(oldValues).containsEntry("bannerOrder", 1);

            Map<String, Object> newValues = AuditContext.getNewValues();
            assertThat(newValues).containsEntry("bannerTag", "updated-tag");
            assertThat(newValues).containsEntry("bannerTheme", "updated-theme");
            assertThat(newValues).containsEntry("bannerOrder", 2);
        }
    }

    // ==================== Architectural invariant tests ====================

    @Test
    @DisplayName("AdminProblemListServiceImpl must depend only on the app-api seams and the admin projection")
    void architecturalInvariant_noPrivateModuleDependency() {
        java.lang.reflect.Constructor<?> ctor = AdminProblemListServiceImpl.class.getDeclaredConstructors()[0];
        Class<?>[] paramTypes = ctor.getParameterTypes();
        assertThat(paramTypes).containsOnly(
                ProblemListAdministrationService.class,
                ProblemListChainReadPort.class,
                AdminProblemListProjection.class);
    }

    @Test
    @DisplayName("every audited admin mutation method is annotated with @Audited")
    void auditedAnnotationsPresent() throws NoSuchMethodException {
        String[] auditedMethods = {
                "updateProblemList",
                "deleteProblemList",
                "updateListProblems",
                "updateBasicInfo",
                "updateVisibility",
                "updateBanner"
        };
        for (String name : auditedMethods) {
            var method = AdminProblemListServiceImpl.class.getDeclaredMethod(name, parameterTypes(name));
            var audited = method.getAnnotation(com.ulticode.common.annotation.Audited.class);
            assertThat(audited)
                    .as("@Audited missing on AdminProblemListServiceImpl.%s", name)
                    .isNotNull();
            if (name.equals("deleteProblemList") || name.equals("updateListProblems")) {
                assertThat(audited.userIdFrom()).isEqualTo("userId");
                assertThat(audited.entityIdFrom()).isEqualTo("id");
            }
        }
    }

    private static Class<?>[] parameterTypes(String methodName) {
        return switch (methodName) {
            case "updateProblemList" -> new Class<?>[]{String.class, UpdateProblemListRequest.class, String.class};
            case "deleteProblemList" -> new Class<?>[]{String.class, String.class};
            case "updateListProblems" -> new Class<?>[]{String.class, UpdateProblemsRequest.class, String.class};
            case "updateBasicInfo" -> new Class<?>[]{String.class, String.class, UpdateBasicInfoRequest.class};
            case "updateVisibility" -> new Class<?>[]{String.class, String.class, UpdateVisibilityRequest.class};
            case "updateBanner" -> new Class<?>[]{String.class, String.class, UpdateBannerRequest.class};
            default -> throw new IllegalArgumentException("unknown method: " + methodName);
        };
    }
}
