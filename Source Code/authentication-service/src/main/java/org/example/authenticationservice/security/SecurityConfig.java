package org.example.authenticationservice.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final UserDetailsService userDetailsService;

    /**
     * Bean pour encoder les mots de passe (BCrypt)
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Configuration principale de la sécurité HTTP
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // API stateless → pas de session HTTP
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )

                // CSRF inutile pour une API REST stateless
                .csrf(csrf -> csrf.disable())

                // CORS (voir bean plus bas)
                .cors(cors -> {
                    // on laisse utiliser la configuration du bean corsConfigurationSource
                })

                // Autorisation des requêtes
                .authorizeHttpRequests(auth -> auth
                        // Endpoints publics (auth)
                        .requestMatchers(
                                "/auth/**"
                        ).permitAll()
                        // Tout le reste nécessite un token JWT valide
                        .anyRequest().authenticated()
                )

                // UserDetailsService pour que Spring Security sache charger les users
                .userDetailsService(userDetailsService);

        // Ajouter notre filtre JWT avant le filtre standard d'auth
        http.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * Bean AuthenticationManager (utilisé si tu veux t’en servir dans un service)
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    /**
     * Configuration globale des CORS pour permettre l’appel depuis tes frontends (Angular, etc.)
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        // Origines autorisées : adapte selon tes fronts
        config.setAllowedOrigins(List.of(
                "http://localhost:4200", // Angular client
                "http://localhost:4300"  // Angular admin
        ));

        // Méthodes HTTP autorisées
        config.setAllowedMethods(List.of(
                "GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"
        ));

        // Headers autorisés
        config.setAllowedHeaders(List.of(
                "Authorization",
                "Content-Type",
                "X-Requested-With",
                "Accept",
                "Origin"
        ));

        // Headers exposés côté client (ex: Authorization si tu renvoies un nouveau token)
        config.setExposedHeaders(List.of(
                "Authorization"
        ));

        // Autoriser l’envoi de cookies / credentials si besoin
        config.setAllowCredentials(true);

        // Durée de mise en cache de la config CORS (en secondes)
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        // appliquer cette config à tous les endpoints
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
