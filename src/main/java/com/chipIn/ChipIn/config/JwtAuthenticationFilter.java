package com.chipIn.ChipIn.config;

import com.chipIn.ChipIn.entities.User;
import com.chipIn.ChipIn.repository.UserRepository;
import com.chipIn.ChipIn.services.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        log.debug("Entering JwtAuthenticationFilter for request URI: {}", request.getRequestURI());

        // 1. Extract the Authorization Header
        final String authHeader = request.getHeader("Authorization");
        final String jwt;
        String userEmail = null; // Initialize to null

        // If no header or doesn't start with "Bearer ", pass to the next filter (likely public endpoints)
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.debug("No Authorization header or not a Bearer token. Passing to next filter.");
            filterChain.doFilter(request, response);
            return;
        }

        try {
            // 2. Extract Token and Email
            jwt = authHeader.substring(7).trim(); // "Bearer " is 7 characters
            log.debug("Extracted JWT: {}", jwt.length() > 50 ? jwt.substring(0, 50) + "..." : jwt); // Log first 50 chars

            // If the token is literally the string "undefined" or "null", ignore it silently
            if (jwt.equals("undefined") || jwt.equals("null") || jwt.isEmpty()) {
                log.warn("Received 'undefined', 'null', or empty JWT. Passing to next filter.");
                filterChain.doFilter(request, response);
                return;
            }
            
            // Only try to parse if the token actually looks like a JWT (contains 2 periods)
            if (jwt.split("\\.").length == 3) {
                userEmail = jwtService.extractUsername(jwt);
                log.debug("Extracted user email from JWT: {}", userEmail);

                // 3. Authenticate if not already authenticated
                if (userEmail != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                    log.debug("User email found and no existing authentication. Attempting to authenticate user: {}", userEmail);

                    // Fetch user from DB to get the latest tokenVersion
                    User user = userRepository.findByEmail(userEmail).orElse(null);

                    if (user == null) {
                        log.warn("User not found in DB for email: {}", userEmail);
                    } else {
                        log.debug("User found in DB: {}", user.getEmail());
                        // 4. Validate Token (This checks expiration AND tokenVersion)
                        if (jwtService.isTokenValid(jwt, user)) {
                            log.debug("JWT token is valid for user: {}", userEmail);

                            // 5. Create Authentication Token
                            UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                                    user,
                                    null,
                                    user.getAuthorities()
                            );
                            authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                            // 6. Update Security Context
                            SecurityContextHolder.getContext().setAuthentication(authToken);
                            log.info("Successfully authenticated user: {}", userEmail);
                        } else {
                            log.warn("JWT token is NOT valid for user: {}", userEmail);
                        }
                    }
                } else if (userEmail != null) {
                    log.debug("User email found but already authenticated or userEmail is null. Current auth: {}", SecurityContextHolder.getContext().getAuthentication());
                }
            } else {
                log.warn("JWT token format invalid (does not contain 2 periods): {}", jwt);
            }
        } catch (Exception e) {
            log.error("Error during JWT authentication for request URI: {}. Error: {}", request.getRequestURI(), e.getMessage(), e);
            // Do nothing, let the security filter chain handle the unauthenticated request
        }

        // 7. Continue the filter chain
        filterChain.doFilter(request, response);
        log.debug("Exiting JwtAuthenticationFilter for request URI: {}", request.getRequestURI());
    }
}
