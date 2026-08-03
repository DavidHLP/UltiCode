package com.ulticode.modules.search.port;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * MyBatis read model for the user search index.
 *
 * <p>Migration-state Q-read of the Auth-owned {@code users} table
 * (ADR-P7-APP-DECOMPOSITION rule 3; precedent: {@code GlobalRankingMapper}
 * and {@code ContestParticipantMapper} already join/read {@code users}
 * from backend-app). Reads only display/identity columns — never
 * credentials. Soft-deleted accounts are excluded.
 */
@Mapper
public interface UserSearchReadMapper {

    @Select("SELECT id, username, name, avatar FROM users "
            + "WHERE is_deleted = 0 "
            + "AND (username LIKE CONCAT('%', #{query}, '%') "
            + "     OR name LIKE CONCAT('%', #{query}, '%')) "
            + "ORDER BY username ASC "
            + "LIMIT #{limit}")
    List<UserSearchRow> searchIndex(@Param("query") String query, @Param("limit") int limit);
}
