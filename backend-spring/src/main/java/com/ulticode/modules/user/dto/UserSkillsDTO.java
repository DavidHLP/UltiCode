package com.ulticode.modules.user.dto;

import java.util.List;

/**
 * DTO for user skills data.
 */
public class UserSkillsDTO {

    private List<UserSkill> skills;
    private int totalSolved;

    public UserSkillsDTO() {
    }

    public UserSkillsDTO(List<UserSkill> skills, int totalSolved) {
        this.skills = skills;
        this.totalSolved = totalSolved;
    }

    public List<UserSkill> getSkills() {
        return skills;
    }

    public void setSkills(List<UserSkill> skills) {
        this.skills = skills;
    }

    public int getTotalSolved() {
        return totalSolved;
    }

    public void setTotalSolved(int totalSolved) {
        this.totalSolved = totalSolved;
    }

    /**
     * Individual user skill entry.
     */
    public static class UserSkill {
        private String tagName;
        private String tagSlug;
        private int count;

        public UserSkill() {
        }

        public UserSkill(String tagName, String tagSlug, int count) {
            this.tagName = tagName;
            this.tagSlug = tagSlug;
            this.count = count;
        }

        public String getTagName() {
            return tagName;
        }

        public void setTagName(String tagName) {
            this.tagName = tagName;
        }

        public String getTagSlug() {
            return tagSlug;
        }

        public void setTagSlug(String tagSlug) {
            this.tagSlug = tagSlug;
        }

        public int getCount() {
            return count;
        }

        public void setCount(int count) {
            this.count = count;
        }
    }
}
