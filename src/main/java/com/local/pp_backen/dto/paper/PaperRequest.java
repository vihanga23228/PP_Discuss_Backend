package com.local.pp_backen.dto.paper;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class PaperRequest {

    @NotBlank(message = "Paper title is required")
    private String title;

    private String description;

    private Integer year;

    /** Sitting length in minutes, set by an admin. Null/absent means untimed. */
    @Positive(message = "Duration must be a positive number of minutes")
    private Integer durationMinutes;
}
