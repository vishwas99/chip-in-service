package com.chipIn.ChipIn.services;

import com.chipIn.ChipIn.entities.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@Service
public class JwtService {
    // 🚨 IMPORTANT: Replace this with a long random string (at least 256 bits/32 bytes) for production!
    private static final String SECRET_KEY = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";

    // 1. Generate Token
    public String generateToken(User user) {
        Map<String, Object> claims = new HashMap<>();
        // 🔑 Embed tokenVersion into claims
        claims.put("tokenVersion", user.getTokenVersion());
        claims.put("userId", user.getUserid());

        return createToken(claims, user.getEmail());
    }

    private String createToken(Map<String, Object> extraClaims, String subject) {
        return Jwts.builder()
                .claims(extraClaims) // 🟢 Modern JJWT 0.12+ method
                .subject(subject)
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + 1000 * 60 * 60 * 24 * 7)) // 7 Days Validity
                .signWith(getSignInKey()) // 🟢 Algorithm is automatically inferred from the key
                .compact();
    }

    // 2. Extract Data
    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public Integer extractTokenVersion(String token) {
        return extractClaim(token, claims -> claims.get("tokenVersion", Integer.class));
    }

    // 3. Validation Logic
    public boolean isTokenValid(String token, User user) {
        final String username = extractUsername(token);
        final Integer tokenVersion = extractTokenVersion(token);

        return (username.equals(user.getEmail())) &&
                !isTokenExpired(token) &&
                tokenVersion.equals(user.getTokenVersion());
    }

    // --- Helpers ---
    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()             // 🟢 Modern JJWT 0.12+ method
                .verifyWith(getSignInKey()) // 🟢 Replaces setSigningKey()
                .build()
                .parseSignedClaims(token)   // 🟢 Replaces parseClaimsJws()
                .getPayload();              // 🟢 Replaces getBody()
    }

    private boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    private Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    private SecretKey getSignInKey() {
        byte[] keyBytes = Decoders.BASE64.decode(SECRET_KEY);
        // 🟢 Strictly returns a SecretKey, required by modern JJWT
        return Keys.hmacShaKeyFor(keyBytes);
    }}
