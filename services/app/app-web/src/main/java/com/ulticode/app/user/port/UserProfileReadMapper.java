package com.ulticode.app.user.port;

import com.ulticode.app.api.dto.UserProfileDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Set;

/** MyBatis Q-read mapper for the App-owned {@code user_profiles} table. */
@Mapper
public interface UserProfileReadMapper {

    String COLUMNS = "account_id AS accountId, name, avatar, bio, company, "
            + "github, location, twitter, website, preferred_language AS preferredLanguage";

    String SEARCH_COLUMNS = "account_id AS accountId, name, avatar, updated_at AS updatedAt";

    @Select("SELECT " + COLUMNS + " FROM user_profiles WHERE account_id = #{accountId} LIMIT 1")
    UserProfileDTO findByAccountId(@Param("accountId") String accountId);

    @Select("<script>"
            + "SELECT " + COLUMNS + " FROM user_profiles WHERE account_id IN "
            + "<foreach collection='accountIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>"
            + "</script>")
    List<UserProfileDTO> findByAccountIds(@Param("accountIds") Set<String> accountIds);

    /** Return all profiles whose display name matches the search query. */
    @Select("SELECT " + SEARCH_COLUMNS
            + " FROM user_profiles"
            + " WHERE name LIKE CONCAT('%', #{query}, '%')"
            + " ORDER BY account_id ASC")
    List<UserProfileReadRow> findSearchCandidates(@Param("query") String query);

    /** Return a bounded, deterministic profile-name search page. */
    @Select("SELECT " + SEARCH_COLUMNS
            + " FROM user_profiles"
            + " WHERE name LIKE CONCAT('%', #{query}, '%')"
            + " ORDER BY account_id ASC LIMIT #{limit}")
    List<UserProfileReadRow> findSearchCandidatesBounded(
            @Param("query") String query, @Param("limit") int limit);

    /** Return versioned display fields for the supplied account ids. */
    @Select("<script>"
            + "SELECT " + SEARCH_COLUMNS + " FROM user_profiles WHERE account_id IN "
            + "<foreach collection='accountIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>"
            + "</script>")
    List<UserProfileReadRow> findSearchRowsByAccountIds(@Param("accountIds") Set<String> accountIds);
}
