package com.ulticode.modules.queue.port;

import com.ulticode.submission.api.queue.JudgeJobEnvelope;

/** Submission's dispatch port for publishing a job once per submission and generation. */
@FunctionalInterface
public interface JudgeJobEnqueuer {

    void enqueue(JudgeJobEnvelope envelope);
}
