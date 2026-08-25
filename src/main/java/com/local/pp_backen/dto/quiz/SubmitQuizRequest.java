package com.local.pp_backen.dto.quiz;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import java.util.List;

@Data
public class SubmitQuizRequest {

    @NotEmpty(message = "Answers are required")
    @Valid
    private List<AnswerSubmission> answers;
}
