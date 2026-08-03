package com.ulticode.modules.problemlist.dto;

import lombok.Data;

@Data
public class UpdateVisibilityDTO {
    private Boolean isPublic;

    private Boolean isFeatured;
}