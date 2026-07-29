package com.ulticode.modules.problem.port;

import com.ulticode.modules.problem.entity.Problem;

public interface ProblemWritePort {
    void insert(Problem problem);
    void updateById(Problem problem);
    void deleteById(Long id);
    Problem selectById(Long id);
    Problem selectBySlug(String slug);
}
