package com.ulticode.modules.contest.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ulticode.modules.contest.entity.ContestAnnouncement;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * MyBatis-Plus mapper for ContestAnnouncement entity.
 */
@Mapper
public interface ContestAnnouncementMapper extends BaseMapper<ContestAnnouncement> {

    @Select("SELECT * FROM contest_announcements WHERE contest_id = #{contestId} ORDER BY is_pinned DESC, created_at DESC")
    List<ContestAnnouncement> findByContestIdOrderByCreatedAtDesc(@Param("contestId") String contestId);

    @Select("SELECT * FROM contest_announcements WHERE contest_id = #{contestId} AND id = #{id} LIMIT 1")
    ContestAnnouncement findByContestIdAndId(
            @Param("contestId") String contestId,
            @Param("id") String id
    );
}
