package org.example.authenticationservice.service;

import org.example.authenticationservice.dto.AuthenticationRequest;
import org.example.authenticationservice.dto.AuthenticationResponse;
import org.example.authenticationservice.dto.ForgotPasswordRequest;
import org.example.authenticationservice.dto.RegisterRequest;
import org.example.authenticationservice.dto.ResetPasswordRequest;
import org.example.authenticationservice.kafka.PasswordResetProducer;
import org.example.authenticationservice.model.PasswordResetToken;
import org.example.authenticationservice.model.Role;
import org.example.authenticationservice.model.User;
import org.example.authenticationservice.repository.PasswordResetTokenRepository;
import org.example.authenticationservice.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private PasswordResetProducer passwordResetProducer;

    @InjectMocks
    private UserService userService;

    private User testUser;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(userService, "masterResetCode", "MASTER_CODE");
        ReflectionTestUtils.setField(userService, "resetExpirationMinutes", 15L);

        testUser = User.builder()
                .id("user-1")
                .email("user@test.com")
                .password("$2a$10$hashedPassword")
                .role(Role.ROLE_CLIENT)
                .enabled(true)
                .build();
    }

    @Test
    void register_shouldHashPasswordAndReturnTokens() {
        RegisterRequest request = new RegisterRequest();
        request.setFullName("John Doe");
        request.setEmail("new@test.com");
        request.setPassword("password123");
        request.setRole(Role.ROLE_CLIENT);

        when(userRepository.existsByEmail("new@test.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("$2a$10$hashed");
        when(userRepository.save(any())).thenReturn(testUser);
        when(jwtService.generateToken(any())).thenReturn("access-token");
        when(jwtService.generateRefreshToken(any())).thenReturn("refresh-token");

        AuthenticationResponse response = userService.register(request);

        assertThat(response.getAccessToken()).isEqualTo("access-token");
        assertThat(response.getRefreshToken()).isEqualTo("refresh-token");
        verify(passwordEncoder).encode("password123");
    }

    @Test
    void register_emailAlreadyInUse_shouldThrow() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("user@test.com");
        request.setPassword("password");

        when(userRepository.existsByEmail("user@test.com")).thenReturn(true);

        assertThatThrownBy(() -> userService.register(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Email already in use");
    }

    @Test
    void authenticate_validCredentials_shouldReturnTokens() {
        AuthenticationRequest request = new AuthenticationRequest();
        request.setEmail("user@test.com");
        request.setPassword("password123");

        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("password123", "$2a$10$hashedPassword")).thenReturn(true);
        when(jwtService.generateToken(testUser)).thenReturn("access-token");
        when(jwtService.generateRefreshToken(testUser)).thenReturn("refresh-token");

        AuthenticationResponse response = userService.authenticate(request);

        assertThat(response.getAccessToken()).isEqualTo("access-token");
    }

    @Test
    void authenticate_wrongPassword_shouldThrow() {
        AuthenticationRequest request = new AuthenticationRequest();
        request.setEmail("user@test.com");
        request.setPassword("wrong");

        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("wrong", "$2a$10$hashedPassword")).thenReturn(false);

        assertThatThrownBy(() -> userService.authenticate(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid email or password");
    }

    @Test
    void authenticate_userNotFound_shouldThrow() {
        AuthenticationRequest request = new AuthenticationRequest();
        request.setEmail("ghost@test.com");
        request.setPassword("password");

        when(userRepository.findByEmail("ghost@test.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.authenticate(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid email or password");
    }

    @Test
    void requestPasswordReset_shouldGenerateCodeAndPublishEvent() {
        ForgotPasswordRequest request = new ForgotPasswordRequest();
        request.setEmail("user@test.com");

        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(testUser));
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$10$hashedCode");
        when(passwordResetTokenRepository.findByUserIdAndUsedIsFalse("user-1")).thenReturn(Optional.empty());
        when(passwordResetTokenRepository.save(any())).thenReturn(mock(PasswordResetToken.class));

        userService.requestPasswordReset(request);

        verify(passwordResetProducer).sendPasswordResetRequested(eq("user@test.com"), anyString());
    }

    @Test
    void resetPassword_validCode_shouldUpdatePassword() {
        ResetPasswordRequest request = new ResetPasswordRequest();
        request.setEmail("user@test.com");
        request.setCode("123456");
        request.setNewPassword("newPassword");

        PasswordResetToken token = PasswordResetToken.builder()
                .id("token-1")
                .userId("user-1")
                .codeHash("$2a$10$hashedCode")
                .expiresAt(Instant.now().plusSeconds(900))
                .used(false)
                .build();

        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(testUser));
        when(passwordResetTokenRepository.findByUserIdAndUsedIsFalse("user-1")).thenReturn(Optional.of(token));
        when(passwordEncoder.matches("123456", "$2a$10$hashedCode")).thenReturn(true);
        when(passwordEncoder.encode("newPassword")).thenReturn("$2a$10$newHashed");

        userService.resetPassword(request);

        assertThat(testUser.getPassword()).isEqualTo("$2a$10$newHashed");
        assertThat(token.isUsed()).isTrue();
        verify(userRepository).save(testUser);
        verify(passwordResetTokenRepository).save(token);
    }

    @Test
    void resetPassword_expiredCode_shouldThrow() {
        ResetPasswordRequest request = new ResetPasswordRequest();
        request.setEmail("user@test.com");
        request.setCode("123456");
        request.setNewPassword("newPassword");

        PasswordResetToken token = PasswordResetToken.builder()
                .userId("user-1")
                .codeHash("$2a$10$hashedCode")
                .expiresAt(Instant.now().minusSeconds(1))
                .used(false)
                .build();

        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(testUser));
        when(passwordResetTokenRepository.findByUserIdAndUsedIsFalse("user-1")).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> userService.resetPassword(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void resetPassword_masterCode_shouldBypassTokenAndUpdatePassword() {
        ResetPasswordRequest request = new ResetPasswordRequest();
        request.setEmail("user@test.com");
        request.setCode("MASTER_CODE");
        request.setNewPassword("newPassword");

        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(testUser));
        when(passwordEncoder.encode("newPassword")).thenReturn("$2a$10$newHashed");
        when(passwordResetTokenRepository.findByUserIdAndUsedIsFalse("user-1")).thenReturn(Optional.empty());

        userService.resetPassword(request);

        assertThat(testUser.getPassword()).isEqualTo("$2a$10$newHashed");
        verify(userRepository).save(testUser);
    }
}
