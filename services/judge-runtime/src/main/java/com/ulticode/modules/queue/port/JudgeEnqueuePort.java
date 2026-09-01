package com.ulticode.modules.queue.port;

/** Internal queue seam for enqueuing judge jobs. */
public interface JudgeEnqueuePort {

    void enqueueJudgeJob(String submissionId, String problemId, String userId,
                         String language, String code);
}
