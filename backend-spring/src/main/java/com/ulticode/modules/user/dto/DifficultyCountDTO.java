package com.ulticode.modules.user.dto;

/**
 * DTO for difficulty count queries.
 */
public class DifficultyCountDTO {
    private final String difficulty;
    private final Long count;

    public DifficultyCountDTO(String difficulty, Long count) {
        this.difficulty = difficulty;
        this.count = count;
    }

    public String getDifficulty() {
        return difficulty;
    }

    public Long getCount() {
        return count;
    }
}
