package com.local.pp_backen.dto.quiz;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class StartQuizRequest {

    @NotNull(message = "Paper ID is required")
    private Long paperId;

    /** Number of questions for the quiz. If null, all questions from the paper are used. */
    private Integer numberOfQuestions;
}
