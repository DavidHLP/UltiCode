package com.ulticode.submission.port.adapter;

import com.ulticode.app.api.service.ProblemTitleLookupPort;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProblemTitleLookupDubboAdapterTest {

    @Test
    void delegatesTheOnlyLookupCapability() {
        ProblemTitleLookupPort owner = mock(ProblemTitleLookupPort.class);
        ProblemTitleLookupDubboAdapter adapter = new ProblemTitleLookupDubboAdapter();
        ReflectionTestUtils.setField(adapter, "appProblemTitles", owner);
        when(owner.searchProblemIdsByTitle("Two Sum")).thenReturn(List.of(101L));

        assertEquals(List.of(101L), adapter.searchProblemIdsByTitle("Two Sum"));
    }
}
