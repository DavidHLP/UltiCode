package com.ulticode.modules.admin.dto.problem;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.List;

/**
 * Code data VO for problem code tab.
 * Contains starter code for different languages.
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CodeDataVO {

    private String id;

    /**
     * List of language starter codes
     */
    private List<LanguageInfo> languages;

    /**
     * Inner class for language info
     */
    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class LanguageInfo {
        private String id;
        private String language;
        private String value;
        private String style;
        private String starterCode;
    }
}
