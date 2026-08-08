package com.ulticode.modules.contest.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ulticode.modules.contest.entity.ScoringRule;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface ScoringRuleMapper extends BaseMapper<ScoringRule> {

    @Select("SELECT * FROM contest_scoring_rules WHERE is_active = 1 ORDER BY created_at DESC")
    List<ScoringRule> findActive();

    @Select("SELECT * FROM contest_scoring_rules ORDER BY created_at DESC")
    List<ScoringRule> findAllOrdered();

    @Update("UPDATE contest_scoring_rules SET is_default = 0 WHERE is_default = 1")
    int clearDefault();

    @Select("SELECT * FROM contest_scoring_rules WHERE is_default = 1 LIMIT 1")
    ScoringRule findDefault();

    @Select("SELECT COUNT(*) FROM contests WHERE scoring_rule_id = #{ruleId} AND is_deleted = 0")
    long countContestsUsingRule(@Param("ruleId") String ruleId);
}
