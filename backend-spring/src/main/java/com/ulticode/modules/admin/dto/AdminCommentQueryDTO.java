package com.ulticode.modules.admin.dto;

import lombok.Data;

@Data
public class AdminCommentQueryDTO {

    private String search;

    private String type;

    private Boolean isFlagged;

    private Boolean isDeleted;

    private Integer page = 1;

    private Integer limit = 10;
}