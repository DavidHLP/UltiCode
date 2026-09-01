package com.ulticode.modules.forum.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ulticode.common.uuid.UuidGenerator;
import com.ulticode.modules.forum.dto.CreatePostDTO;
import com.ulticode.modules.forum.dto.ForumPostVO;
import com.ulticode.modules.forum.dto.UpdatePostDTO;
import com.ulticode.modules.forum.entity.ForumCommunity;
import com.ulticode.modules.forum.entity.ForumPost;
import com.ulticode.modules.forum.lifecycle.ForumUserLifecyclePort;
import com.ulticode.modules.forum.mapper.ForumCommentMapper;
import com.ulticode.modules.forum.mapper.ForumCommunityMapper;
import com.ulticode.modules.forum.mapper.ForumCommunityMemberMapper;
import com.ulticode.modules.forum.mapper.ForumPostMapper;
import com.ulticode.modules.forum.port.ForumUserReadPort;
import com.ulticode.modules.forum.projection.ForumPostProjection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * SEARCH-001 wiring test: every forum_post write path publishes a
 * SearchDocumentChanged event in the same transaction.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ForumPostServiceImplTest {

    @Mock private ForumPostMapper postMapper;
    @Mock private ForumCommunityMapper communityMapper;
    @Mock private ForumCommunityMemberMapper memberMapper;
    @Mock private ForumCommentMapper commentMapper;
    @Mock private ForumUserLifecyclePort forumUserLifecycle;
    @Mock private ForumUserReadPort forumUserReadPort;
    @Mock private UuidGenerator uuidGenerator;
    @Mock private com.ulticode.modules.search.source.SearchDocumentChangedPublisher searchPublisher;
    @Mock private ForumPostProjection postProjection;

    private ForumPostServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ForumPostServiceImpl(
                postMapper,
                communityMapper,
                memberMapper,
                commentMapper,
                forumUserLifecycle,
                forumUserReadPort,
                uuidGenerator,
                searchPublisher,
                postProjection);
    }

    private ForumCommunity publicCommunity(String id) {
        ForumCommunity community = new ForumCommunity();
        community.setId(id);
        community.setVisibility("PUBLIC");
        return community;
    }

    @Test
    @DisplayName("createPost publishes an UPSERT document in the write transaction")
    void createPost_publishesUpsert() {
        when(communityMapper.selectById("c-1")).thenReturn(publicCommunity("c-1"));
        when(forumUserLifecycle.resolveOrCreate("u-1")).thenReturn(new com.ulticode.modules.forum.entity.ForumUser());
        when(uuidGenerator.newId()).thenReturn("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
        when(postProjection.toPostVO(any(ForumPost.class), anyString(), any())).thenReturn(new ForumPostVO());

        CreatePostDTO dto = new CreatePostDTO();
        dto.setCommunityId("c-1");
        service.createPost(dto, "u-1");

        ArgumentCaptor<ForumPost> captor = ArgumentCaptor.forClass(ForumPost.class);
        verify(searchPublisher).publishForumPost(captor.capture(), org.mockito.ArgumentMatchers.eq(true));
        assertThat(captor.getValue().getCommunityId()).isEqualTo("c-1");
        assertThat(captor.getValue().getPermalink()).isEqualTo("aaaaaaaabbbb");
    }

    @Test
    @DisplayName("updatePost publishes an UPSERT document after persisting changes")
    void updatePost_publishesUpsert() {
        ForumPost post = new ForumPost();
        post.setId("p-1");
        post.setUserId("u-1");
        when(postMapper.selectById("p-1")).thenReturn(post);
        when(postProjection.toPostVO(any(ForumPost.class), anyString(), any(), any(), anyLong())).thenReturn(new ForumPostVO());

        UpdatePostDTO dto = new UpdatePostDTO();
        dto.setTitle("New title");
        service.updatePost("p-1", dto, "u-1");

        ArgumentCaptor<ForumPost> captor = ArgumentCaptor.forClass(ForumPost.class);
        verify(searchPublisher).publishForumPost(captor.capture(), org.mockito.ArgumentMatchers.eq(true));
        assertThat(captor.getValue().getTitle()).isEqualTo("New title");
    }

    @Test
    @DisplayName("deletePost publishes a DELETE tombstone before the soft delete")
    void deletePost_publishesTombstone() {
        ForumPost post = new ForumPost();
        post.setId("p-1");
        post.setUserId("u-1");
        when(postMapper.selectById("p-1")).thenReturn(post);

        service.deletePost("p-1", "u-1");

        verify(searchPublisher).publishForumPost(post, false);
        verify(postMapper).softDelete("p-1", "u-1");
    }
}
