package com.ulticode.modules.admin.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;


@Data
public class AdminNotificationQueryDTO {


    @Size(max = 200, message = "keyword must be <= 200 characters")
    private String keyword;

    private String type;

    private String category;

    private String announcementId;

    private String sortBy;

    private String sortOrder;

    @Min(value = 1, message = "page must be >= 1")
    private Integer page = 1;

    @Min(value = 1, message = "limit must be >= 1")
    @Max(value = 100, message = "limit must be <= 100")
    private Integer limit = 10;
}