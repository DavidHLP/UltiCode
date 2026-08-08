package com.ulticode.modules.admin.dto.tag;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class CreateTagDTO {

 @NotBlank(message = "Tag name is required")
 private String name;

 private String slug;

 private String description;

 private String color;

 /**
 * Tag storage bucket. Must be one of {@link TagTypes#WHITELIST_REGEX};
 * unknown values are rejected with HTTP400 — see docs/admin-tags-test-plan.md
 * §7 Bug #2.
 */
 @NotNull(message = "Tag type is required")
 @Pattern(regexp = TagTypes.WHITELIST_REGEX,
 flags = Pattern.Flag.CASE_INSENSITIVE,
 message = "type must be one of PROBLEM, FORUM")
 private String type;
}
