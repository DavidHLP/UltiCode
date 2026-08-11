package com.ulticode.modules.admin.service;

import com.ulticode.admin.error.AdminErrorCode;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.response.PageResult;
import com.ulticode.common.response.PaginationRequest;
import com.ulticode.common.uuid.UuidGenerator;
import com.ulticode.modules.admin.dto.testcase.BulkImportResponse;
import com.ulticode.modules.admin.dto.testcase.BulkImportTestCasesDTO;
import com.ulticode.modules.admin.dto.testcase.CreateTestCaseDTO;
import com.ulticode.modules.admin.dto.testcase.UpdateTestCaseDTO;
import com.ulticode.app.api.dto.ProblemAdminTestCaseDTO;
import com.ulticode.app.api.service.ProblemAdminReadPort;
import com.ulticode.app.api.service.TestCaseOwnerPort;
import com.ulticode.app.api.service.TestCaseOwnerPort.TestCaseOrder;
import com.ulticode.app.api.service.TestCaseOwnerPort.TestCaseWrite;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import org.springframework.util.StringUtils;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Admin service for test case CRUD operations.
 *
 * <p>ADMIN-003: every test-case read (list / get / export) and the owning
 * problem existence check flow through the public {@link ProblemAdminReadPort}
 * contract; writes stay on {@link TestCaseOwnerPort}. The App-private
 * {@code TestCaseMapper}/{@code ProblemMapper}/{@code TestCase} entity
 * imports are gone — the row shape is the entity-free
 * {@link ProblemAdminTestCaseDTO}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminTestCaseService {

    private final ProblemAdminReadPort problemReadPort;
    private final TestCaseOwnerPort testCaseOwnerPort;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final UuidGenerator uuidGenerator;

    /** Fail fast with PROBLEM_NOT_FOUND when the owning problem does not exist. */
    private void requireProblem(Long problemId) {
        if (problemReadPort.findProblem(problemId) == null) {
            throw new BusinessException(AdminErrorCode.PROBLEM_NOT_FOUND);
        }
    }

    public PageResult<ProblemAdminTestCaseDTO> listTestCases(Long problemId, Boolean isSample, Boolean isHidden,
                                                              Integer page, Integer limit) {
        requireProblem(problemId);
        PaginationRequest pageRequest = PaginationRequest.of(page, limit);
        return problemReadPort.listTestCases(problemId, isSample, isHidden,
                pageRequest.page(), pageRequest.pageSize());
    }

    public ProblemAdminTestCaseDTO getTestCase(Long problemId, String testCaseId) {
        requireProblem(problemId);
        ProblemAdminTestCaseDTO testCase = problemReadPort.getTestCase(problemId, testCaseId);
        if (testCase == null) {
            throw new BusinessException(AdminErrorCode.TEST_CASE_NOT_FOUND);
        }
        return testCase;
    }

    private void validateInputsJson(String inputs) {
        if (inputs == null || inputs.isBlank()) {
            return;
        }
        try {
            objectMapper.readTree(inputs);
        } catch (Exception e) {
            throw new BusinessException(AdminErrorCode.VALIDATION_FAILED, "Invalid JSON in inputs field: " + e.getMessage());
        }
    }

    public ProblemAdminTestCaseDTO createTestCase(Long problemId, CreateTestCaseDTO dto) {
        requireProblem(problemId);
        return persistNewTestCase(problemId, dto);
    }

    /**
     * Build and insert a single test case. Caller is responsible for confirming
     * the owning problem exists; bulk import checks once for the whole batch
     * rather than re-querying on every item.
     */
    private TestCaseWrite buildNewTestCaseWrite(Long problemId, CreateTestCaseDTO dto) {
        validateInputsJson(dto.getInputs());
        boolean[] scope = resolveCaseScopeFlags(dto.getIsSample(), dto.getIsHidden());
        boolean isSample = scope[0];
        boolean isHidden = scope[1];
        LocalDateTime now = LocalDateTime.now(clock);

        return new TestCaseWrite(
                uuidGenerator.newId().replace("-", ""),
                problemId,
                isSample,
                isHidden,
                dto.getTestOrder() != null ? dto.getTestOrder() : 0,
                dto.getInputText(),
                dto.getOutputText(),
                dto.getExplanation(),
                dto.getConstraints(),
                dto.getInputs(),
                now,
                now);
    }

    private ProblemAdminTestCaseDTO persistNewTestCase(Long problemId, CreateTestCaseDTO dto) {
        TestCaseWrite write = buildNewTestCaseWrite(problemId, dto);
        // P3-BURNDOWN-001: write routed through the problem-module owner port;
        // no local TestCaseMapper touch.
        testCaseOwnerPort.insertTestCase(write);
        log.info("Test case created: {} for problem {}", write.id(), problemId);
        return toDto(write);
    }

    /**
     * Resolve the canonical {@code (is_sample, is_hidden)} pair from a create
     * payload. The backend test_cases table models author intent as a single
     * "CaseScope" dimension (SAMPLE or HIDDEN) — the judging pipeline's
     * {@code findActiveCasesForJudging} only certifies XOR pairs, so every
     * persisted row MUST satisfy XOR. {@link CreateTestCaseDTO#getIsSample()}
     * is {@code @NotNull}; when {@code isHidden} is omitted we default it to
     * {@code !isSample} (the inverse) so an admin that sends only one flag
     * still produces a valid scope instead of the draft {@code (false, false)}
     * or the illegal {@code (true, true)} combination.
     */
    private boolean[] resolveCaseScopeFlags(Boolean isSample, Boolean isHidden) {
        boolean sample = Boolean.TRUE.equals(isSample);
        boolean hidden = isHidden != null ? isHidden : !sample;
        if (sample == hidden) {
            throw new BusinessException(AdminErrorCode.TEST_CASE_INVALID_SCOPE);
        }
        return new boolean[]{sample, hidden};
    }

    /** Map a write command onto the read projection shape (live row: no soft-delete). */
    private static ProblemAdminTestCaseDTO toDto(TestCaseWrite write) {
        return new ProblemAdminTestCaseDTO(
                write.id(), write.problemId(), write.isSample(), write.isHidden(), write.testOrder(),
                write.inputText(), write.outputText(), write.inputs(), write.explanation(),
                write.constraints(), write.createdAt(), write.updatedAt(), null, null);
    }

    public ProblemAdminTestCaseDTO updateTestCase(Long problemId, String testCaseId, UpdateTestCaseDTO dto) {
        ProblemAdminTestCaseDTO existing = getTestCase(problemId, testCaseId);
        // Validate the supplied JSON before any partial writes touch the row.
        validateInputsJson(dto.getInputs());

        // PartialUpdate merge semantics preserved: present fields win, absent
        // (or blank for text) fields keep the persisted value.
        boolean isSample = dto.getIsSample() != null ? dto.getIsSample() : existing.isSample();
        boolean isHidden = dto.getIsHidden() != null ? dto.getIsHidden() : existing.isHidden();
        // The judging pipeline only certifies XOR (is_sample XOR is_hidden) rows,
        // so every partial update must still leave the row in SAMPLE or HIDDEN
        // scope. Reject any merge that lands on the disallowed (false,false)
        // "draft" or the illegal (true,true) combination before it persists.
        if (isSample == isHidden) {
            throw new BusinessException(AdminErrorCode.TEST_CASE_INVALID_SCOPE);
        }
        Integer testOrder = dto.getTestOrder() != null ? dto.getTestOrder()
                : (existing.testOrder() != null ? existing.testOrder() : 0);
        String inputText = hasText(dto.getInputText()) ? dto.getInputText() : existing.inputText();
        String outputText = hasText(dto.getOutputText()) ? dto.getOutputText() : existing.outputText();
        String explanation = hasText(dto.getExplanation()) ? dto.getExplanation() : existing.explanation();
        String constraints = hasText(dto.getConstraints()) ? dto.getConstraints() : existing.constraints();
        String inputs = hasText(dto.getInputs()) ? dto.getInputs() : existing.inputs();
        LocalDateTime now = LocalDateTime.now(clock);

        TestCaseWrite write = new TestCaseWrite(
                existing.id(), problemId, isSample, isHidden, testOrder,
                inputText, outputText, explanation, constraints, inputs,
                existing.createdAt(), now);

        testCaseOwnerPort.updateTestCase(write);
        log.info("Test case updated: {} for problem {}", testCaseId, problemId);
        return toDto(write);
    }

    private static boolean hasText(String value) {
        return value != null && StringUtils.hasText(value);
    }

    public void deleteTestCase(Long problemId, String testCaseId) {
        ProblemAdminTestCaseDTO existing = getTestCase(problemId, testCaseId);
        testCaseOwnerPort.deleteTestCase(existing.id());
        log.info("Test case deleted: {} for problem {}", testCaseId, problemId);
    }

    /**
     * Bulk-import test cases. When {@code replaceExisting} is true, every existing
     * test case for the problem is replaced by one owner-side transaction, so a
     * failed import never leaves the problem with a mix of old and partial-new cases.
     */
    public BulkImportResponse bulkImportTestCases(Long problemId, BulkImportTestCasesDTO dto) {
        requireProblem(problemId);

        boolean replace = Boolean.TRUE.equals(dto.getReplaceExisting());

        List<CreateTestCaseDTO> dtos = dto.getTestCases();
        List<TestCaseWrite> writes = new ArrayList<>(dtos.size());
        for (CreateTestCaseDTO createDto : dtos) {
            // Build and validate every row before asking the owner to delete
            // anything. Replacement itself is one owner-side transaction.
            writes.add(buildNewTestCaseWrite(problemId, createDto));
        }

        if (replace) {
            testCaseOwnerPort.replaceAllForProblem(problemId, writes);
        }

        List<ProblemAdminTestCaseDTO> created = new ArrayList<>(writes.size());
        for (TestCaseWrite write : writes) {
            if (!replace) {
                testCaseOwnerPort.insertTestCase(write);
            }
            created.add(toDto(write));
        }
        log.info("Bulk imported {} test cases for problem {} (replace={})",
                created.size(), problemId, replace);
        return new BulkImportResponse(created.size());
    }

    public void reorderTestCases(Long problemId, List<String> testCaseIds) {
        requireProblem(problemId);
        Set<String> uniqueIds = new HashSet<>(testCaseIds);
        if (uniqueIds.size() != testCaseIds.size()) {
            throw new BusinessException(AdminErrorCode.BAD_REQUEST, "Duplicate test case IDs");
        }

        List<ProblemAdminTestCaseDTO> existingCases =
                problemReadPort.findTestCasesByIds(problemId, testCaseIds);
        Map<String, ProblemAdminTestCaseDTO> casesById = new HashMap<>();
        if (existingCases != null) {
            for (ProblemAdminTestCaseDTO existing : existingCases) {
                if (existing != null && problemId.equals(existing.problemId())) {
                    casesById.put(existing.id(), existing);
                }
            }
        }
        for (String testCaseId : testCaseIds) {
            if (!casesById.containsKey(testCaseId)) {
                throw new BusinessException(AdminErrorCode.TEST_CASE_NOT_FOUND);
            }
        }

        LocalDateTime updatedAt = LocalDateTime.now(clock);
        List<TestCaseOrder> commands = new ArrayList<>(testCaseIds.size());
        for (int i = 0; i < testCaseIds.size(); i++) {
            commands.add(new TestCaseOrder(casesById.get(testCaseIds.get(i)).id(), i, updatedAt));
        }
        testCaseOwnerPort.updateTestOrders(commands);
        log.info("Reordered {} test cases for problem {}", testCaseIds.size(), problemId);
    }

    public List<ProblemAdminTestCaseDTO> exportTestCases(Long problemId) {
        requireProblem(problemId);
        return problemReadPort.exportTestCases(problemId);
    }

    /**
     * Export all test cases for a problem as a JSON string.
     * Used by the controller to return an octet-stream response.
     *
     * @param problemId the problem ID
     * @return JSON string representation of the test cases list
     * @throws BusinessException PROBLEM_NOT_FOUND if the problem doesn't exist
     */
    public String exportTestCasesAsJson(Long problemId) {
        // Reuse exportTestCases() — it already pre-checks problem existence and
        // throws BusinessException(PROBLEM_NOT_FOUND) on miss, so we don't need
        // a duplicate problem lookup here.
        List<ProblemAdminTestCaseDTO> cases = exportTestCases(problemId);
        try {
            return objectMapper.writeValueAsString(cases);
        } catch (Exception e) {
            log.error("Failed to serialize test cases for problem {}", problemId, e);
            throw new BusinessException(AdminErrorCode.DATABASE_ERROR, "Failed to serialize test cases");
        }
    }
}
