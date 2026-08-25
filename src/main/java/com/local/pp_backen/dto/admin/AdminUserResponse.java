package com.local.pp_backen.dto.admin;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class AdminUserResponse {
    private Long id;
    private String username;
    private String email;
    private String displayName;
    private String avatarUrl;
    private String role;
    private String provider;
    private boolean enabled;
    private LocalDateTime createdAt;
    private LocalDateTime lastLoginAt;
    /** Completed quiz attempts */
    private long attemptCount;
    private Double bestScorePct;
}
