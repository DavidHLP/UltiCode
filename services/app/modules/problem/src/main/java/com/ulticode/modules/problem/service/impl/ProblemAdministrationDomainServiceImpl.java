package com.ulticode.modules.problem.service.impl;

import com.ulticode.app.api.error.AppErrorCode;
import com.ulticode.common.error.BaseErrorCode;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.modules.problem.dto.CreateProblemDTO;
import com.ulticode.modules.problem.dto.UpdateProblemDTO;
import com.ulticode.modules.problem.entity.Problem;
import com.ulticode.modules.problem.port.ProblemDetailDomainPort;
import com.ulticode.modules.problem.port.ProblemVersionPort;
import com.ulticode.modules.problem.port.ProblemWritePort;
import com.ulticode.modules.problem.service.ProblemAdministrationDomainService;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;

@Slf4j
public class ProblemAdministrationDomainServiceImpl implements ProblemAdministrationDomainService {

    private final ProblemWritePort writePort;
    private final ProblemDetailDomainPort detailPort;
    private final ProblemVersionPort versionPort;
    private final Clock clock;

    public ProblemAdministrationDomainServiceImpl(
            ProblemWritePort writePort,
            ProblemDetailDomainPort detailPort,
            ProblemVersionPort versionPort,
            Clock clock) {
        this.writePort = writePort;
        this.detailPort = detailPort;
        this.versionPort = versionPort;
        this.clock = clock;
    }

    @Override
    public Optional<Problem> findById(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(writePort.selectById(id));
    }

    @Override
    public Optional<Problem> findBySlug(String slug) {
        if (slug == null || slug.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(writePort.selectBySlug(slug));
    }

    @Override
    public Problem createProblem(CreateProblemDTO dto, String actorId) {
        if (findBySlug(dto.getSlug()).isPresent()) {
            throw new BusinessException(BaseErrorCode.CONFLICT, "Problem with this slug already exists");
        }

        Problem problem = new Problem();
        problem.setSlug(dto.getSlug());
        problem.setTitle(dto.getTitle());
        problem.setDifficulty(dto.getDifficulty());
        problem.setIsPremium(dto.getIsPremium() != null ? dto.getIsPremium() : false);
        problem.setIsPublished(dto.getIsPublished() != null ? dto.getIsPublished() : true);
        problem.setStatus("todo");
        problem.setHasSolution(false);
        problem.setAcceptanceRate(BigDecimal.ZERO);
        problem.setIsFlagged(false);
        problem.setIsDeleted(false);
        problem.setVersion(1);

        if (Boolean.TRUE.equals(problem.getIsPublished())) {
            problem.setPublishedAt(LocalDateTime.now(clock));
            problem.setPublishedBy(actorId);
        }

        writePort.insert(problem);

        versionPort.createInitialVersion(problem.getId(), actorId);

        log.info("Problem created: {} by user {}", problem.getId(), actorId);
        return problem;
    }

    @Override
    public Problem updateProblem(Long id, UpdateProblemDTO dto, String actorId, Long expectedVersion) {
        Problem problem = findById(id)
                .orElseThrow(() -> new BusinessException(BaseErrorCode.NOT_FOUND, "Problem not found"));
        requireExpectedVersion(problem, expectedVersion);

        if (dto.getSlug() != null && !dto.getSlug().equals(problem.getSlug())) {
            Optional<Problem> existingProblem = findBySlug(dto.getSlug());
            if (existingProblem.isPresent() && !existingProblem.get().getId().equals(id)) {
                throw new BusinessException(BaseErrorCode.CONFLICT, "Problem with this slug already exists");
            }
            problem.setSlug(dto.getSlug());
        }
        if (dto.getTitle() != null) {
            problem.setTitle(dto.getTitle());
        }
        if (dto.getDifficulty() != null) {
            problem.setDifficulty(dto.getDifficulty());
        }
        if (dto.getIsPremium() != null) {
            problem.setIsPremium(dto.getIsPremium());
        }
        if (dto.getIsPublished() != null) {
            problem.setIsPublished(dto.getIsPublished());
            if (Boolean.TRUE.equals(dto.getIsPublished()) && problem.getPublishedAt() == null) {
                problem.setPublishedAt(LocalDateTime.now(clock));
                problem.setPublishedBy(actorId);
            }
        }
        if (dto.getHasSolution() != null) {
            problem.setHasSolution(dto.getHasSolution());
        }

        if (expectedVersion == null) {
            writePort.updateById(problem);
        } else {
            requireAffected(writePort.updateById(problem, expectedVersion));
            problem.setVersion(nextVersion(expectedVersion));
        }
        detailPort.applyDetailUpdate(id, problem, dto);
        versionPort.createVersion(id, "UPDATE", null, actorId);

        log.info("Problem updated: {} by user {}", id, actorId);
        return problem;
    }

    @Override
    public void deleteProblem(Long id, String actorId, Long expectedVersion) {
        Problem problem = findById(id)
                .orElseThrow(() -> new BusinessException(BaseErrorCode.NOT_FOUND, "Problem not found"));
        requireExpectedVersion(problem, expectedVersion);

        if (expectedVersion == null) {
            writePort.deleteById(id);
        } else {
            requireAffected(writePort.deleteById(id, expectedVersion));
        }
        log.info("Problem deleted: {} by user {}", id, actorId);
    }

    @Override
    public Problem publishProblem(Long id, String actorId, Long expectedVersion) {
        Problem problem = findById(id)
                .orElseThrow(() -> new BusinessException(BaseErrorCode.NOT_FOUND, "Problem not found"));
        requireExpectedVersion(problem, expectedVersion);

        problem.setIsPublished(true);
        if (problem.getPublishedAt() == null) {
            problem.setPublishedAt(LocalDateTime.now(clock));
            problem.setPublishedBy(actorId);
        }
        persistPublishedState(problem, expectedVersion);

        log.info("Problem published: {} by user {}", id, actorId);
        return problem;
    }

    @Override
    public Problem unpublishProblem(Long id, String actorId, Long expectedVersion) {
        Problem problem = findById(id)
                .orElseThrow(() -> new BusinessException(BaseErrorCode.NOT_FOUND, "Problem not found"));
        requireExpectedVersion(problem, expectedVersion);

        problem.setIsPublished(false);
        persistPublishedState(problem, expectedVersion);
        log.info("Problem unpublished: {} by user {}", id, actorId);
        return problem;
    }

    private void persistPublishedState(Problem problem, Long expectedVersion) {
        if (expectedVersion == null) {
            writePort.updateById(problem);
        } else {
            requireAffected(writePort.updateById(problem, expectedVersion));
            problem.setVersion(nextVersion(expectedVersion));
        }
    }

    private static void requireExpectedVersion(Problem problem, Long expectedVersion) {
        if (expectedVersion != null
                && (problem.getVersion() == null
                || problem.getVersion().longValue() != expectedVersion.longValue())) {
            throw versionConflict();
        }
    }

    private static void requireAffected(int affectedRows) {
        if (affectedRows != 1) {
            throw versionConflict();
        }
    }

    private static Integer nextVersion(Long expectedVersion) {
        return Math.toIntExact(Math.addExact(expectedVersion, 1L));
    }

    private static BusinessException versionConflict() {
        return new BusinessException(AppErrorCode.VERSION_CONFLICT, "Problem version conflict");
    }
}
