package com.czar.user.service;

import com.czar.common.exception.ResourceNotFoundException;
import com.czar.user.domain.UserProfile;
import com.czar.user.dto.UserProfileResponse;
import com.czar.user.dto.UserProfileUpdateRequest;
import com.czar.user.repository.UserProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class UserService {

    private final UserProfileRepository profileRepository;

    public UserService(UserProfileRepository profileRepository) {
        this.profileRepository = profileRepository;
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
}
