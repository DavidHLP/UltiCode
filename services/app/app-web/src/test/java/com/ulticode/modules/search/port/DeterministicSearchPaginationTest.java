package com.ulticode.modules.search.port;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.ulticode.modules.forum.mapper.ForumPostMapper;
import com.ulticode.modules.forum.port.DefaultForumPostReadAdapter;
import com.ulticode.modules.problem.mapper.ProblemMapper;
import com.ulticode.modules.problem.port.DefaultProblemSearchReadPort;
import com.ulticode.modules.solution.mapper.SolutionMapper;
import com.ulticode.modules.solution.port.DefaultSolutionReadAdapter;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class DeterministicSearchPaginationTest {

    @Test
    void problemSearchOrdersByOwnerIdBeforeOffset() {
        ProblemMapper mapper = mock(ProblemMapper.class);
        when(mapper.selectList(any())).thenReturn(List.of());

        new DefaultProblemSearchReadPort(mapper).searchForIndex("java", 20, 10);

        assertStableOwnerOrder(mapper);
    }

    @Test
    void forumSearchOrdersByOwnerIdBeforeOffset() {
        ForumPostMapper mapper = mock(ForumPostMapper.class);
        when(mapper.selectList(any())).thenReturn(List.of());

        new DefaultForumPostReadAdapter(mapper).searchForIndex("java", 20, 10);

        assertStableOwnerOrder(mapper);
    }

    @Test
    void solutionSearchOrdersByOwnerIdBeforeOffset() {
        SolutionMapper mapper = mock(SolutionMapper.class);
        when(mapper.selectList(any())).thenReturn(List.of());

        new DefaultSolutionReadAdapter(mapper).searchForIndex("java", 20, 10);

        assertStableOwnerOrder(mapper);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void assertStableOwnerOrder(Object mapper) {
        ArgumentCaptor<Wrapper> wrapper = ArgumentCaptor.forClass(Wrapper.class);
        if (mapper instanceof ProblemMapper problemMapper) {
            verify(problemMapper).selectList(wrapper.capture());
        } else if (mapper instanceof ForumPostMapper forumPostMapper) {
            verify(forumPostMapper).selectList(wrapper.capture());
        } else if (mapper instanceof SolutionMapper solutionMapper) {
            verify(solutionMapper).selectList(wrapper.capture());
        }
        assertThat(wrapper.getValue().getCustomSqlSegment())
                .contains("ORDER BY id ASC LIMIT 10 OFFSET 20");
    }
}
