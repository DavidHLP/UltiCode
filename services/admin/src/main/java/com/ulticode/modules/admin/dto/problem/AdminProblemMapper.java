package com.ulticode.modules.admin.dto.problem;

import com.ulticode.app.api.dto.ProblemAdminCasesDTO;
import com.ulticode.app.api.dto.ProblemAdminCodeDTO;
import com.ulticode.app.api.dto.ProblemAdminDescriptionDTO;
import com.ulticode.app.api.dto.ProblemAdminExampleDTO;
import com.ulticode.app.api.dto.ProblemAdminLanguageDTO;
import com.ulticode.app.api.dto.ProblemAdminRowDTO;
import com.ulticode.app.api.dto.ProblemAdminTagDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import java.util.List;

/**
 * Maps the entity-free {@code ProblemAdminReadPort} DTOs onto the Admin
 * problem VOs. Replaces the former entity-based mapping; every source type
 * is a public app-api contract record, so the Admin module no longer imports
 * problem entities or internal DTOs.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface AdminProblemMapper {

    // ── Header tab ──────────────────────────────────────────────

    @Mapping(target = "id", expression = "java(String.valueOf(problem.id()))")
    HeaderDataVO toHeaderDataVO(ProblemAdminRowDTO problem);

    // ── Description tab ─────────────────────────────────────────

    @Mapping(target = "id", expression = "java(String.valueOf(dto.problem().id()))")
    @Mapping(target = "title", source = "problem.title")
    @Mapping(target = "slug", source = "problem.slug")
    @Mapping(target = "difficulty", source = "problem.difficulty")
    @Mapping(target = "status", source = "problem.status")
    @Mapping(target = "isPremium", source = "problem.isPremium")
    @Mapping(target = "isPublished", source = "problem.isPublished")
    @Mapping(target = "publishedAt", source = "problem.publishedAt")
    @Mapping(target = "createdAt", source = "problem.createdAt")
    @Mapping(target = "updatedAt", source = "problem.updatedAt")
    DescriptionDataVO toDescriptionDataVO(ProblemAdminDescriptionDTO dto);

    default DescriptionDataVO.DetailInfo toDetailInfo(ProblemAdminDescriptionDTO dto) {
        if (dto == null) {
            return null;
        }
        DescriptionDataVO.DetailInfo info = new DescriptionDataVO.DetailInfo();
        info.setSummary(dto.summary());
        info.setContent(dto.content());
        info.setConstraintsJson(dto.constraintsJson());
        info.setHints(dto.hints());
        return info;
    }

    // ── Code tab ────────────────────────────────────────────────

    @Mapping(target = "id", expression = "java(String.valueOf(dto.problem().id()))")
    CodeDataVO toCodeDataVO(ProblemAdminCodeDTO dto);

    @Mapping(target = "language", source = "label")
    CodeDataVO.LanguageInfo toLanguageInfo(ProblemAdminLanguageDTO language);

    List<CodeDataVO.LanguageInfo> toLanguageInfoList(List<ProblemAdminLanguageDTO> languages);

    // ── Cases tab ───────────────────────────────────────────────

    @Mapping(target = "id", expression = "java(String.valueOf(dto.problem().id()))")
    CasesDataVO toCasesDataVO(ProblemAdminCasesDTO dto);

    default CasesDataVO.DetailInfo toCasesDetailInfo(ProblemAdminCasesDTO dto) {
        if (dto == null) {
            return null;
        }
        CasesDataVO.DetailInfo info = new CasesDataVO.DetailInfo();
        info.setConstraintsJson(dto.constraintsJson());
        info.setHints(dto.hints());
        return info;
    }

    // ── Tags ────────────────────────────────────────────────────

    @Mapping(target = "id", source = "id")
    ProblemTagVO toProblemTagVO(ProblemAdminTagDTO tag);

    List<ProblemTagVO> toProblemTagVOList(List<ProblemAdminTagDTO> tags);

    // ── Examples ────────────────────────────────────────────────

    @Mapping(target = "input", source = "inputText")
    @Mapping(target = "output", source = "outputText")
    @Mapping(target = "order", source = "exampleOrder")
    @Mapping(target = "inputs", expression = "java(parseExampleInputs(example.inputs()))")
    ProblemExampleVO toProblemExampleVO(ProblemAdminExampleDTO example);

    List<ProblemExampleVO> toProblemExampleVOList(List<ProblemAdminExampleDTO> examples);

    @Mapping(target = "input", source = "inputText")
    @Mapping(target = "output", source = "outputText")
    @Mapping(target = "order", source = "exampleOrder")
    @Mapping(target = "inputs", expression = "java(parseExampleInputs(example.inputs()))")
    CasesDataVO.ExampleInfo toExampleInfo(ProblemAdminExampleDTO example);

    List<CasesDataVO.ExampleInfo> toExampleInfoList(List<ProblemAdminExampleDTO> examples);

    // ── Problem admin row → wire VO ─────────────────────────────

    ProblemAdminVO toAdminVO(ProblemAdminRowDTO row);

    @Mapping(target = "id", source = "id")
    ProblemAdminVO.ProblemTagVO toProblemAdminTagVO(ProblemAdminTagDTO tag);

    List<ProblemAdminVO.ProblemTagVO> toProblemAdminTagVOList(List<ProblemAdminTagDTO> tags);

    // ── Shared helpers ──────────────────────────────────────────

    static List<String> parseJsonToList(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return com.fasterxml.jackson.databind.json.JsonMapper.builder().build()
                    .readValue(json, new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {});
        } catch (Exception e) {
            return null;
        }
    }

    default List<InputDataVO> parseExampleInputs(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return com.fasterxml.jackson.databind.json.JsonMapper.builder().build()
                    .readValue(json, new com.fasterxml.jackson.core.type.TypeReference<List<InputDataVO>>() {});
        } catch (Exception e) {
            return null;
        }
    }
}
