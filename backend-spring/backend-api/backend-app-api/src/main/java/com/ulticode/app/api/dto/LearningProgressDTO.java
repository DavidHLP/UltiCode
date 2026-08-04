package com.ulticode.app.api.dto;

import java.util.List;
import java.io.Serializable;

/**
 * DTO for learning progress data.
 */
public class LearningProgressDTO implements Serializable {

    private List<WeeklyProgress> weeklyProgress;
    private List<DifficultyProgress> difficultyProgress;
    private int totalProblems;
    private double totalTimeHours;
    private double avgTimePerProblem;
    private int currentStreak;
    private int longestStreak;

    public LearningProgressDTO() {
    }

    public LearningProgressDTO(List<WeeklyProgress> weeklyProgress,
                              List<DifficultyProgress> difficultyProgress,
                              int totalProblems,
                              double totalTimeHours,
                              double avgTimePerProblem,
                              int currentStreak,
                              int longestStreak) {
        this.weeklyProgress = weeklyProgress;
        this.difficultyProgress = difficultyProgress;
        this.totalProblems = totalProblems;
        this.totalTimeHours = totalTimeHours;
        this.avgTimePerProblem = avgTimePerProblem;
        this.currentStreak = currentStreak;
        this.longestStreak = longestStreak;
    }

    public List<WeeklyProgress> getWeeklyProgress() {
        return weeklyProgress;
    }

    public void setWeeklyProgress(List<WeeklyProgress> weeklyProgress) {
        this.weeklyProgress = weeklyProgress;
    }

    public List<DifficultyProgress> getDifficultyProgress() {
        return difficultyProgress;
    }

    public void setDifficultyProgress(List<DifficultyProgress> difficultyProgress) {
        this.difficultyProgress = difficultyProgress;
    }

    public int getTotalProblems() {
        return totalProblems;
    }

    public void setTotalProblems(int totalProblems) {
        this.totalProblems = totalProblems;
    }

    public double getTotalTimeHours() {
        return totalTimeHours;
    }

    public void setTotalTimeHours(double totalTimeHours) {
        this.totalTimeHours = totalTimeHours;
    }

    public double getAvgTimePerProblem() {
        return avgTimePerProblem;
    }

    public void setAvgTimePerProblem(double avgTimePerProblem) {
        this.avgTimePerProblem = avgTimePerProblem;
    }

    public int getCurrentStreak() {
        return currentStreak;
    }

    public void setCurrentStreak(int currentStreak) {
        this.currentStreak = currentStreak;
    }

    public int getLongestStreak() {
        return longestStreak;
    }

    public void setLongestStreak(int longestStreak) {
        this.longestStreak = longestStreak;
    }

    /**
     * Weekly submission progress entry.
     */
    public static class WeeklyProgress implements Serializable {
        private String week;
        private int solved;
        private double timeSpent;

        public WeeklyProgress() {
        }

        public WeeklyProgress(String week, int solved, double timeSpent) {
            this.week = week;
            this.solved = solved;
            this.timeSpent = timeSpent;
        }

        public String getWeek() {
            return week;
        }

        public void setWeek(String week) {
            this.week = week;
        }

        public int getSolved() {
            return solved;
        }

        public void setSolved(int solved) {
            this.solved = solved;
        }

        public double getTimeSpent() {
            return timeSpent;
        }

        public void setTimeSpent(double timeSpent) {
            this.timeSpent = timeSpent;
        }
    }

    /**
     * Difficulty-based progress entry.
     */
    public static class DifficultyProgress implements Serializable {
        private String difficulty;
        private int count;
        private double avgTime;

        public DifficultyProgress() {
        }

        public DifficultyProgress(String difficulty, int count, double avgTime) {
            this.difficulty = difficulty;
            this.count = count;
            this.avgTime = avgTime;
        }

        public String getDifficulty() {
            return difficulty;
        }

        public void setDifficulty(String difficulty) {
            this.difficulty = difficulty;
        }

        public int getCount() {
            return count;
        }

        public void setCount(int count) {
            this.count = count;
        }

        public double getAvgTime() {
            return avgTime;
        }

        public void setAvgTime(double avgTime) {
            this.avgTime = avgTime;
        }
    }
}
