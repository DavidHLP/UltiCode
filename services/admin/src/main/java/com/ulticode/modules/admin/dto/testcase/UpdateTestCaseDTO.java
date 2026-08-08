package com.ulticode.modules.admin.dto.testcase;

import lombok.Data;

/**
 * DTO for updating a test case.
 */
@Data
public class UpdateTestCaseDTO {

    private Boolean isSample;

    private Boolean isHidden;

    private Integer testOrder;

    private String inputText;

    private String outputText;

    private String explanation;

    private String constraints;

    private String inputs;
}
