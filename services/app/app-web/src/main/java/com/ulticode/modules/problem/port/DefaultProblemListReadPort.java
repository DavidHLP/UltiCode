package com.ulticode.modules.problem.port;

import com.ulticode.app.api.dto.ProblemListItemDTO;
import com.ulticode.modules.problem.entity.Problem;
import com.ulticode.modules.problem.mapper.ProblemMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class DefaultProblemListReadPort implements ProblemListReadPort {

    private final ProblemMapper problemMapper;

    @Override
    public List<ProblemListItemDTO> findByIds(Collection<Long> problemIds) {
        if (problemIds == null || problemIds.isEmpty()) {
            return List.of();
        }
        List<Problem> problems = problemMapper.selectBatchIds(problemIds);
        if (problems == null || problems.isEmpty()) {
            return List.of();
        }
        List<ProblemMapper.ProblemTagDTO> tags = problemMapper.selectTagsByProblemIds(new ArrayList<>(problemIds));
        Map<Long, List<ProblemListItemDTO.Tag>> tagsByProblemId = tags.stream()
                .collect(Collectors.groupingBy(
                        ProblemMapper.ProblemTagDTO::problemId,
                        Collectors.mapping(
                                tag -> new ProblemListItemDTO.Tag(tag.tagId(), tag.tagName()),
                                Collectors.toList()
                        )
                ));
        return problems.stream()
                .map(p -> new ProblemListItemDTO(
                        p.getId(),
                        p.getSlug(),
                        p.getTitle(),
                        p.getDifficulty(),
                        p.getStatus(),
                        p.getAcceptanceRate(),
                        p.getIsPremium(),
                        p.getHasSolution(),
                        tagsByProblemId.getOrDefault(p.getId(), List.of())
                ))
                .toList();
    }
}