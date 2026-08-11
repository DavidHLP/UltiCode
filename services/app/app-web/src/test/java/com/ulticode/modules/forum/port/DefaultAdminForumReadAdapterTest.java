package com.ulticode.modules.forum.port;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ulticode.app.api.dto.AdminForumPostPage;
import com.ulticode.app.api.dto.AdminForumPostQuery;
import com.ulticode.modules.forum.entity.ForumPost;
import com.ulticode.modules.forum.mapper.ForumCommentMapper;
import com.ulticode.modules.forum.mapper.ForumCommunityMapper;
import com.ulticode.modules.forum.mapper.ForumPostMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultAdminForumReadAdapterTest {

    @Mock
    private ForumPostMapper forumPostMapper;

    @Mock
    private ForumCommentMapper forumCommentMapper;

    @Mock
    private ForumCommunityMapper forumCommunityMapper;

    @Test
    void listPostsUsesDeletedAwareQueryAndMapsViewCount() {
        ForumPost deletedPost = new ForumPost();
        deletedPost.setId("post-1");
        deletedPost.setTitle("Deleted post");
        deletedPost.setCommunityId("community-1");
        deletedPost.setViews(7);
        deletedPost.setIsDeleted(true);

        doAnswer(invocation -> {
            Page<ForumPost> page = invocation.getArgument(0);
            page.setTotal(1);
            return List.of(deletedPost);
        }).when(forumPostMapper).selectPageIgnoreDeleted(
                any(Page.class),
                org.mockito.ArgumentMatchers.<String>any(),
                org.mockito.ArgumentMatchers.<String>any(),
                org.mockito.ArgumentMatchers.<String>any(),
                org.mockito.ArgumentMatchers.<Boolean>any(),
                org.mockito.ArgumentMatchers.<Boolean>any(),
                org.mockito.ArgumentMatchers.<Boolean>any(),
                org.mockito.ArgumentMatchers.<Boolean>any(),
                org.mockito.ArgumentMatchers.<String>any(),
                org.mockito.ArgumentMatchers.<String>any());
        when(forumCommentMapper.countByPostIds(List.of("post-1"))).thenReturn(List.of());
        when(forumCommunityMapper.selectBatchIds(any())).thenReturn(List.of());

        AdminForumPostQuery query = new AdminForumPostQuery(
                null, null, null, null, null, null, true,
                "viewCount", "desc", 1, 10);

        AdminForumPostPage result = new DefaultAdminForumReadAdapter(
                forumPostMapper, forumCommentMapper, forumCommunityMapper).listPosts(query);

        assertThat(result.total()).isEqualTo(1);
        assertThat(result.rows()).singleElement().satisfies(row -> {
            assertThat(row.getId()).isEqualTo("post-1");
            assertThat(row.getViews()).isEqualTo(7);
            assertThat(row.getIsDeleted()).isTrue();
        });
        verify(forumPostMapper).selectPageIgnoreDeleted(
                any(Page.class),
                org.mockito.ArgumentMatchers.<String>any(),
                org.mockito.ArgumentMatchers.<String>any(),
                org.mockito.ArgumentMatchers.<String>any(),
                org.mockito.ArgumentMatchers.<Boolean>any(),
                org.mockito.ArgumentMatchers.<Boolean>any(),
                org.mockito.ArgumentMatchers.<Boolean>any(),
                org.mockito.ArgumentMatchers.eq(Boolean.TRUE),
                org.mockito.ArgumentMatchers.eq("viewCount"),
                org.mockito.ArgumentMatchers.eq("desc"));
    }
}
