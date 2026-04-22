package com.ulticode.modules.admin.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ulticode.modules.admin.dto.AdminForumPostQueryDTO;
import com.ulticode.modules.admin.service.AdminForumService;
import com.ulticode.modules.forum.entity.ForumPost;
import com.ulticode.modules.forum.mapper.ForumCommentMapper;
import com.ulticode.modules.forum.mapper.ForumPostMapper;
import com.ulticode.modules.user.mapper.UserMapper;
import com.ulticode.modules.vote.mapper.EdgeOperationMapper;
import com.ulticode.modules.forum.mapper.ForumCommunityMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

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
        when(forumCommentMapper.countByPostId("post-test-001")).thenReturn(5L);
        when(edgeOperationMapper.countByTargetAndOperation("post-test-001", "FORUM_POST", "VOTE_UP")).thenReturn(10);
        when(edgeOperationMapper.countByTargetAndOperation("post-test-001", "FORUM_POST", "VOTE_DOWN")).thenReturn(2);

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
        when(forumCommentMapper.countByPostId("post-test-001")).thenReturn(0L);
        when(edgeOperationMapper.countByTargetAndOperation("post-test-001", "FORUM_POST", "VOTE_UP")).thenReturn(0);
        when(edgeOperationMapper.countByTargetAndOperation("post-test-001", "FORUM_POST", "VOTE_DOWN")).thenReturn(0);

        var result = adminForumService.getPosts(createDefaultQuery());

        assertThat(result.getItems().get(0).getCommentCount()).isEqualTo(0);
        assertThat(result.getItems().get(0).getUpvotes()).isEqualTo(0);
        assertThat(result.getItems().get(0).getDownvotes()).isEqualTo(0);
    }

    private AdminForumPostQueryDTO createDefaultQuery() {
        AdminForumPostQueryDTO dto = new AdminForumPostQueryDTO();
        dto.setPage(1);
        dto.setLimit(10);
        return dto;
    }
}
