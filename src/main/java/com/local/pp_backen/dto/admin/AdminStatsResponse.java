package com.local.pp_backen.dto.admin;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/** Everything the admin dashboard needs in a single call. */
@Data
@Builder
public class AdminStatsResponse {

    private Members members;
    private Content content;
    private Activity activity;
    private List<AdminUserResponse> recentSignups;
    private List<RecentAttempt> recentAttempts;

    @Data
    @Builder
    public static class Members {
        private long total;
        /** Signed in within the last 30 days */
        private long active;
        /** Signed in within the last 7 days */
        private long activeThisWeek;
        private long newThisWeek;
        private long admins;
        private long suspended;
        /** Have never signed in since sign-in tracking began */
        private long neverSignedIn;
    }

    @Data
    @Builder
    public static class Content {
        private long subjects;
        private long exams;
        private long papers;
        private long questions;
    }

    @Data
    @Builder
    public static class Activity {
        private long totalAttempts;
        private long completedAttempts;
        private double averageScorePct;
    }

    @Data
    @Builder
    public static class RecentAttempt {
        private Long attemptId;
        private String username;
        private String paperTitle;
        private int earnedMarks;
        private int totalMarks;
        private double scorePct;
        private String completedAt;
    }
}
