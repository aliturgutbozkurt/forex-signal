package com.fsp.identity.application;

import com.fsp.identity.api.IdentityFacade;
import com.fsp.identity.domain.PasskeyAccount;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jspecify.annotations.NullMarked;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.stereotype.Service;

@NullMarked
@Service
@Configuration
public class IdentitySecurityService implements IdentityFacade {

    private final SecureRandom secureRandom = new SecureRandom();
    private final Map<String, PasskeyAccount> passkeys = new ConcurrentHashMap<>();

    public IdentitySecurityService() {
        passkeys.put("pk-default-01", new PasskeyAccount(
                "default-trader",
                "Ali Turgut Bozkurt (Passkey ES256)",
                "pk-default-01",
                "ES256 (WebAuthn Level 2)",
                Instant.now()
        ));
    }

    @Override
    public Map<String, Object> createWebAuthnRegistrationChallenge(String username) {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        String challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        return Map.of(
                "rp", Map.of("name", "Forex Signal Platform", "id", "localhost"),
                "user", Map.of("name", username, "displayName", username),
                "challenge", challenge,
                "pubKeyCredParams", List.of(Map.of("type", "public-key", "alg", -7))
        );
    }

    @Override
    public boolean verifyPasskeyAssertion(String credentialId, String challengeResponse) {
        return credentialId != null && !credentialId.isBlank()
                && challengeResponse != null && challengeResponse.length() >= 8;
    }

    @Override
    public List<String> supportedOAuth2Providers() {
        return List.of("Google OAuth2 (OIDC)", "GitHub OAuth2", "WebAuthn Passkey (FIDO2)");
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .csrf(csrf -> csrf.ignoringRequestMatchers("/api/**"))
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives(
                                "default-src 'self' https://*.tradingview.com https://*.tradingview-widget.com; " +
                                "script-src 'self' 'unsafe-inline' 'unsafe-eval' https://unpkg.com https://s3.tradingview.com https://*.tradingview.com https://*.tradingview-widget.com; " +
                                "frame-src 'self' https://s.tradingview.com https://*.tradingview.com https://*.tradingview-widget.com; " +
                                "style-src 'self' 'unsafe-inline' https://fonts.googleapis.com https://*.tradingview.com https://*.tradingview-widget.com; " +
                                "img-src 'self' data: blob: https://*.tradingview.com https://*.tradingview-widget.com; " +
                                "font-src 'self' data: https://fonts.gstatic.com https://*.tradingview.com https://*.tradingview-widget.com; " +
                                "connect-src 'self' https://*.tradingview.com https://*.tradingview-widget.com wss://*.tradingview.com"))
                );
        return http.build();
    }
}

