package com.ulticode.modules.admin.dto.problem;

import com.ulticode.modules.problem.entity.*;
import com.ulticode.modules.problem.dto.ProblemDetailPublicVO;
import org.mapstruct.*;

import java.util.List;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface AdminProblemMapper {

    @Mapping(target = "id", expression = "java(String.valueOf(problem.getId()))")
    HeaderDataVO toHeaderDataVO(Problem problem);

    @Mapping(target = "detail", ignore = true)
    @Mapping(target = "tags", ignore = true)
    @Mapping(target = "examples", ignore = true)
    @Mapping(target = "id", expression = "java(String.valueOf(problem.getId()))")
    DescriptionDataVO toDescriptionDataVO(Problem problem);

    @Mapping(target = "id", expression = "java(String.valueOf(id))")
    CodeDataVO toCodeDataVO(Long id);

    @Mapping(target = "examples", ignore = true)
    @Mapping(target = "detail", ignore = true)
    @Mapping(target = "tags", ignore = true)
    @Mapping(target = "id", expression = "java(String.valueOf(id))")
    CasesDataVO toCasesDataVO(Long id);

    @Mapping(target = "id", source = "id")
    ProblemTagVO toProblemTagVO(ProblemTag tag);

    List<ProblemTagVO> toProblemTagVOList(List<ProblemTag> tags);

    @Mapping(target = "input", source = "inputText")
    @Mapping(target = "output", source = "outputText")
    @Mapping(target = "order", source = "exampleOrder")
    @Mapping(target = "inputs", expression = "java(parseExampleInputs(example.getInputs()))")
    ProblemExampleVO toProblemExampleVO(ProblemExample example);

    List<ProblemExampleVO> toProblemExampleVOList(List<ProblemExample> examples);

    @Mapping(target = "language", source = "label")
    CodeDataVO.LanguageInfo toLanguageInfo(ProblemLanguage language);

    List<CodeDataVO.LanguageInfo> toLanguageInfoList(List<ProblemLanguage> languages);

    @Mapping(target = "input", source = "inputText")
    @Mapping(target = "output", source = "outputText")
    @Mapping(target = "order", source = "exampleOrder")
    @Mapping(target = "inputs", expression = "java(parseExampleInputs(example.getInputs()))")
    CasesDataVO.ExampleInfo toExampleInfo(ProblemExample example);

    List<CasesDataVO.ExampleInfo> toExampleInfoList(List<ProblemExample> examples);

    default DescriptionDataVO.DetailInfo toDetailInfo(ProblemDetail detail) {
        if (detail == null) {
            return null;
        }
        DescriptionDataVO.DetailInfo info = new DescriptionDataVO.DetailInfo();
        info.setSummary(detail.getSummary());
        info.setContent(detail.getContent());
        info.setConstraintsJson(parseJsonToList(detail.getConstraintsJson()));
        info.setHints(parseJsonToList(detail.getHints()));
        return info;
    }

    default CasesDataVO.DetailInfo toCasesDetailInfo(ProblemDetail detail) {
        if (detail == null) {
            return null;
        }
        CasesDataVO.DetailInfo info = new CasesDataVO.DetailInfo();
        info.setConstraintsJson(parseJsonToList(detail.getConstraintsJson()));
        info.setHints(parseJsonToList(detail.getHints()));
        return info;
    }

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

    default List<ProblemDetailPublicVO.InputData> parseExampleInputs(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return com.fasterxml.jackson.databind.json.JsonMapper.builder().build()
                    .readValue(json, new com.fasterxml.jackson.core.type.TypeReference<List<ProblemDetailPublicVO.InputData>>() {});
        } catch (Exception e) {
            return null;
        }
    }
}
