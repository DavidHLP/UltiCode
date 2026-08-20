package com.ulticode.modules.search.source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ulticode.app.api.dto.ProblemIndexDTO;
import com.ulticode.app.api.service.ProblemSearchReadPort;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SearchSourceContractTest {

    @Mock
    private ProblemSearchReadPort problemSearchReadPort;

    @Test
    void passesOffsetAndExposesExactCountContract() {
        when(problemSearchReadPort.searchForIndex("two", 7, 3))
                .thenReturn(List.of(new ProblemIndexDTO("p-1", "Two Sum", "two-sum", "Easy")));
        when(problemSearchReadPort.countForIndex("two")).thenReturn(9L);

        ProblemSearchSource source = new ProblemSearchSource(problemSearchReadPort);

        assertThat(source.searchDatabase("two", 7, 3)).singleElement()
                .satisfies(item -> assertThat(item.getId()).isEqualTo("p-1"));
        assertThat(source.countDatabase("two")).isEqualTo(9L);
        verify(problemSearchReadPort).searchForIndex("two", 7, 3);
        verify(problemSearchReadPort).countForIndex("two");
    }
}
