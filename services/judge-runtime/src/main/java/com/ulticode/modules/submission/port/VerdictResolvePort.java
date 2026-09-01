package com.ulticode.modules.submission.port;

import com.ulticode.domain.submission.enums.SubmissionStatus;

import java.util.List;

/** Internal judge-runtime seam for reducing case verdicts. */
public interface VerdictResolvePort {

    SubmissionStatus reduceWire(List<String> caseWireValues);
}
