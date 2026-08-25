package com.local.pp_backen.dto.exam;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ExamRequest {

    @NotBlank(message = "Exam title is required")
    private String title;

    private String description;

    /** Which subject this exam belongs to. Required when creating. */
    private Long subjectId;
}
