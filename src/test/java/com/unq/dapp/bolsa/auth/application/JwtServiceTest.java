package com.unq.dapp.bolsa.auth.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String SECRET = "dGVzdC1zZWNyZXQta2V5LWZvci11bml0LXRlc3Rpbmctb25seQ==";

    private JwtService jwtService;
    private UserDetails userDetails;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(SECRET, 15);
        userDetails = new User("test@example.com", "password", List.of());
    }

    @Test
    void deberiaGenerarTokenNoNulo() {
        String token = jwtService.generateToken(userDetails);
        assertThat(token).isNotBlank();
    }

    @Test
    void deberiaExtraerUsernameDelToken() {
        String token = jwtService.generateToken(userDetails);
        assertThat(jwtService.extractUsername(token)).isEqualTo("test@example.com");
    }

    @Test
    void deberiaValidarTokenCorrecto() {
        String token = jwtService.generateToken(userDetails);
        assertThat(jwtService.isTokenValid(token, userDetails)).isTrue();
    }

    @Test
    void deberiaRechazarTokenDeOtroUsuario() {
        String token = jwtService.generateToken(userDetails);
        UserDetails otroUsuario = new User("otro@example.com", "password", List.of());
        assertThat(jwtService.isTokenValid(token, otroUsuario)).isFalse();
    }

    @Test
    void deberiaRechazarTokenMalformado() {
        assertThatThrownBy(() -> jwtService.extractUsername("token.invalido.abc"))
                .isInstanceOf(Exception.class);
    }

    @Test
    void deberiaRechazarTokenExpirado() {
        JwtService servicioConTtlNegativo = new JwtService(SECRET, -1);
        String tokenExpirado = servicioConTtlNegativo.generateToken(userDetails);
        assertThatThrownBy(() -> servicioConTtlNegativo.isTokenValid(tokenExpirado, userDetails))
                .isInstanceOf(Exception.class);
    }
}
