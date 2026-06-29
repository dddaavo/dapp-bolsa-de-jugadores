package com.unq.dapp.bolsa.auth.application;

import com.unq.dapp.bolsa.auth.api.AuthResponse;
import com.unq.dapp.bolsa.auth.api.LoginRequest;
import com.unq.dapp.bolsa.auth.api.RegisterRequest;
import com.unq.dapp.bolsa.auth.domain.Role;
import com.unq.dapp.bolsa.auth.domain.User;
import com.unq.dapp.bolsa.auth.infrastructure.UserRepository;
import com.unq.dapp.bolsa.shared.error.DomainException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private AuthenticationManager authenticationManager;

    private AuthService authService;
    private PasswordEncoder passwordEncoder;
    private JwtService jwtService;
    private User usuarioExistente;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder();
        jwtService = new JwtService("dGVzdC1zZWNyZXQta2V5LWZvci11bml0LXRlc3Rpbmctb25seQ==", 15);
        authService = new AuthService(userRepository, passwordEncoder, jwtService, authenticationManager);

        usuarioExistente = new User();
        usuarioExistente.setEmail("existente@test.com");
        usuarioExistente.setPasswordHash(passwordEncoder.encode("password123"));
        usuarioExistente.setRole(Role.USER);
    }

    @Test
    void deberiaLanzarExcepcionCuandoEmailYaExiste() {
        when(userRepository.existsByEmail("existente@test.com")).thenReturn(true);

        var request = new RegisterRequest("existente@test.com", "password123");
        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("existente@test.com");
    }

    @Test
    void deberiaRegistrarUsuarioNuevoYDevolverToken() {
        when(userRepository.existsByEmail("nuevo@test.com")).thenReturn(false);
        when(userRepository.save(any())).thenReturn(usuarioExistente);

        AuthResponse response = authService.register(new RegisterRequest("nuevo@test.com", "password123"));

        assertThat(response.accessToken()).isNotBlank();
        assertThat(response.expiresIn()).isEqualTo(900L);
    }

    @Test
    void deberiaLanzarExcepcionCuandoCredencialesInvalidas() {
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new BadCredentialsException("credenciales inválidas"));

        var loginRequest = new LoginRequest("user@test.com", "wrongpass");
        assertThatThrownBy(() -> authService.login(loginRequest))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void deberiaRetornarTokenEnLoginExitoso() {
        when(authenticationManager.authenticate(any())).thenReturn(null);
        when(userRepository.findByEmail("existente@test.com")).thenReturn(Optional.of(usuarioExistente));

        AuthResponse response = authService.login(new LoginRequest("existente@test.com", "password123"));

        assertThat(response.accessToken()).isNotBlank();
        assertThat(response.expiresIn()).isEqualTo(900L);
    }
}
