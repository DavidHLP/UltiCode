package com.ulticode.modules.problem.port;

import com.ulticode.app.api.service.ProblemInteractionQueryPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Fallback {@link ProblemInteractionQueryPort} implementation for the problem
 * domain in {@code backend-app}.
 *
 * <p>Returns safe-degrade values ({@code 0} for counts, {@code null} for
 * reactions) so that callers that need interaction data degrade gracefully
 * when the data is not yet available.
 *
 * <p><b>When edgeoperations relocates</b> to {@code backend-app} under
 * {@code P7-RELOCATE-INFRA-001}, replace this adapter with the real
 * {@code EdgeOperationInspector}-backed implementation (the same one
 * currently living in {@code backend-legacy}). Until then the public
 * problem page shows no favorite count and no viewer reaction — this is
 * the documented safe-degrade contract of {@link ProblemInteractionQueryPort}.
 *
 * @author ulticode
 */
@Slf4j
@Component
public class DefaultProblemInteractionQueryPort implements ProblemInteractionQueryPort {

    @Override
    public int countFavorites(Long problemId) {
        log.debug("ProblemInteractionQueryPort.countFavorites called but edgeoperations "
                + "has not yet relocated — returning 0 for problem {}", problemId);
        return 0;
    }

    @Override
    public String findViewerReaction(String userId, Long problemId) {
        log.debug("ProblemInteractionQueryPort.findViewerReaction called but edgeoperations "
                + "has not yet relocated — returning null for user {} problem {}", userId, problemId);
        return null;
    }
}
