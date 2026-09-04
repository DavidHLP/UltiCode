package com.ulticode.modules.submission.port;

import com.ulticode.modules.submission.controller.RunResultDTO;
import com.ulticode.modules.submission.controller.RunSubmissionDTO;

/** App-private seam for interactive public-case runs. */
public interface InteractiveCodeRunner {

    /** Runs caller-supplied public cases; Judge remains the remote Adapter. */
    RunResultDTO run(RunSubmissionDTO request, Long problemId, String userId);
}
