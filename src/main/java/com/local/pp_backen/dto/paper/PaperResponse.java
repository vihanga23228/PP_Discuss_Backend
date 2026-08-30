package com.local.pp_backen.dto.paper;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class PaperResponse {
    private Long id;
    private String title;
    private String description;
    private Integer year;
    private Integer durationMinutes;
    private Long examId;
    private String examTitle;
    private Long subjectId;
    private String subjectName;
    private LocalDateTime createdAt;
    private int questionCount;
}
