package com.ulticode.modules.admin.service;

import com.ulticode.app.api.dto.ProblemAdminRowDTO;
import com.ulticode.app.api.service.ProblemAdministrationService;
import com.ulticode.app.api.service.ProblemAdminReadPort;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.admin.error.AdminErrorCode;
import com.ulticode.common.auth.CurrentUserProvider;
import com.ulticode.common.rpc.RpcResult;
import com.ulticode.modules.admin.dto.problem.AdminProblemMapper;
import com.ulticode.modules.admin.dto.problem.CreateProblemDTO;
import com.ulticode.modules.admin.dto.problem.ProblemAdminVO;
import com.ulticode.modules.admin.dto.problem.UpdateProblemDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.argThat;

/**
 * P4-CUTOVER-001 / ADMIN-003: unit test {@link ProblemCutoverService}.
 *
 * <p>Pins the Dubbo write path and the read-back through the public
 * {@link ProblemAdminReadPort}: every lifecycle write (create / update /
 * publish / unpublish / delete) goes through the {@code backend-app}
 * {@link ProblemAdministrationService} provider, and RPC failures map to
 * the closest admin error code. The former local {@code ProblemService}
 * fallback is gone (no App-private imports).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ProblemCutoverService")
class ProblemCutoverServiceTest {

    @Mock
    private ProblemAdminReadPort problemReadPort;
    @Mock
    private AdminProblemMapper mapper;
    @Mock
    private ProblemAdministrationService dubboProvider;
    @Mock
    private CurrentUserProvider currentUserProvider;

    private ProblemCutoverService cutoverService;

    @BeforeEach
    void setUp() {
        cutoverService = new ProblemCutoverService(problemReadPort, mapper, currentUserProvider);
        when(currentUserProvider.getCurrentUserId()).thenReturn("admin-1");
        ReflectionTestUtils.setField(cutoverService, "dubboProvider", dubboProvider);
    }
    private static ProblemAdminRowDTO row(Long id, String slug, String title) {
        return new ProblemAdminRowDTO(
                id, slug, title, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null,
                null, null, null, null, 0L, 0L, null, null, null, 1L);
    }

    @Nested
    @DisplayName("Dubbo write path")
    class DubboPath {

        @Test
        @DisplayName("createProblem writes via Dubbo and reads back via the read port")
        void createViaDubbo() {
            CreateProblemDTO dto = new CreateProblemDTO();
            dto.setSlug("two-sum");
            dto.setTitle("Two Sum");
            when(dubboProvider.createProblem(any())).thenReturn(RpcResult.success(
                    new com.ulticode.app.api.dto.ProblemAdminViewDTO(
                            "42", "two-sum", "Two Sum", 1L, "todo"),
                    "t-1"));
            ProblemAdminRowDTO row = row(42L, "two-sum", "Two Sum");
            when(problemReadPort.findBySlug("two-sum")).thenReturn(row);
            ProblemAdminVO vo = new ProblemAdminVO();
            vo.setTitle("Two Sum");
            when(mapper.toAdminVO(row)).thenReturn(vo);

            ProblemAdminVO result = cutoverService.createProblem(dto);

            verify(dubboProvider).createProblem(argThat(command ->
                    command.trace() != null
                            && command.trace().traceId() != null
                            && !command.trace().traceId().isBlank()
                            && "admin-1".equals(command.actor().actorId())));
            verify(problemReadPort).findBySlug("two-sum");
            assertThat(result.getTitle()).isEqualTo("Two Sum");
        }

        @Test
        @DisplayName("publishProblem writes via Dubbo and reads back via the read port")
        void publishViaDubbo() {
            when(dubboProvider.publishProblem(any())).thenReturn(RpcResult.success("t-1"));
            ProblemAdminRowDTO row = row(42L, "two-sum", "Two Sum");
            when(problemReadPort.findProblem(42L)).thenReturn(row);
            ProblemAdminVO vo = new ProblemAdminVO();
            vo.setTitle("Two Sum");
            when(mapper.toAdminVO(row)).thenReturn(vo);

            ProblemAdminVO result = cutoverService.publishProblem(42L);

            verify(dubboProvider).publishProblem(any());
            verify(problemReadPort, times(2)).findProblem(42L);
            assertThat(result.getTitle()).isEqualTo("Two Sum");
        }

        @Test
        @DisplayName("updateProblem forwards the read row version")
        void updateForwardsVersion() {
            UpdateProblemDTO dto = new UpdateProblemDTO();
            dto.setTitle("Updated");
            ProblemAdminRowDTO row = row(42L, "two-sum", "Two Sum");
            when(problemReadPort.findProblem(42L)).thenReturn(row);
            when(dubboProvider.updateProblem(any())).thenReturn(RpcResult.success(
                    new com.ulticode.app.api.dto.ProblemAdminViewDTO(
                            "42", "two-sum", "Updated", 2L, "todo"),
                    "t-1"));
            ProblemAdminVO vo = new ProblemAdminVO();
            vo.setTitle("Updated");
            when(mapper.toAdminVO(row)).thenReturn(vo);

            ProblemAdminVO result = cutoverService.updateProblem(42L, dto);

            verify(dubboProvider).updateProblem(argThat(command ->
                    command.expectedVersion().equals(1L)));
            assertThat(result.getTitle()).isEqualTo("Updated");
        }

        @Test
        @DisplayName("deleteProblem forwards the read row version")
        void deleteViaDubbo() {
            when(problemReadPort.findProblem(42L)).thenReturn(row(42L, "two-sum", "Two Sum"));
            when(dubboProvider.deleteProblem(any())).thenReturn(RpcResult.success("t-1"));

            cutoverService.deleteProblem(42L);

            verify(dubboProvider).deleteProblem(argThat(command ->
                    command.expectedVersion().equals(1L)));
        }

        @Test
        @DisplayName("RPC CONTENT_NOT_FOUND maps to BusinessException(PROBLEM_NOT_FOUND)")
        void mapsNotFound() {
            when(problemReadPort.findProblem(99L)).thenReturn(row(99L, "missing", "Missing"));
            when(dubboProvider.publishProblem(any())).thenReturn(RpcResult.failure(
                    new RpcResult.ErrorPayload("app", 40401, "not found"), "t-1"));

            assertThatThrownBy(() -> cutoverService.publishProblem(99L))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(AdminErrorCode.PROBLEM_NOT_FOUND);
        }

        @Test
        @DisplayName("RPC CONTENT_STATE_CONFLICT maps to BusinessException(CONFLICT)")
        void mapsConflict() {
            when(problemReadPort.findProblem(99L)).thenReturn(row(99L, "missing", "Missing"));
            when(dubboProvider.publishProblem(any())).thenReturn(RpcResult.failure(
                    new RpcResult.ErrorPayload("app", 40902, "conflict"), "t-1"));

            assertThatThrownBy(() -> cutoverService.publishProblem(99L))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(AdminErrorCode.CONFLICT);
        }
    }
}
