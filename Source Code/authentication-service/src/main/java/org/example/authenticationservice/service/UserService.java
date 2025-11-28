package org.example.authenticationservice.service;



// package org.example.authenticationservice.service;

import org.example.authenticationservice.dto.*;
import org.example.authenticationservice.kafka.PasswordResetProducer;
import org.example.authenticationservice.model.PasswordResetToken;
import org.example.authenticationservice.model.Role;
import org.example.authenticationservice.model.User;
import org.example.authenticationservice.repository.PasswordResetTokenRepository;
import org.example.authenticationservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final PasswordResetProducer passwordResetProducer;

    @Value("${app.security.password-reset.master-code:}")
    private String masterResetCode; // ton code secret dev

    @Value("${app.security.password-reset.expiration-minutes:15}")
    private long resetExpirationMinutes;

    // ... tes méthodes register() / authenticate() déjà existantes ...

    // ---------- REFRESH TOKEN ----------

    public AuthenticationResponse refreshToken(String refreshToken) {
        String email = jwtService.extractUsername(refreshToken);
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (!jwtService.isTokenValid(refreshToken, user)) {
            throw new IllegalArgumentException("Invalid refresh token");
        }

        String newAccessToken = jwtService.generateToken(user);
        String newRefreshToken = jwtService.generateRefreshToken(user); // rotation

        return AuthenticationResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .build();
    }

    // ---------- MOT DE PASSE OUBLIÉ : DEMANDE ----------

    public void requestPasswordReset(ForgotPasswordRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        // générer un code aléatoire 6 chiffres
        String code = generateCode();

        // on hash le code pour ne pas le stocker en clair
        String codeHash = passwordEncoder.encode(code);

        Instant expiresAt = Instant.now().plus(resetExpirationMinutes, ChronoUnit.MINUTES);

        // un seul token actif par user
        PasswordResetToken token = passwordResetTokenRepository
                .findByUserIdAndUsedIsFalse(user.getId())
                .orElse(
                        PasswordResetToken.builder()
                                .userId(user.getId())
                                .build()
                );

        token.setCodeHash(codeHash);
        token.setExpiresAt(expiresAt);
        token.setUsed(false);

        passwordResetTokenRepository.save(token);

        // envoyer l'event Kafka (dans le vrai système, le service de notification enverra l'e-mail)
        passwordResetProducer.sendPasswordResetRequested(user.getEmail(), code);

        // pour dev, tu peux aussi logguer le code :
        System.out.println("DEV - Password reset code for " + user.getEmail() + " = " + code);
    }

    // ---------- MOT DE PASSE OUBLIÉ : CONFIRMATION ----------

    public void resetPassword(ResetPasswordRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        String providedCode = request.getCode();

        // 1) Master code (DEV ONLY) : permet de reset sans email
        if (masterResetCode != null && !masterResetCode.isBlank()
                && masterResetCode.equals(providedCode)) {

            user.setPassword(passwordEncoder.encode(request.getNewPassword()));
            userRepository.save(user);

            passwordResetTokenRepository.findByUserIdAndUsedIsFalse(user.getId())
                    .ifPresent(token -> {
                        token.setUsed(true);// marquer
                        passwordResetTokenRepository.save(token);
                    });

            return;
        }

        // 2) Cas normal : on vérifie le code stocké
        PasswordResetToken token = passwordResetTokenRepository
                .findByUserIdAndUsedIsFalse(user.getId())
                .orElseThrow(() -> new IllegalArgumentException("No active reset token"));

        if (token.getExpiresAt().isBefore(Instant.now())) {
            throw new IllegalArgumentException("Reset code expired");
        }

        if (!passwordEncoder.matches(providedCode, token.getCodeHash())) {
            throw new IllegalArgumentException("Invalid reset code");
        }

        // OK → on change le mot de passe
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        token.setUsed(true);
        passwordResetTokenRepository.save(token);
    }

    private String generateCode() {
        SecureRandom random = new SecureRandom();
        int value = random.nextInt(1_000_000); // 0..999999
        return String.format("%06d", value);
    }




public AuthenticationResponse register(RegisterRequest request) {
    // 1) vérifier si l'email existe déjà
    if (userRepository.existsByEmail(request.getEmail())) {
        throw new IllegalArgumentException("Email already in use");
    }

    // 2) déterminer le rôle (par défaut : ROLE_CLIENT)
    Role role = request.getRole() != null ? request.getRole() : Role.ROLE_CLIENT;

    // 3) construire l'utilisateur
    User user = User.builder()
            .fullName(request.getFullName())
            .email(request.getEmail())
            .password(passwordEncoder.encode(request.getPassword()))
            .role(role)
            .enabled(true)
            .build();

    // 4) enregistrer en base
    user = userRepository.save(user);

    // 5) générer les tokens
    String accessToken = jwtService.generateToken(user);
    String refreshToken = jwtService.generateRefreshToken(user);

    // 6) renvoyer la réponse
    return AuthenticationResponse.builder()
            .accessToken(accessToken)
            .refreshToken(refreshToken)
            .build();
}

public AuthenticationResponse authenticate(AuthenticationRequest request) {
    // 1) trouver l'utilisateur
    User user = userRepository.findByEmail(request.getEmail())
            .orElseThrow(() -> new IllegalArgumentException("Invalid email or password"));

    // 2) vérifier le mot de passe
    if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
        throw new IllegalArgumentException("Invalid email or password");
    }

    if (!user.isEnabled()) {
        throw new IllegalStateException("User is disabled");
    }

    // 3) générer les tokens
    String accessToken = jwtService.generateToken(user);
    String refreshToken = jwtService.generateRefreshToken(user);

    // 4) renvoyer la réponse
    return AuthenticationResponse.builder()
            .accessToken(accessToken)
            .refreshToken(refreshToken)
            .build();
}

}