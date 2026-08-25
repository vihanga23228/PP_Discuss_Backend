package com.local.pp_backen.dto.quiz;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class QuizResultResponse {
    private Long attemptId;
    private Long paperId;
    private String paperTitle;
    private int totalQuestions;
    /** Questions answered completely correctly */
    private int correctCount;
    /** Marks available across the paper */
    private int totalMarks;
    /** Marks earned */
    private int earnedMarks;
    private double scorePct;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private List<QuestionResult> questionResults;

    @Data
    @Builder
    public static class QuestionResult {
        private Long questionId;
        private String stem;
        private String stemSi;
        private String imageUrl;
        private String type;
        /** True when full marks were earned on this question */
        private boolean correct;
        private int marksEarned;
        private int marksAvailable;
        private String explanation;
        private String explanationSi;
        private List<OptionResult> options;
        private List<Long> selectedOptionIds;
    }

    @Data
    @Builder
    public static class OptionResult {
        private Long id;
        private String label;
        private String text;
        private String textSi;
        private String imageUrl;
        private boolean correct;
        /** The user picked this option (single/multi) or marked it True (tf) */
        private boolean selected;
        private String explanationEn;
        private String explanationSi;
    }
}
