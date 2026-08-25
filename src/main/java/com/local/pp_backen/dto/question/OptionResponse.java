package com.local.pp_backen.dto.question;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class OptionResponse {
    private Long id;
    private String label;
    private String text;
    private String textSi;
    private String imageUrl;
    private Boolean correct;
    private String explanationEn;
    private String explanationSi;
}
