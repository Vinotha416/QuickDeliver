package com.quickdeliver.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quickdeliver.common.ApiResponse;
import com.quickdeliver.model.User;
import com.quickdeliver.repository.UserRepository;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.util.Arrays;

/**
 * Port of middleware/auth.js. For handlers annotated with {@link Auth} it verifies the Bearer token,
 * loads the user, checks the role and exposes the user as request attribute {@value #CURRENT_USER}
 * (read it in controllers with {@code @RequestAttribute("currentUser") User user}).
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    public static final String CURRENT_USER = "currentUser";

    private final JwtService jwt;
    private final UserRepository users;
    private final ObjectMapper mapper;

    public AuthInterceptor(JwtService jwt, UserRepository users, ObjectMapper mapper) {
        this.jwt = jwt;
        this.users = users;
        this.mapper = mapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler) throws IOException {
        if (!(handler instanceof HandlerMethod hm)) {
            return true;
        }
        Auth auth = hm.getMethodAnnotation(Auth.class);
        if (auth == null) {
            auth = hm.getBeanType().getAnnotation(Auth.class);
        }
        if (auth == null) {
            return true; // public endpoint
        }

        String token = null;
        String header = req.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer")) {
            String[] parts = header.split(" ");
            if (parts.length > 1) token = parts[1];
        }
        if (token == null || token.isEmpty()) {
            return deny(res, 401, "Please login first");
        }

        User user;
        try {
            String id = jwt.parseUserId(token);
            user = (id == null) ? null : users.findById(id).orElse(null);
        } catch (JwtException | IllegalArgumentException e) {
            return deny(res, 401, "Invalid or expired token");
        }
        if (user == null || !user.activeAccount()) {
            return deny(res, 401, "Account not found");
        }

        String[] roles = auth.roles();
        if (roles.length > 0 && !Arrays.asList(roles).contains(user.getRole())) {
            return deny(res, 403, "Access denied for your role");
        }

        req.setAttribute(CURRENT_USER, user);
        return true;
    }

    private boolean deny(HttpServletResponse res, int status, String message) throws IOException {
        res.setStatus(status);
        res.setContentType("application/json");
        res.setCharacterEncoding("UTF-8");
        mapper.writeValue(res.getWriter(), ApiResponse.fail(message));
        return false;
    }
}
