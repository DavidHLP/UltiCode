package com.ulticode.modules.user.service.impl;

import com.ulticode.modules.follow.mapper.FollowMapper;
import com.ulticode.modules.problem.mapper.ProblemMapper;
import com.ulticode.modules.problem.mapper.ProblemTagRelationMapper;
import com.ulticode.modules.submission.mapper.SubmissionMapper;
import com.ulticode.modules.user.entity.User;
import com.ulticode.modules.user.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserMapper userMapper;
    @Mock
    private SubmissionMapper submissionMapper;
    @Mock
    private ProblemMapper problemMapper;
    @Mock
    private ProblemTagRelationMapper problemTagRelationMapper;
    @Mock
    private FollowMapper followMapper;
    @Mock
    private PasswordEncoder passwordEncoder;

    private UserServiceImpl userService;

    @BeforeEach
    void setUp() {
        userService = new UserServiceImpl(
                userMapper,
                submissionMapper,
                problemMapper,
                problemTagRelationMapper,
                followMapper,
                passwordEncoder);
    }

    @Test
    @DisplayName("getUserSkillsById maps MyBatis row maps to user skills")
    void getUserSkillsById_mapsRowMaps() {
        User user = new User();
        user.setId("user-123");
        when(userMapper.selectById("user-123")).thenReturn(user);
        when(problemTagRelationMapper.findTagStatsByUserId("user-123")).thenReturn(List.of(
                Map.of("tagName", "动态规划", "tagSlug", "dynamic-programming", "count", 4L),
                Map.of("tagName", "数组", "tagSlug", "array", "count", 2)));
        when(submissionMapper.countAcceptedProblemsByUserId("user-123")).thenReturn(6L);

        var result = userService.getUserSkillsById("user-123");

        assertThat(result.getTotalSolved()).isEqualTo(6);
        assertThat(result.getSkills()).hasSize(2);
        assertThat(result.getSkills().get(0).getTagName()).isEqualTo("动态规划");
        assertThat(result.getSkills().get(0).getTagSlug()).isEqualTo("dynamic-programming");
        assertThat(result.getSkills().get(0).getCount()).isEqualTo(4);
    }

    @Test
    @DisplayName("getUserSkillsById returns empty skills when no tag stats exist")
    void getUserSkillsById_handlesNoTagStats() {
        User user = new User();
        user.setId("user-123");
        when(userMapper.selectById("user-123")).thenReturn(user);
        when(problemTagRelationMapper.findTagStatsByUserId("user-123")).thenReturn(null);
        when(submissionMapper.countAcceptedProblemsByUserId("user-123")).thenReturn(null);

        var result = userService.getUserSkillsById("user-123");

        assertThat(result.getTotalSolved()).isZero();
        assertThat(result.getSkills()).isEmpty();
    }
}
