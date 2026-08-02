package com.ulticode.modules.problem.port;

import com.ulticode.modules.problem.entity.TestCase;
import com.ulticode.modules.problem.mapper.TestCaseMapper;
import com.ulticode.modules.problem.port.TestCaseOwnerPort.TestCaseWrite;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * P3-BURNDOWN-001: unit test for {@link DefaultTestCaseOwnerPort}.
 *
 * <p>Pins the owner write seam that replaced AdminTestCaseService's direct
 * {@link TestCaseMapper} write calls: every write still lands on the same
 * mapper method with the same row shape.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DefaultTestCaseOwnerPort")
class DefaultTestCaseOwnerPortTest {

    @Mock
    private TestCaseMapper testCaseMapper;

    private TestCaseOwnerPort port;

    @BeforeEach
    void setUp() {
        port = new DefaultTestCaseOwnerPort(testCaseMapper);
    }

    private static TestCaseWrite command() {
        return new TestCaseWrite("tc-1", 7L, false, true, 3,
                "in", "out", "expl", "cons", "[]",
                LocalDateTime.parse("2026-07-28T00:00:00"), LocalDateTime.parse("2026-07-28T01:00:00"));
    }

    @Nested
    @DisplayName("insertTestCase()")
    class Insert {

        @Test
        @DisplayName("maps the full command onto TestCaseMapper.insert")
        void mapsCommand() {
            port.insertTestCase(command());

            ArgumentCaptor<TestCase> captor = ArgumentCaptor.forClass(TestCase.class);
            verify(testCaseMapper).insert(captor.capture());
            TestCase row = captor.getValue();
            assertThat(row.getId()).isEqualTo("tc-1");
            assertThat(row.getProblemId()).isEqualTo(7L);
            assertThat(row.getIsSample()).isFalse();
            assertThat(row.getIsHidden()).isTrue();
            assertThat(row.getTestOrder()).isEqualTo(3);
            assertThat(row.getInputText()).isEqualTo("in");
            assertThat(row.getOutputText()).isEqualTo("out");
            assertThat(row.getExplanation()).isEqualTo("expl");
            assertThat(row.getConstraints()).isEqualTo("cons");
            assertThat(row.getInputs()).isEqualTo("[]");
            assertThat(row.getCreatedAt()).isEqualTo(LocalDateTime.parse("2026-07-28T00:00:00"));
            assertThat(row.getUpdatedAt()).isEqualTo(LocalDateTime.parse("2026-07-28T01:00:00"));
        }
    }

    @Nested
    @DisplayName("updateTestCase()")
    class Update {

        @Test
        @DisplayName("persists the full row via updateById")
        void persistsFullRow() {
            port.updateTestCase(command());

            ArgumentCaptor<TestCase> captor = ArgumentCaptor.forClass(TestCase.class);
            verify(testCaseMapper).updateById(captor.capture());
            assertThat(captor.getValue().getId()).isEqualTo("tc-1");
            assertThat(captor.getValue().getTestOrder()).isEqualTo(3);
        }
    }

    @Nested
    @DisplayName("deleteTestCase() / deleteAllForProblem()")
    class Delete {

        @Test
        @DisplayName("deletes one row by primary key")
        void deletesById() {
            port.deleteTestCase("tc-1");
            verify(testCaseMapper).deleteById("tc-1");
        }

        @Test
        @DisplayName("deletes every row of a problem and reports the count")
        void deletesAllForProblem() {
            when(testCaseMapper.delete(any())).thenReturn(2);

            int deleted = port.deleteAllForProblem(7L);

            assertThat(deleted).isEqualTo(2);
            verify(testCaseMapper).delete(any());
        }
    }

    @Nested
    @DisplayName("updateTestOrder()")
    class Reorder {

        @Test
        @DisplayName("updates only id/test_order/updated_at via a shell row")
        void shellUpdate() {
            LocalDateTime now = LocalDateTime.parse("2026-07-28T02:00:00");

            port.updateTestOrder("tc-1", 5, now);

            ArgumentCaptor<TestCase> captor = ArgumentCaptor.forClass(TestCase.class);
            verify(testCaseMapper).updateById(captor.capture());
            TestCase shell = captor.getValue();
            assertThat(shell.getId()).isEqualTo("tc-1");
            assertThat(shell.getTestOrder()).isEqualTo(5);
            assertThat(shell.getUpdatedAt()).isEqualTo(now);
            // Columns outside the reorder must stay untouched (null in shell).
            assertThat(shell.getProblemId()).isNull();
            assertThat(shell.getInputText()).isNull();
        }
    }
}
