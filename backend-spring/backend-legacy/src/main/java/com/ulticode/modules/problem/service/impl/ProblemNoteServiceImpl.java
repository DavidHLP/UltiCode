package com.ulticode.modules.problem.service.impl;

import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.modules.problem.entity.Problem;
import com.ulticode.modules.problem.entity.ProblemNote;
import com.ulticode.modules.problem.mapper.ProblemMapper;
import com.ulticode.modules.problem.mapper.ProblemNoteMapper;
import com.ulticode.modules.problem.service.ProblemNoteService;
import com.ulticode.modules.problem.vo.ProblemNoteVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Implementation of {@link ProblemNoteService}.
 *
 * @author Claude
 * @since 2026-06-11
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProblemNoteServiceImpl implements ProblemNoteService {

    private final ProblemNoteMapper noteMapper;
    private final ProblemMapper problemMapper;
    private final Clock clock;

    @Override
    public ProblemNoteVO getNote(String userId, Long problemId) {
        return noteMapper.findByUserAndProblem(userId, problemId)
                .map(ProblemNoteVO::from)
                .orElse(null);
    }

    @Override
    @Transactional
    public ProblemNoteVO upsertNote(String userId, Long problemId, String content) {
        // 1. Verify the problem exists. Notes on a deleted problem are not allowed.
        Problem problem = problemMapper.selectById(problemId);
        if (problem == null) {
            throw new BusinessException(ErrorCode.PROBLEM_NOT_FOUND);
        }

        // 2. Upsert: prefer update; insert only if no existing row.
        Optional<ProblemNote> existing = noteMapper.findByUserAndProblem(userId, problemId);
        ProblemNote note = existing.orElseGet(() -> {
            ProblemNote n = new ProblemNote();
            n.setUserId(userId);
            n.setProblemId(problemId);
            return n;
        });
        note.setContent(content);

        if (existing.isPresent()) {
            // Refresh update_time on every write. create_time is preserved on update
            // because the column is excluded from the UPDATE statement via
            // FieldStrategy.NEVER in the entity.
            note.setUpdateTime(LocalDateTime.now(clock));
            noteMapper.updateById(note);
            log.debug("Updated note user={} problem={} contentLen={}", userId, problemId, content.length());
            return ProblemNoteVO.from(note);
        }

        // 3. Insert path. The (user_id, problem_id) unique constraint makes a
        //    concurrent insert race possible if two requests for the same pair
        //    arrive within the read-then-insert window. Downgrade such races
        //    to an update so the second writer does not see a 500.
        LocalDateTime now = LocalDateTime.now(clock);
        note.setCreateTime(now);
        note.setUpdateTime(now);
        try {
            noteMapper.insert(note);
            log.debug("Created note user={} problem={} contentLen={}", userId, problemId, content.length());
        } catch (DuplicateKeyException raceLost) {
            log.debug("Concurrent insert lost race for user={} problem={} — downgrading to update",
                    userId, problemId);
            // Reload the row that the winner just inserted so we have its id,
            // then apply the new content + refreshed update_time on top.
            ProblemNote winner = noteMapper.findByUserAndProblem(userId, problemId)
                    .orElseThrow(() -> raceLost);
            winner.setContent(content);
            winner.setUpdateTime(LocalDateTime.now(clock));
            noteMapper.updateById(winner);
            return ProblemNoteVO.from(winner);
        }
        return ProblemNoteVO.from(note);
    }
}
