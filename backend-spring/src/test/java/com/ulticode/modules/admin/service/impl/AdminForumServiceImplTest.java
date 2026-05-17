package com.ulticode.modules.admin.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ulticode.common.util.AuditActionUtil;
import com.ulticode.common.util.AuditHelper;
import com.ulticode.modules.admin.dto.AdminForumPostQueryDTO;
import com.ulticode.modules.admin.service.AdminForumService;
import com.ulticode.modules.admin.service.AuditService;
import com.ulticode.modules.forum.entity.ForumPost;
import com.ulticode.modules.forum.mapper.ForumCommentMapper;
import com.ulticode.modules.forum.mapper.ForumPostMapper;
import com.ulticode.modules.user.mapper.UserMapper;
import com.ulticode.modules.vote.mapper.EdgeOperationMapper;
import com.ulticode.modules.forum.mapper.ForumCommunityMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminForumServiceImplTest {

    @Mock
    private ForumPostMapper forumPostMapper;
    @Mock
    private ForumCommentMapper forumCommentMapper;
    @Mock
    private EdgeOperationMapper edgeOperationMapper;
    @Mock
    private UserMapper userMapper;
    @Mock
    private ForumCommunityMapper forumCommunityMapper;
    @Mock
    private AuditService auditService;
    @Mock
    private AuditHelper auditHelper;

    @InjectMocks
    private AdminForumServiceImpl adminForumService;

    private ForumPost testPost;

    @BeforeEach
    void setUp() {
        testPost = new ForumPost();
        testPost.setId("post-test-001");
        testPost.setTitle("Test Post");
        testPost.setUserId("user-001");
        testPost.setCommunityId("community-001");
        testPost.setViews(100);
        testPost.setIsPinned(false);
        testPost.setIsLocked(false);
        testPost.setIsFlagged(false);
        testPost.setIsDeleted(false);
    }

    @Test
    @DisplayName("getPosts returns real comment count from forum_comments table")
    void getPosts_returnsRealCommentCount() {
        Page<ForumPost> mockPage = new Page<>();
        mockPage.setRecords(List.of(testPost));
        mockPage.setTotal(1L);
        when(forumPostMapper.selectPage(any(Page.class), any())).thenReturn(mockPage);
        when(forumCommentMapper.countByPostIds(anyList())).thenReturn(
                List.of(Map.of("post_id", "post-test-001", "cnt", 5L)));
        when(edgeOperationMapper.countByTargetsAndOperation(anyList(), eq("FORUM_POST"), eq("VOTE_UP"))).thenReturn(
                List.of(Map.of("target_id", "post-test-001", "cnt", 10)));
        when(edgeOperationMapper.countByTargetsAndOperation(anyList(), eq("FORUM_POST"), eq("VOTE_DOWN"))).thenReturn(
                List.of(Map.of("target_id", "post-test-001", "cnt", 2)));

        var result = adminForumService.getPosts(createDefaultQuery());

        assertThat(result.getItems()).hasSize(1);
        assertThat(result.getItems().get(0).getCommentCount()).isEqualTo(5);
        assertThat(result.getItems().get(0).getUpvotes()).isEqualTo(10);
        assertThat(result.getItems().get(0).getDownvotes()).isEqualTo(2);
    }

    @Test
    @DisplayName("getPosts returns zero when no comments exist")
    void getPosts_returnsZeroCommentCountWhenNoComments() {
        Page<ForumPost> mockPage = new Page<>();
        mockPage.setRecords(List.of(testPost));
        mockPage.setTotal(1L);
        when(forumPostMapper.selectPage(any(Page.class), any())).thenReturn(mockPage);
        when(forumCommentMapper.countByPostIds(anyList())).thenReturn(List.of());
        when(edgeOperationMapper.countByTargetsAndOperation(anyList(), eq("FORUM_POST"), eq("VOTE_UP"))).thenReturn(List.of());
        when(edgeOperationMapper.countByTargetsAndOperation(anyList(), eq("FORUM_POST"), eq("VOTE_DOWN"))).thenReturn(List.of());

        var result = adminForumService.getPosts(createDefaultQuery());

        assertThat(result.getItems().get(0).getCommentCount()).isEqualTo(0);
        assertThat(result.getItems().get(0).getUpvotes()).isEqualTo(0);
        assertThat(result.getItems().get(0).getDownvotes()).isEqualTo(0);
    }

    @Nested
    @DisplayName("audit logging")
    class AuditLogging {

        @Test
        @DisplayName("pinPost logs audit with old and new isPinned values")
        void pinPost_logsAudit() {
            when(forumPostMapper.selectById("post-1")).thenReturn(testPost);

            adminForumService.pinPost("post-1");

            verify(auditHelper).logForUser(
                    eq(AuditActionUtil.PIN_POST),
                    eq(AuditActionUtil.ENTITY_FORUM_POST),
                    eq("post-1"),
                    eq("user-001"),
                    eq(Map.of("isPinned", false)),
                    eq(Map.of("isPinned", true))
            );
            verify(forumPostMapper).updateById(testPost);
        }

        @Test
        @DisplayName("unpinPost logs audit with old and new isPinned values")
        void unpinPost_logsAudit() {
            testPost.setIsPinned(true);
            when(forumPostMapper.selectById("post-1")).thenReturn(testPost);

            adminForumService.unpinPost("post-1");

            verify(auditHelper).logForUser(
                    eq(AuditActionUtil.UNPIN_POST),
                    eq(AuditActionUtil.ENTITY_FORUM_POST),
                    eq("post-1"),
                    eq("user-001"),
                    eq(Map.of("isPinned", true)),
                    eq(Map.of("isPinned", false))
            );
        }

        @Test
        @DisplayName("lockPost logs audit with old and new isLocked values")
        void lockPost_logsAudit() {
            when(forumPostMapper.selectById("post-1")).thenReturn(testPost);

            adminForumService.lockPost("post-1");

            verify(auditHelper).logForUser(
                    eq(AuditActionUtil.LOCK_POST),
                    eq(AuditActionUtil.ENTITY_FORUM_POST),
                    eq("post-1"),
                    eq("user-001"),
                    eq(Map.of("isLocked", false)),
                    eq(Map.of("isLocked", true))
            );
        }

        @Test
        @DisplayName("unlockPost logs audit with old and new isLocked values")
        void unlockPost_logsAudit() {
            testPost.setIsLocked(true);
            when(forumPostMapper.selectById("post-1")).thenReturn(testPost);

            adminForumService.unlockPost("post-1");

            verify(auditHelper).logForUser(
                    eq(AuditActionUtil.UNLOCK_POST),
                    eq(AuditActionUtil.ENTITY_FORUM_POST),
                    eq("post-1"),
                    eq("user-001"),
                    eq(Map.of("isLocked", true)),
                    eq(Map.of("isLocked", false))
            );
        }

        @Test
        @DisplayName("deletePost logs audit with old and new deleted state")
        void deletePost_logsAudit() {
            when(forumPostMapper.selectById("post-1")).thenReturn(testPost);

            adminForumService.deletePost("post-1");

            verify(auditHelper).logForUser(
                    eq(AuditActionUtil.DELETE_FORUM_POST),
                    eq(AuditActionUtil.ENTITY_FORUM_POST),
                    eq("post-1"),
                    eq("user-001"),
                    anyMap(),
                    anyMap()
            );
            verify(forumPostMapper).updateById(testPost);
        }
    }

    private AdminForumPostQueryDTO createDefaultQuery() {
        AdminForumPostQueryDTO dto = new AdminForumPostQueryDTO();
        dto.setPage(1);
        dto.setLimit(10);
        return dto;
    }
}
