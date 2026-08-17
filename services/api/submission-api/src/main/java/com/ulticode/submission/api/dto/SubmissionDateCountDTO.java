package com.ulticode.submission.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SubmissionDateCountDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String date;
    private Long count;
}
