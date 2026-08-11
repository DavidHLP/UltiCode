package com.ulticode.app.api.architecture;

import com.ulticode.app.api.dto.ProblemCompletionReportDTO;
import com.ulticode.app.api.dto.ProblemIndexDTO;
import com.ulticode.app.api.dto.ProblemJudgingCaseDTO;
import com.ulticode.app.api.dto.ProblemListItemDTO;
import com.ulticode.app.api.service.ProblemAnalyticsReadPort;
import com.ulticode.app.api.service.ProblemAdminReadPort;
import com.ulticode.app.api.service.ProblemDifficultyReadPort;
import com.ulticode.app.api.service.ProblemInteractionQueryPort;
import com.ulticode.app.api.service.ProblemJudgingCaseReadPort;
import com.ulticode.app.api.service.ProblemListReadPort;
import com.ulticode.app.api.service.ProblemOwnerPort;
import com.ulticode.app.api.service.ProblemSearchReadPort;
import com.ulticode.app.api.service.TestCaseOwnerPort;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.RecordComponent;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contract-shape tests for the first Problem relocation seam.
 *
 * <p>These tests intentionally inspect only public Java shapes. They prevent
 * implementation/entity leakage while pinning the owner-port signatures that
 * the later app-side adapters must implement.
 */
class ProblemApiContractShapeTest {

    @Test
    void promoted_problem_owner_signatures_are_exact() throws Exception {
        assertMethod(ProblemOwnerPort.class, "resolveAuthorId", String.class, String.class);
        assertMethod(ProblemOwnerPort.class, "updateModerationFlag", void.class,
                String.class, boolean.class, String.class);
        assertMethod(ProblemOwnerPort.class, "flagProblem", void.class,
                Long.class, String.class, String.class);
        assertMethod(ProblemOwnerPort.class, "moderateProblem", void.class,
                Long.class, String.class, String.class, String.class);
        assertMethod(ProblemOwnerPort.class, "restoreDeletedByIds", int.class, List.class);
        assertMethod(ProblemOwnerPort.class, "moderateProblems", int.class,
                List.class, String.class, String.class, String.class);
        assertMethod(ProblemOwnerPort.class, "updateDifficulty", void.class,
                Long.class, String.class);
        assertMethod(ProblemOwnerPort.class, "insertImportedProblem", void.class,
                String.class, String.class, String.class, String.class,
                Boolean.class, Boolean.class);
        assertMethod(ProblemOwnerPort.class, "applyImportedUpdate", void.class,
                Long.class, String.class, String.class, String.class,
                Boolean.class, Boolean.class);
        assertMethod(ProblemOwnerPort.class, "applyImportedBatch", List.class, List.class);

        assertThat(ProblemOwnerPort.ImportWriteRequest.class.isRecord()).isTrue();
        assertThat(Serializable.class.isAssignableFrom(ProblemOwnerPort.ImportWriteRequest.class))
                .isTrue();
        assertThat(Arrays.stream(ProblemOwnerPort.ImportWriteRequest.class.getRecordComponents())
                .map(RecordComponent::getName).toList())
                .containsExactly("key", "create", "id", "slug", "title", "difficulty",
                        "status", "isPremium", "isPublished");
        Assertions.<Class<?>>assertThat(Arrays.stream(ProblemOwnerPort.ImportWriteRequest.class.getRecordComponents())
                .map(RecordComponent::getType).toList())
                .containsExactly(String.class, boolean.class, Long.class, String.class,
                        String.class, String.class, String.class, Boolean.class, Boolean.class);
        assertThat(ProblemOwnerPort.ImportWriteResult.class.isRecord()).isTrue();
        assertThat(Serializable.class.isAssignableFrom(ProblemOwnerPort.ImportWriteResult.class))
                .isTrue();
    }

    @Test
    void promoted_test_case_owner_contract_preserves_write_shape() throws Exception {
        assertMethod(TestCaseOwnerPort.class, "insertTestCase", void.class,
                TestCaseOwnerPort.TestCaseWrite.class);
        assertMethod(TestCaseOwnerPort.class, "updateTestCase", void.class,
                TestCaseOwnerPort.TestCaseWrite.class);
        assertMethod(TestCaseOwnerPort.class, "deleteTestCase", void.class, String.class);
        assertMethod(TestCaseOwnerPort.class, "deleteAllForProblem", int.class, Long.class);
        assertMethod(TestCaseOwnerPort.class, "updateTestOrder", void.class,
                String.class, int.class, LocalDateTime.class);

        assertMethod(TestCaseOwnerPort.class, "updateTestOrders", void.class, List.class);
        assertThat(TestCaseOwnerPort.TestCaseOrder.class.isRecord()).isTrue();
        assertThat(Serializable.class.isAssignableFrom(TestCaseOwnerPort.TestCaseOrder.class))
                .isTrue();
        assertThat(Arrays.stream(TestCaseOwnerPort.TestCaseOrder.class.getRecordComponents())
                .map(RecordComponent::getName).toList())
                .containsExactly("id", "testOrder", "updatedAt");

        RecordComponent[] components = TestCaseOwnerPort.TestCaseWrite.class.getRecordComponents();
        assertThat(Arrays.stream(components).map(RecordComponent::getName).toList())
                .containsExactly(
                        "id", "problemId", "isSample", "isHidden", "testOrder",
                        "inputText", "outputText", "explanation", "constraints", "inputs",
                        "createdAt", "updatedAt");
        Assertions.<Class<?>>assertThat(Arrays.stream(components).map(RecordComponent::getType).toList())
                .containsExactlyElementsOf(Arrays.<Class<?>>asList(
                        String.class, Long.class, boolean.class, boolean.class, int.class,
                        String.class, String.class, String.class, String.class, String.class,
                        LocalDateTime.class, LocalDateTime.class));
    }

    @Test
    void problem_read_ports_match_the_verified_consumer_seams() throws Exception {
        assertMethod(ProblemAdminReadPort.class, "findBySlugs", List.class, Collection.class);
        assertMethod(ProblemAdminReadPort.class, "findTestCasesByIds", List.class,
                Long.class, Collection.class);
        assertMethod(ProblemListReadPort.class, "findByIds", List.class, Collection.class);
        assertMethod(ProblemSearchReadPort.class, "searchForIndex", List.class,
                String.class, int.class);
        assertMethod(ProblemDifficultyReadPort.class, "countByDifficulty", List.class);
        assertMethod(ProblemJudgingCaseReadPort.class, "loadCases", List.class, long.class);
        assertMethod(ProblemInteractionQueryPort.class, "countFavorites", int.class, Long.class);
        assertMethod(ProblemInteractionQueryPort.class, "findViewerReaction", String.class,
                String.class, Long.class);
        assertMethod(ProblemAnalyticsReadPort.class, "loadProblemCompletionReport",
                ProblemCompletionReportDTO.class, Integer.class);
    }

    @Test
    void app_api_problem_contracts_are_plain_java_and_entity_free() {
        List<Class<?>> contracts = List.of(
                ProblemOwnerPort.class,
                TestCaseOwnerPort.class,
                ProblemListReadPort.class,
                ProblemSearchReadPort.class,
                ProblemDifficultyReadPort.class,
                ProblemJudgingCaseReadPort.class,
                ProblemInteractionQueryPort.class,
                ProblemAdminReadPort.class,
                ProblemAnalyticsReadPort.class);

        for (Class<?> contract : contracts) {
            assertThat(contract.isInterface()).isTrue();
            assertThat(contract.getAnnotations())
                    .as(contract.getName() + " must not carry framework annotations")
                    .isEmpty();
            for (Method method : contract.getDeclaredMethods()) {
                assertThat(method.getAnnotations())
                        .as(contract.getName() + "#" + method.getName()
                                + " must not carry framework annotations")
                        .isEmpty();
                for (Parameter parameter : method.getParameters()) {
                    assertThat(parameter.getType().getName())
                            .as(contract.getName() + "#" + method.getName()
                                    + " must not accept an implementation/entity type")
                            .doesNotStartWith("com.ulticode.modules.");
                }
                assertThat(method.getReturnType().getName())
                        .as(contract.getName() + "#" + method.getName()
                                + " must not return an implementation/entity type")
                        .doesNotStartWith("com.ulticode.modules.");
            }
        }
    }

    @Test
    void read_dto_collections_are_non_null_and_wire_shapes_are_records() {
        ProblemListItemDTO item = new ProblemListItemDTO(
                1L, "two-sum", "Two Sum", "EASY", "PUBLISHED",
                null, Boolean.FALSE, Boolean.FALSE, null);
        assertThat(item.tags()).isEmpty();

        ProblemCompletionReportDTO report = new ProblemCompletionReportDTO(
                0L, 0L, 0.0, null, null, null, null);
        assertThat(report.byDifficulty()).isEmpty();
        assertThat(report.byTag()).isEmpty();
        assertThat(report.trendingProblems()).isEmpty();
        assertThat(report.hardestProblems()).isEmpty();

        assertThat(ProblemListItemDTO.class.isRecord()).isTrue();
        assertThat(ProblemIndexDTO.class.isRecord()).isTrue();
        assertThat(ProblemJudgingCaseDTO.class.isRecord()).isTrue();
        assertThat(ProblemCompletionReportDTO.class.isRecord()).isTrue();
        assertThat(Arrays.stream(ProblemJudgingCaseDTO.class.getRecordComponents())
                .map(RecordComponent::getName).toList())
                .containsExactly("id", "testOrder", "inputText", "outputText", "inputs",
                        "isHidden", "isSample");
    }

    private static void assertMethod(Class<?> owner, String name, Class<?> returnType,
                                     Class<?>... parameterTypes) throws Exception {
        Method method = owner.getDeclaredMethod(name, parameterTypes);
        assertThat(method.getReturnType())
                .as(owner.getSimpleName() + "#" + name + " return type")
                .isEqualTo(returnType);
    }
}
