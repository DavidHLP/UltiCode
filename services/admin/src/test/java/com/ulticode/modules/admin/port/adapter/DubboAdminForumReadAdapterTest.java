package com.ulticode.modules.admin.port.adapter;

import com.ulticode.app.api.dto.AdminForumPostPage;
import com.ulticode.app.api.dto.AdminForumPostQuery;
import com.ulticode.app.api.dto.AdminForumPostRowDTO;
import com.ulticode.app.api.service.AdminForumReadPort;
import com.ulticode.app.api.service.ForumPostVoteCountReadPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DubboAdminForumReadAdapterTest {

    @Mock
    private AdminForumReadPort forumReadPort;

    @Mock
    private ForumPostVoteCountReadPort voteCountReadPort;

    private DubboAdminForumReadAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new DubboAdminForumReadAdapter();
        ReflectionTestUtils.setField(adapter, "forumReadPort", forumReadPort);
        ReflectionTestUtils.setField(adapter, "voteCountReadPort", voteCountReadPort);
    }

    @Test
    void listComposesVoteCountsOnceForTheBoundedPage() {
        AdminForumPostRowDTO first = row("post-1");
        AdminForumPostRowDTO second = row("post-2");
        when(forumReadPort.listPosts(any())).thenReturn(new AdminForumPostPage(List.of(first, second), 20));
        when(voteCountReadPort.countVoteUpByTargets(List.of("post-1", "post-2")))
                .thenReturn(Map.of("post-1", 3L));
        when(voteCountReadPort.countVoteDownByTargets(List.of("post-1", "post-2")))
                .thenReturn(Map.of("post-2", 2L));

        var result = adapter.listPosts(new AdminForumPostQuery(
                null, null, null, null, null, null, null,
                "createdAt", "desc", 1, 10));

        assertThat(result.rows()).extracting(AdminForumPostRowDTO::getUpvotes)
                .containsExactly(3, 0);
        assertThat(result.rows()).extracting(AdminForumPostRowDTO::getDownvotes)
                .containsExactly(0, 2);
        verify(voteCountReadPort).countVoteUpByTargets(List.of("post-1", "post-2"));
        verify(voteCountReadPort).countVoteDownByTargets(List.of("post-1", "post-2"));
    }

    @Test
    void emptyPageDoesNotIssueVoteBatchCalls() {
        when(forumReadPort.listPosts(any())).thenReturn(new AdminForumPostPage(List.of(), 0));

        var result = adapter.listPosts(new AdminForumPostQuery(
                null, null, null, null, null, null, null,
                "createdAt", "desc", 1, 10));

        assertThat(result.rows()).isEmpty();
        verify(voteCountReadPort, never()).countVoteUpByTargets(any());
        verify(voteCountReadPort, never()).countVoteDownByTargets(any());
    }

    @Test
    void detailComposesMissingVoteCountsAsZero() {
        AdminForumPostRowDTO post = row("post-1");
        when(forumReadPort.getPost("post-1")).thenReturn(post);
        when(voteCountReadPort.countVoteUpByTargets(List.of("post-1"))).thenReturn(Map.of());
        when(voteCountReadPort.countVoteDownByTargets(List.of("post-1"))).thenReturn(Map.of());

        var result = adapter.getPost("post-1");

        assertThat(result.getUpvotes()).isZero();
        assertThat(result.getDownvotes()).isZero();
    }

    private static AdminForumPostRowDTO row(String id) {
        AdminForumPostRowDTO row = new AdminForumPostRowDTO();
        row.setId(id);
        return row;
    }
}
