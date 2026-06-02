package com.czar.user.repository;

import com.czar.user.domain.Preferences;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface PreferencesRepository extends JpaRepository<Preferences, UUID> {
    Optional<Preferences> findByUserId(UUID userId);
}
