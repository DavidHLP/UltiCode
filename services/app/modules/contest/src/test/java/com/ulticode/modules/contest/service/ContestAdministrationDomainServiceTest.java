package com.ulticode.modules.contest.service;

import com.ulticode.common.error.BaseErrorCode;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.modules.contest.dto.CreateContestDTO;
import com.ulticode.modules.contest.entity.Contest;
import com.ulticode.modules.contest.entity.enums.ContestStatus;
import com.ulticode.modules.contest.port.ContestWritePort;
import com.ulticode.modules.contest.service.impl.ContestAdministrationDomainServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ContestAdministrationDomainServiceTest {

    @Mock
    private ContestWritePort writePort;

    private Clock fixedClock;
    private ContestAdministrationDomainServiceImpl service;

    @BeforeEach
    void setUp() {
        fixedClock = Clock.fixed(Instant.parse("2026-07-29T10:00:00Z"), ZoneId.of("UTC"));
        service = new ContestAdministrationDomainServiceImpl(writePort, fixedClock);
    }

    @Nested
    @DisplayName("createContest")
    class CreateContest {

        @Test
        @DisplayName("successful creation sets defaults and inserts entity")
        void success() {
            CreateContestDTO dto = new CreateContestDTO();
            dto.setSlug("weekly-contest-1");
            dto.setTitle("Weekly Contest 1");
            dto.setStartTime(LocalDateTime.of(2026, 8, 1, 10, 0));
            dto.setDuration(120);

            when(writePort.selectBySlug("weekly-contest-1")).thenReturn(null);
            doAnswer(inv -> {
                Contest c = inv.getArgument(0);
                c.setId("contest-100");
                return null;
            }).when(writePort).insert(any(Contest.class));

            Contest created = service.createContest(dto, "user-1");

            assertThat(created.getId()).isEqualTo("contest-100");
            assertThat(created.getSlug()).isEqualTo("weekly-contest-1");
            assertThat(created.getStatus()).isEqualTo(ContestStatus.UPCOMING.name());
            verify(writePort).insert(any(Contest.class));
        }

        @Test
        @DisplayName("duplicate slug throws CONFLICT BusinessException")
        void duplicateSlug() {
            CreateContestDTO dto = new CreateContestDTO();
            dto.setSlug("dup");

            when(writePort.selectBySlug("dup")).thenReturn(new Contest());

            assertThatThrownBy(() -> service.createContest(dto, "user-1"))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(BaseErrorCode.CONFLICT);
        }
    }

    @Nested
    @DisplayName("lifecycle transitions")
    class Lifecycle {

        @Test
        @DisplayName("startContest sets status to RUNNING")
        void start() {
            Contest contest = new Contest();
            contest.setId("contest-10");
            contest.setStatus(ContestStatus.UPCOMING.name());

            when(writePort.selectById("contest-10")).thenReturn(contest);

            Contest started = service.startContest("contest-10", "admin-1");

            assertThat(started.getStatus()).isEqualTo(ContestStatus.RUNNING.name());
            verify(writePort).updateById(contest);
        }

        @Test
        @DisplayName("endContest sets status to FINISHED")
        void end() {
            Contest contest = new Contest();
            contest.setId("contest-10");
            contest.setStatus(ContestStatus.RUNNING.name());

            when(writePort.selectById("contest-10")).thenReturn(contest);

            Contest ended = service.endContest("contest-10", "admin-1");

            assertThat(ended.getStatus()).isEqualTo(ContestStatus.FINISHED.name());
            verify(writePort).updateById(contest);
        }
    }
}
