package com.unq.dapp.bolsa.auth.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private static final String TEST_SECRET =
            "dGVzdC1zZWNyZXQta2V5LXBsZWFzZS1jaGFuZ2UtaW4tcHJvZHVjdGlvbi10aGlzLWlzLXRlc3Qtb25seQ==";

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(TEST_SECRET, 15);
    }

    @Test
    void generateToken_returnsNonBlankToken() {
        UserDetails userDetails = buildTestUser("test@example.com");

        String token = jwtService.generateToken(userDetails);

        assertThat(token).isNotBlank();
    }

    @Test
    void isTokenValid_returnsTrueForValidToken() {
        UserDetails userDetails = buildTestUser("test@example.com");
        String token = jwtService.generateToken(userDetails);

        boolean valid = jwtService.isTokenValid(token, userDetails);

        assertThat(valid).isTrue();
    }

    @Test
    void extractUsername_returnsCorrectEmail() {
        String email = "user@example.com";
        UserDetails userDetails = buildTestUser(email);
        String token = jwtService.generateToken(userDetails);

        String extracted = jwtService.extractUsername(token);

        assertThat(extracted).isEqualTo(email);
    }

    @Test
    void isTokenValid_returnsFalseForWrongUser() {
        UserDetails owner = buildTestUser("owner@example.com");
        UserDetails other = buildTestUser("other@example.com");
        String token = jwtService.generateToken(owner);

        boolean valid = jwtService.isTokenValid(token, other);

        assertThat(valid).isFalse();
    }

    private UserDetails buildTestUser(String email) {
        return User.withUsername(email)
                .password("irrelevant")
                .authorities(Collections.emptyList())
                .build();
    }
}
