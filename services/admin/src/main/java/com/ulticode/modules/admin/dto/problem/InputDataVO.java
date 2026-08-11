package com.ulticode.modules.admin.dto.problem;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/**
 * Admin-owned example input row, wire-compatible mirror of the App module's
 * {@code ProblemDetailPublicVO.InputData} ({@code name}/{@code value}).
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class InputDataVO {

    private String name;

    private Object value;
}
