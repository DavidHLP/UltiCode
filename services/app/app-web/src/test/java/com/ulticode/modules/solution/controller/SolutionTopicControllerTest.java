package com.ulticode.modules.solution.controller;

import com.ulticode.common.response.Result;
import com.ulticode.modules.solution.dto.SolutionTopicVO;
import com.ulticode.modules.solution.service.SolutionTopicService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("SolutionTopicController")
class SolutionTopicControllerTest {

    @Mock
    private SolutionTopicService solutionTopicService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        SolutionTopicController controller = new SolutionTopicController(solutionTopicService);
        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setMessageConverters(new MappingJackson2HttpMessageConverter())
                .build();
    }

    @Test
    @DisplayName("GET /solution-topics returns 200 with topic list")
    void listTopics_returnsOkWithTopicList() throws Exception {
        when(solutionTopicService.listTopics()).thenReturn(List.of(
                new SolutionTopicVO("topic-greedy", "贪心算法", 0),
                new SolutionTopicVO("topic-dp", "动态规划", 5)
        ));

        mockMvc.perform(get("/solution-topics")
                        .accept(MediaType.APPLICATION_JSON))
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
