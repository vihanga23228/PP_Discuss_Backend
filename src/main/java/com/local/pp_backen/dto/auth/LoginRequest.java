package com.local.pp_backen.dto.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginRequest {

    /** Accepts either the username or the email address of the account */
    @NotBlank(message = "Username or email is required")
    private String username;

    @NotBlank(message = "Password is required")
    private String password;
}
