package com.czar.auth.dto;

public record TokenPairResponse(
        String accessToken,
        String refreshToken,
        int expiresIn,      // access token TTL in seconds
        String tokenType    // always "Bearer"
) {
    public static TokenPairResponse of(String access, String refresh, int expiryMinutes) {
        return new TokenPairResponse(access, refresh, expiryMinutes * 60, "Bearer");
    }
}
