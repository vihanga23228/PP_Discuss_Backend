package com.local.pp_backen.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Uploads a whole past paper in one go.
 *
 * <p>Either target an existing exam with {@code examId}, or name a new one with
 * {@code newExamTitle}. The {@code questions} array uses the same shape as the
 * seed files in {@code resources/data/}.
 */
@Data
public class ImportPaperRequest {

    /** Target an existing subject, or name a new one with {@link #newSubjectName}. */
    private Long subjectId;
    private String newSubjectName;
    private String newSubjectDescription;

    private Long examId;
    private String newExamTitle;
    private String newExamDescription;

    @NotBlank(message = "Paper title is required")
    private String title;

    private String description;
    private Integer year;

    /** Replace the questions of a paper that already has this title, instead of refusing */
    private boolean replaceExisting;

    @NotEmpty(message = "The paper must contain at least one question")
    private List<Map<String, Object>> questions;
}
