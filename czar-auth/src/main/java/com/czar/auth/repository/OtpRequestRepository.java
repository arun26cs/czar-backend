package com.czar.auth.repository;

import com.czar.auth.entity.OtpRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface OtpRequestRepository extends JpaRepository<OtpRequest, UUID> {

    List<OtpRequest> findByIdentifierAndUsedFalseOrderByCreatedAtDesc(String identifier);

    @Modifying
    @Query("UPDATE OtpRequest o SET o.used = true WHERE o.identifier = :identifier AND o.used = false")
    void markAllUsedForIdentifier(String identifier);

    long countByIdentifierAndCreatedAtAfter(String identifier, Instant since);

    long countByIpAddressAndCreatedAtAfter(String ipAddress, Instant since);
}
