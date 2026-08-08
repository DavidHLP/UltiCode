package com.ulticode.modules.admin.dto.tag;

import lombok.Data;

import java.util.List;

@Data
public class TagListResponse {

    private List<TagVO> data;
    private Long total;
    private Integer page;
    private Integer limit;
    private Integer totalPages;

    public static TagListResponse of(List<TagVO> data, Long total, Integer page, Integer limit) {
        TagListResponse response = new TagListResponse();
        response.setData(data);
        response.setTotal(total);
        response.setPage(page);
        response.setLimit(limit);
        int totalPages = 0;
        if (limit != null && limit > 0) {
            totalPages = (int) Math.ceil((double) total / limit);
        }
        response.setTotalPages(totalPages);
        return response;
    }
}
