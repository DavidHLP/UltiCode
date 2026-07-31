package com.ulticode.modules.solution.controller;

import com.ulticode.UlticodeBackendApplication;
import com.ulticode.common.config.MapperConfig;
import com.ulticode.modules.solution.dto.SolutionTopicVO;
import com.ulticode.modules.solution.service.SolutionTopicService;
import com.ulticode.security.AuthenticationEntryPointImpl;
import com.ulticode.security.jwt.JwtAuthenticationFilter;
import com.ulticode.security.jwt.JwtProperties;
import com.ulticode.security.jwt.JwtTokenProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        value = SolutionTopicController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = MapperConfig.class
        )
)
@ContextConfiguration(classes = UlticodeBackendApplication.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("SolutionTopicController")
class SolutionTopicControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SolutionTopicService solutionTopicService;

    @MockBean private JwtTokenProvider jwtTokenProvider;
    @MockBean private JwtProperties jwtProperties;
    @MockBean private JwtAuthenticationFilter jwtAuthenticationFilter;
    @MockBean private AuthenticationEntryPointImpl authenticationEntryPoint;

    @Test
    @DisplayName("GET /solution-topics returns 200 with topic list")
    void listTopics_returnsOkWithTopicList() throws Exception {
        when(solutionTopicService.listTopics()).thenReturn(List.of(
                new SolutionTopicVO("topic-greedy", "贪心算法", 0),
                new SolutionTopicVO("topic-dp", "动态规划", 5)
        ));

        mockMvc.perform(get("/solution-topics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].id").value("topic-greedy"))
                .andExpect(jsonPath("$.data[0].name").value("贪心算法"))
                .andExpect(jsonPath("$.data[0].count").value(0))
                .andExpect(jsonPath("$.data[1].id").value("topic-dp"))
                .andExpect(jsonPath("$.data[1].name").value("动态规划"))
                .andExpect(jsonPath("$.data[1].count").value(5));
    }
}
