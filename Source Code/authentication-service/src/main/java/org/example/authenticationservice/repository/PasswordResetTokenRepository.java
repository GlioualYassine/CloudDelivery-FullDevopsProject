package org.example.authenticationservice.repository;

import org.example.authenticationservice.model.PasswordResetToken;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface PasswordResetTokenRepository extends MongoRepository<PasswordResetToken, String> {

    Optional<PasswordResetToken> findByUserIdAndUsedIsFalse(String userId);
}
