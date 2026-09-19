package com.ulticode.modules.admin.dto.tag;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateTagDTO {

 /**
 * Optional: omit (or send null) to keep the existing name. Empty string is
 * rejected with HTTP400 to prevent silent no-op updates; validation is covered
 * by the DTO/controller regression tests.
 */
 @Size(min =1, message = "name must not be empty if provided")
 private String name;

 /**
 * Optional: omit (or send null) to keep the existing slug. Empty string is
 * rejected with HTTP400; validation is covered by the DTO/controller regression tests.
 */
 @Size(min =1, message = "slug must not be empty if provided")
 private String slug;

 private String description;

 private String color;

 /**
 * Tag storage bucket. Must be one of {@link TagTypes#WHITELIST_REGEX};
 * unknown values are rejected with HTTP400; the Service-layer guard
 * {@code AdminTagServiceImpl#normalizeType} enforces the same whitelist as
 * a defense-in-depth check for direct callers.
 */
 @NotNull(message = "Tag type is required")
 @Pattern(regexp = TagTypes.WHITELIST_REGEX,
 flags = Pattern.Flag.CASE_INSENSITIVE,
 message = "type must be one of PROBLEM, FORUM")
 private String type;
}
