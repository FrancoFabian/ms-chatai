package mx.mrw.chattodolist.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import mx.mrw.chattodolist.config.AppAuthServiceTokenProperties;
import mx.mrw.chattodolist.config.AppAuthTokenProperties;
import mx.mrw.chattodolist.exception.ApiException;
import mx.mrw.chattodolist.rate.InMemoryRateLimiter;
import mx.mrw.chattodolist.support.RequestContext;

@Component
public class FeedbackAuthInterceptor implements HandlerInterceptor {

    private static final String AUTH_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final AppAuthTokenProperties authTokenProperties;
    private final AppAuthServiceTokenProperties serviceTokenProperties;
    private final TokenVerifier tokenVerifier;
    private final InMemoryRateLimiter rateLimiter;

    public FeedbackAuthInterceptor(
            AppAuthTokenProperties authTokenProperties,
            AppAuthServiceTokenProperties serviceTokenProperties,
            TokenVerifier tokenVerifier,
            InMemoryRateLimiter rateLimiter) {
        this.authTokenProperties = authTokenProperties;
        this.serviceTokenProperties = serviceTokenProperties;
        this.tokenVerifier = tokenVerifier;
        this.rateLimiter = rateLimiter;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }

        AuthContext authContext = authenticate(request);
        request.setAttribute(RequestContext.AUTH_CONTEXT, authContext);
        rateLimiter.assertAllowed(authContext.subject());
        return true;
    }

    private AuthContext authenticate(HttpServletRequest request) {
        if (!authTokenProperties.isRequired()) {
            return new AuthContext("anonymous", java.util.Set.of(), null);
        }

        String authorization = request.getHeader(AUTH_HEADER);
        if (!StringUtils.hasText(authorization) || !authorization.startsWith(BEARER_PREFIX)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Bearer token is required");
        }

        String token = authorization.substring(BEARER_PREFIX.length()).trim();
        if (!StringUtils.hasText(token)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Bearer token is required");
        }

        if (isServiceToken(token)) {
            String subject = StringUtils.hasText(serviceTokenProperties.getSubject())
                    ? serviceTokenProperties.getSubject().trim()
                    : "service-client";
            return new AuthContext(subject, java.util.Set.of("feedback_chat"), null);
        }

        return tokenVerifier.verify(token);
    }

    private boolean isServiceToken(String candidate) {
        String configured = serviceTokenProperties.getValue();
        if (!StringUtils.hasText(configured) || !StringUtils.hasText(candidate)) {
            return false;
        }
        return MessageDigest.isEqual(
                candidate.trim().getBytes(StandardCharsets.UTF_8),
                configured.trim().getBytes(StandardCharsets.UTF_8));
    }
}
