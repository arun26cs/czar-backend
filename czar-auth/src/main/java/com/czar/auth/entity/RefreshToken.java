package com.czar.auth.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(schema = "auth", name = "refresh_tokens")
@Getter @Setter @NoArgsConstructor
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserAuth user;

    @Column(nullable = false, unique = true)
    private String tokenHash; // BCrypt hash of the raw token

    @Column(nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private boolean revoked = false;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    private String deviceHint;

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    public static RefreshToken create(UserAuth user, String tokenHash, int expiryDays, String deviceHint) {
        RefreshToken rt = new RefreshToken();
        rt.user = user;
        rt.tokenHash = tokenHash;
        rt.expiresAt = Instant.now().plusSeconds(expiryDays * 86400L);
        rt.deviceHint = deviceHint;
        return rt;
    }
}
