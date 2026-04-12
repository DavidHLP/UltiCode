package com.ulticode.modules.i18n.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "Request to parse Accept-Language header")
public class ParseLocaleRequest {

    @NotBlank(message = "Header value must not be blank")
    @Schema(description = "Accept-Language header value", required = true, example = "zh-CN,zh;q=0.9,en;q=0.8")
    private String header;
}
