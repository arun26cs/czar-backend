package com.czar.user.repository;

import com.czar.user.domain.DeviceToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface DeviceTokenRepository extends JpaRepository<DeviceToken, UUID> {

    Optional<DeviceToken> findByUserId(UUID userId);

    Optional<DeviceToken> findByFcmToken(String fcmToken);

    @Modifying
    @Query("DELETE FROM DeviceToken dt WHERE dt.userId = :userId")
    void deleteByUserId(@Param("userId") UUID userId);
}
