package com.quickdeliver.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Issues and verifies HS256 tokens carrying {"id": "<userId>"} - same payload the Node app used. */
@Service
public class JwtService {

    private static final Pattern DURATION = Pattern.compile("^\\s*(\\d+)\\s*([smhdw]?)\\s*$", Pattern.CASE_INSENSITIVE);

    private final SecretKey key;
    private final long ttlMillis;

    public JwtService(@Value("${app.jwt.secret:}") String secret,
                      @Value("${app.jwt.expire:7d}") String expire) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("JWT_SECRET is not set. Put it in .env or the environment.");
        }
        // jjwt refuses HMAC keys shorter than 256 bits. Hashing the secret gives a 256-bit key for any
        // secret length, so short secrets (like a 24-char one) keep working.
        this.key = Keys.hmacShaKeyFor(sha256(secret));
        this.ttlMillis = parseDuration(expire);
    }

    public String sign(String userId) {
        Date now = new Date();
        return Jwts.builder()
                .claim("id", userId)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + ttlMillis))
                .signWith(key)
                .compact();
    }

    /** @throws io.jsonwebtoken.JwtException if the token is malformed, tampered with or expired */
    public String parseUserId(String token) {
        Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        return claims.get("id", String.class);
    }

    /** "7d", "12h", "30m", "45s", "2w"; a bare number means seconds. */
    static long parseDuration(String text) {
        Matcher m = DURATION.matcher(text == null ? "" : text);
        if (!m.matches()) {
            throw new IllegalStateException("Invalid JWT_EXPIRE value: '" + text + "' (use e.g. 7d, 12h, 30m)");
        }
        long n = Long.parseLong(m.group(1));
        String unit = m.group(2).toLowerCase();
        long seconds;
        switch (unit) {
            case "m": seconds = n * 60L; break;
            case "h": seconds = n * 3600L; break;
            case "d": seconds = n * 86400L; break;
            case "w": seconds = n * 7L * 86400L; break;
            default:  seconds = n; break;
        }
        return seconds * 1000L;
    }

    private static byte[] sha256(String s) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
