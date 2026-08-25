package com.local.pp_backen.dto.quiz;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class QuizResponse {
    private Long attemptId;
    private Long paperId;
    private String paperTitle;
    private int totalQuestions;
    private List<QuizQuestionResponse> questions;
}
