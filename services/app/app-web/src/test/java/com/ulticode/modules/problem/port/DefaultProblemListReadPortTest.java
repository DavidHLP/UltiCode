package com.ulticode.modules.problem.port;

import com.ulticode.app.api.dto.ProblemListItemDTO;
import com.ulticode.modules.problem.entity.Problem;
import com.ulticode.modules.problem.mapper.ProblemMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DefaultProblemListReadPortTest {

    @Test
    void separatesNamespacedCategoryTagsFromFineGrainedTags() {
        ProblemMapper problemMapper = mock(ProblemMapper.class);
        Problem problem = mock(Problem.class);
        when(problem.getId()).thenReturn(42L);
        when(problemMapper.selectBatchIds(anyCollection())).thenReturn(List.of(problem));
        when(problemMapper.selectTagsByProblemIds(anyList())).thenReturn(List.of(
                new ProblemMapper.ProblemTagDTO(
                        42L, "category", "Algorithms", "problem-category-algorithms"),
                new ProblemMapper.ProblemTagDTO(42L, "array", "Array", "array")
        ));

        List<ProblemListItemDTO> items = new DefaultProblemListReadPort(problemMapper)
                .findByIds(List.of(42L));

        assertThat(items).singleElement().satisfies(item -> {
            assertThat(item.categorySlugs()).containsExactly("algorithms");
            assertThat(item.tags())
                    .extracting(ProblemListItemDTO.Tag::id)
                    .containsExactly("category", "array");
        });
    }
}
