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
                .filter(method -> joinedSql(method).contains("g.*"))
                .filter(method -> !joinedSql(method).contains("for update"))
                .toList();

        assertThat(displayQueries).isNotEmpty();
        for (Method method : displayQueries) {
            String sql = joinedSql(method);
            assertThat(sql)
                    .as("%s must read profile display data from user_profiles", method.getName())
                    .contains("left join user_profiles p on g.user_id = p.account_id")
                    .contains("p.name")
                    .doesNotContain("u.name");
        }
    }

    @Test
    @DisplayName("contest ranking queries read profile name and avatar from user_profiles")
    void contestRankingQueriesUseUserProfiles() {
        List<Method> rankingQueries = Arrays.stream(ContestParticipantMapper.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(Select.class))
                .filter(method -> joinedSql(method).contains("u.username"))
                .toList();

        assertThat(rankingQueries).isNotEmpty();
        for (Method method : rankingQueries) {
            String sql = joinedSql(method);
            assertThat(sql)
                    .as("%s must read profile display data from user_profiles", method.getName())
                    .contains("left join user_profiles p on cp.user_id = p.account_id")
                    .contains("p.name")
                    .contains("p.avatar")
                    .doesNotContain("u.name")
                    .doesNotContain("u.avatar");
        }
    }

    private static String joinedSql(Method method) {
        return String.join(" ", method.getAnnotation(Select.class).value())
                .toLowerCase(Locale.ROOT);
    }
}
