package com.ulticode.modules.admin.dto.problem;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.List;

/**
 * Cases data VO for problem test cases tab.
 * Contains examples, constraints, and hints.
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CasesDataVO {

    private String id;

    /**
     * Sample test cases / examples
     */
    private List<ExampleInfo> examples;

    /**
     * Problem detail constraints and hints
     */
    private DetailInfo detail;

    /**
     * Problem tags
     */
    private List<ProblemTagVO> tags;

    /**
     * Inner class for example info
     */
    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ExampleInfo {
        private String id;
        private String input;
        private String output;
        private String explanation;
        private List<InputDataVO> inputs;
        private Integer order;
    }

    /**
     * Inner class for detail info
     */
    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class DetailInfo {
        private List<String> constraintsJson;
        private List<String> hints;
    }
}
