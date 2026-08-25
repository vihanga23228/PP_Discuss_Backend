package com.local.pp_backen.dto.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class GoogleLoginRequest {

    /** The credential (JWT ID token) returned by Google Identity Services in the browser */
    @NotBlank(message = "Google ID token is required")
    private String idToken;
}
