package com.local.pp_backen.dto.admin;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ImportPaperResponse {
    private Long subjectId;
    private String subjectName;
    private Long examId;
    private String examTitle;
    private Long paperId;
    private String paperTitle;
    private int questionsImported;
    private int optionsImported;
    private int totalMarks;
    private boolean replacedExisting;
    /** Non-fatal notes, e.g. a question that was skipped */
    private List<String> warnings;
}
