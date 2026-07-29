package com.ulticode.modules.submission.port;

import com.ulticode.modules.submission.entity.Submission;

import java.util.List;

public interface SubmissionWritePort {
    Submission selectById(String id);
    List<Submission> selectByIds(List<String> ids);
    void updateById(Submission submission);
}
