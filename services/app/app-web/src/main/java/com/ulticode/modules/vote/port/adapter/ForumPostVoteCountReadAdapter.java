package com.ulticode.modules.vote.port.adapter;

import com.ulticode.app.api.service.ForumPostVoteCountReadPort;
import com.ulticode.modules.vote.entity.enums.EdgeOperationTargetType;
import com.ulticode.modules.vote.entity.enums.EdgeOperationType;
import com.ulticode.modules.vote.mapper.EdgeOperationMapper;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * ADMIN-007: adapter that satisfies {@link ForumPostVoteCountReadPort}
 * from {@code EdgeOperationMapper}. Lives in the {@code vote} module so
 * the forum / admin modules never import the mapper.
 *
 * <p>The port is forum-post-scoped, so the {@code target_type} filter is
 * fixed here to {@link EdgeOperationTargetType#FORUM_POST} rather than
 * threaded through the seam (mirrors {@link SolutionVoteReadAdapter}).
 *
 * @author ulticode
 */
@Component
@Primary
public class ForumPostVoteCountReadAdapter implements ForumPostVoteCountReadPort {

    /** Forum posts are the only target this port reads votes for. */
    private static final String FORUM_POST_TARGET = EdgeOperationTargetType.FORUM_POST.getValue();

    private final EdgeOperationMapper edgeOperationMapper;

    public ForumPostVoteCountReadAdapter(EdgeOperationMapper edgeOperationMapper) {
        this.edgeOperationMapper = edgeOperationMapper;
    }

    @Override
    public Map<String, Long> countVoteUpByTargets(Collection<String> postIds) {
        if (postIds == null || postIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return aggregateCounts(edgeOperationMapper.countByTargetsAndOperation(
                toList(postIds), FORUM_POST_TARGET, EdgeOperationType.VOTE_UP.getValue()));
    }

    @Override
    public Map<String, Long> countVoteDownByTargets(Collection<String> postIds) {
        if (postIds == null || postIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return aggregateCounts(edgeOperationMapper.countByTargetsAndOperation(
                toList(postIds), FORUM_POST_TARGET, EdgeOperationType.VOTE_DOWN.getValue()));
    }

    private static List<String> toList(Collection<String> c) {
        return new ArrayList<>(c);
    }

    private static Map<String, Long> aggregateCounts(List<Map<String, Object>> rows) {
        return rows.stream().collect(Collectors.toMap(
                m -> (String) m.get("target_id"),
                m -> ((Number) m.get("cnt")).longValue(),
                (a, b) -> a));
    }
}
