package com.local.pp_backen.dto.exam;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class ExamResponse {
    private Long id;
    private String title;
    private String description;
    private Long subjectId;
    private String subjectName;
    private LocalDateTime createdAt;
    private int paperCount;
    private int questionCount;
}
