package com.ulticode.modules.problem.port.adapter;

import com.ulticode.modules.problem.entity.ProblemTag;
import com.ulticode.modules.problem.mapper.ProblemTagMapper;
import com.ulticode.modules.problem.mapper.ProblemTagRelationMapper;
import com.ulticode.modules.solution.port.ProblemTagReadPort;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * Default adapter for {@link ProblemTagReadPort} backed by
 * {@code ProblemTagRelationMapper} + {@code ProblemTagMapper}. Lives in
 * the {@code problem} module so {@code solution} never imports the
 * mappers directly.
 *
 * @author ulticode
 */
@Component
public class ProblemTagReadAdapter implements ProblemTagReadPort {

    private final ProblemTagRelationMapper relationMapper;
    private final ProblemTagMapper tagMapper;

    public ProblemTagReadAdapter(ProblemTagRelationMapper relationMapper,
                                 ProblemTagMapper tagMapper) {
        this.relationMapper = relationMapper;
        this.tagMapper = tagMapper;
    }

    @Override
    public List<String> findTagIdsByProblemId(Long problemId) {
        if (problemId == null) return Collections.emptyList();
        List<String> ids = relationMapper.findTagIdsByProblemId(problemId);
        return ids == null ? Collections.emptyList() : ids;
    }

    @Override
    public String findFirstTagLabel(Long problemId) {
        List<String> ids = findTagIdsByProblemId(problemId);
        if (ids.isEmpty()) return null;
        ProblemTag tag = tagMapper.selectById(ids.get(0));
        return tag == null ? null : tag.getLabel();
    }
}