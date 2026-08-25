package com.local.pp_backen.dto.auth;

import com.local.pp_backen.entity.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {
    private String token;
    @Builder.Default
    private String type = "Bearer";
    private Long id;
    private String username;
    private String email;
    private String role;
    private String provider;
    private String displayName;
    private String avatarUrl;

    public static AuthResponse from(User user, String token) {
        return AuthResponse.builder()
                .token(token)
                .type("Bearer")
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .role(user.getRole())
                .provider(user.getProvider())
                .displayName(user.getDisplayName() != null ? user.getDisplayName() : user.getUsername())
                .avatarUrl(user.getAvatarUrl())
                .build();
    }
}
