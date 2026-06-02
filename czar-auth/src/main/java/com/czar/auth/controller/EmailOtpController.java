package com.czar.auth.controller;

import com.czar.auth.dto.TokenPairResponse;
import com.czar.auth.service.OtpService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/auth/email")
public class EmailOtpController {

    private final OtpService otpService;

    public EmailOtpController(OtpService otpService) {
        this.otpService = otpService;
    }

    @PostMapping("/request-otp")
    public ResponseEntity<Map<String, String>> requestOtp(
            @Valid @RequestBody OtpRequest body,
            HttpServletRequest request) {
        String ip = resolveIp(request);
        otpService.requestEmailOtp(body.email(), ip);
        return ResponseEntity.ok(Map.of("message", "OTP sent to " + body.email()));
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<TokenPairResponse> verifyOtp(
            @Valid @RequestBody OtpVerifyRequest body,
            HttpServletRequest request) {
        String deviceHint = request.getHeader("X-Device-Hint");
        TokenPairResponse tokens = otpService.verifyEmailOtp(body.email(), body.otp(), deviceHint);
        return ResponseEntity.ok(tokens);
    }

    // ── inner request records ─────────────────────────────────────────────────

    public record OtpRequest(@NotBlank @Email String email) {}

    public record OtpVerifyRequest(
            @NotBlank @Email String email,
            @NotBlank String otp
    ) {}

    // ── helpers ───────────────────────────────────────────────────────────────

    private String resolveIp(HttpServletRequest req) {
        String forwarded = req.getHeader("X-Forwarded-For");
        return (forwarded != null && !forwarded.isBlank())
                ? forwarded.split(",")[0].trim()
                : req.getRemoteAddr();
    }
}
