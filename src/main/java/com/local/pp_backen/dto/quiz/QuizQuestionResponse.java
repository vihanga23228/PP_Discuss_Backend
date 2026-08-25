package com.local.pp_backen.dto.quiz;

import com.local.pp_backen.dto.question.OptionResponse;
import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class QuizQuestionResponse {
    private Long questionId;
    private String type;
    private String stem;
    private String stemSi;
    private String note;
    private String noteSi;
    private String imageUrl;
    private List<OptionResponse> options;
}
