package com.ulticode.modules.forum.port;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ulticode.app.api.dto.ForumPostIndexDTO;
import com.ulticode.modules.forum.entity.ForumPost;
import com.ulticode.modules.forum.mapper.ForumPostMapper;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link DefaultForumPostReadAdapter} — the missing
 * implementation of {@link ForumPostReadPort} (P7-LEAF-PLAN-001).
 */
@ExtendWith(MockitoExtension.class)
class DefaultForumPostReadAdapterTest {

    @Mock
    private ForumPostMapper forumPostMapper;

    @InjectMocks
    private DefaultForumPostReadAdapter adapter;

    @Test
    @DisplayName("blank query returns empty list without touching the mapper")
    void blankQuerySkipsMapper() {
        assertThat(adapter.searchForIndex("  ", 10)).isEmpty();
        assertThat(adapter.searchForIndex(null, 10)).isEmpty();
        verify(forumPostMapper, never()).selectList(ArgumentMatchers.any());
    }

    @Test
    @DisplayName("non-positive limit returns empty list")
    void nonPositiveLimitSkipsMapper() {
        assertThat(adapter.searchForIndex("contest", 0)).isEmpty();
        verify(forumPostMapper, never()).selectList(ArgumentMatchers.any());
    }

    @Test
    @DisplayName("maps posts to index DTOs preserving title/excerpt/permalink")
    void mapsPostsToDtos() {
        ForumPost post = new ForumPost();
        post.setId("42");
        post.setTitle("Spring Boot guide");
        post.setExcerpt("A walkthrough");
        post.setPermalink("spring-boot-guide");
        when(forumPostMapper.selectList(ArgumentMatchers.any())).thenReturn(List.of(post));

        List<ForumPostIndexDTO> result = adapter.searchForIndex("spring", 10);

        assertThat(result).hasSize(1);
        ForumPostIndexDTO dto = result.get(0);
        assertThat(dto.id()).isEqualTo("42");
        assertThat(dto.title()).isEqualTo("Spring Boot guide");
        assertThat(dto.excerpt()).isEqualTo("A walkthrough");
        assertThat(dto.permalink()).isEqualTo("spring-boot-guide");
    }

    @Test
    @DisplayName("empty mapper result yields empty list, never null")
    void emptyMapperResultYieldsEmptyList() {
        when(forumPostMapper.selectList(ArgumentMatchers.any())).thenReturn(List.of());
        assertThat(adapter.searchForIndex("nothing", 10)).isEmpty();
    }
}
