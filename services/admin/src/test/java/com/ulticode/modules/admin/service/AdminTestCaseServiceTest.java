package com.ulticode.modules.admin.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.admin.error.AdminErrorCode;
import com.ulticode.common.uuid.UuidGenerator;
import com.ulticode.modules.admin.dto.testcase.BulkImportResponse;
import com.ulticode.modules.admin.dto.testcase.BulkImportTestCasesDTO;
import com.ulticode.modules.admin.dto.testcase.CreateTestCaseDTO;
import com.ulticode.modules.admin.dto.testcase.UpdateTestCaseDTO;
import com.ulticode.app.api.dto.ProblemAdminRowDTO;
import com.ulticode.app.api.dto.ProblemAdminTestCaseDTO;
import com.ulticode.app.api.service.ProblemAdminReadPort;
import com.ulticode.app.api.service.TestCaseOwnerPort;
import com.ulticode.app.api.service.TestCaseOwnerPort.TestCaseOrder;
import com.ulticode.app.api.service.TestCaseOwnerPort.TestCaseWrite;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AdminTestCaseService}.
 *
 * <p>The test-case authoring seam is security-sensitive (hidden judge data), so
 * every write path is covered: create, partial update, delete, bulk import
 * (append vs replace), reorder (with duplicate-id rejection), export, and the
 * JSON-input validation guard. Mappers are mocked; a real {@link ObjectMapper}
 * exercises the validation parser.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AdminTestCaseService")
class AdminTestCaseServiceTest {

    private static final Long PROBLEM_ID = 1L;

    @Mock private ProblemAdminReadPort problemReadPort;
    @Mock private TestCaseOwnerPort testCaseOwnerPort;
    @Mock private UuidGenerator uuidGenerator;

    private AdminTestCaseService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-07-17T00:00:00Z"), ZoneOffset.UTC);
        service = new AdminTestCaseService(problemReadPort, testCaseOwnerPort, new ObjectMapper(),
                clock, uuidGenerator);
        when(uuidGenerator.newId()).thenReturn("test-uuid");
    }

    private void problemExists() {
        when(problemReadPort.findProblem(PROBLEM_ID)).thenReturn(new ProblemAdminRowDTO(
                PROBLEM_ID, "two-sum", "Two Sum", null, null, null, null, null,
                null, null, null, null, null, null, null, null, null,
                null, null, null, null, 0L, 0L, null, null, null));
    }

    private CreateTestCaseDTO newCase(String input, String output) {
        CreateTestCaseDTO dto = new CreateTestCaseDTO();
        dto.setIsSample(false);
        dto.setIsHidden(true);
        dto.setInputText(input);
        dto.setOutputText(output);
        return dto;
    }

    private ProblemAdminTestCaseDTO existingCase(String id) {
        return new ProblemAdminTestCaseDTO(
                id, PROBLEM_ID, null, null, null, null, null, null, null, null,
                null, null, null, null);
    }

    @Nested
    @DisplayName("createTestCase")
    class Create {

        @Test
        @DisplayName("inserts a new test case when the problem exists")
        void createsTestCase() {
            problemExists();
            CreateTestCaseDTO dto = newCase("1\n", "1\n");

            ProblemAdminTestCaseDTO created = service.createTestCase(PROBLEM_ID, dto);

            assertThat(created.problemId()).isEqualTo(PROBLEM_ID);
            assertThat(created.isHidden()).isTrue();
            assertThat(created.testOrder()).isZero();
            assertThat(created.id()).isEqualTo("test-uuid".replace("-", ""));
            verify(testCaseOwnerPort).insertTestCase(any(TestCaseWrite.class));
        }

        @Test
        @DisplayName("throws PROBLEM_NOT_FOUND when the owning problem is missing")
        void rejectsMissingProblem() {
            when(problemReadPort.findProblem(PROBLEM_ID)).thenReturn(null);

            assertThatThrownBy(() -> service.createTestCase(PROBLEM_ID, newCase("a", "b")))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(AdminErrorCode.PROBLEM_NOT_FOUND);
            verify(testCaseOwnerPort, never()).insertTestCase(any(TestCaseWrite.class));
        }

        @Test
        @DisplayName("rejects malformed inputs JSON before any write")
        void rejectsInvalidInputsJson() {
            problemExists();
            CreateTestCaseDTO dto = newCase("1\n", "1\n");
            dto.setInputs("{not valid json");

            assertThatThrownBy(() -> service.createTestCase(PROBLEM_ID, dto))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(AdminErrorCode.VALIDATION_FAILED);
            verify(testCaseOwnerPort, never()).insertTestCase(any(TestCaseWrite.class));
        }

        @Test
        @DisplayName("rejects (true,true) scope before any write")
        void rejectsIllegalTrueTrueScope() {
            problemExists();
            CreateTestCaseDTO dto = new CreateTestCaseDTO();
            dto.setIsSample(true);
            dto.setIsHidden(true);
            dto.setInputText("1\n");
            dto.setOutputText("1\n");

            assertThatThrownBy(() -> service.createTestCase(PROBLEM_ID, dto))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(AdminErrorCode.TEST_CASE_INVALID_SCOPE);
            verify(testCaseOwnerPort, never()).insertTestCase(any(TestCaseWrite.class));
        }

        @Test
        @DisplayName("rejects (false,false) scope before any write")
        void rejectsDraftFalseFalseScope() {
            problemExists();
            CreateTestCaseDTO dto = new CreateTestCaseDTO();
            dto.setIsSample(false);
            dto.setIsHidden(false);
            dto.setInputText("1\n");
            dto.setOutputText("1\n");

            assertThatThrownBy(() -> service.createTestCase(PROBLEM_ID, dto))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(AdminErrorCode.TEST_CASE_INVALID_SCOPE);
            verify(testCaseOwnerPort, never()).insertTestCase(any(TestCaseWrite.class));
        }

        @Test
        @DisplayName("defaults isHidden=!isSample when isHidden is null (back-compat for partial payloads)")
        void defaultsHiddenFromSampleWhenMissing() {
            problemExists();
            CreateTestCaseDTO dto = new CreateTestCaseDTO();
            dto.setIsSample(true);
            // isHidden intentionally left null: a non-frontend admin caller that
            // sends only isSample=true should still produce a valid SAMPLE row,
            // not the (true, isHidden-defaults-to-false-vue-service) shape pre-fix.
            // The @NotNull constraint is enforced at the controller layer; the
            // service still defends in depth for direct service callers (tests,
            // internal admin scripts).
            dto.setInputText("1\n");
            dto.setOutputText("1\n");

            ProblemAdminTestCaseDTO created = service.createTestCase(PROBLEM_ID, dto);

            assertThat(created.isSample()).isTrue();
            assertThat(created.isHidden()).isFalse();
            verify(testCaseOwnerPort).insertTestCase(any(TestCaseWrite.class));
        }
    }

    @Nested
    @DisplayName("bulkImportTestCases")
    class BulkImport {

        @Test
        @DisplayName("append mode inserts every case and never deletes (replaceExisting=null)")
        void appendsWithoutDeleting() {
            problemExists();
            BulkImportTestCasesDTO dto = new BulkImportTestCasesDTO();
            dto.setTestCases(List.of(newCase("1\n", "1\n"), newCase("2\n", "2\n")));

            BulkImportResponse response = service.bulkImportTestCases(PROBLEM_ID, dto);

            assertThat(response.getCount()).isEqualTo(2);
            verify(testCaseOwnerPort, never()).deleteAllForProblem(any());
            verify(testCaseOwnerPort, times(2)).insertTestCase(any(TestCaseWrite.class));
        }

        @Test
        @DisplayName("replace mode deletes existing cases before insert within the same call")
        void replacesExistingBeforeInsert() {
            problemExists();
            BulkImportTestCasesDTO dto = new BulkImportTestCasesDTO();
            dto.setReplaceExisting(true);
            dto.setTestCases(List.of(newCase("1\n", "1\n")));

            BulkImportResponse response = service.bulkImportTestCases(PROBLEM_ID, dto);

            assertThat(response.getCount()).isEqualTo(1);
            verify(testCaseOwnerPort).replaceAllForProblem(eq(PROBLEM_ID), any());
            verify(testCaseOwnerPort, never()).deleteAllForProblem(any());
            verify(testCaseOwnerPort, never()).insertTestCase(any(TestCaseWrite.class));
        }

        @Test
        @DisplayName("rejects a batch entry with (true,true) scope before any insert")
        void rejectsInvalidScopeInBatch() {
            problemExists();
            CreateTestCaseDTO bad = new CreateTestCaseDTO();
            bad.setIsSample(true);
            bad.setIsHidden(true);
            bad.setInputText("1\n");
            bad.setOutputText("1\n");
            BulkImportTestCasesDTO dto = new BulkImportTestCasesDTO();
            // Validation completes before replacement is sent to the owner, so
            // mocks see no delete/replace/insert call.
            dto.setTestCases(List.of(bad, newCase("1\n", "1\n")));

            assertThatThrownBy(() -> service.bulkImportTestCases(PROBLEM_ID, dto))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(AdminErrorCode.TEST_CASE_INVALID_SCOPE);
            verify(testCaseOwnerPort, never()).insertTestCase(any(TestCaseWrite.class));
            verify(testCaseOwnerPort, never()).replaceAllForProblem(any(), any());
        }

        @Test
        @DisplayName("throws PROBLEM_NOT_FOUND when the owning problem is missing")
        void rejectsMissingProblem() {
            when(problemReadPort.findProblem(PROBLEM_ID)).thenReturn(null);
            BulkImportTestCasesDTO dto = new BulkImportTestCasesDTO();
            dto.setTestCases(List.of(newCase("1\n", "1\n")));

            assertThatThrownBy(() -> service.bulkImportTestCases(PROBLEM_ID, dto))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(AdminErrorCode.PROBLEM_NOT_FOUND);
            verify(testCaseOwnerPort, never()).deleteAllForProblem(any());
            verify(testCaseOwnerPort, never()).replaceAllForProblem(any(), any());
            verify(testCaseOwnerPort, never()).insertTestCase(any(TestCaseWrite.class));
        }
    }

    @Nested
    @DisplayName("reorderTestCases")
    class Reorder {

        @Test
        @DisplayName("assigns sequential test_order by list index")
        void reordersByIndex() {
            problemExists();
            when(problemReadPort.findTestCasesByIds(PROBLEM_ID, List.of("a", "b")))
                    .thenReturn(List.of(existingCase("a"), existingCase("b")));

            service.reorderTestCases(PROBLEM_ID, List.of("a", "b"));
            verify(problemReadPort).findTestCasesByIds(PROBLEM_ID, List.of("a", "b"));
            verify(problemReadPort, never()).getTestCase(any(), any());

            verify(testCaseOwnerPort).updateTestOrders(List.of(
                    new TestCaseOrder("a", 0, LocalDateTime.of(2026, 7, 17, 0, 0)),
                    new TestCaseOrder("b", 1, LocalDateTime.of(2026, 7, 17, 0, 0))));
        }

        @Test
        @DisplayName("uses one batch read and rejects missing IDs before writing")
        void rejectsMissingIdWithoutPerIdReadsOrWrites() {
            problemExists();
            when(problemReadPort.findTestCasesByIds(PROBLEM_ID, List.of("a", "missing")))
                    .thenReturn(List.of(existingCase("a")));

            assertThatThrownBy(() -> service.reorderTestCases(PROBLEM_ID, List.of("a", "missing")))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(AdminErrorCode.TEST_CASE_NOT_FOUND);

            verify(problemReadPort).findTestCasesByIds(PROBLEM_ID, List.of("a", "missing"));
            verify(problemReadPort, never()).getTestCase(any(), any());
            verify(testCaseOwnerPort, never()).updateTestOrders(any());
        }

        @Test
        @DisplayName("rejects duplicate ids with BAD_REQUEST")
        void rejectsDuplicateIds() {
            problemExists();

            assertThatThrownBy(() -> service.reorderTestCases(PROBLEM_ID, List.of("a", "a")))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(AdminErrorCode.BAD_REQUEST);
            verify(testCaseOwnerPort, never()).updateTestOrders(any());
        }
    }

    @Nested
    @DisplayName("updateTestCase / deleteTestCase / export")
    class MutationsAndExport {

        @Test
        @DisplayName("partial update applies only supplied fields while preserving XOR scope")
        void partialUpdate() {
            problemExists();
            ProblemAdminTestCaseDTO existing = existingCase("a");
            // Start from a valid HIDDEN scope (false, true).
            existing = new ProblemAdminTestCaseDTO(
                    existing.id(), existing.problemId(), false, true, existing.testOrder(),
                    existing.inputText(), existing.outputText(), existing.inputs(),
                    existing.explanation(), existing.constraints(), existing.createdAt(), existing.updatedAt(),
                    null, null);
            when(problemReadPort.getTestCase(PROBLEM_ID, "a")).thenReturn(existing);

            // Flip the whole scope to SAMPLE by sending both flags together
            // (the wire contract the CaseScope seam emits).
            UpdateTestCaseDTO dto = new UpdateTestCaseDTO();
            dto.setIsSample(true);
            dto.setIsHidden(false);

            ProblemAdminTestCaseDTO updated = service.updateTestCase(PROBLEM_ID, "a", dto);

            assertThat(updated.isSample()).isTrue();
            assertThat(updated.isHidden()).isFalse();
            verify(testCaseOwnerPort).updateTestCase(any(TestCaseWrite.class));
        }

        @Test
        @DisplayName("updateTestCase rejects a partial update that lands on the (false,false) draft scope")
        void rejectsUpdateToDraftScope() {
            problemExists();
            ProblemAdminTestCaseDTO existing = existingCase("a");
            existing = new ProblemAdminTestCaseDTO(
                    existing.id(), existing.problemId(), false, true, existing.testOrder(),
                    existing.inputText(), existing.outputText(), existing.inputs(),
                    existing.explanation(), existing.constraints(), existing.createdAt(), existing.updatedAt(),
                    null, null);
            when(problemReadPort.getTestCase(PROBLEM_ID, "a")).thenReturn(existing);

            // Send only isHidden=false → merged result (false, false) is invalid.
            UpdateTestCaseDTO dto = new UpdateTestCaseDTO();
            dto.setIsHidden(false);

            assertThatThrownBy(() -> service.updateTestCase(PROBLEM_ID, "a", dto))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(AdminErrorCode.TEST_CASE_INVALID_SCOPE);
            verify(testCaseOwnerPort, never()).updateTestCase(any(TestCaseWrite.class));
        }

        @Test
        @DisplayName("updateTestCase rejects a partial update that lands on the (true,true) illegal scope")
        void rejectsUpdateToIllegalScope() {
            problemExists();
            ProblemAdminTestCaseDTO existing = existingCase("a");
            existing = new ProblemAdminTestCaseDTO(
                    existing.id(), existing.problemId(), true, false, existing.testOrder(),
                    existing.inputText(), existing.outputText(), existing.inputs(),
                    existing.explanation(), existing.constraints(), existing.createdAt(), existing.updatedAt(),
                    null, null);
            when(problemReadPort.getTestCase(PROBLEM_ID, "a")).thenReturn(existing);

            UpdateTestCaseDTO dto = new UpdateTestCaseDTO();
            dto.setIsHidden(true);

            assertThatThrownBy(() -> service.updateTestCase(PROBLEM_ID, "a", dto))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(AdminErrorCode.TEST_CASE_INVALID_SCOPE);
            verify(testCaseOwnerPort, never()).updateTestCase(any(TestCaseWrite.class));
        }

        @Test
        @DisplayName("delete removes the resolved case")
        void deletesCase() {
            problemExists();
            when(problemReadPort.getTestCase(PROBLEM_ID, "a")).thenReturn(existingCase("a"));

            service.deleteTestCase(PROBLEM_ID, "a");

            verify(testCaseOwnerPort).deleteTestCase("a");
        }

        @Test
        @DisplayName("getTestCase throws TEST_CASE_NOT_FOUND when id belongs another problem")
        void rejectsCrossProblemCase() {
            problemExists();
            // The ownership (problemId match) check lives in the owner provider:
            // getTestCase returns null for a row owned by a different problem, and
            // the admin edge maps that to TEST_CASE_NOT_FOUND.
            when(problemReadPort.getTestCase(PROBLEM_ID, "a")).thenReturn(null);

            assertThatThrownBy(() -> service.getTestCase(PROBLEM_ID, "a"))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(AdminErrorCode.TEST_CASE_NOT_FOUND);
        }

        @Test
        @DisplayName("export returns cases ordered by test_order")
        void exportsOrdered() {
            problemExists();
            ProblemAdminTestCaseDTO first = existingCase("a");
            first = new ProblemAdminTestCaseDTO(
                    first.id(), first.problemId(), first.isSample(), first.isHidden(), 0,
                    first.inputText(), first.outputText(), first.inputs(),
                    first.explanation(), first.constraints(), first.createdAt(), first.updatedAt(),
                    null, null);
            ProblemAdminTestCaseDTO second = existingCase("b");
            second = new ProblemAdminTestCaseDTO(
                    second.id(), second.problemId(), second.isSample(), second.isHidden(), 1,
                    second.inputText(), second.outputText(), second.inputs(),
                    second.explanation(), second.constraints(), second.createdAt(), second.updatedAt(),
                    null, null);
            when(problemReadPort.exportTestCases(PROBLEM_ID)).thenReturn(List.of(first, second));

            List<ProblemAdminTestCaseDTO> exported = service.exportTestCases(PROBLEM_ID);

            assertThat(exported).hasSize(2);
            assertThat(exported.get(0).id()).isEqualTo("a");
        }
    }
}
