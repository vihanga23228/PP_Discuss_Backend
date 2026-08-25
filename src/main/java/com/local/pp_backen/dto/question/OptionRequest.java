package com.local.pp_backen.dto.question;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class OptionRequest {

    @NotBlank(message = "Option label is required")
    private String label;

    @NotBlank(message = "Option text is required")
    private String text;
    private String textSi;
    private String imageUrl;

    @NotNull(message = "Correct flag is required")
    private Boolean correct;

    private String explanationEn;
    private String explanationSi;
}
