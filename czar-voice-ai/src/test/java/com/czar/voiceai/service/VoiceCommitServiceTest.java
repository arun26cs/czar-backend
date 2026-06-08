package com.czar.voiceai.service;

import com.czar.voiceai.dto.ParsedItem;
import com.czar.voiceai.dto.VoiceCommitRequest;
import com.czar.voiceai.dto.VoiceCommitResponse;
import com.czar.voiceai.messaging.VoiceResultPublisher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VoiceCommitServiceTest {

    @Mock
    VoiceResultPublisher publisher;

    @InjectMocks
    VoiceCommitService voiceCommitService;

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private ParsedItem completePlan() {
        return new ParsedItem("plan", "Morning Run",
                LocalDate.of(2025, 6, 2), 7, 0, 30,
                null, "task", null, null, List.of());
    }

    private ParsedItem completeNote() {
        return new ParsedItem("note", "Shopping list",
                null, null, null, null,
                "milk, eggs", null, null, null, List.of());
    }

    private ParsedItem incompletePlan() {
        return new ParsedItem("plan", "Gym",
                null, null, null, 60,
                null, "task", null, null, List.of("scheduledDate", "hour"));
    }

    // -------------------------------------------------------------------------
    // Happy path: plan + note
    // -------------------------------------------------------------------------

    @Test
    void commit_validItems_publishesAndReturnsJobId() {
        VoiceCommitRequest req = new VoiceCommitRequest(List.of(completePlan(), completeNote()));

        VoiceCommitResponse resp = voiceCommitService.commit(USER_ID, req);

        assertThat(resp.jobId()).isNotNull();
        assertThat(resp.published()).isEqualTo(2);
        verify(publisher).publish(eq("ai.result.ready"), eq(USER_ID.toString()),
                anyString(), eq(List.of(completePlan(), completeNote())));
    }

    // -------------------------------------------------------------------------
    // Single note
    // -------------------------------------------------------------------------

    @Test
    void commit_singleNote_publishesOnce() {
        VoiceCommitResponse resp = voiceCommitService.commit(USER_ID,
                new VoiceCommitRequest(List.of(completeNote())));

        assertThat(resp.published()).isEqualTo(1);
        verify(publisher, times(1)).publish(any(), any(), any(), any());
    }

    // -------------------------------------------------------------------------
    // Reject: plan with missing scheduledDate
    // -------------------------------------------------------------------------

    @Test
    void commit_planMissingScheduledDate_throws() {
        ParsedItem bad = new ParsedItem("plan", "Run", null, 7, 0, 30,
                null, "task", null, null, List.of());

        assertThatThrownBy(() -> voiceCommitService.commit(USER_ID,
                new VoiceCommitRequest(List.of(bad))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("scheduledDate");
    }

    // -------------------------------------------------------------------------
    // Reject: plan with missing hour
    // -------------------------------------------------------------------------

    @Test
    void commit_planMissingHour_throws() {
        ParsedItem bad = new ParsedItem("plan", "Run",
                LocalDate.of(2025, 6, 2), null, 0, 30,
                null, "task", null, null, List.of());

        assertThatThrownBy(() -> voiceCommitService.commit(USER_ID,
                new VoiceCommitRequest(List.of(bad))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("hour");
    }

    // -------------------------------------------------------------------------
    // Reject: plan with zero duration
    // -------------------------------------------------------------------------

    @Test
    void commit_planZeroDuration_throws() {
        ParsedItem bad = new ParsedItem("plan", "Run",
                LocalDate.of(2025, 6, 2), 7, 0, 0,
                null, "task", null, null, List.of());

        assertThatThrownBy(() -> voiceCommitService.commit(USER_ID,
                new VoiceCommitRequest(List.of(bad))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("durationMinutes");
    }

    // -------------------------------------------------------------------------
    // Reject: note with blank title
    // -------------------------------------------------------------------------

    @Test
    void commit_noteBlankTitle_throws() {
        ParsedItem bad = new ParsedItem("note", "  ",
                null, null, null, null,
                "some body", null, null, null, List.of());

        assertThatThrownBy(() -> voiceCommitService.commit(USER_ID,
                new VoiceCommitRequest(List.of(bad))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("title");
    }

    // -------------------------------------------------------------------------
    // Reject: unknown type
    // -------------------------------------------------------------------------

    @Test
    void commit_unknownType_throws() {
        ParsedItem bad = new ParsedItem("reminder", "Check email",
                null, null, null, null,
                null, null, null, null, List.of());

        assertThatThrownBy(() -> voiceCommitService.commit(USER_ID,
                new VoiceCommitRequest(List.of(bad))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("type");
    }
}
