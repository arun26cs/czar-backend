package com.czar.auth.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(schema = "auth", name = "oauth_connections")
@Getter @Setter @NoArgsConstructor
public class OauthConnection {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserAuth user;

    @Column(nullable = false)
    private String provider; // "google" | "github"

    @Column(nullable = false)
    private String providerUserId;

    private String providerEmail;

    private String accessToken; // encrypted at app layer before storage

    @Column(nullable = false, updatable = false)
    private Instant connectedAt = Instant.now();
}
