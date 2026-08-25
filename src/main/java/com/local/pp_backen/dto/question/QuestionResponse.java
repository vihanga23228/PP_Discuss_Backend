package com.local.pp_backen.dto.question;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class QuestionResponse {
    private Long id;
    private String type;
    private String stem;
    private String stemSi;
    private String note;
    private String noteSi;
    private String imageUrl;
    private String explanation;
    private String explanationSi;
    private Long paperId;
    private String paperTitle;
    private Long subjectId;
    private String subjectName;
    private List<OptionResponse> options;
}
