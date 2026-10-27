package com.ulticode.modules.admin.dto.problem;

import com.ulticode.app.api.dto.ProblemAdminDescriptionDTO;
import com.ulticode.app.api.dto.ProblemAdminRowDTO;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdminProblemMapperTest {
    private final AdminProblemMapper mapper = Mappers.getMapper(AdminProblemMapper.class);

    @Test
    void descriptionReadbackIncludesPersistedDetailSections() {
        var row = mock(ProblemAdminRowDTO.class);
        when(row.id()).thenReturn(7L);
        var dto = new ProblemAdminDescriptionDTO(row, "summary", "content", List.of("constraint"), List.of("hint"), List.of(), List.of());
        var result = mapper.toDescriptionDataVO(dto);
        assertThat(result.getId()).isEqualTo("7");
        assertThat(result.getDetail().getSummary()).isEqualTo("summary");
        assertThat(result.getDetail().getContent()).isEqualTo("content");
        assertThat(result.getDetail().getConstraintsJson()).containsExactly("constraint");
        assertThat(result.getDetail().getHints()).containsExactly("hint");
    }

    @Test
    void absentDetailRemainsAbsent() {
        var row = mock(ProblemAdminRowDTO.class);
        var dto = new ProblemAdminDescriptionDTO(row, null, null, null, null, List.of(), List.of());
        assertThat(mapper.toDescriptionDataVO(dto).getDetail()).isNull();
    }
}
