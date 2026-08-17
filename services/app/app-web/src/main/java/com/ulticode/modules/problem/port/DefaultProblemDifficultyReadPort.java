package com.ulticode.modules.problem.port;

import com.ulticode.common.dto.DifficultyCountDTO;
import com.ulticode.app.api.service.ProblemDifficultyReadPort;
import com.ulticode.modules.problem.mapper.ProblemMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class DefaultProblemDifficultyReadPort implements ProblemDifficultyReadPort {

    private final ProblemMapper problemMapper;

    @Override
    public List<DifficultyCountDTO> countByDifficulty() {
        return problemMapper.countByDifficulty();
    }
}
