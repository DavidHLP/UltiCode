package com.ulticode.modules.submission.port.adapter;

import com.ulticode.app.api.service.UserExistencePort;
import com.ulticode.app.api.service.SubmissionUserReadPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Production adapter for UserExistencePort, delegating to
 * SubmissionUserReadPort.existsById.
 */
@Component
@RequiredArgsConstructor
public class UserExistenceAdapter implements UserExistencePort {

    private final SubmissionUserReadPort submissionUserReadPort;

    @Override
    public boolean existsById(String userId) {
        return submissionUserReadPort.existsById(userId);
    }
}
