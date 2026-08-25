package com.local.pp_backen.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * Verifies Google ID tokens received from the browser's Google Sign-In button.
 *
 * <p>Uses Google's public tokeninfo endpoint, which validates the token signature,
 * issuer and expiry server-side. We additionally check that the token was minted for
 * <em>our</em> client id, otherwise any Google token from any app would be accepted.
 */
@Component
public class GoogleTokenVerifier {

    private static final Logger log = LoggerFactory.getLogger(GoogleTokenVerifier.class);
    private static final String TOKEN_INFO_URL = "https://oauth2.googleapis.com/tokeninfo";

    private final String clientId;
    private final RestClient restClient;

    public GoogleTokenVerifier(@Value("${app.google.client-id:}") String clientId) {
        this.clientId = clientId;
        this.restClient = RestClient.create();
    }

    public boolean isConfigured() {
        return StringUtils.hasText(clientId);
    }

    /**
     * @return the verified Google profile
     * @throws IllegalArgumentException if the token is missing, invalid, or issued to another app
     */
    @SuppressWarnings("unchecked")
    public GoogleProfile verify(String idToken) {
        if (!isConfigured()) {
            throw new IllegalArgumentException(
                    "Google sign-in is not configured on the server. Set app.google.client-id.");
        }
        if (!StringUtils.hasText(idToken)) {
            throw new IllegalArgumentException("Google ID token is required");
        }

        Map<String, Object> payload;
        try {
            payload = restClient.get()
                    .uri(TOKEN_INFO_URL + "?id_token={token}", idToken)
                    .retrieve()
                    .body(Map.class);
        } catch (Exception e) {
            log.warn("Google token verification call failed: {}", e.getMessage());
            throw new IllegalArgumentException("Could not verify Google sign-in token");
        }

        if (payload == null) {
            throw new IllegalArgumentException("Could not verify Google sign-in token");
        }

        String audience = asString(payload.get("aud"));
        if (!clientId.equals(audience)) {
            log.warn("Rejected Google token issued for a different client id: {}", audience);
            throw new IllegalArgumentException("This Google token was not issued for this application");
        }

        String issuer = asString(payload.get("iss"));
        if (issuer == null || !(issuer.equals("accounts.google.com")
                || issuer.equals("https://accounts.google.com"))) {
            throw new IllegalArgumentException("Unexpected Google token issuer");
        }

        String email = asString(payload.get("email"));
        if (!StringUtils.hasText(email)) {
            throw new IllegalArgumentException("Google account did not return an email address");
        }

        if (!"true".equalsIgnoreCase(asString(payload.get("email_verified")))) {
            throw new IllegalArgumentException("This Google account's email address is not verified");
        }

        return new GoogleProfile(
                asString(payload.get("sub")),
                email,
                asString(payload.get("name")),
                asString(payload.get("picture")));
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    public record GoogleProfile(String subject, String email, String name, String pictureUrl) {
    }
}
