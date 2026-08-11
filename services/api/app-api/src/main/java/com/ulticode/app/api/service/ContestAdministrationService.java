package com.ulticode.app.api.service;

import com.ulticode.app.api.command.AddContestProblemCommand;
import com.ulticode.app.api.command.CreateContestCommand;
import com.ulticode.app.api.command.DeleteContestCommand;
import com.ulticode.app.api.command.EndContestCommand;
import com.ulticode.app.api.command.RemoveContestProblemCommand;
import com.ulticode.app.api.command.StartContestCommand;
import com.ulticode.app.api.command.UpdateContestCommand;
import com.ulticode.app.api.dto.ContestAdminViewDTO;
import com.ulticode.app.api.dto.ContestProblemAdminDTO;
import com.ulticode.common.rpc.RpcResult;

/**
 * App-owned administrative write provider for contest lifecycle and
 * contest-problem attachment management.
 */
public interface ContestAdministrationService {

    RpcResult<ContestAdminViewDTO> createContest(CreateContestCommand command);

    RpcResult<ContestAdminViewDTO> updateContest(UpdateContestCommand command);

    RpcResult<Void> deleteContest(DeleteContestCommand command);

    RpcResult<ContestAdminViewDTO> startContest(StartContestCommand command);

    RpcResult<ContestAdminViewDTO> endContest(EndContestCommand command);

    /** Add one problem and return the persisted mapping view. */
    RpcResult<ContestProblemAdminDTO> addProblem(AddContestProblemCommand command);

    /** Remove one problem mapping when the contest has no owned results. */
    RpcResult<Void> removeProblem(RemoveContestProblemCommand command);
}
