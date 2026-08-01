package com.ulticode.modules.problem.port;

import com.ulticode.app.api.service.SolutionReadPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Default adapter for {@link ProblemSolutionQueryPort} backed by
 * {@link SolutionReadPort} from backend-app-api.
 *
 * <p>P7-RELOCATE-SOLUTION-001: cut over from direct {@code SolutionMapper}
 * to {@code SolutionReadPort} so this adapter no longer imports the
 * solution entity or mapper.
 *
 * @author ulticode
 */
@Component
@RequiredArgsConstructor
public class DefaultProblemSolutionQueryAdapter implements ProblemSolutionQueryPort {

    private final SolutionReadPort solutionReadPort;

    @Override
    public long countSolutionsByProblemId(Long problemId) {
        return solutionReadPort.countByProblemId(problemId);
    }
}
