package com.ulticode.modules.submission.port;

import com.ulticode.app.api.service.SubmissionUserReadPort;
import com.ulticode.app.user.port.UserDirectoryProjection;
import com.ulticode.app.user.port.UserSummaryView;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Default {@link SubmissionUserReadPort} adapter.
 *
 * <p>Delegates account/profile composition to the App-owned directory view
 * and keeps the submission-specific summary shape at this boundary.</p>
 */
@Component
@Primary
@RequiredArgsConstructor
public class DefaultSubmissionUserReadAdapter implements SubmissionUserReadPort {

    private final UserDirectoryProjection userDirectoryProjection;

    @Override
    public boolean existsById(String userId) {
        return userId != null && !userId.isBlank()
                && userDirectoryProjection.selectById(userId) != null;
    }

    @Override
    public UserSummary findById(String userId) {
        return toSummary(userDirectoryProjection.selectById(userId));
    }

    @Override
    public Map<String, UserSummary> findAllById(Iterable<String> userIds) {
        if (userIds == null) {
            return Map.of();
        }
        List<String> requestedIds = StreamSupport.stream(userIds.spliterator(), false).toList();
        return userDirectoryProjection.selectByIds(requestedIds).entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> toSummary(entry.getValue()),
                        (first, ignored) -> first,
                        LinkedHashMap::new));
    }

    private UserSummary toSummary(UserSummaryView view) {
        return view == null ? null : new UserSummary(view.id(), view.username(), view.name(), view.avatar());
    }
}
