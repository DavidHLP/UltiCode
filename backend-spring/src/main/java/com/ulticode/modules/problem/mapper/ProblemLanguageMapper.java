package com.ulticode.modules.problem.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ulticode.modules.problem.entity.ProblemLanguage;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * Mapper for problem_languages table.
 */
@Mapper
public interface ProblemLanguageMapper extends BaseMapper<ProblemLanguage> {

    /**
     * Select all languages for a problem.
     */
    default List<ProblemLanguage> findByProblemId(Long problemId) {
        return selectList(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ProblemLanguage>()
                .eq(ProblemLanguage::getProblemId, problemId)
        );
    }

    /**
     * Find a language template by its value across all problems.
     * Used to validate language values and get template data.
     */
    default ProblemLanguage findByValue(String value) {
        return selectOne(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ProblemLanguage>()
                .eq(ProblemLanguage::getValue, value)
                .last("LIMIT 1")
        );
    }
}
