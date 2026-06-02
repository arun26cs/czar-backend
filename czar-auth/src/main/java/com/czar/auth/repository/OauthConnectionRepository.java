package com.czar.auth.repository;

import com.czar.auth.entity.OauthConnection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface OauthConnectionRepository extends JpaRepository<OauthConnection, UUID> {
    Optional<OauthConnection> findByProviderAndProviderUserId(String provider, String providerUserId);
}
