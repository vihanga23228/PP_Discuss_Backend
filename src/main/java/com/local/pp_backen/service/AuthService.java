package com.local.pp_backen.service;

import com.local.pp_backen.dto.auth.AuthResponse;
import com.local.pp_backen.dto.auth.GoogleLoginRequest;
import com.local.pp_backen.dto.auth.LoginRequest;
import com.local.pp_backen.dto.auth.RegisterRequest;
import com.local.pp_backen.entity.User;
import com.local.pp_backen.exception.ResourceNotFoundException;
import com.local.pp_backen.repository.UserRepository;
import com.local.pp_backen.security.GoogleTokenVerifier;
import com.local.pp_backen.security.JwtTokenProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@Transactional
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final GoogleTokenVerifier googleTokenVerifier;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtTokenProvider jwtTokenProvider,
                       GoogleTokenVerifier googleTokenVerifier) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.googleTokenVerifier = googleTokenVerifier;
    }

    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new IllegalArgumentException("Username is already taken");
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email is already in use");
        }

        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .role("USER")
                .provider("LOCAL")
                .displayName(request.getUsername())
                .build();

        user.setLastLoginAt(LocalDateTime.now());
        user = userRepository.save(user);
        return AuthResponse.from(user, token(user));
    }

    public AuthResponse login(LoginRequest request) {
        String identifier = request.getUsername().trim();

        User user = userRepository.findByUsername(identifier)
                .or(() -> userRepository.findByEmail(identifier.toLowerCase()))
                .orElseThrow(() -> new IllegalArgumentException("Invalid username or password"));

        if (!StringUtils.hasText(user.getPassword())) {
            throw new IllegalArgumentException(
                    "This account was created with Google sign-in. Use the \"Continue with Google\" button.");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new IllegalArgumentException("Invalid username or password");
        }

        requireEnabled(user);
        return AuthResponse.from(stampLogin(user), token(user));
    }

    /**
     * Signs a user in from a Google ID token, creating the account on first use.
     * An existing local account with the same email is linked rather than duplicated.
     */
    public AuthResponse loginWithGoogle(GoogleLoginRequest request) {
        GoogleTokenVerifier.GoogleProfile profile = googleTokenVerifier.verify(request.getIdToken());
        String email = profile.email().toLowerCase();

        User user = userRepository.findByGoogleId(profile.subject())
                .or(() -> userRepository.findByEmail(email))
                .orElseGet(() -> User.builder()
                        .username(generateUsername(email))
                        .email(email)
                        .role("USER")
                        .provider("GOOGLE")
                        .build());

        // Link / refresh the Google details on every sign-in
        user.setGoogleId(profile.subject());
        if (StringUtils.hasText(profile.name())) {
            user.setDisplayName(profile.name());
        } else if (!StringUtils.hasText(user.getDisplayName())) {
            user.setDisplayName(user.getUsername());
        }
        if (StringUtils.hasText(profile.pictureUrl())) {
            user.setAvatarUrl(profile.pictureUrl());
        }

        requireEnabled(user);
        user.setLastLoginAt(LocalDateTime.now());
        user = userRepository.save(user);
        return AuthResponse.from(user, token(user));
    }

    @Transactional(readOnly = true)
    public AuthResponse currentUser(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User", "username", username));
        return AuthResponse.from(user, null);
    }

    private void requireEnabled(User user) {
        if (Boolean.FALSE.equals(user.getEnabled())) {
            throw new IllegalArgumentException(
                    "This account has been suspended. Contact an administrator.");
        }
    }

    private User stampLogin(User user) {
        user.setLastLoginAt(LocalDateTime.now());
        return userRepository.save(user);
    }

    private String token(User user) {
        return jwtTokenProvider.generateToken(user.getUsername(), user.getRole());
    }

    /** Derives a unique username from the email local-part, e.g. jane.doe@x.com -> jane.doe, jane.doe2 ... */
    private String generateUsername(String email) {
        String base = email.substring(0, email.indexOf('@')).replaceAll("[^a-zA-Z0-9._-]", "");
        if (base.length() < 3) {
            base = "user" + base;
        }
        if (base.length() > 45) {
            base = base.substring(0, 45);
        }

        String candidate = base;
        int suffix = 1;
        while (Boolean.TRUE.equals(userRepository.existsByUsername(candidate))) {
            candidate = base + (++suffix);
        }
        return candidate;
    }

    /** Used by the seeder to look up an optional existing account without throwing */
    @Transactional(readOnly = true)
    public Optional<User> findByUsername(String username) {
        return userRepository.findByUsername(username);
    }
}
