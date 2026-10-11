package com.knowledgevault.web;

import com.knowledgevault.model.User;
import com.knowledgevault.repository.UserRepository;
import com.knowledgevault.security.JwtService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.Locale;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthController(UserRepository users, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody Credentials credentials) {
        String email = credentials.email().trim().toLowerCase(Locale.ROOT);
        if (users.existsByEmail(email)) throw new ResponseStatusException(HttpStatus.CONFLICT, "An account with this email already exists");
        User user = users.save(new User(email, passwordEncoder.encode(credentials.password())));
        return new AuthResponse(jwtService.issue(user), user.getEmail());
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody Credentials credentials) {
        String email = credentials.email().trim().toLowerCase(Locale.ROOT);
        User user = users.findByEmail(email)
                .filter(found -> passwordEncoder.matches(credentials.password(), found.getPasswordHash()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Email or password is incorrect"));
        return new AuthResponse(jwtService.issue(user), user.getEmail());
    }

    public record Credentials(@NotBlank @Email String email, @NotBlank @Size(min = 8, max = 72) String password) {}
    public record AuthResponse(String token, String email) {}
}
