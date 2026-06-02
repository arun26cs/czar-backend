package com.czar.user.service;

import com.czar.common.exception.ResourceNotFoundException;
import com.czar.user.domain.Preferences;
import com.czar.user.dto.PreferencesResponse;
import com.czar.user.dto.PreferencesUpdateRequest;
import com.czar.user.repository.PreferencesRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class PreferencesService {

    private final PreferencesRepository preferencesRepository;

    public PreferencesService(PreferencesRepository preferencesRepository) {
        this.preferencesRepository = preferencesRepository;
    }

    @Transactional(readOnly = true)
    public PreferencesResponse getPreferences(UUID userId) {
        Preferences prefs = preferencesRepository.findByUserId(userId)
                .orElseThrow(() -> ResourceNotFoundException.of("Preferences", userId.toString()));
        return toResponse(prefs);
    }

    @Transactional
    public PreferencesResponse updatePreferences(UUID userId, PreferencesUpdateRequest request) {
        Preferences prefs = preferencesRepository.findByUserId(userId)
                .orElseThrow(() -> ResourceNotFoundException.of("Preferences", userId.toString()));

        if (request.theme() != null) {
            prefs.setTheme(request.theme());
        }
        if (request.dashboardCollapsed() != null) {
            prefs.setDashboardCollapsed(request.dashboardCollapsed());
        }
        if (request.defaultView() != null) {
            prefs.setDefaultView(request.defaultView());
        }
        if (request.reminderMinutes() != null) {
            prefs.setReminderMinutes(request.reminderMinutes());
        }
        return toResponse(preferencesRepository.save(prefs));
    }

    private PreferencesResponse toResponse(Preferences p) {
        return new PreferencesResponse(
                p.getId(), p.getUserId(), p.getTheme(),
                p.isDashboardCollapsed(), p.getDefaultView(),
                p.getReminderMinutes(), p.getUpdatedAt());
    }
}
