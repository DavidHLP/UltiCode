package com.ulticode.modules.forum.service.impl;

import com.ulticode.modules.forum.dto.ForumPostVO;
import com.ulticode.modules.forum.entity.ForumPost;
import com.ulticode.modules.forum.mapper.ForumCommentMapper;
import com.ulticode.modules.forum.mapper.ForumCommunityMapper;
import com.ulticode.modules.forum.mapper.ForumCommunityMemberMapper;
import com.ulticode.modules.forum.mapper.ForumPostMapper;
import com.ulticode.modules.forum.mapper.ForumUserMapper;
import com.ulticode.modules.user.service.UserService;
import com.ulticode.modules.vote.dto.VoteResultVO;
import com.ulticode.modules.vote.entity.enums.EdgeOperationTargetType;
import com.ulticode.modules.vote.service.VoteService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ForumPostServiceImplTest {

    @Mock
    private ForumPostMapper postMapper;
    @Mock
    private ForumCommunityMapper communityMapper;
    @Mock
    private ForumCommunityMemberMapper memberMapper;
    @Mock
    private ForumUserMapper forumUserMapper;
    @Mock
    private ForumCommentMapper commentMapper;
    @Mock
    private UserService userService;
    @Mock
    private VoteService voteService;

    @InjectMocks
    private ForumPostServiceImpl forumPostService;

    @Test
    @DisplayName("convertToPostVO returns real comment count instead of stale stats comments")
    void convertToPostVO_returnsRealCommentCount() {
        ForumPost post = new ForumPost();
        post.setId("post-001");
        post.setTitle("Real comments");
        post.setUserId("user-001");
        post.setStats(Map.of("comments", 256, "shares", 4));

        when(commentMapper.countByPostId("post-001")).thenReturn(3L);
        when(voteService.getVoteStatus(null, "post-001", EdgeOperationTargetType.FORUM_POST))
                .thenReturn(new VoteResultVO("post-001", "FORUM_POST", 7L, 2L, 0));

        ForumPostVO vo = forumPostService.convertToPostVO(post, null, null, null);

        assertThat(vo.getCommentCount()).isEqualTo(3L);
        assertThat(vo.getStats()).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> stats = (Map<String, Object>) vo.getStats();
        assertThat(stats).containsEntry("comments", 3L);
        assertThat(stats).containsEntry("shares", 4);
    }
}
