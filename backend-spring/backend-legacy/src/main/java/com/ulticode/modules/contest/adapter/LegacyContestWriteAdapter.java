package com.ulticode.modules.contest.adapter;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ulticode.modules.contest.entity.Contest;
import com.ulticode.modules.contest.mapper.ContestMapper;
import com.ulticode.modules.contest.port.ContestWritePort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LegacyContestWriteAdapter implements ContestWritePort {

    private final ContestMapper contestMapper;

    @Override
    public void insert(Contest contest) {
        contestMapper.insert(contest);
    }

    @Override
    public void updateById(Contest contest) {
        contestMapper.updateById(contest);
    }

    @Override
    public void deleteById(String id) {
        contestMapper.deleteById(id);
    }

    @Override
    public Contest selectById(String id) {
        return contestMapper.selectById(id);
    }

    @Override
    public Contest selectBySlug(String slug) {
        if (slug == null || slug.isBlank()) {
            return null;
        }
        LambdaQueryWrapper<Contest> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Contest::getSlug, slug);
        return contestMapper.selectOne(queryWrapper);
    }
}
