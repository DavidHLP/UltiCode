package com.ulticode.modules.forum.projection;

import com.ulticode.modules.forum.dto.ForumCommentVO;
import com.ulticode.modules.forum.entity.ForumComment;
import com.ulticode.modules.forum.port.ForumUserReadPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit coverage for the comment VO shaping and tree assembly that moved from
 * {@code ForumCommentServiceImpl} into this projection (seam 03).
 *
 * <p>P7-RELOCATE-FORUM-001: {@code User} replaced with
 * {@link ForumUserReadPort.UserSummary}.
 */
class DefaultForumCommentProjectionTest {

    private final DefaultForumCommentProjection projection = new DefaultForumCommentProjection();

    private ForumComment comment(String id, String parentId, String authorId) {
        ForumComment c = new ForumComment();
        c.setId(id);
        c.setParentId(parentId);
        c.setAuthorId(authorId);
        c.setBody("body-" + id);
        c.setCreatedAt(LocalDateTime.of(2026, 6, 1, 0, 0));
        return c;
    }

    @Test
    @DisplayName("toCommentVO enriches author fields when the author is known")
    void toCommentVoEnrichesAuthor() {
        ForumComment c = comment("c1", null, "u1");
        c.setEditedAt(LocalDateTime.of(2026, 6, 2, 0, 0));
        ForumUserReadPort.UserSummary author = new ForumUserReadPort.UserSummary(
                "u1", "alice", "avatar-alice");
        Map<String, ForumUserReadPort.UserSummary> authors = new HashMap<>();
        authors.put("u1", author);

        ForumCommentVO vo = projection.toCommentVO(c, authors);

        assertThat(vo.getId()).isEqualTo("c1");
        assertThat(vo.getAuthorUsername()).isEqualTo("alice");
        assertThat(vo.getAuthorAvatar()).isEqualTo("avatar-alice");
        assertThat(vo.getEditedAt()).isEqualTo(LocalDateTime.of(2026, 6, 2, 0, 0));
    }

    @Test
    @DisplayName("toCommentVO leaves author fields null when the author is unknown")
    void toCommentVoWithoutAuthor() {
        ForumCommentVO vo = projection.toCommentVO(comment("c1", null, "ghost"), new HashMap<>());

        assertThat(vo.getAuthorUsername()).isNull();
        assertThat(vo.getAuthorAvatar()).isNull();
    }

    @Test
    @DisplayName("buildCommentTree nests replies under their parent root")
    void buildCommentTreeNestsReplies() {
        List<ForumComment> flat = List.of(
                comment("root", null, "u1"),
                comment("child", "root", "u2"));

        List<ForumCommentVO> tree = projection.buildCommentTree(flat, new HashMap<>());

        assertThat(tree).hasSize(1);
        assertThat(tree.get(0).getId()).isEqualTo("root");
        assertThat(tree.get(0).getReplies()).hasSize(1);
        assertThat(tree.get(0).getReplies().get(0).getId()).isEqualTo("child");
    }
}
