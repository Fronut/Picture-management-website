package com.imagemanagement.repository;

import com.imagemanagement.entity.RefreshToken;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByToken(String token);

    List<RefreshToken> findAllByUser_IdAndRevokedFalse(Long userId);

    long countByUser_IdAndRevokedFalse(Long userId);

    long deleteByExpiresAtBefore(Instant threshold);
}