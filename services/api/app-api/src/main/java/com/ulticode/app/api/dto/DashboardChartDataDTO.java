package com.ulticode.app.api.dto;

import java.io.Serializable;

/** Entity-free date bucket returned by an owner dashboard read. */
public record DashboardChartDataDTO(String date, long count) implements Serializable {
    private static final long serialVersionUID = 1L;
}
