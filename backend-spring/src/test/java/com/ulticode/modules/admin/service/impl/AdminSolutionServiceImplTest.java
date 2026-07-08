package com.ulticode.modules.admin.service.impl;

import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.util.AuditContext;
import com.ulticode.modules.admin.projection.AdminSolutionProjection;
import com.ulticode.modules.solution.entity.Solution;
import com.ulticode.modules.solution.mapper.SolutionMapper;
import com.ulticode.modules.problem.mapper.ProblemMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.ulticode.common.auth.CurrentUserProvider;

/**
 * Smoke / guard tests for {@link AdminSolutionServiceImpl}.
 *
 * <p>Per the project convention (see {@code AdminCommentServiceImplTest} header comment),
 * MyBatis-Plus {@code LambdaUpdateWrapper.set(SFunction, value)} mutators need a
 * Spring-initialized lambda-method-reference cache; full SQL behavior is therefore
 * verified by the curl integration tests in
 * {@code docs/solutions-admin-api-qa-2026-06-09.md} §2.
 *
 * <p>This class covers:
 * <ul>
 *   <li>Guard logic — throws when solution is missing (BUG-Q4 / BUG-Q1)</li>
 *   <li>AuditContext population — solution author written to {@code userId} (BUG-Q5)</li>
 *   <li>Bulk pre-check — missing id returns failure, no update issued (BUG-Q4)</li>
 *   <li>Bulk action whitelist — unknown action per-row fails</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminSolutionServiceImplTest {

    @Mock
    private CurrentUserProvider currentUserProvider;

    @Mock private SolutionMapper solutionMapper;
    @Mock private ProblemMapper problemMapper;
    @Mock private AdminSolutionProjection solutionProjection;
    @Mock private Clock clock;

    private AdminSolutionServiceImpl service;

    private static final String SOL_ID = "sol-test-001";
    private static final String AUTHOR_ID = "user-test-author";

    @BeforeEach
    void setUp() {
        when(clock.instant()).thenReturn(Instant.EPOCH);
        when(clock.getZone()).thenReturn(ZoneId.systemDefault());
        service = new AdminSolutionServiceImpl(solutionMapper, problemMapper, solutionProjection, clock);
    }

    @AfterEach
    void tearDown() {
        AuditContext.clear();
    }

    @Test
    @DisplayName("flagSolution throws BusinessException(SOLUTION_NOT_FOUND) when id absent")
    void flagSolution_throwsWhenNotFound() {
        when(solutionMapper.selectById(SOL_ID)).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.flagSolution(SOL_ID, "spam"));
        assertNull(AuditContext.getUserId(), "AuditContext userId must NOT be set when solution missing");
        assertNull(AuditContext.getEntityId());
    }

    @Test
    @DisplayName("flagSolution populates AuditContext.userId with solution author (BUG-Q5)")
    void flagSolution_setsAuditUserIdToAuthor() {
        Solution sol = new Solution();
        sol.setId(SOL_ID);
        sol.setUserId(AUTHOR_ID);
        sol.setIsFlagged(false);
        when(solutionMapper.selectById(SOL_ID)).thenReturn(sol);
        // LambdaUpdateWrapper in plain Mockito will throw MybatisPlusException for the
        // .update() call; we only care that the audit context is set BEFORE the exception.
        try {
            service.flagSolution(SOL_ID, "spam");
        } catch (Exception ignored) {
            // expected in unit test
        }
        assertEquals(AUTHOR_ID, AuditContext.getUserId(),
                "AuditContext.userId must be the solution author, not the solution id");
        assertEquals(SOL_ID, AuditContext.getEntityId());
    }

    @Test
    @DisplayName("bulkAction rejects unknown action by returning per-row failure (service-layer fail-safe)")
    void bulkAction_unknownActionPerRowFailure() {
        Solution sol = new Solution();
        sol.setId(SOL_ID);
        sol.setUserId(AUTHOR_ID);
        when(solutionMapper.selectBatchIds(anyCollection())).thenReturn(List.of(sol));
        var results = service.bulkAction(List.of(SOL_ID), "flag");
        assertEquals(1, results.size());
        assertFalse(results.get(0).success());
        assertTrue(results.get(0).error().contains("Unknown action"));
    }

    @Test
    @DisplayName("bulkAction pre-checks existence: missing id returns failure (BUG-Q4)")
    void bulkAction_preCheckMissingId() {
        when(solutionMapper.selectBatchIds(anyCollection())).thenReturn(Collections.emptyList());
        var results = service.bulkAction(List.of("sol-missing"), "publish");
        assertEquals(1, results.size());
        assertFalse(results.get(0).success());
        assertEquals("Solution not found", results.get(0).error());
        // Verify the pre-check issued exactly one batched query and no per-row updates.
        verify(solutionMapper, times(1)).selectBatchIds(anyCollection());
        verify(solutionMapper, never()).update(any(), any());
    }

    @Test
    @DisplayName("bulkAction with mixed ids: existing enters publish branch, missing fails (BUG-Q4)")
    void bulkAction_mixedIds() {
        Solution sol = new Solution();
        sol.setId(SOL_ID);
        sol.setUserId(AUTHOR_ID);
        when(solutionMapper.selectBatchIds(anyCollection())).thenReturn(List.of(sol));
        var results = service.bulkAction(List.of(SOL_ID, "sol-missing"), "publish");
        assertEquals(2, results.size());
        // Positive assertion (L-2): pre-check returned SOL_ID in the existing set, so the
        // service proceeded into the publish branch for that id (lambda cache throws in
        // unit test, captured by the per-row try/catch). The non-"Solution not found"
        // error is the proof we did NOT skip the publish branch.
        assertTrue(results.get(0).error() != null && !results.get(0).error().isBlank()
                        && !results.get(0).error().contains("Solution not found"),
                "existing solution should not be reported as missing; got: "
                        + results.get(0).error());
        // The missing solution is caught by the pre-check and reported as not found.
        assertFalse(results.get(1).success());
        assertEquals("Solution not found", results.get(1).error());
    }
}
