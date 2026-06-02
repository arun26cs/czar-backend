package com.czar.auth.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(schema = "auth", name = "otp_requests")
@Getter @Setter @NoArgsConstructor
public class OtpRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String identifier; // email or E.164 phone

    @Column(nullable = false)
    private String otpHash; // BCrypt hash — never plaintext

    @Column(nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private boolean used = false;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    private String ipAddress;

    public static OtpRequest create(String identifier, String otpHash, int expiryMinutes, String ipAddress) {
        OtpRequest r = new OtpRequest();
        r.identifier = identifier;
        r.otpHash = otpHash;
        r.expiresAt = Instant.now().plusSeconds(expiryMinutes * 60L);
        r.ipAddress = ipAddress;
        return r;
    }

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }
}
