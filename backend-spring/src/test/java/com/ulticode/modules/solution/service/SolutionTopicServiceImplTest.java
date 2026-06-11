package com.ulticode.modules.solution.service;

import com.ulticode.modules.solution.dto.SolutionTopicVO;
import com.ulticode.modules.solution.mapper.SolutionTopicMapper;
import com.ulticode.modules.solution.service.impl.SolutionTopicServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SolutionTopicService")
class SolutionTopicServiceImplTest {

    @Mock
    private SolutionTopicMapper solutionTopicMapper;

    @InjectMocks
    private SolutionTopicServiceImpl service;

    @Test
    @DisplayName("listTopics returns mapped VO list when DB has rows")
    void listTopics_returnsMappedList() {
        // Use HashMap (not Map.of) so non-null safety holds for any dialect
        Map<String, Object> row1 = new HashMap<>();
        row1.put("id", "topic-greedy");
        row1.put("name", "贪心算法");
        row1.put("sort_order", 10);
        row1.put("solution_count", 0);

        Map<String, Object> row2 = new HashMap<>();
        row2.put("id", "topic-dp");
        row2.put("name", "动态规划");
        row2.put("sort_order", 20);
        row2.put("solution_count", 5);

        when(solutionTopicMapper.listActiveTopicsWithCount()).thenReturn(List.of(row1, row2));

        List<SolutionTopicVO> result = service.listTopics();

        assertEquals(2, result.size());
        assertEquals("topic-greedy", result.get(0).getId());
        assertEquals("贪心算法", result.get(0).getName());
        assertEquals(0, result.get(0).getCount());
        assertEquals("topic-dp", result.get(1).getId());
        assertEquals("动态规划", result.get(1).getName());
        assertEquals(5, result.get(1).getCount());
    }

    @Test
    @DisplayName("listTopics returns empty list when DB has no rows")
    void listTopics_returnsEmptyList() {
        when(solutionTopicMapper.listActiveTopicsWithCount()).thenReturn(List.of());

        assertTrue(service.listTopics().isEmpty());
    }
}
