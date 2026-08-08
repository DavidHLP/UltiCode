package com.ulticode.app.api.service;

import com.ulticode.app.api.dto.ProblemExampleDTO;

import java.util.List;

/**
 * Read port for problem examples (sample inputs/outputs).
 */
public interface ProblemExampleReadPort {
    List<ProblemExampleDTO> findByProblemId(Long problemId);
}