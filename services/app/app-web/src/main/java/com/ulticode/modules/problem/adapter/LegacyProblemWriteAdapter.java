package com.ulticode.modules.problem.adapter;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ulticode.modules.problem.entity.Problem;
import com.ulticode.modules.problem.mapper.ProblemMapper;
import com.ulticode.modules.problem.port.ProblemWritePort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LegacyProblemWriteAdapter implements ProblemWritePort {

    private final ProblemMapper problemMapper;

    @Override
    public void insert(Problem problem) {
        problemMapper.insert(problem);
    }

    @Override
    public void updateById(Problem problem) {
        problemMapper.updateById(problem);
    }

    @Override
    public int updateById(Problem problem, Long expectedVersion) {
        return problemMapper.updateByIdWithExpectedVersion(problem, expectedVersion);
    }

    @Override
    public int deleteById(Long id, Long expectedVersion) {
        return problemMapper.deleteByIdWithExpectedVersion(id, expectedVersion);
    }

    @Override
    public void deleteById(Long id) {
        problemMapper.deleteById(id);
    }

    @Override
    public Problem selectById(Long id) {
        return problemMapper.selectById(id);
    }

    @Override
    public Problem selectBySlug(String slug) {
        if (slug == null || slug.isBlank()) {
            return null;
        }
        LambdaQueryWrapper<Problem> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Problem::getSlug, slug);
        return problemMapper.selectOne(queryWrapper);
    }
}
