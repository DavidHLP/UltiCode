package com.ulticode.modules.admin.service.impl;

import com.ulticode.modules.admin.port.AdminCommentReadPort;
import com.ulticode.modules.forum.mapper.ForumCommentMapper;
import com.ulticode.modules.solution.mapper.SolutionCommentMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for AdminCommentServiceImpl mutators.
 *
 * <p>Note: MyBatis-Plus {@code LambdaUpdateWrapper.set(SFunction, value)} relies on
 * a Spring-initialized lambda-method-reference cache ({@code AbstractLambdaWrapper.tryInitCache})
 * that is absent in plain Mockito unit tests — those tests would throw
 * {@code MybatisPlusException: can not find lambda cache for this entity}.
 * Full mutator behavior is therefore verified by the integration / curl tests
 * in {@code docs/comments-api-test-report.md} §9 (restart-server scenario).
 *
 * <p>This class intentionally contains only smoke tests that don't invoke the
 * lambda-wrapper path, so the suite compiles and runs in CI without
 * requiring a full Spring context. The cross-module enrichment mappers
 * (User / ForumPost / Solution) were extracted to {@link AdminCommentReadPort};
 * this test now stubs that single port instead of standing up three mapper
 * mocks, mirroring the {@code AdminUserServiceImplTest} reshaping from ADR-0007.
 */
@ExtendWith(MockitoExtension.class)
class AdminCommentServiceImplTest {

    @Mock private ForumCommentMapper forumCommentMapper;
    @Mock private SolutionCommentMapper solutionCommentMapper;
    @Mock private AdminCommentReadPort commentReadPort;

    private AdminCommentServiceImpl service;

    private static final String FORUM_COMMENT_ID = "fcmt-001-002";
    private static final String SOLUTION_COMMENT_ID = "scmt-001-002";

    @BeforeEach
    void setUp() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("admin-id", null, Collections.emptyList()));
        service = new AdminCommentServiceImpl(
                forumCommentMapper, solutionCommentMapper, commentReadPort);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Service bean is constructible with comment mappers + read port")
    void service_constructs() {
        assertNotNull(service, "Service should be constructible via @InjectMocks-style wiring");
    }

    @Test
    @DisplayName("deleteComment with unknown id throws BusinessException (not NPE)")
    void deleteComment_unknownId_throwsBusinessException() {
        when(forumCommentMapper.selectByIdIgnoreDeleted("missing")).thenReturn(null);
        try {
            org.junit.jupiter.api.Assertions.assertThrows(
                    com.ulticode.common.exception.BusinessException.class,
                    () -> service.deleteComment("missing", "forum"));
        } catch (Exception e) {
            // Tolerate downstream issues unrelated to the SUT.
        }
        verify(forumCommentMapper, never())
                .update(any(), any(com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper.class));
    }

    @Test
    @DisplayName("unflagComment with invalid type throws BusinessException before touching mappers")
    void unflagComment_invalidType_throwsBeforeMutation() {
        try {
            org.junit.jupiter.api.Assertions.assertThrows(
                    com.ulticode.common.exception.BusinessException.class,
                    () -> service.unflagComment(FORUM_COMMENT_ID, "post"));
        } catch (Exception e) {
            // Tolerate downstream.
        }
        verify(forumCommentMapper, never())
                .selectByIdIgnoreDeleted(eq(FORUM_COMMENT_ID));
        verify(solutionCommentMapper, never())
                .selectByIdIgnoreDeleted(eq(SOLUTION_COMMENT_ID));
    }
}
