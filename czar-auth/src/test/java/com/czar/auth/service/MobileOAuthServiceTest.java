package com.czar.auth.service;

import com.czar.auth.dto.MobileOAuthRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.*;

/**
 * Fix 2 — MobileOAuthService unit tests.
 *
 * Validates the redirect URI gate (validateRedirectUri) which is called before
 * any network I/O. The happy-path (full token exchange) is covered at the
 * integration level via MobileOAuthControllerTest.
 */
@ExtendWith(MockitoExtension.class)
class MobileOAuthServiceTest {

    @Mock
    private OAuthUserService oauthUserService;

    private MobileOAuthService service;

    @BeforeEach
    void setUp() {
        service = new MobileOAuthService(oauthUserService);
        // Inject dummy OAuth2 credentials so @Value fields are non-null
        ReflectionTestUtils.setField(service, "googleClientId",     "test-google-id");
        ReflectionTestUtils.setField(service, "googleClientSecret", "test-google-secret");
        ReflectionTestUtils.setField(service, "githubClientId",     "test-github-id");
        ReflectionTestUtils.setField(service, "githubClientSecret", "test-github-secret");
    }

    @Test
    void googleMobileLogin_wrongRedirectUri_throwsIllegalArgumentException() {
        MobileOAuthRequest request = new MobileOAuthRequest("code123", "https://evil.com/callback");

        assertThatThrownBy(() -> service.googleMobileLogin(request, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("czar://auth/callback");
    }

    @Test
    void githubMobileLogin_wrongRedirectUri_throwsIllegalArgumentException() {
        MobileOAuthRequest request = new MobileOAuthRequest("code123", "http://localhost/callback");

        assertThatThrownBy(() -> service.githubMobileLogin(request, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("czar://auth/callback");
    }

    @Test
    void googleMobileLogin_nullRedirectUri_throwsIllegalArgumentException() {
        MobileOAuthRequest request = new MobileOAuthRequest("code123", null);

        assertThatThrownBy(() -> service.googleMobileLogin(request, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void googleMobileLogin_correctRedirectUri_proceedsToTokenExchange() {
        // Correct redirect URI passes validation — then calls RestTemplate (which fails in unit test).
        // We verify it gets past the validation gate and throws a different error.
        MobileOAuthRequest request = new MobileOAuthRequest("code123", "czar://auth/callback");

        // Throws IllegalStateException or RuntimeException from the RestTemplate HTTP call,
        // NOT IllegalArgumentException (which would mean redirect URI validation failed).
        assertThatThrownBy(() -> service.googleMobileLogin(request, null))
                .isNotInstanceOf(IllegalArgumentException.class);
    }
}
