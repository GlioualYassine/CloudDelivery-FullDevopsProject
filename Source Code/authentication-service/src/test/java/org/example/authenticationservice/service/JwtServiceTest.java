package org.example.authenticationservice.service;

import org.example.authenticationservice.model.Role;
import org.example.authenticationservice.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String SECRET = "test-secret-key-that-is-long-enough-for-hs256-signing";
    private static final long EXPIRATION_MS = 3_600_000L;
    private static final long REFRESH_EXPIRATION_MS = 2_592_000_000L;

    private JwtService jwtService;

    private User testUser;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "secret", SECRET);
        ReflectionTestUtils.setField(jwtService, "jwtExpirationMs", EXPIRATION_MS);
        ReflectionTestUtils.setField(jwtService, "refreshExpirationMs", REFRESH_EXPIRATION_MS);
        jwtService.init();

        testUser = User.builder()
                .id("user-1")
                .email("user@test.com")
                .password("hashedPwd")
                .role(Role.ROLE_CLIENT)
                .enabled(true)
                .build();
    }

    @Test
    void generateToken_shouldReturnNonBlankJwt() {
        String token = jwtService.generateToken(testUser);

        assertThat(token).isNotBlank();
        assertThat(token.split("\\.")).hasSize(3);
    }

    @Test
    void extractUsername_shouldReturnEmailFromToken() {
        String token = jwtService.generateToken(testUser);

        assertThat(jwtService.extractUsername(token)).isEqualTo("user@test.com");
    }

    @Test
    void isTokenValid_validToken_shouldReturnTrue() {
        String token = jwtService.generateToken(testUser);

        assertThat(jwtService.isTokenValid(token, testUser)).isTrue();
    }

    @Test
    void isTokenValid_tokenForDifferentUser_shouldReturnFalse() {
        User otherUser = User.builder()
                .id("user-2")
                .email("other@test.com")
                .password("pwd")
                .role(Role.ROLE_CLIENT)
                .enabled(true)
                .build();
        String token = jwtService.generateToken(testUser);

        assertThat(jwtService.isTokenValid(token, otherUser)).isFalse();
    }

    @Test
    void generateToken_expiredToken_shouldFailValidation() {
        ReflectionTestUtils.setField(jwtService, "jwtExpirationMs", -1000L);
        jwtService.init();
        String expiredToken = jwtService.generateToken(testUser);

        assertThatThrownBy(() -> jwtService.extractUsername(expiredToken));
    }

    @Test
    void generateRefreshToken_shouldHaveIndependentExpiry() {
        String accessToken = jwtService.generateToken(testUser);
        String refreshToken = jwtService.generateRefreshToken(testUser);

        assertThat(accessToken).isNotEqualTo(refreshToken);
        assertThat(jwtService.extractUsername(refreshToken)).isEqualTo("user@test.com");
    }
}
