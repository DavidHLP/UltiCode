package com.ulticode.modules.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/** HTTP request owned by the Admin BFF for creating a contest. */
@Data
@Schema(description = "Create contest request")
public class CreateContestDTO {

    @NotBlank
    @Size(max = 255)
    @Pattern(regexp = "^[\\p{L}\\p{N}\\s\\p{P}]+$")
    private String title;

    @Size(max = 2000)
    private String description;

    @NotNull
    @Future
    private LocalDateTime startTime;

    @NotNull
    @Min(5)
    @Max(1440)
    private Integer duration;

    @Min(1)
    @Max(10000)
    private Integer maxParticipants;

    private Boolean isPremium;
    private Boolean isPublished;
    private List<Long> problemIds;

    @Valid
    private List<AddContestProblemDTO> problems;

    private List<String> tags;

    @Size(max = 255)
    @Pattern(regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$")
    private String slug;

    @Pattern(regexp = "^(ICPC|IOI|CUSTOM)$")
    private String contestType;

    private String scoringRuleId;
}
