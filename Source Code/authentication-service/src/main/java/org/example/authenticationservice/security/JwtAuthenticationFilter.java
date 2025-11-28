package org.example.authenticationservice.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import lombok.RequiredArgsConstructor;
import org.example.authenticationservice.service.JwtService;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Filtre JWT exécuté à chaque requête.
 * - Récupère le header Authorization
 * - Extrait le token "Bearer ..."
 * - Valide le token avec JwtService
 * - Charge l'utilisateur avec UserDetailsService
 * - Remplit le SecurityContext si tout est OK
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        // 1) On ignore les préflight CORS (OPTIONS)
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        // 2) Récupérer le header Authorization
        final String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            // Pas de token → on laisse passer, la security chain décidera
            filterChain.doFilter(request, response);
            return;
        }

        // 3) Extraire le token
        final String jwt = authHeader.substring(7); // après "Bearer "
        String username = null;

        try {
            username = jwtService.extractUsername(jwt);
        } catch (Exception e) {
            // Token invalide/malformé/expiré → on ne set pas d'auth
            filterChain.doFilter(request, response);
            return;
        }

        // 4) Si on a bien un username et qu'il n'y a pas déjà une auth dans le contexte
        if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {

            UserDetails userDetails = userDetailsService.loadUserByUsername(username);

            // 5) Vérifier que le token est valide pour cet utilisateur
            if (jwtService.isTokenValid(jwt, userDetails)) {
                UsernamePasswordAuthenticationToken authToken =
                        new UsernamePasswordAuthenticationToken(
                                userDetails,
                                null,
                                userDetails.getAuthorities()
                        );

                // 6) Mettre l'authentification dans le SecurityContext
                SecurityContextHolder.getContext().setAuthentication(authToken);
            }
        }

        // 7) Continuer la chaîne de filtres
        filterChain.doFilter(request, response);
    }
}
