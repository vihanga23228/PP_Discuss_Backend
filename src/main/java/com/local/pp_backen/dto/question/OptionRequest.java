package com.local.pp_backen.dto.question;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class OptionRequest {

    /**
     * Identifies an existing option so an update can edit it in place. Recorded
     * answers reference option ids, so replacing the rows on every edit would
     * break the attempts students have already made. Null means a new option.
     */
    private Long id;

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
