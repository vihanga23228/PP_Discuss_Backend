package com.local.pp_backen.controller;

import com.local.pp_backen.dto.auth.AuthResponse;
import com.local.pp_backen.dto.auth.GoogleLoginRequest;
import com.local.pp_backen.dto.auth.LoginRequest;
import com.local.pp_backen.dto.auth.RegisterRequest;
import com.local.pp_backen.security.GoogleTokenVerifier;
import com.local.pp_backen.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final GoogleTokenVerifier googleTokenVerifier;

    public AuthController(AuthService authService, GoogleTokenVerifier googleTokenVerifier) {
        this.authService = authService;
        this.googleTokenVerifier = googleTokenVerifier;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/google")
    public ResponseEntity<AuthResponse> google(@Valid @RequestBody GoogleLoginRequest request) {
        return ResponseEntity.ok(authService.loginWithGoogle(request));
    }

    /** Lets the frontend show or hide the Google button without hard-coding server config. */
    @GetMapping("/providers")
    public ResponseEntity<Map<String, Object>> providers() {
        return ResponseEntity.ok(Map.of("google", googleTokenVerifier.isConfigured()));
    }

    /** Returns the signed-in user; used by the frontend to validate a stored token on boot. */
    @GetMapping("/me")
    public ResponseEntity<AuthResponse> me(Authentication authentication) {
        return ResponseEntity.ok(authService.currentUser(authentication.getName()));
    }
}
