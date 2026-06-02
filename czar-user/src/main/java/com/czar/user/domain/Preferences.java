package com.czar.user.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "preferences", schema = "users")
public class Preferences {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;

    @Column(nullable = false)
    private String theme = "system";

    @Column(name = "dashboard_collapsed", nullable = false)
    private boolean dashboardCollapsed = false;

    @Column(name = "default_view", nullable = false)
    private String defaultView = "list";

    @Column(name = "reminder_minutes", nullable = false)
    private int reminderMinutes = 15;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public String getTheme() { return theme; }
    public void setTheme(String theme) { this.theme = theme; }

    public boolean isDashboardCollapsed() { return dashboardCollapsed; }
    public void setDashboardCollapsed(boolean dashboardCollapsed) { this.dashboardCollapsed = dashboardCollapsed; }

    public String getDefaultView() { return defaultView; }
    public void setDefaultView(String defaultView) { this.defaultView = defaultView; }

    public int getReminderMinutes() { return reminderMinutes; }
    public void setReminderMinutes(int reminderMinutes) { this.reminderMinutes = reminderMinutes; }

    public Instant getUpdatedAt() { return updatedAt; }
}
