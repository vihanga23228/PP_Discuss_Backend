package com.local.pp_backen.dto.paper;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PaperRequest {

    @NotBlank(message = "Paper title is required")
    private String title;

    private String description;

    private Integer year;
}
