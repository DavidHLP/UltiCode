package com.ulticode.modules.problem.port;

import com.ulticode.modules.problem.entity.Problem;
import com.ulticode.modules.problem.entity.ProblemLanguage;
import com.ulticode.modules.problem.mapper.ProblemLanguageMapper;
import com.ulticode.modules.problem.mapper.ProblemMapper;
import com.ulticode.app.api.service.ProblemFactsPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Production adapter for {@link ProblemFactsPort}. Owns every
 * {@link ProblemMapper} / {@link ProblemLanguageMapper} read the submission
 * module needs, so the four submission paths stop reaching across the module
 * seam for Problem facts.
 *
 * <p><b>Non-throwing contract</b>: missing rows and data-access exceptions
 * are absorbed as {@code null} so callers keep their safe-degrade defaults,
 * matching the inline mapper reads this adapter replaces.
 *
 * @author ulticode
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProblemFactsAdapter implements ProblemFactsPort {

    private final ProblemMapper problemMapper;
    private final ProblemLanguageMapper problemLanguageMapper;

    @Override
    public ProblemDisplayFacts findDisplayFacts(Long problemId) {
        if (problemId == null) {
            return null;
        }
        try {
            Problem problem = problemMapper.selectById(problemId);
            if (problem == null) {
                return null;
            }
            return new ProblemDisplayFacts(problem.getId(), problem.getTitle(), problem.getSlug());
        } catch (RuntimeException e) {
            log.debug("Failed to read problem {} display facts: {}", problemId, e.getMessage());
            return null;
        }
    }

    @Override
    public ProblemLimits findLimits(Long problemId) {
        if (problemId == null) {
            return null;
        }
        try {
            Problem problem = problemMapper.selectById(problemId);
            if (problem == null) {
                return null;
            }
            return new ProblemLimits(problem.getTimeLimit(), problem.getMemoryLimit());
        } catch (RuntimeException e) {
            log.debug("Failed to read problem {} resource limits: {}", problemId, e.getMessage());
            return null;
        }
    }

    @Override
    public String findStarterCode(Long problemId, String language) {
        if (problemId == null || language == null) {
            return null;
        }
        try {
            List<ProblemLanguage> langs = problemLanguageMapper.findByProblemId(problemId);
            if (langs == null) {
                return null;
            }
            return langs.stream()
                    .filter(l -> l.getValue() != null && l.getValue().equalsIgnoreCase(language))
                    .map(ProblemLanguage::getStarterCode)
                    .findFirst()
                    .orElse(null);
        } catch (RuntimeException e) {
            log.debug("Failed to read starter code for problem {} lang {}: {}",
                    problemId, language, e.getMessage());
            return null;
        }
    }

    @Override
    public ContestProblemFacts findContestProblemFacts(Long problemId) {
        if (problemId == null) {
            return null;
        }
        try {
            Problem problem = problemMapper.selectById(problemId);
            if (problem == null) {
                return null;
            }
            return new ContestProblemFacts(
                    problem.getId(),
                    problem.getTitle(),
                    problem.getSlug(),
                    problem.getDifficulty(),
                    problem.getAcceptanceRate());
        } catch (RuntimeException e) {
            log.debug("Failed to read problem {} contest facts: {}", problemId, e.getMessage());
            return null;
        }
    }
}
