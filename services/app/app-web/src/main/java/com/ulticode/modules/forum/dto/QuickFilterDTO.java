package com.ulticode.modules.forum.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for forum quick filter options.
 *
 * <p>Represents a filter that users can apply to post listings. The {@code value}
 * is the contract the backend understands and the sort key passed to the listing
 * endpoint; the frontend renders its own i18n label from
 * {@code forum.sort.<value>} and does not consume anything from the wire.
 *
 * <p>A previous iteration shipped a {@code label} field with an English string,
 * but the frontend always overwrote it with its i18n lookup, so the wire value
 * was dead. The field is intentionally removed; if a future change wants the
 * backend to act as an i18n fallback (e.g. for clients without translations),
 * re-introduce it as {@code labelFallback} so the semantics stay explicit.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Quick filter option for forum posts")
public class QuickFilterDTO {

    @Schema(description = "Filter value identifier (e.g., 'hot', 'new', 'top'); pass back to the listing endpoint as sortBy")
    private String value;
}
