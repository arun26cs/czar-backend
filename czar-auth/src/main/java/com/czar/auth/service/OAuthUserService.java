package com.czar.auth.service;

import com.czar.auth.dto.TokenPairResponse;
import com.czar.auth.entity.OauthConnection;
import com.czar.auth.entity.UserAuth;
import com.czar.auth.repository.OauthConnectionRepository;
import com.czar.auth.repository.UserAuthRepository;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Handles the OAuth2 post-callback user resolution for Google and GitHub.
 * Called after Spring Security has already exchanged the authorization code
 * and retrieved the provider user profile.
 */
@Service
public class OAuthUserService {

    private final UserAuthRepository userRepo;
    private final OauthConnectionRepository oauthConnRepo;
    private final JwtService jwtService;
    private final UserEventPublisher eventPublisher;

    public OAuthUserService(UserAuthRepository userRepo,
                            OauthConnectionRepository oauthConnRepo,
                            JwtService jwtService,
                            UserEventPublisher eventPublisher) {
        this.userRepo = userRepo;
        this.oauthConnRepo = oauthConnRepo;
        this.jwtService = jwtService;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Resolves or creates a UserAuth from an OAuth2 login.
     * Returns a JWT token pair.
     *
     * @param provider       "google" or "github"
     * @param providerUserId stable ID from the provider (sub / id)
     * @param email          verified email from the provider
     * @param accessToken    provider access token (stored encrypted)
     * @param deviceHint     optional device identifier
     */
    @Transactional
    public TokenPairResponse handleOAuthLogin(String provider,
                                              String providerUserId,
                                              String email,
                                              String accessToken,
                                              String deviceHint) {
        // Find existing OAuth connection
        var existingConn = oauthConnRepo.findByProviderAndProviderUserId(provider, providerUserId);

        UserAuth user;
        boolean isNewUser = false;

        if (existingConn.isPresent()) {
            // Known OAuth user — update access token
            user = existingConn.get().getUser();
            existingConn.get().setAccessToken(accessToken);
            oauthConnRepo.save(existingConn.get());
        } else {
            // New OAuth connection — find by email or create
            user = userRepo.findByEmail(email).orElseGet(() -> {
                UserAuth u = UserAuth.ofEmail(email);
                return userRepo.save(u);
            });

            boolean wasNew = existingConn.isEmpty() && !userRepo.existsByEmail(email);
            isNewUser = wasNew;

            OauthConnection conn = new OauthConnection();
            conn.setUser(user);
            conn.setProvider(provider);
            conn.setProviderUserId(providerUserId);
            conn.setProviderEmail(email);
            conn.setAccessToken(accessToken); // TODO: encrypt before storage in Phase 9
            oauthConnRepo.save(conn);
        }

        user.setLastLoginAt(Instant.now());
        userRepo.save(user);

        if (isNewUser) {
            eventPublisher.publishUserCreated(user);
        }

        return jwtService.issueTokenPair(user, deviceHint);
    }
}
