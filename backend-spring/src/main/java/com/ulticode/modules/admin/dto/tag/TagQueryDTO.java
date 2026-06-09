package com.ulticode.modules.admin.dto.tag;

import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class TagQueryDTO {

 private String search;

 /**
 * Tag storage bucket. Unknown values are rejected with HTTP400 by the
 * {@code @Validated} controller before the service is reached — see
 * docs/admin-tags-test-plan.md §7 Bug #2. The whitelist lives in
 * {@link TagTypes#WHITELIST_REGEX} so adding a new bucket only requires
 * updating one place.
 */
 @Pattern(regexp = TagTypes.WHITELIST_REGEX,
 flags = Pattern.Flag.CASE_INSENSITIVE,
 message = "type must be one of PROBLEM, FORUM")
 private String type;

 private Integer page =1;
 private Integer limit =20;
 private String sortBy = "name";
 private String sortOrder = "asc";
}
