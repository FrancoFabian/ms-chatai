package mx.mrw.chattodolist.security;

import java.text.ParseException;
import java.time.Clock;
import java.time.Instant;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import mx.mrw.chattodolist.config.AppAuthTokenProperties;
import mx.mrw.chattodolist.exception.ApiException;

@Component
public class TokenVerifier {

    private final AppAuthTokenProperties properties;
    private final Clock clock;

    public TokenVerifier(AppAuthTokenProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    public AuthContext verify(String token) {
        if (!StringUtils.hasText(properties.getPublicKeyPem())) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AUTH_NOT_CONFIGURED", "Auth public key is not configured");
        }

        SignedJWT jwt = parse(token);
        verifySignature(jwt);

        JWTClaimsSet claimsSet = claims(jwt);
        Instant now = Instant.now(clock);
        Instant expiresAt = validateExpiration(claimsSet, now);
        String subject = claimsSet.getSubject();
        if (!StringUtils.hasText(subject)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_TOKEN", "Token subject is missing");
        }

        Set<String> scopes = extractScopes(claimsSet);
        validateAcceptedScopes(scopes);

        return new AuthContext(subject, scopes, expiresAt);
    }

    private SignedJWT parse(String token) {
        try {
            return SignedJWT.parse(token);
        }
        catch (ParseException exception) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_TOKEN", "Token format is invalid");
        }
    }

    private void verifySignature(SignedJWT jwt) {
        if (!JWSAlgorithm.EdDSA.equals(jwt.getHeader().getAlgorithm())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_TOKEN", "Token algorithm is not supported");
        }

        try {
            PublicKey publicKey = parsePublicKey(properties.getPublicKeyPem());
            Signature signature = Signature.getInstance("Ed25519");
            signature.initVerify(publicKey);
            signature.update(jwt.getSigningInput());
            boolean verified = signature.verify(jwt.getSignature().decode());
            if (!verified) {
                throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_TOKEN", "Token signature is invalid");
            }
        }
        catch (Exception exception) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_TOKEN", "Unable to verify token signature");
        }
    }

    private PublicKey parsePublicKey(String pem) throws Exception {
        String normalized = pem
            .replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "")
            .replaceAll("\\s", "");
        byte[] decoded = Base64.getDecoder().decode(normalized);
        X509EncodedKeySpec keySpec = new X509EncodedKeySpec(decoded);
        return KeyFactory.getInstance("Ed25519").generatePublic(keySpec);
    }

    private JWTClaimsSet claims(SignedJWT jwt) {
        try {
            return jwt.getJWTClaimsSet();
        }
        catch (ParseException exception) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_TOKEN", "Unable to parse token claims");
        }
    }

    private Instant validateExpiration(JWTClaimsSet claimsSet, Instant now) {
        if (claimsSet.getExpirationTime() == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_TOKEN", "Token expiration is missing");
        }

        Instant expiresAt = claimsSet.getExpirationTime().toInstant();
        Instant allowed = expiresAt.plusSeconds(properties.getClockSkewSeconds());
        if (allowed.isBefore(now)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "TOKEN_EXPIRED", "Token has expired");
        }
        return expiresAt;
    }

    private void validateAcceptedScopes(Set<String> scopes) {
        List<String> acceptedScopes = properties.getAcceptedScopes() == null
                ? List.of()
                : properties.getAcceptedScopes();
        Set<String> normalizedAccepted = acceptedScopes.stream()
            .filter(StringUtils::hasText)
            .map(this::normalizeScope)
            .collect(java.util.stream.Collectors.toSet());

        if (normalizedAccepted.isEmpty()) {
            return;
        }

        boolean anyAccepted = scopes.stream()
            .map(this::normalizeScope)
            .anyMatch(normalizedAccepted::contains);

        if (!anyAccepted) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN_SCOPE", "Token scope is not allowed");
        }
    }

    private String normalizeScope(String scope) {
        return scope.trim().toLowerCase(Locale.ROOT);
    }

    private Set<String> extractScopes(JWTClaimsSet claimsSet) {
        Set<String> scopes = new HashSet<>();

        Object scopeClaim = claimsSet.getClaim("scope");
        Object scpClaim = claimsSet.getClaim("scp");

        extractScopeValues(scopeClaim, scopes);
        extractScopeValues(scpClaim, scopes);
        return scopes;
    }

    private void extractScopeValues(Object claim, Set<String> scopes) {
        if (claim instanceof String scopeString) {
            for (String scope : scopeString.split("[\\s,]+")) {
                if (StringUtils.hasText(scope)) {
                    scopes.add(scope.trim());
                }
            }
            return;
        }

        if (claim instanceof Collection<?> values) {
            for (Object value : values) {
                if (value != null) {
                    String scope = value.toString().trim();
                    if (!scope.isEmpty()) {
                        scopes.add(scope);
                    }
                }
            }
        }
    }
}
