package com.ulticode.modules.admin.dto.problem;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
@Schema(description = "Response from import problems operation")
public class ImportProblemsResponseDTO {

    @Schema(description = "Total problems processed")
    private int total;

    @Schema(description = "Problems created")
    private int created;

    @Schema(description = "Problems updated")
    private int updated;

    @Schema(description = "Problems skipped")
    private int skipped;

    @Schema(description = "Problems failed")
    private int failed;

    @Schema(description = "Detailed results per problem")
    private List<ImportResultItem> results;

    @Data
    @AllArgsConstructor
    public static class ImportResultItem {
        private String slug;
        private boolean success;
        private String error;
        private String action;
    }
}
