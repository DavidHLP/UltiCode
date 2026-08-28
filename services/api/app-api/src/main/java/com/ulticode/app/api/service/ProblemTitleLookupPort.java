package com.ulticode.app.api.service;

import java.util.List;

/** Narrow App-owner lookup used by Submission admin search. */
public interface ProblemTitleLookupPort {

    /** Problem ids whose title contains the supplied text. */
    List<Long> searchProblemIdsByTitle(String title);
}
