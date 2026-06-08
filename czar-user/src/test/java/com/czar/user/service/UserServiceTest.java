package com.czar.user.service;

import com.czar.common.exception.ResourceNotFoundException;
import com.czar.user.domain.UserProfile;
import com.czar.user.messaging.UserDeletionPublisher;
import com.czar.user.repository.DeviceTokenRepository;
import com.czar.user.repository.UserProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Fix 3 — UserService.deleteAccount() unit tests.
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserProfileRepository profileRepository;

    @Mock
    private DeviceTokenRepository deviceTokenRepository;

    @Mock
    private UserDeletionPublisher deletionPublisher;

    @InjectMocks
    private UserService userService;

    private UUID userId;
    private UserProfile profile;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        profile = new UserProfile();
        profile.setDisplayName("Test User");
    }

    @Test
    void deleteAccount_softDeletesProfile() {
        when(profileRepository.findById(userId)).thenReturn(Optional.of(profile));
        when(profileRepository.save(any(UserProfile.class))).thenAnswer(inv -> inv.getArgument(0));

        userService.deleteAccount(userId);

        assertThat(profile.getDeletedAt()).isNotNull();
        assertThat(profile.getDeletedAt()).isBeforeOrEqualTo(Instant.now());
        verify(profileRepository).save(profile);
    }

    @Test
    void deleteAccount_removesAllDeviceTokens() {
        when(profileRepository.findById(userId)).thenReturn(Optional.of(profile));
        when(profileRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        userService.deleteAccount(userId);

        verify(deviceTokenRepository).deleteByUserId(userId);
    }

    @Test
    void deleteAccount_publishesDeletionEvent() {
        when(profileRepository.findById(userId)).thenReturn(Optional.of(profile));
        when(profileRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        userService.deleteAccount(userId);

        verify(deletionPublisher).publishUserDeleted(userId);
    }

    @Test
    void deleteAccount_throwsNotFound_whenProfileMissing() {
        when(profileRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.deleteAccount(userId))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(profileRepository, never()).save(any());
        verify(deviceTokenRepository, never()).deleteByUserId(any());
        verify(deletionPublisher, never()).publishUserDeleted(any());
    }

    @Test
    void deleteAccount_executesAllThreeStepsInOrder() {
        when(profileRepository.findById(userId)).thenReturn(Optional.of(profile));
        when(profileRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        userService.deleteAccount(userId);

        // Verify all three steps executed exactly once each
        verify(profileRepository).save(any());
        verify(deviceTokenRepository).deleteByUserId(userId);
        verify(deletionPublisher).publishUserDeleted(userId);
        verifyNoMoreInteractions(deletionPublisher);
    }
}
