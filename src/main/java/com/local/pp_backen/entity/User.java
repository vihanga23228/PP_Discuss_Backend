package com.local.pp_backen.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false, unique = true)
    private String email;

    /** BCrypt hash. Null for accounts created purely through Google sign-in. */
    @Column
    private String password;

    @Column(nullable = false)
    @Builder.Default
    private String role = "USER";

    /** How the account was created: LOCAL or GOOGLE */
    @Column(nullable = false, length = 20)
    @Builder.Default
    private String provider = "LOCAL";

    /** Google's stable subject id, set when the user signs in with Google */
    @Column(name = "google_id", unique = true)
    private String googleId;

    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    @Column(name = "display_name")
    private String displayName;

    /** Suspended accounts keep their data but cannot sign in or use their token. */
    @Column(nullable = false)
    @Builder.Default
    private Boolean enabled = true;

    /** Stamped on every successful sign-in; drives the "active members" figure. */
    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<QuizAttempt> quizAttempts = new ArrayList<>();
}
