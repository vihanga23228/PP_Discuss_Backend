package com.local.pp_backen.dto.admin;

import lombok.Data;

/** Partial update from the admin user table. Any null field is left unchanged. */
@Data
public class UpdateUserRequest {
    /** USER or ADMIN */
    private String role;
    /** false suspends the account */
    private Boolean enabled;
}
