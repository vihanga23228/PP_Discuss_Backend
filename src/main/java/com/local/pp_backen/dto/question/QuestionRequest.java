package com.local.pp_backen.dto.question;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import java.util.List;

@Data
public class QuestionRequest {

    @NotBlank(message = "Question type is required (tf, single, multi)")
    private String type;

    @NotBlank(message = "Question stem is required")
    private String stem;

    private String stemSi;

    private String note;
    private String noteSi;
    private String imageUrl;

    private String explanation;
    private String explanationSi;

    private Long paperId;
    private Long subjectId;

    @NotEmpty(message = "At least one option is required")
    @Valid
    private List<OptionRequest> options;
}
