package com.czar.auth.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(schema = "auth", name = "users_auth")
@Getter @Setter @NoArgsConstructor
public class UserAuth {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(unique = true)
    private String email;

    @Column(unique = true)
    private String phone;

    @Column(nullable = false)
    private boolean emailVerified = false;

    @Column(nullable = false)
    private boolean phoneVerified = false;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    private Instant lastLoginAt;

    public static UserAuth ofEmail(String email) {
        UserAuth u = new UserAuth();
        u.email = email;
        u.emailVerified = true;
        return u;
    }

    public static UserAuth ofPhone(String phone) {
        UserAuth u = new UserAuth();
        u.phone = phone;
        u.phoneVerified = true;
        return u;
    }
}
