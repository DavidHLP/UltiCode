package com.ulticode.app.dubbo.provider;

import com.ulticode.modules.problem.adapter.DefaultProblemAdminReadAdapter;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProblemTitleLookupProviderTest {

    @Test
    void delegatesTheOnlyLookupCapability() {
        DefaultProblemAdminReadAdapter delegate = mock(DefaultProblemAdminReadAdapter.class);
        ProblemTitleLookupProvider provider = new ProblemTitleLookupProvider(delegate);
        when(delegate.searchProblemIdsByTitle("Two Sum")).thenReturn(List.of(101L));

        assertEquals(List.of(101L), provider.searchProblemIdsByTitle("Two Sum"));
    }
}
