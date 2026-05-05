package com.ulticode.modules.admin.dto.tag;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TagVO {

    private String id;

    private String name;

    private String slug;

    private String description;

    private String color;

    @JsonProperty("usage_count")
    private Integer usageCount;

    private String type;

    @JsonProperty("created_at")
    private LocalDateTime createdAt;

    @JsonProperty("updated_at")
    private LocalDateTime updatedAt;
}
