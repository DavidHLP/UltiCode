package com.ulticode.modules.contest.port;

import com.ulticode.app.api.command.AddContestProblemCommand;
import com.ulticode.app.api.command.CreateContestCommand;
import com.ulticode.app.api.command.RemoveContestProblemCommand;
import com.ulticode.app.api.command.UpdateContestCommand;
import com.ulticode.app.api.dto.ContestProblemAdminDTO;

/**
 * Owner-only write surface for contest tables. The implementation lives in
 * the contest owner; external modules use the app-api Dubbo contract instead
 * of importing this seam or any contest implementation type.
 */
public interface ContestOwnerPort {

    String createContest(CreateContestCommand command);

    void updateContest(UpdateContestCommand command);

    void deleteContest(String id, String deletedBy);

    void startContest(String id);

    void endContest(String id);

    ContestProblemAdminDTO addProblem(AddContestProblemCommand command);

    void removeProblem(RemoveContestProblemCommand command);

    String createAnnouncement(String contestId, String title, String content, Boolean isPinned);

    void updateAnnouncement(String contestId, String announcementId,
                            String title, String content, Boolean isPinned);

    void deleteAnnouncement(String contestId, String announcementId);
}
