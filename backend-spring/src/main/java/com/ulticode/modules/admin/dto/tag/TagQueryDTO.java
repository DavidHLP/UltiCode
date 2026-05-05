package com.ulticode.modules.admin.dto.tag;

import lombok.Data;

@Data
public class TagQueryDTO {

    private String search;
    private String type;
    private Integer page = 1;
    private Integer limit = 20;
    private String sortBy = "name";
    private String sortOrder = "asc";
}
