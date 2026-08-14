package com.ulticode.modules.problem.port;

import com.ulticode.app.api.dto.ProblemExampleDTO;
import com.ulticode.app.api.service.ProblemExampleReadPort;
import com.ulticode.modules.problem.entity.ProblemExample;
import com.ulticode.modules.problem.mapper.ProblemExampleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Default implementation of ProblemExampleReadPort.
 */
@Component
@org.springframework.context.annotation.Primary
@RequiredArgsConstructor
public class DefaultProblemExampleReadPort implements ProblemExampleReadPort {

    private final ProblemExampleMapper problemExampleMapper;

    @Override
    public List<ProblemExampleDTO> findByProblemId(Long problemId) {
        List<ProblemExample> examples = problemExampleMapper.findByProblemIdOrderByOrder(problemId);
        if (examples == null || examples.isEmpty()) {
            return List.of();
        }
        return examples.stream()
            .map(pe -> new ProblemExampleDTO(
                String.valueOf(pe.getId()),
                pe.getExampleOrder(),
                pe.getInputText(),
                pe.getOutputText(),
                pe.getInputs()
            ))
            .toList();
    }
}
