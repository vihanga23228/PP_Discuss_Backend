package com.local.pp_backen.dto.subject;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class SubjectResponse {
    private Long id;
    private String name;
    private String description;
    private LocalDateTime createdAt;
    private int examCount;
    private int paperCount;
    private int questionCount;
}
