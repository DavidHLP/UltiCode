package com.ulticode.modules.problem.port;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.modules.problem.dto.LanguageConfigDTO;
import com.ulticode.modules.problem.dto.UpdateProblemDTO;
import com.ulticode.modules.problem.entity.Problem;
import com.ulticode.modules.problem.entity.ProblemDetail;
import com.ulticode.modules.problem.entity.ProblemExample;
import com.ulticode.modules.problem.entity.ProblemLanguage;
import com.ulticode.modules.problem.entity.ProblemTag;
import com.ulticode.modules.problem.entity.ProblemTagRelation;
import com.ulticode.modules.problem.mapper.ProblemDetailMapper;
import com.ulticode.modules.problem.mapper.ProblemExampleMapper;
import com.ulticode.modules.problem.mapper.ProblemLanguageMapper;
import com.ulticode.modules.problem.mapper.ProblemTagMapper;
import com.ulticode.modules.problem.mapper.ProblemTagRelationMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Clock;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import com.ulticode.common.auth.CurrentUserProvider;

/**
 * Unit tests for {@link DefaultProblemDetailPort}. The interface is the test
 * surface — each of the four satellite-write branches is exercised directly
 * with mapper mocks, with no need to stand up the {@code ProblemServiceImpl}
 * collaborators. A real {@link ObjectMapper} is used so the examples-JSON
 * parsing path is exercised end-to-end.
 *
 * <p>Note: {@code argThat} / {@code any} calls specify explicit type arguments
 * because MyBatis-Plus {@code BaseMapper} overloads {@code insert(T)} and
 * {@code insert(Collection<T>)} (likewise {@code updateById}); an untyped
 * matcher makes the call ambiguous to the compiler.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DefaultProblemDetailPortTest {

    @Mock
    private ProblemDetailMapper problemDetailMapper;

    @Mock
    private ProblemExampleMapper problemExampleMapper;

    @Mock
    private ProblemLanguageMapper problemLanguageMapper;

    @Mock
    private ProblemTagMapper problemTagMapper;

    @Mock
    private ProblemTagRelationMapper problemTagRelationMapper;
    @Mock
    private CurrentUserProvider currentUserProvider;

    private DefaultProblemDetailPort port;

    @BeforeEach
    void setUp() {
        port = new DefaultProblemDetailPort(
                problemDetailMapper,
                problemExampleMapper,
                problemLanguageMapper,
                problemTagMapper,
                problemTagRelationMapper,
                new ObjectMapper(),
                Clock.systemDefaultZone(),
                new com.ulticode.common.uuid.FixedUuidGenerator());
    }

    private Problem problem(Long id, String slug) {
        Problem p = new Problem();
        p.setId(id);
        p.setSlug(slug);
        return p;
    }

    @Test
    void applyDetailUpdate_noSections_isNoOp() {
        UpdateProblemDTO dto = new UpdateProblemDTO();

        port.applyDetailUpdate(1L, problem(1L, "two-sum"), dto);

        verifyNoInteractions(problemDetailMapper, problemExampleMapper,
                problemLanguageMapper, problemTagMapper, problemTagRelationMapper);
    }

    @Test
    void applyDetailUpdate_nullProblem_throwsNpe() {
        assertThatThrownBy(() -> port.applyDetailUpdate(1L, null, new UpdateProblemDTO()))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void applyDetailUpdate_nullDto_throwsNpe() {
        assertThatThrownBy(() -> port.applyDetailUpdate(1L, problem(1L, "two-sum"), null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void upsertDetailContent_existingDetail_updatesById() {
        UpdateProblemDTO dto = new UpdateProblemDTO();
        dto.setSummary("new summary");
        ProblemDetail existing = new ProblemDetail();
        existing.setProblemId(1L);
        when(problemDetailMapper.selectOne(any())).thenReturn(existing);

        port.applyDetailUpdate(1L, problem(1L, "two-sum"), dto);

        verify(problemDetailMapper).updateById(argThat((ProblemDetail d) -> "new summary".equals(d.getSummary())));
        verify(problemDetailMapper, never()).insert(any(ProblemDetail.class));
    }

    @Test
    void upsertDetailContent_newDetail_insertsAndDenormalizesSlug() {
        UpdateProblemDTO dto = new UpdateProblemDTO();
        dto.setContent("## content");
        when(problemDetailMapper.selectOne(any())).thenReturn(null);

        port.applyDetailUpdate(1L, problem(1L, "two-sum"), dto);

        verify(problemDetailMapper).insert(argThat((ProblemDetail d) ->
                "two-sum".equals(d.getSlug())
                        && ProblemDetail.EMPTY_JSON_ARRAY.equals(d.getConstraintsJson())));
    }

    @Test
    void rebuildLanguages_normal_rebuildsFromTemplate() {
        UpdateProblemDTO dto = new UpdateProblemDTO();
        LanguageConfigDTO cfg = new LanguageConfigDTO();
        cfg.setLanguage("python");
        cfg.setStarterCode("print('hi')");
        dto.setLanguages(List.of(cfg));
        ProblemLanguage template = new ProblemLanguage();
        template.setLabel("Python");
        template.setValue("python");
        template.setStyle("python");
        template.setStarterCode("# default");
        when(problemLanguageMapper.findByValue("python")).thenReturn(template);

        port.applyDetailUpdate(1L, problem(1L, "two-sum"), dto);

        verify(problemLanguageMapper).delete(any());
        verify(problemLanguageMapper).insert(argThat((ProblemLanguage lang) ->
                "Python".equals(lang.getLabel()) && "print('hi')".equals(lang.getStarterCode())));
    }

    @Test
    void rebuildLanguages_unknownLanguage_throws() {
        UpdateProblemDTO dto = new UpdateProblemDTO();
        LanguageConfigDTO cfg = new LanguageConfigDTO();
        cfg.setLanguage("brainfuck");
        dto.setLanguages(List.of(cfg));
        when(problemLanguageMapper.findByValue("brainfuck")).thenReturn(null);

        assertThatThrownBy(() -> port.applyDetailUpdate(1L, problem(1L, "two-sum"), dto))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void rebuildExamples_validJson_rebuilds() {
        UpdateProblemDTO dto = new UpdateProblemDTO();
        dto.setExamples("[{\"inputText\":\"in\",\"outputText\":\"out\",\"explanation\":\"e\"}]");

        port.applyDetailUpdate(1L, problem(1L, "two-sum"), dto);

        verify(problemExampleMapper).delete(any());
        verify(problemExampleMapper).insert(argThat((ProblemExample ex) ->
                "in".equals(ex.getInputText())
                        && "out".equals(ex.getOutputText())
                        && Integer.valueOf(1).equals(ex.getExampleOrder())));
    }

    @Test
    void rebuildExamples_invalidJson_throws() {
        UpdateProblemDTO dto = new UpdateProblemDTO();
        dto.setExamples("not-json");

        assertThatThrownBy(() -> port.applyDetailUpdate(1L, problem(1L, "two-sum"), dto))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void rebuildTags_normal_rebuildsRelations() {
        UpdateProblemDTO dto = new UpdateProblemDTO();
        dto.setTags(List.of("array"));
        ProblemTag tag = new ProblemTag();
        tag.setId("tag-1");
        tag.setLabel("array");
        when(problemTagMapper.selectOne(any())).thenReturn(tag);

        port.applyDetailUpdate(1L, problem(1L, "two-sum"), dto);

        verify(problemTagRelationMapper).delete(any());
        verify(problemTagRelationMapper).insert(argThat((ProblemTagRelation r) ->
                "tag-1".equals(r.getTagId()) && Long.valueOf(1L).equals(r.getProblemId())));
    }

    @Test
    void rebuildTags_unknownLabel_throws() {
        UpdateProblemDTO dto = new UpdateProblemDTO();
        dto.setTags(List.of("nonexistent"));
        when(problemTagMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> port.applyDetailUpdate(1L, problem(1L, "two-sum"), dto))
                .isInstanceOf(BusinessException.class);
    }
}
