package com.local.pp_backen.dto.subject;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SubjectRequest {

    @NotBlank(message = "Subject name is required")
    @Size(max = 120, message = "Subject name must be 120 characters or fewer")
    private String name;

    private String description;
}
