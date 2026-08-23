package com.ulticode.modules.contest.mapper;

import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("GlobalRankingMapper profile-column SQL guard")
class GlobalRankingMapperSQLGuardTest {

    @Test
    @DisplayName("ranking display queries join user_profiles instead of removed users.name")
    void displayQueriesUseUserProfiles() {
        List<Method> displayQueries = Arrays.stream(GlobalRankingMapper.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(Select.class))
                .filter(method -> joinedSql(method).contains("user_profiles"))
                .filter(method -> !joinedSql(method).contains("for update"))
                .toList();

        assertThat(displayQueries).isNotEmpty();
        for (Method method : displayQueries) {
            String sql = joinedSql(method);
            assertThat(sql)
                    .as("%s must read profile display data from user_profiles", method.getName())
                    .contains("left join user_profiles p on g.user_id = p.account_id")
                    .contains("p.name")
                    .contains("p.avatar")
                    .doesNotContain("u.name");
        }
    }

    @Test
    @DisplayName("contest ranking queries leave user display resolution to the App read seam")
    void contestRankingQueriesDoNotJoinOwnerUserTables() {
        List<Method> rankingQueries = Arrays.stream(ContestParticipantMapper.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(Select.class))
                .filter(method -> joinedSql(method).contains("null as username"))
                .toList();

        assertThat(rankingQueries).hasSize(3);
        for (Method method : rankingQueries) {
            String sql = joinedSql(method);
            assertThat(sql)
                    .as("%s must not cross the Auth/App user boundary", method.getName())
                    .doesNotContain("join users")
                    .doesNotContain("join user_profiles")
                    .contains("null as username")
                    .contains("null as name")
                    .contains("null as avatar");
        }
    }

    @Test
    @DisplayName("generic participant reads are restricted to real contests")
    void genericParticipantReadsExcludeVirtualRows() throws NoSuchMethodException {
        Method byContestAndUser = ContestParticipantMapper.class.getDeclaredMethod(
                "findByContestIdAndUserId", String.class, String.class);
        Method byContestsAndUser = ContestParticipantMapper.class.getDeclaredMethod(
                "findByContestIdsAndUserId", List.class, String.class);

        assertThat(joinedSql(byContestAndUser))
                .as("single-contest participant lookup must not select virtual rows")
                .contains("and is_virtual = 0");
        assertThat(joinedSql(byContestsAndUser))
                .as("batch participant lookup must not select virtual rows")
                .contains("and is_virtual = 0");
    }

    private static String joinedSql(Method method) {
        return String.join(" ", method.getAnnotation(Select.class).value())
                .toLowerCase(Locale.ROOT);
    }
}
