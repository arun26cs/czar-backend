package com.czar.auth.controller;

import com.czar.auth.dto.TokenPairResponse;
import com.czar.auth.service.JwtService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/auth/token")
public class TokenController {

    private final JwtService jwtService;

    public TokenController(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @PostMapping("/refresh")
    public ResponseEntity<TokenPairResponse> refresh(
            @Valid @RequestBody RefreshRequest body,
            HttpServletRequest request) {
        String deviceHint = request.getHeader("X-Device-Hint");
        return ResponseEntity.ok(jwtService.refresh(body.refreshToken(), deviceHint));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshRequest body) {
        jwtService.logout(body.refreshToken());
        return ResponseEntity.noContent().build();
    }

    // ── inner request records ─────────────────────────────────────────────────

    public record RefreshRequest(@NotBlank String refreshToken) {}
}
