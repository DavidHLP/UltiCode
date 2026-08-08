package com.ulticode.modules.admin.port.adapter;

import com.ulticode.modules.admin.port.AdminCommentReadPort;
import com.ulticode.modules.admin.projection.AdminUserEnricher;
import com.ulticode.modules.admin.projection.AdminUserSummary;
import com.ulticode.modules.forum.entity.ForumPost;
import com.ulticode.modules.forum.mapper.ForumPostMapper;
import com.ulticode.modules.solution.entity.Solution;
import com.ulticode.modules.solution.mapper.SolutionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Production adapter for {@link AdminCommentReadPort}.
 *
 * <p>Backed by {@code UserMapper} + {@code ForumPostMapper} +
 * {@code SolutionMapper} — the only place in the admin module that touches
 * these three mappers. Coerces the batch result into typed views so the port
 * interface stays entity-free. Tests substitute a fixture by providing another
 * bean of the port interface; admin never sees the mappers.
 *
 * <p>Null-value tolerance: {@link java.util.stream.Collectors#toMap} throws
 * NPE on null values, and post / solution titles may legitimately be null.
 * The manual {@link HashMap} accumulation below preserves null titles rather
 * than coercing them, so callers can distinguish "entity missing" (absent
 * from map) from "entity present, title null" (present with null value) —
 * matching the contract in {@link AdminCommentReadPort}.
 *
 * @author ulticode
 */
@Component
@RequiredArgsConstructor
public class AdminCommentReadAdapter implements AdminCommentReadPort {

    private final AdminUserEnricher userEnricher;
    private final ForumPostMapper forumPostMapper;
    private final SolutionMapper solutionMapper;

    @Override
    public Map<String, AuthorSummary> findAuthorSummariesByIds(Set<String> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        Map<String, AuthorSummary> result = new HashMap<>();
        Map<String, AdminUserSummary> userMap = userEnricher.enrich(userIds);
        for (AdminUserSummary u : userMap.values()) {
            result.put(u.accountId(), new AuthorSummary(u.accountId(), u.username(), u.avatar()));
        }
        return result;
    }

    @Override
    public Map<String, String> findForumPostTitlesByIds(Set<String> postIds) {
        if (postIds.isEmpty()) {
            return Map.of();
        }
        Map<String, String> result = new HashMap<>();
        for (ForumPost p : forumPostMapper.selectBatchIds(postIds)) {
            result.put(p.getId(), p.getTitle());
        }
        return result;
    }

    @Override
    public Map<String, String> findSolutionTitlesByIds(Set<String> solutionIds) {
        if (solutionIds.isEmpty()) {
            return Map.of();
        }
        Map<String, String> result = new HashMap<>();
        for (Solution s : solutionMapper.selectBatchIds(solutionIds)) {
            result.put(s.getId(), s.getTitle());
        }
        return result;
    }
}
