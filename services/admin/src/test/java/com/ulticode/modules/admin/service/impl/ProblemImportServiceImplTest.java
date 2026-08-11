package com.ulticode.modules.admin.service.impl;

import com.ulticode.app.api.dto.ProblemAdminRowDTO;
import com.ulticode.app.api.service.ProblemAdminReadPort;
import com.ulticode.app.api.service.ProblemOwnerPort;
import com.ulticode.app.api.service.ProblemOwnerPort.ImportWriteRequest;
import com.ulticode.app.api.service.ProblemOwnerPort.ImportWriteResult;
import com.ulticode.modules.admin.dto.problem.ImportProblemItemDTO;
import com.ulticode.modules.admin.dto.problem.ImportProblemsRequestDTO;
import com.ulticode.modules.admin.dto.problem.ImportProblemsResponseDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProblemImportServiceImpl (problem batch import)")
class ProblemImportServiceImplTest {

    @Mock
    private ProblemOwnerPort problemOwnerPort;

    @Mock
    private ProblemAdminReadPort problemReadPort;

    private ProblemImportServiceImpl importService;

    @BeforeEach
    void setUp() {
        importService = new ProblemImportServiceImpl(problemOwnerPort, problemReadPort);
    }

    @Test
    @DisplayName("reads once, writes once, and maps out-of-order row results")
    void usesOneReadAndOneWriteWithStableResultMapping() {
        List<ImportProblemItemDTO> items = List.of(item("new-a"), item("existing"), item("new-c"));
        when(problemReadPort.findBySlugs(any())).thenReturn(List.of(row(7L, "existing")));
        when(problemOwnerPort.applyImportedBatch(any())).thenReturn(List.of(
                new ImportWriteResult("2", true, null),
                new ImportWriteResult("1", false, "update failed"),
                new ImportWriteResult("0", true, null)));

        ImportProblemsResponseDTO response = importService.importProblems(request(items, "update"));

        ArgumentCaptor<List<String>> slugs = ArgumentCaptor.forClass(List.class);
        verify(problemReadPort).findBySlugs(slugs.capture());
        assertThat(slugs.getValue()).containsExactly("new-a", "existing", "new-c");

        ArgumentCaptor<List<ImportWriteRequest>> writes = ArgumentCaptor.forClass(List.class);
        verify(problemOwnerPort).applyImportedBatch(writes.capture());
        assertThat(writes.getValue()).extracting(ImportWriteRequest::key)
                .containsExactly("0", "1", "2");
        assertThat(writes.getValue().get(0).create()).isTrue();
        assertThat(writes.getValue().get(1).create()).isFalse();
        assertThat(writes.getValue().get(1).id()).isEqualTo(7L);
        assertThat(writes.getValue().get(2).create()).isTrue();
        verify(problemReadPort, never()).findBySlug(any());
        verify(problemOwnerPort, never()).insertImportedProblem(
                any(), any(), any(), any(), any(), any());
        verify(problemOwnerPort, never()).applyImportedUpdate(
                any(), any(), any(), any(), any(), any());

        assertThat(response.getTotal()).isEqualTo(3);
        assertThat(response.getCreated()).isEqualTo(2);
        assertThat(response.getUpdated()).isZero();
        assertThat(response.getFailed()).isEqualTo(1);
        assertThat(response.getResults()).extracting(ImportProblemsResponseDTO.ImportResultItem::getSlug)
                .containsExactly("new-a", "existing", "new-c");
        assertThat(response.getResults().get(0).getAction()).isEqualTo("created");
        assertThat(response.getResults().get(1).getError()).isEqualTo("update failed");
        assertThat(response.getResults().get(2).getAction()).isEqualTo("created");
    }

    @Test
    @DisplayName("duplicate new slug becomes an existing row for later conflict handling")
    void duplicateSlugUsesCreatedState() {
        List<ImportProblemItemDTO> items = List.of(item("dup"), item("dup"));
        when(problemReadPort.findBySlugs(any())).thenReturn(List.of());
        when(problemOwnerPort.applyImportedBatch(any())).thenReturn(List.of(
                new ImportWriteResult("0", true, null),
                new ImportWriteResult("1", true, null)));

        ImportProblemsResponseDTO response = importService.importProblems(request(items, "update"));

        ArgumentCaptor<List<ImportWriteRequest>> writes = ArgumentCaptor.forClass(List.class);
        verify(problemOwnerPort).applyImportedBatch(writes.capture());
        assertThat(writes.getValue()).extracting(ImportWriteRequest::key)
                .containsExactly("0", "1");
        assertThat(writes.getValue().get(0).create()).isTrue();
        assertThat(writes.getValue().get(1).create()).isFalse();
        assertThat(writes.getValue().get(1).id()).isNull();
        assertThat(writes.getValue().get(1).slug()).isEqualTo("dup");
        assertThat(response.getCreated()).isEqualTo(1);
        assertThat(response.getUpdated()).isEqualTo(1);
        assertThat(response.getResults()).extracting(ImportProblemsResponseDTO.ImportResultItem::getAction)
                .containsExactly("created", "updated");
    }

    @Test
    @DisplayName("skip conflicts do not enter the write batch")
    void skipsWithoutWriting() {
        List<ImportProblemItemDTO> items = List.of(item("missing"), item("missing"), item("present"));
        when(problemReadPort.findBySlugs(any())).thenReturn(List.of(row(9L, "present")));
        when(problemOwnerPort.applyImportedBatch(any())).thenReturn(
                List.of(new ImportWriteResult("0", true, null)));

        ImportProblemsResponseDTO response = importService.importProblems(request(items, "skip"));

        ArgumentCaptor<List<ImportWriteRequest>> writes = ArgumentCaptor.forClass(List.class);
        verify(problemOwnerPort).applyImportedBatch(writes.capture());
        assertThat(writes.getValue()).singleElement().satisfies(write -> {
            assertThat(write.key()).isEqualTo("0");
            assertThat(write.create()).isTrue();
        });
        assertThat(response.getCreated()).isEqualTo(1);
        assertThat(response.getSkipped()).isEqualTo(2);
        assertThat(response.getResults()).extracting(ImportProblemsResponseDTO.ImportResultItem::getAction)
                .containsExactly("created", "skipped", "skipped");
    }

    @Test
    @DisplayName("per-row owner errors fail only that result")
    void isolatesOwnerFailure() {
        List<ImportProblemItemDTO> items = List.of(item("ok"), item("bad"));
        when(problemReadPort.findBySlugs(any())).thenReturn(List.of());
        when(problemOwnerPort.applyImportedBatch(any())).thenReturn(List.of(
                new ImportWriteResult("0", true, null),
                new ImportWriteResult("1", false, "boom")));

        ImportProblemsResponseDTO response = importService.importProblems(request(items, "skip"));

        assertThat(response.getCreated()).isEqualTo(1);
        assertThat(response.getFailed()).isEqualTo(1);
        assertThat(response.getResults().get(0).isSuccess()).isTrue();
        assertThat(response.getResults().get(1).isSuccess()).isFalse();
        assertThat(response.getResults().get(1).getError()).isEqualTo("boom");
    }

    @Test
    @DisplayName("create_new preserves the slug-plus-millis suffix")
    void createNewKeepsSuffixRule() {
        when(problemReadPort.findBySlugs(any())).thenReturn(List.of(row(11L, "dup")));
        when(problemOwnerPort.applyImportedBatch(any())).thenReturn(
                List.of(new ImportWriteResult("0", true, null)));

        importService.importProblems(request(List.of(item("dup")), "create_new"));

        ArgumentCaptor<List<ImportWriteRequest>> writes = ArgumentCaptor.forClass(List.class);
        verify(problemOwnerPort).applyImportedBatch(writes.capture());
        assertThat(writes.getValue().get(0).slug()).startsWith("dup-");
    }

    private ImportProblemItemDTO item(String slug) {
        ImportProblemItemDTO item = new ImportProblemItemDTO();
        item.setSlug(slug);
        item.setTitle("Title " + slug);
        item.setDifficulty("Easy");
        return item;
    }

    private ImportProblemsRequestDTO request(List<ImportProblemItemDTO> items, String onConflict) {
        ImportProblemsRequestDTO request = new ImportProblemsRequestDTO();
        request.setProblems(items);
        request.setOnConflict(onConflict);
        return request;
    }

    private ProblemAdminRowDTO row(Long id, String slug) {
        return new ProblemAdminRowDTO(
                id, slug, "Old", "Easy", null, "todo", false, null, false,
                null, null, false, null, false, null, null, null, null,
                null, null, null, 0L, 0L, List.of(), null, null);
    }
}
