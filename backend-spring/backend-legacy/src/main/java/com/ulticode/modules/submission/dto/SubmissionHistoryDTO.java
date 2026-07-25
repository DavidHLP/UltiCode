package com.ulticode.modules.submission.dto;

import java.util.List;

/**
 * DTO for submission history data.
 */
public class SubmissionHistoryDTO {

    private List<MonthlySubmission> monthly;
    private List<LanguageSubmission> languages;
    private int totalSubmissions;
    private int totalAccepted;
    private double acceptanceRate;

    public SubmissionHistoryDTO() {
    }

    public SubmissionHistoryDTO(List<MonthlySubmission> monthly,
                                List<LanguageSubmission> languages,
                                int totalSubmissions,
                                int totalAccepted,
                                double acceptanceRate) {
        this.monthly = monthly;
        this.languages = languages;
        this.totalSubmissions = totalSubmissions;
        this.totalAccepted = totalAccepted;
        this.acceptanceRate = acceptanceRate;
    }

    public List<MonthlySubmission> getMonthly() {
        return monthly;
    }

    public void setMonthly(List<MonthlySubmission> monthly) {
        this.monthly = monthly;
    }

    public List<LanguageSubmission> getLanguages() {
        return languages;
    }

    public void setLanguages(List<LanguageSubmission> languages) {
        this.languages = languages;
    }

    public int getTotalSubmissions() {
        return totalSubmissions;
    }

    public void setTotalSubmissions(int totalSubmissions) {
        this.totalSubmissions = totalSubmissions;
    }

    public int getTotalAccepted() {
        return totalAccepted;
    }

    public void setTotalAccepted(int totalAccepted) {
        this.totalAccepted = totalAccepted;
    }

    public double getAcceptanceRate() {
        return acceptanceRate;
    }

    public void setAcceptanceRate(double acceptanceRate) {
        this.acceptanceRate = acceptanceRate;
    }

    /**
     * Monthly submission stats.
     */
    public static class MonthlySubmission {
        private String month;
        private int count;
        private int accepted;

        public MonthlySubmission() {
        }

        public MonthlySubmission(String month, int count, int accepted) {
            this.month = month;
            this.count = count;
            this.accepted = accepted;
        }

        public String getMonth() {
            return month;
        }

        public void setMonth(String month) {
            this.month = month;
        }

        public int getCount() {
            return count;
        }

        public void setCount(int count) {
            this.count = count;
        }

        public int getAccepted() {
            return accepted;
        }

        public void setAccepted(int accepted) {
            this.accepted = accepted;
        }
    }

    /**
     * Language usage stats.
     */
    public static class LanguageSubmission {
        private String language;
        private int count;

        public LanguageSubmission() {
        }

        public LanguageSubmission(String language, int count) {
            this.language = language;
            this.count = count;
        }

        public String getLanguage() {
            return language;
        }

        public void setLanguage(String language) {
            this.language = language;
        }

        public int getCount() {
            return count;
        }

        public void setCount(int count) {
            this.count = count;
        }
    }
}
