package com.ulticode.modules.search.source;

import com.ulticode.modules.forum.entity.ForumPost;
import com.ulticode.modules.problem.entity.Problem;
import com.ulticode.modules.solution.entity.Solution;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Single source of truth for SearchDocumentChanged document shapes.
 *
 * <p>Live publishers ({@link SearchDocumentChangedPublisher}) and the
 * SEARCH-003 backfill enumeration ports build documents through these
 * builders so a backfilled snapshot is byte-for-byte the same document
 * a live write would publish (DEC-017).
 */
public final class SearchDocumentBuilders {

    private SearchDocumentBuilders() {
    }

    public static Map<String, Object> problem(Problem problem) {
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("id", problem.getId());
        document.put("title", problem.getTitle());
        document.put("slug", problem.getSlug());
        document.put("difficulty", problem.getDifficulty());
        return document;
    }

    public static Map<String, Object> forumPost(ForumPost post) {
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("id", post.getId());
        document.put("title", post.getTitle());
        document.put("excerpt", post.getExcerpt());
        document.put("permalink", post.getPermalink());
        return document;
    }

    public static Map<String, Object> solution(Solution solution) {
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("id", solution.getId());
        document.put("title", solution.getTitle());
        document.put("summary", solution.getSummary());
        document.put("problemId", solution.getProblemId());
        return document;
    }

    public static Map<String, Object> user(String aggregateId, String username, String name, String avatar) {
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("id", aggregateId);
        document.put("username", username);
        if (name != null) {
            document.put("name", name);
        }
        if (avatar != null) {
            document.put("avatar", avatar);
        }
        return document;
    }
}
