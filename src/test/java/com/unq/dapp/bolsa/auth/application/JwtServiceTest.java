package com.unq.dapp.bolsa.auth.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private JwtService jwtService;
    private UserDetails userDetails;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService("dGVzdC1zZWNyZXQta2V5LWZvci11bml0LXRlc3Rpbmctb25seQ==", 15);
        userDetails = new User("test@example.com", "password", List.of());
    }

    @Test
    void generaTokenNoNulo() {
        String token = jwtService.generateToken(userDetails);
        assertThat(token).isNotBlank();
    }

    @Test
    void extraeUsernameCorrectamente() {
        String token = jwtService.generateToken(userDetails);
        assertThat(jwtService.extractUsername(token)).isEqualTo("test@example.com");
    }

    @Test
    void validaTokenCorrecto() {
        String token = jwtService.generateToken(userDetails);
        assertThat(jwtService.isTokenValid(token, userDetails)).isTrue();
    }

    @Test
    void rechazaTokenDeOtroUsuario() {
        String token = jwtService.generateToken(userDetails);
        UserDetails otroUsuario = new User("otro@example.com", "password", List.of());
        assertThat(jwtService.isTokenValid(token, otroUsuario)).isFalse();
    }

    @Test
    void rechazaTokenMalformado() {
        assertThatThrownBy(() -> jwtService.extractUsername("token.invalido.abc"))
                .isInstanceOf(Exception.class);
    }
}
