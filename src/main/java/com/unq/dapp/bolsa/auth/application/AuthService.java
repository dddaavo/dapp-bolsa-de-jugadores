package com.unq.dapp.bolsa.auth.application;

import com.unq.dapp.bolsa.auth.api.AuthResponse;
import com.unq.dapp.bolsa.auth.api.LoginRequest;
import com.unq.dapp.bolsa.auth.api.RegisterRequest;
import com.unq.dapp.bolsa.auth.domain.Role;
import com.unq.dapp.bolsa.auth.domain.User;
import com.unq.dapp.bolsa.auth.infrastructure.UserRepository;
import com.unq.dapp.bolsa.shared.error.DomainException;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       AuthenticationManager authenticationManager) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.authenticationManager = authenticationManager;
    }

    public AuthResponse register(RegisterRequest req) {
        if (userRepository.existsByEmail(req.email())) {
            throw new DomainException("EMAIL_TAKEN", "El email ya está registrado: " + req.email(), HttpStatus.CONFLICT);
        }
        User user = new User();
        user.setEmail(req.email());
        user.setPasswordHash(passwordEncoder.encode(req.password()));
        user.setRole(Role.USER);
        userRepository.save(user);
        return buildResponse(user);
    }

    public AuthResponse login(LoginRequest req) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(req.email(), req.password())
        );
        User user = userRepository.findByEmail(req.email())
                .orElseThrow(() -> new DomainException("USER_NOT_FOUND",
                        "Usuario no encontrado", HttpStatus.UNAUTHORIZED));
        return buildResponse(user);
    }

    private AuthResponse buildResponse(User user) {
        String token = jwtService.generateToken(user);
        return new AuthResponse(user.getId(), token, jwtService.getAccessTtlMinutes() * 60L);
    }
}
