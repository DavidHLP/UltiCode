package com.ulticode.modules.solution.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ulticode.modules.solution.entity.SolutionTopic;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * MyBatis-Plus mapper for SolutionTopic entity.
 * Provides listTopics() that aggregates solution counts per topic.
 *
 * <p>NOTE: solution_count is hardcoded to 0 because the
 * {@code solution_topic_relations} join table does not exist yet.
 * A future PR will introduce the relations table and replace this
 * with a real COUNT subquery.</p>
 */
@Mapper
public interface SolutionTopicMapper extends BaseMapper<SolutionTopic> {

    /**
     * List active, non-deleted topics ordered by sort_order, with a stub
     * solution_count column (always 0; see class Javadoc).
     *
     * @return list of maps with keys: id, name, sort_order, solution_count
     */
    @Select("""
        SELECT
            t.id          AS id,
            t.name        AS name,
            t.sort_order  AS sort_order,
            0             AS solution_count
        FROM solution_topics t
        WHERE t.is_active = 1 AND t.is_deleted = 0
        ORDER BY t.sort_order ASC
        """)
    List<Map<String, Object>> listActiveTopicsWithCount();
}
