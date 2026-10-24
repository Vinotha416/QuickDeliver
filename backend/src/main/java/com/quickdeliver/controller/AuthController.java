package com.quickdeliver.controller;

import com.quickdeliver.common.ApiException;
import com.quickdeliver.common.ApiResponse;
import com.quickdeliver.common.Constants;
import com.quickdeliver.common.Num;
import com.quickdeliver.model.User;
import com.quickdeliver.repository.UserRepository;
import com.quickdeliver.security.Auth;
import com.quickdeliver.security.AuthInterceptor;
import com.quickdeliver.security.JwtService;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Port of routes/auth.js */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    public record RegisterRequest(String name, String email, String password, String phone, String address, String role) { }

    public record LoginRequest(String email, String password) { }

    public record ProfileRequest(String name, String phone, String address) { }

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;

    public AuthController(UserRepository users, PasswordEncoder encoder, JwtService jwt) {
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
    }

    /** Register - customers and agents only (the admin account is created by the seeder). */
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse register(@RequestBody RegisterRequest body) {
        if (Num.blank(body.name()) || Num.blank(body.email()) || Num.blank(body.password())) {
            throw ApiException.badRequest("Name, email and password are required");
        }
        String email = body.email().trim().toLowerCase();
        if (users.findByEmail(email).isPresent()) {
            throw ApiException.badRequest("Email already registered");
        }
        if (body.password().length() < 6) {
            throw ApiException.badRequest("Password must be at least 6 characters");
        }

        User user = new User();
        user.setName(body.name().trim());
        user.setEmail(email);
        user.setPassword(encoder.encode(body.password()));
        if (body.phone() != null) user.setPhone(body.phone());
        if (body.address() != null) user.setAddress(body.address());
        user.setRole(Constants.AGENT.equals(body.role()) ? Constants.AGENT : Constants.CUSTOMER);
        user = users.save(user);

        return ApiResponse.ok()
                .with("token", jwt.sign(user.getId()))
                .with("user", ApiResponse.map()
                        .with("id", user.getId())
                        .with("name", user.getName())
                        .with("email", user.getEmail())
                        .with("role", user.getRole()));
    }

    @PostMapping("/login")
    public ApiResponse login(@RequestBody LoginRequest body) {
        User user = Num.blank(body.email()) ? null : users.findByEmail(body.email().trim().toLowerCase()).orElse(null);
        if (user == null || !user.activeAccount() || body.password() == null || user.getPassword() == null
                || !encoder.matches(body.password(), user.getPassword())) {
            throw ApiException.unauthorized("Invalid email or password");
        }
        return ApiResponse.ok()
                .with("token", jwt.sign(user.getId()))
                .with("user", ApiResponse.map()
                        .with("id", user.getId())
                        .with("name", user.getName())
                        .with("email", user.getEmail())
                        .with("role", user.getRole())
                        .with("phone", user.getPhone())
                        .with("address", user.getAddress()));
    }

    @GetMapping("/me")
    @Auth
    public ApiResponse me(@RequestAttribute(AuthInterceptor.CURRENT_USER) User user) {
        return ApiResponse.ok().with("user", user);
    }

    @PutMapping("/profile")
    @Auth
    public ApiResponse updateProfile(@RequestAttribute(AuthInterceptor.CURRENT_USER) User current,
                                     @RequestBody ProfileRequest body) {
        User user = users.findById(current.getId()).orElseThrow(() -> ApiException.unauthorized("Account not found"));
        if (body.name() != null) user.setName(body.name());
        if (body.phone() != null) user.setPhone(body.phone());
        if (body.address() != null) user.setAddress(body.address());
        return ApiResponse.ok().with("user", users.save(user));
    }
}
