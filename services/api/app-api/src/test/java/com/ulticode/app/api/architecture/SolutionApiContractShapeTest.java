package com.ulticode.app.api.architecture;

import com.ulticode.app.api.service.SolutionAdminReadPort;
import com.ulticode.app.api.service.SolutionCommentOwnerPort;
import com.ulticode.app.api.service.SolutionCommentReadPort;
import com.ulticode.app.api.service.SolutionOwnerPort;
import com.ulticode.app.api.service.SolutionReadPort;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SolutionApiContractShapeTest {

    private static final List<Class<?>> CONTRACTS = List.of(
            SolutionAdminReadPort.class,
            SolutionCommentReadPort.class,
            SolutionOwnerPort.class,
            SolutionCommentOwnerPort.class,
            SolutionReadPort.class);

    @Test
    void contracts_are_entity_free_and_expose_typed_solution_seams() {
        for (Class<?> contract : CONTRACTS) {
            assertThat(Arrays.stream(contract.getDeclaredMethods())
                    .flatMap(method -> Arrays.stream(method.getParameterTypes()))
                    .map(Class::getName)
                    .filter(name -> name.startsWith("com.ulticode.modules.")))
                    .as(contract.getName())
                    .isEmpty();
        }

        assertThat(methodNames(SolutionAdminReadPort.class))
                .containsExactlyInAnyOrder("page", "getById");
        assertThat(methodNames(SolutionCommentReadPort.class))
                .containsExactlyInAnyOrder("page", "getById");
        assertThat(methodNames(SolutionOwnerPort.class))
                .containsExactlyInAnyOrder("flagSolution", "unflagSolution", "deleteSolution",
                        "setPublished", "findExistingIds", "resolveAuthorId", "updateVoteCounts");
        assertThat(methodNames(SolutionCommentOwnerPort.class))
                .containsExactlyInAnyOrder("flagComment", "unflagComment", "resolveAuthorId",
                        "resolveSolutionId", "deleteComment");
    }

    @Test
    void admin_rows_and_pages_are_records_with_stable_fields() {
        assertThat(recordComponentNames(SolutionAdminReadPort.SolutionAdminRow.class))
                .containsExactly("id", "problemId", "userId", "title", "content", "summary", "language",
                        "tags", "views", "isPublished", "publishedAt", "publishedBy", "isFlagged",
                        "flaggedReason", "flaggedAt", "isDeleted", "deletedAt", "deletedBy", "createdAt",
                        "updatedAt");
        assertThat(recordComponentNames(SolutionCommentReadPort.SolutionCommentRow.class))
                .containsExactly("id", "content", "createdAt", "updatedAt", "userId", "parentId", "solutionId",
                        "isFlagged", "flaggedReason", "flaggedAt", "isDeleted", "deletedAt", "deletedBy");
        assertThat(recordComponentNames(SolutionAdminReadPort.SolutionAdminPage.class))
                .containsExactly("rows", "total");
        assertThat(recordComponentNames(SolutionCommentReadPort.SolutionCommentPage.class))
                .containsExactly("rows", "total");
    }

    private static List<String> methodNames(Class<?> type) {
        return Arrays.stream(type.getDeclaredMethods()).map(Method::getName).toList();
    }

    private static List<String> recordComponentNames(Class<?> type) {
        RecordComponent[] components = type.getRecordComponents();
        assertThat(components).as(type.getName()).isNotNull();
        return Arrays.stream(components).map(RecordComponent::getName).toList();
    }
}
