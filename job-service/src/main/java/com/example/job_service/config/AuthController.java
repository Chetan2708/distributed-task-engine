
package com.example.job_service.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@CrossOrigin(origins = "${app.frontend.url:http://localhost:5173}")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final InMemoryUserDetailsManager userDetailsManager;
    private final PasswordEncoder passwordEncoder;

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody AuthRequest request) {

        UsernamePasswordAuthenticationToken authRequest =
                new UsernamePasswordAuthenticationToken(
                        request.getUsername().trim(),
                        request.getPassword()
                );

        Authentication authentication =
                authenticationManager
                        .authenticate(authRequest);

        String role = authentication.getAuthorities()
                .stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.joining(","));

        String token = jwtUtil.generateToken(
                authentication.getName(), role
        );

        return ResponseEntity.ok(new AuthResponse(token));
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(
            @Valid @RequestBody AuthRequest request) {

        String username = request.getUsername().trim();

        if (username.length() < 3 || username.length() > 50) {
            return ResponseEntity.badRequest().build();
        }

        if (userDetailsManager.userExists(username)) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }

        userDetailsManager.createUser(
                User.withUsername(username)
                        .password(passwordEncoder.encode(
                                request.getPassword()))
                        .roles("USER")
                        .build()
        );

        String token = jwtUtil.generateToken(
                username, "ROLE_USER"
        );

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new AuthResponse(token));
    }

    @Data
    public static class AuthRequest {

        @NotBlank(message = "Username is required")
        @Size(min = 3, max = 50,
              message = "Username must be between 3 and 50 characters")
        private String username;

        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 72,
              message = "Password must be between 8 and 72 characters")
        private String password;
    }

    @Data
    public static class AuthResponse {
        private final String token;
    }
}
