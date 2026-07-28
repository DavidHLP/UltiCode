package com.ulticode.modules.admin.service;

import com.ulticode.app.api.dto.ProblemAdminViewDTO;
import com.ulticode.app.api.service.ProblemAdministrationService;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.common.rpc.RpcResult;
import com.ulticode.common.error.BaseErrorCode;
import com.ulticode.modules.problem.dto.CreateProblemDTO;
import com.ulticode.modules.problem.dto.ProblemVO;
import com.ulticode.modules.problem.dto.UpdateProblemDTO;
import com.ulticode.modules.problem.service.ProblemService;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * P4-CUTOVER-001: unit test for {@link ProblemCutoverService}.
 *
 * <p>Pins both routing paths:
 * <ul>
 *   <li><b>flag=off (default):</b> every method delegates to the local
 *       {@link ProblemService} — zero behavioral change from Phase 3.</li>
 *   <li><b>flag=on:</b> writes go through the Dubbo Provider; read-back
 *       via local {@link ProblemService}. RPC errors map back to
 *       {@link BusinessException}.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ProblemCutoverService")
class ProblemCutoverServiceTest {

    @Mock
    private ProblemService problemService;

    @Mock
    private ProblemAdministrationService dubboProvider;

    private ProblemCutoverService cutoverService;

    @BeforeEach
    void setUp() {
        cutoverService = new ProblemCutoverService(problemService);
        // Inject the @DubboReference mock field by reflection
        ReflectionTestUtils.setField(cutoverService, "dubboProvider", dubboProvider);
    }

    private void flagOn() {
        ReflectionTestUtils.setField(cutoverService, "dubboEnabled", true);
    }

    private void flagOff() {
        ReflectionTestUtils.setField(cutoverService, "dubboEnabled", false);
    }

    @Nested
    @DisplayName("flag=off (local path)")
    class LocalPath {

        @BeforeEach
        void setUp() {
            flagOff();
        }

        @Test
        @DisplayName("createProblem delegates to local ProblemService")
        void createLocal() {
            CreateProblemDTO dto = new CreateProblemDTO();
            dto.setSlug("test");
            dto.setTitle("Test");
            ProblemVO vo = new ProblemVO();
            when(problemService.createProblem(dto)).thenReturn(vo);

            ProblemVO result = cutoverService.createProblem(dto);

            assertThat(result).isSameAs(vo);
            verify(dubboProvider, never()).createProblem(any());
        }

        @Test
        @DisplayName("publishProblem delegates to local ProblemService")
        void publishLocal() {
            ProblemVO vo = new ProblemVO();
            when(problemService.publishProblem(1L)).thenReturn(vo);

            ProblemVO result = cutoverService.publishProblem(1L);

            assertThat(result).isSameAs(vo);
            verify(dubboProvider, never()).publishProblem(any());
        }

        @Test
        @DisplayName("deleteProblem delegates to local ProblemService")
        void deleteLocal() {
            cutoverService.deleteProblem(1L);

            verify(problemService).deleteProblem(1L);
            verify(dubboProvider, never()).publishProblem(any());
        }
    }

    @Nested
    @DisplayName("flag=on (Dubbo path)")
    class DubboPath {

        @BeforeEach
        void setUp() {
            flagOn();
        }

        @Test
        @DisplayName("createProblem writes via Dubbo, reads back via local service")
        void createViaDubbo() {
            CreateProblemDTO dto = new CreateProblemDTO();
            dto.setSlug("two-sum");
            dto.setTitle("Two Sum");

            ProblemAdminViewDTO adminView = new ProblemAdminViewDTO(
                    "42", "two-sum", "Two Sum", 1L, "todo");
            when(dubboProvider.createProblem(any())).thenReturn(
                    RpcResult.success(adminView, "t-1"));

            ProblemVO vo = new ProblemVO();
            vo.setSlug("two-sum");
            vo.setTitle("Two Sum");
            when(problemService.getProblemBySlug("two-sum")).thenReturn(vo);

            ProblemVO result = cutoverService.createProblem(dto);

            verify(dubboProvider).createProblem(any());
            verify(problemService).getProblemBySlug("two-sum");
            assertThat(result.getTitle()).isEqualTo("Two Sum");
        }

        @Test
        @DisplayName("publishProblem writes via Dubbo, reads back via local service")
        void publishViaDubbo() {
            when(dubboProvider.publishProblem(any())).thenReturn(
                    RpcResult.success("t-1"));
            ProblemVO vo = new ProblemVO();
            when(problemService.getProblemById(42L)).thenReturn(vo);

            ProblemVO result = cutoverService.publishProblem(42L);

            verify(dubboProvider).publishProblem(any());
            verify(problemService).getProblemById(42L);
            assertThat(result).isSameAs(vo);
        }

        @Test
        @DisplayName("RPC error CONTENT_NOT_FOUND maps to BusinessException(PROBLEM_NOT_FOUND)")
        void mapsNotFound() {
            when(dubboProvider.publishProblem(any())).thenReturn(
                    RpcResult.failure(
                            new RpcResult.ErrorPayload("app", 40401, "not found"),
                            "t-1"));

            assertThatThrownBy(() -> cutoverService.publishProblem(99L))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.PROBLEM_NOT_FOUND);
        }

        @Test
        @DisplayName("RPC error CONTENT_STATE_CONFLICT maps to BusinessException(CONFLICT)")
        void mapsConflict() {
            when(dubboProvider.publishProblem(any())).thenReturn(
                    RpcResult.failure(
                            new RpcResult.ErrorPayload("app", 40902, "conflict"),
                            "t-1"));

            assertThatThrownBy(() -> cutoverService.publishProblem(99L))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.CONFLICT);
        }
    }
}
