package com.czar.user.service;

import com.czar.common.exception.ResourceNotFoundException;
import com.czar.user.domain.UserProfile;
import com.czar.user.dto.UserProfileResponse;
import com.czar.user.dto.UserProfileUpdateRequest;
import com.czar.user.messaging.UserDeletionPublisher;
import com.czar.user.repository.DeviceTokenRepository;
import com.czar.user.repository.UserProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class UserService {

    private final UserProfileRepository profileRepository;
    private final DeviceTokenRepository deviceTokenRepository;
    private final UserDeletionPublisher deletionPublisher;

    public UserService(UserProfileRepository profileRepository,
                       DeviceTokenRepository deviceTokenRepository,
                       UserDeletionPublisher deletionPublisher) {
        this.profileRepository = profileRepository;
        this.deviceTokenRepository = deviceTokenRepository;
        this.deletionPublisher = deletionPublisher;
    }

    @Transactional(readOnly = true)
    public UserProfileResponse getProfile(UUID userId) {
        UserProfile profile = profileRepository.findById(userId)
                .orElseThrow(() -> ResourceNotFoundException.of("UserProfile", userId.toString()));
        return toResponse(profile);
    }

    @Transactional
    public UserProfileResponse updateProfile(UUID userId, UserProfileUpdateRequest request) {
        UserProfile profile = profileRepository.findById(userId)
                .orElseThrow(() -> ResourceNotFoundException.of("UserProfile", userId.toString()));

        if (request.displayName() != null) {
            profile.setDisplayName(request.displayName());
        }
        if (request.avatarUrl() != null) {
            profile.setAvatarUrl(request.avatarUrl());
        }
        return toResponse(profileRepository.save(profile));
    }

    private UserProfileResponse toResponse(UserProfile p) {
        return new UserProfileResponse(
                p.getId(), p.getDisplayName(), p.getAvatarUrl(),
                p.getCreatedAt(), p.getUpdatedAt());
    }

    /**
     * Soft-deletes the user account.
     *
     * <ol>
     *   <li>Sets deleted_at on the users_profile row (soft delete).</li>
     *   <li>Removes all device tokens immediately (stops FCM pushes).</li>
     *   <li>Publishes user.deleted to Pub/Sub → czar-auth revokes refresh tokens,
     *       downstream services soft-delete the user's notes and plans.</li>
     * </ol>
     *
     * Hard-delete of the profile row is handled by a Cloud Scheduler job
     * running daily that purges records where deleted_at < now() - 30 days.
     */
    @Transactional
    public void deleteAccount(UUID userId) {
        UserProfile profile = profileRepository.findById(userId)
                .orElseThrow(() -> ResourceNotFoundException.of("UserProfile", userId.toString()));

        // Soft-delete the profile
        profile.setDeletedAt(Instant.now());
        profileRepository.save(profile);

        // Remove device tokens immediately — stops FCM pushes
        deviceTokenRepository.deleteByUserId(userId);

        // Publish event — czar-auth will revoke refresh tokens; others soft-delete data
        deletionPublisher.publishUserDeleted(userId);
    }
}
