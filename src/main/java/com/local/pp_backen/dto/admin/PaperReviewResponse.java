package com.local.pp_backen.dto.admin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Which questions in a paper still need a person to look at them, and why.
 *
 * <p>Only the questions with something wrong are listed: on a 50-question paper
 * the useful answer is the short list to fix, not a verdict on all fifty.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaperReviewResponse {

    private Long paperId;
    private String paperTitle;
    private int questionCount;

    /** How many questions carry at least one flag. */
    private int needsAttention;

    private List<QuestionFlags> questions;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QuestionFlags {
        private Long questionId;
        /** Position in the paper, 1-based — what the admin console shows as "Q7". */
        private int number;
        private String type;
        /** Enough of the stem to recognise the question in a list. */
        private String preview;
        private List<String> reasons;
    }
}
