package com.ulticode.modules.problem.port;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.ulticode.app.api.service.ProblemTagOwnerPort;
import com.ulticode.modules.problem.entity.ProblemTag;
import com.ulticode.modules.problem.entity.ProblemTagRelation;
import com.ulticode.modules.problem.mapper.ProblemTagMapper;
import com.ulticode.modules.problem.mapper.ProblemTagRelationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Default {@link ProblemTagOwnerPort} implementation. Lives in the problem
 * module (the OWNER); the only class allowed to call
 * {@link ProblemTagMapper}/{@link ProblemTagRelationMapper} write methods.
 */
@Slf4j
@Component
@Primary
@RequiredArgsConstructor
public class DefaultProblemTagOwnerPort implements ProblemTagOwnerPort {

    private final ProblemTagMapper problemTagMapper;
    private final ProblemTagRelationMapper problemTagRelationMapper;

    @Override
    @Transactional
    public void createTag(TagWrite command) {
        ProblemTag tag = new ProblemTag();
        tag.setId(command.id());
        tag.setLabel(command.label());
        tag.setSlug(command.slug());
        tag.setDescription(command.description());
        tag.setColor(command.color());
        tag.setUsageCount(command.usageCount() != null ? command.usageCount() : 0);
        tag.setCreatedAt(command.createdAt());
        tag.setUpdatedAt(command.updatedAt());
        problemTagMapper.insert(tag);
    }

    @Override
    @Transactional
    public void updateTag(TagWrite command) {
        ProblemTag tag = new ProblemTag();
        tag.setId(command.id());
        tag.setLabel(command.label());
        tag.setSlug(command.slug());
        tag.setDescription(command.description());
        tag.setColor(command.color());
        tag.setUsageCount(command.usageCount());
        tag.setCreatedAt(command.createdAt());
        tag.setUpdatedAt(command.updatedAt());
        problemTagMapper.updateById(tag);
    }

    @Override
    @Transactional
    public void deleteTag(String id) {
        problemTagMapper.deleteById(id);
    }

    @Override
    @Transactional
    public void mergeTags(String sourceId, String targetTagId) {
        LambdaUpdateWrapper<ProblemTagRelation> repointWrapper = new LambdaUpdateWrapper<>();
        repointWrapper.eq(ProblemTagRelation::getTagId, sourceId)
                .set(ProblemTagRelation::getTagId, targetTagId);
        problemTagRelationMapper.update(repointWrapper);
        problemTagMapper.deleteById(sourceId);

        ProblemTag target = problemTagMapper.selectById(targetTagId);
        if (target != null) {
            LambdaQueryWrapper<ProblemTagRelation> countWrapper = new LambdaQueryWrapper<>();
            countWrapper.eq(ProblemTagRelation::getTagId, targetTagId);
            target.setUsageCount(Math.toIntExact(problemTagRelationMapper.selectCount(countWrapper)));
            target.setUpdatedAt(java.time.LocalDateTime.now());
            problemTagMapper.updateById(target);
        }
    }
}
