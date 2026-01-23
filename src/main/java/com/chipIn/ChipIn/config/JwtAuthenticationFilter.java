package com.chipIn.ChipIn.config;

import com.chipIn.ChipIn.entities.User;
import com.chipIn.ChipIn.repository.UserRepository;
import com.chipIn.ChipIn.services.JwtService;
import com.chipIn.ChipIn.services.UserService;
import com.chipIn.ChipIn.util.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        // 1. Extract the Authorization Header
        final String authHeader = request.getHeader("Authorization");
        final String jwt;
        final String userEmail;

        // If no header or doesn't start with "Bearer ", pass to the next filter (likely public endpoints)
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        // 2. Extract Token and Email
        jwt = authHeader.substring(7); // "Bearer " is 7 characters
        userEmail = jwtService.extractUsername(jwt);

        // 3. Authenticate if not already authenticated
        if (userEmail != null && SecurityContextHolder.getContext().getAuthentication() == null) {

            // Fetch user from DB to get the latest tokenVersion
            User user = userRepository.findByEmail(userEmail).orElse(null);

            // 4. Validate Token (This checks expiration AND tokenVersion)
            if (user != null && jwtService.isTokenValid(jwt, user)) {

                // 5. Create Authentication Token
                UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                        user,
                        null,
                        user.getAuthorities()
                );
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                // 6. Update Security Context
                SecurityContextHolder.getContext().setAuthentication(authToken);
            }
        }

        // 7. Continue the filter chain
        filterChain.doFilter(request, response);
    }
}
