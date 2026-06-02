package com.czar.auth.service;

import com.czar.auth.dto.TokenPairResponse;
import com.czar.auth.entity.OtpRequest;
import com.czar.auth.entity.UserAuth;
import com.czar.auth.repository.OtpRequestRepository;
import com.czar.auth.repository.UserAuthRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;

@Service
public class OtpService {

    private static final int OTP_EXPIRY_MINUTES = 10;
    private static final int MAX_OTP_PER_IP_PER_15MIN = 5;
    private static final int MAX_OTP_PER_IDENTIFIER_PER_HOUR = 5;

    private final OtpRequestRepository otpRepo;
    private final UserAuthRepository userRepo;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final JwtService jwtService;
    private final UserEventPublisher eventPublisher;
    private final SecureRandom random = new SecureRandom();

    public OtpService(OtpRequestRepository otpRepo,
                      UserAuthRepository userRepo,
                      PasswordEncoder passwordEncoder,
                      EmailService emailService,
                      JwtService jwtService,
                      UserEventPublisher eventPublisher) {
        this.otpRepo = otpRepo;
        this.userRepo = userRepo;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.jwtService = jwtService;
        this.eventPublisher = eventPublisher;
    }

    // ── Request OTP ───────────────────────────────────────────────────────────

    @Transactional
    public void requestEmailOtp(String email, String ipAddress) {
        enforceRateLimit(email, ipAddress);

        String otp = generateOtp();
        String hash = passwordEncoder.encode(otp);
        otpRepo.save(OtpRequest.create(email, hash, OTP_EXPIRY_MINUTES, ipAddress));
        emailService.sendOtp(email, otp);
    }

    // ── Verify OTP ────────────────────────────────────────────────────────────

    @Transactional
    public TokenPairResponse verifyEmailOtp(String email, String otp, String deviceHint) {
        var candidates = otpRepo.findByIdentifierAndUsedFalseOrderByCreatedAtDesc(email);

        OtpRequest valid = candidates.stream()
                .filter(r -> !r.isExpired())
                .filter(r -> passwordEncoder.matches(otp, r.getOtpHash()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired OTP"));

        // Mark this and all previous unused OTPs for this identifier as used
        otpRepo.markAllUsedForIdentifier(email);

        boolean isNewUser = !userRepo.existsByEmail(email);
        UserAuth user = userRepo.findByEmail(email)
                .orElseGet(() -> {
                    UserAuth u = UserAuth.ofEmail(email);
                    return userRepo.save(u);
                });

        user.setLastLoginAt(Instant.now());
        userRepo.save(user);

        if (isNewUser) {
            eventPublisher.publishUserCreated(user);
        }

        return jwtService.issueTokenPair(user, deviceHint);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String generateOtp() {
        int code = 100000 + random.nextInt(900000);
        return String.valueOf(code);
    }

    private void enforceRateLimit(String identifier, String ipAddress) {
        Instant window15min = Instant.now().minusSeconds(15 * 60);
        long byIp = otpRepo.countByIpAddressAndCreatedAtAfter(ipAddress, window15min);
        if (byIp >= MAX_OTP_PER_IP_PER_15MIN) {
            throw new IllegalArgumentException("Too many OTP requests from this IP. Please wait.");
        }

        Instant window1hour = Instant.now().minusSeconds(60 * 60);
        long byIdentifier = otpRepo.countByIdentifierAndCreatedAtAfter(identifier, window1hour);
        if (byIdentifier >= MAX_OTP_PER_IDENTIFIER_PER_HOUR) {
            throw new IllegalArgumentException("Too many OTP requests for this address. Please wait.");
        }
    }
}
