package mx.mrw.chattodolist.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import mx.mrw.chattodolist.config.AppAuthServiceTokenProperties;
import mx.mrw.chattodolist.config.AppAuthTokenProperties;
import mx.mrw.chattodolist.config.AppRateLimitProperties;
import mx.mrw.chattodolist.rate.InMemoryRateLimiter;
import mx.mrw.chattodolist.support.RequestContext;

class FeedbackAuthInterceptorTest {

    @Test
    void preHandleShouldAcceptConfiguredServiceToken() throws Exception {
        FeedbackAuthInterceptor interceptor = new FeedbackAuthInterceptor(
                tokenProperties(),
                serviceTokenProperties("shared-service-token", "isp-edge-clients"),
                mock(TokenVerifier.class),
                rateLimiterDisabled());

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/feedback-chat");
        request.addHeader("Authorization", "Bearer shared-service-token");

        boolean handled = interceptor.preHandle(request, new MockHttpServletResponse(), new Object());

        assertTrue(handled);
        AuthContext authContext = (AuthContext) request.getAttribute(RequestContext.AUTH_CONTEXT);
        assertEquals("isp-edge-clients", authContext.subject());
        assertEquals(Set.of("feedback_chat"), authContext.scopes());
    }

    @Test
    void preHandleShouldFallbackToJwtVerificationWhenServiceTokenDoesNotMatch() throws Exception {
        TokenVerifier tokenVerifier = mock(TokenVerifier.class);
        when(tokenVerifier.verify("jwt-like-token"))
                .thenReturn(new AuthContext("user-123", Set.of("feedback_chat"), Instant.parse("2026-03-19T00:00:00Z")));

        FeedbackAuthInterceptor interceptor = new FeedbackAuthInterceptor(
                tokenProperties(),
                serviceTokenProperties("shared-service-token", "isp-edge-clients"),
                tokenVerifier,
                rateLimiterDisabled());

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/feedback-chat");
        request.addHeader("Authorization", "Bearer jwt-like-token");

        boolean handled = interceptor.preHandle(request, new MockHttpServletResponse(), new Object());

        assertTrue(handled);
        AuthContext authContext = (AuthContext) request.getAttribute(RequestContext.AUTH_CONTEXT);
        assertEquals("user-123", authContext.subject());
        verify(tokenVerifier).verify("jwt-like-token");
    }

    private AppAuthTokenProperties tokenProperties() {
        AppAuthTokenProperties properties = new AppAuthTokenProperties();
        properties.setRequired(true);
        return properties;
    }

    private AppAuthServiceTokenProperties serviceTokenProperties(String value, String subject) {
        AppAuthServiceTokenProperties properties = new AppAuthServiceTokenProperties();
        properties.setValue(value);
        properties.setSubject(subject);
        return properties;
    }

    private InMemoryRateLimiter rateLimiterDisabled() {
        AppRateLimitProperties properties = new AppRateLimitProperties();
        properties.setEnabled(false);
        return new InMemoryRateLimiter(properties, Clock.fixed(Instant.parse("2026-03-18T00:00:00Z"), ZoneOffset.UTC));
    }
}
