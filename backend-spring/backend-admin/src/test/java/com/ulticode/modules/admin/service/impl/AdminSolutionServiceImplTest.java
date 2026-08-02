package com.ulticode.modules.admin.service.impl;

import com.ulticode.common.util.AuditContext;
import com.ulticode.modules.admin.bulk.AdminBulkExecutor;
import com.ulticode.modules.admin.projection.AdminSolutionProjection;
import com.ulticode.app.api.service.SolutionOwnerPort;
import com.ulticode.app.api.service.SolutionOwnerPort.DeleteResult;
import com.ulticode.app.api.service.SolutionOwnerPort.FlagResult;
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
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminSolutionServiceImplTest {

    @Mock
    private SolutionOwnerPort solutionOwnerPort;

    @Mock
    private AdminSolutionProjection solutionProjection;

    private Clock clock = Clock.fixed(Instant.parse("2026-06-09T00:00:00Z"), ZoneId.of("UTC"));

    private AdminBulkExecutor bulkExecutor = new AdminBulkExecutor();

    private AdminSolutionServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AdminSolutionServiceImpl(solutionOwnerPort, solutionProjection, clock, bulkExecutor);
    }

    @AfterEach
    void tearDown() {
        AuditContext.clear();
    }

    @Test
    @DisplayName("flagSolution sets AuditContext and delegates to SolutionOwnerPort")
    void flagSolution_setsAuditContextAndDelegates() {
        when(solutionOwnerPort.flagSolution(eq("s1"), eq("spam"), any()))
                .thenReturn(new FlagResult("author-1", false, ""));

        service.flagSolution("s1", "spam");

        assertEquals("author-1", AuditContext.getUserId());
        assertEquals("s1", AuditContext.getEntityId());
        verify(solutionOwnerPort).flagSolution(eq("s1"), eq("spam"), any());
    }

    @Test
    @DisplayName("unflagSolution sets AuditContext and delegates to SolutionOwnerPort")
    void unflagSolution_setsAuditContextAndDelegates() {
        when(solutionOwnerPort.unflagSolution("s1"))
                .thenReturn(new FlagResult("author-1", true, "spam"));

        service.unflagSolution("s1");

        assertEquals("author-1", AuditContext.getUserId());
        assertEquals("s1", AuditContext.getEntityId());
        verify(solutionOwnerPort).unflagSolution("s1");
    }

    @Test
    @DisplayName("deleteSolution sets AuditContext and delegates to SolutionOwnerPort")
    void deleteSolution_setsAuditContextAndDelegates() {
        when(solutionOwnerPort.deleteSolution("s1"))
                .thenReturn(new DeleteResult("author-1", "Title", 100L));

        service.deleteSolution("s1");

        assertEquals("author-1", AuditContext.getUserId());
        assertEquals("s1", AuditContext.getEntityId());
        verify(solutionOwnerPort).deleteSolution("s1");
    }

    @Test
    @DisplayName("bulkAction checks existence and executes actions through port")
    void bulkAction_executesThroughPort() {
        when(solutionOwnerPort.findExistingIds(List.of("s1"))).thenReturn(Set.of("s1"));

        service.bulkAction(List.of("s1"), "publish");

        verify(solutionOwnerPort).setPublished(eq("s1"), eq(true), any());
    }
}
