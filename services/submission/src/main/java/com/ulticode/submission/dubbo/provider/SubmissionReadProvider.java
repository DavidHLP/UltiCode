package com.ulticode.submission.dubbo.provider;

import com.ulticode.submission.api.dto.SubmissionVO;
import com.ulticode.app.api.service.ProblemFactsPort;
import com.ulticode.submission.api.service.SubmissionReadPort;
import com.ulticode.app.api.service.SubmissionUserReadPort;
import com.ulticode.modules.submission.entity.Submission;
import com.ulticode.modules.submission.mapper.SubmissionMapper;
import com.ulticode.modules.submission.projection.SubmissionProjection;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboService;

/**
 * Dubbo provider for {@link SubmissionReadPort} exported by
 * {@code backend-submission} so external modules (contest) project
 * submission entities to VOs from the Submission owner schema.
 *
 * <p>SPLIT-004 slice-6: user-visible projection runs locally
 * ({@link SubmissionProjection}, P0-1 hidden-case filter), then user and
 * problem summaries are enriched through the App/Auth-owned seams
 * ({@link SubmissionUserReadPort}, {@link ProblemFactsPort}) — never
 * reading user or problem tables (DEC-011). The App provider
 * (group=backend-app) remains the active route until the read-routing
 * cutover slice; this provider is the capability, not the switch.
 */
@DubboService(group = "backend-submission", version = "1.0.0")
@RequiredArgsConstructor
public class SubmissionReadProvider implements SubmissionReadPort {

    private final SubmissionMapper submissionMapper;
    private final SubmissionProjection submissionProjection;
    private final SubmissionUserReadPort userReadPort;
    private final ProblemFactsPort problemFactsPort;

    @Override
    public SubmissionVO toVO(String submissionId) {
        Submission submission = submissionMapper.selectById(submissionId);
        if (submission == null) {
            return null;
        }
        SubmissionVO vo = submissionProjection.toVO(submission);

        SubmissionUserReadPort.UserSummary user = userReadPort.findById(submission.getUserId());
        if (user != null) {
            SubmissionVO.UserInfo userInfo = new SubmissionVO.UserInfo();
            userInfo.setId(user.id());
            userInfo.setUsername(user.username());
            userInfo.setName(user.name());
            userInfo.setAvatar(user.avatar());
            vo.setUser(userInfo);
        }

        ProblemFactsPort.ProblemDisplayFacts facts =
                problemFactsPort.findDisplayFacts(submission.getProblemId());
        if (facts != null) {
            SubmissionVO.ProblemInfo problemInfo = new SubmissionVO.ProblemInfo();
            problemInfo.setId(facts.id());
            problemInfo.setTitle(facts.title());
            problemInfo.setSlug(facts.slug());
            vo.setProblem(problemInfo);
        }

        return vo;
    }
}
