package com.ulticode.modules.submission.runtime;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/** Runtime-private request model; App HTTP DTOs stop at the Judge provider Adapter. */
@Data
public class JudgeRunRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    private String language;
    private String code;
    private List<TestCase> testCases;

    @Data
    public static class TestCase implements Serializable {
        private static final long serialVersionUID = 1L;

        private String id;
        private String label;
        private String output;
        private List<Input> inputs;
    }

    @Data
    public static class Input implements Serializable {
        private static final long serialVersionUID = 1L;

        private String id;
        private String label;
        private String name;
        private String value;
        private String type;
    }
}
