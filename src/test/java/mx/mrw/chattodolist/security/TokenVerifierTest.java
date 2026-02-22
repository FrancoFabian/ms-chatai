package mx.mrw.chattodolist.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.fasterxml.jackson.databind.ObjectMapper;

import mx.mrw.chattodolist.config.AppAuthTokenProperties;
import mx.mrw.chattodolist.exception.ApiException;

class TokenVerifierTest {

    private final Clock fixedClock = Clock.fixed(Instant.parse("2026-02-21T00:00:00Z"), ZoneOffset.UTC);
    private AppAuthTokenProperties properties;

    @BeforeEach
    void setUp() {
        properties = new AppAuthTokenProperties();
        properties.setAcceptedScopes(List.of("feedback_chat"));
        properties.setClockSkewSeconds(60);
    }

    @Test
    void verifyShouldRejectInvalidSignature() throws Exception {
        KeyPair signerKey = generateEd25519KeyPair();
        KeyPair verifierKey = generateEd25519KeyPair();
        properties.setPublicKeyPem(toPem("PUBLIC KEY", verifierKey.getPublic().getEncoded()));

        TokenVerifier tokenVerifier = new TokenVerifier(properties, fixedClock);
        String token = signToken(signerKey, Instant.now(fixedClock).plusSeconds(300), "user-1", "feedback_chat");

        ApiException exception = assertThrows(ApiException.class, () -> tokenVerifier.verify(token));
        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatus());
        assertEquals("INVALID_TOKEN", exception.getErrorCode());
    }

    @Test
    void verifyShouldRejectExpiredToken() throws Exception {
        KeyPair key = generateEd25519KeyPair();
        properties.setPublicKeyPem(toPem("PUBLIC KEY", key.getPublic().getEncoded()));

        TokenVerifier tokenVerifier = new TokenVerifier(properties, fixedClock);
        String token = signToken(key, Instant.now(fixedClock).minusSeconds(120), "user-1", "feedback_chat");

        ApiException exception = assertThrows(ApiException.class, () -> tokenVerifier.verify(token));
        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatus());
        assertEquals("TOKEN_EXPIRED", exception.getErrorCode());
    }

    @Test
    void verifyShouldRejectTokenWithoutRequiredScope() throws Exception {
        KeyPair key = generateEd25519KeyPair();
        properties.setPublicKeyPem(toPem("PUBLIC KEY", key.getPublic().getEncoded()));

        TokenVerifier tokenVerifier = new TokenVerifier(properties, fixedClock);
        String token = signToken(key, Instant.now(fixedClock).plusSeconds(300), "user-1", "other_scope");

        ApiException exception = assertThrows(ApiException.class, () -> tokenVerifier.verify(token));
        assertEquals(HttpStatus.FORBIDDEN, exception.getStatus());
        assertEquals("FORBIDDEN_SCOPE", exception.getErrorCode());
    }

    private String signToken(KeyPair key, Instant expirationTime, String subject, String scope) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        String headerJson = mapper.writeValueAsString(Map.of("alg", "EdDSA", "typ", "JWT"));
        String payloadJson = mapper.writeValueAsString(Map.of(
                "sub", subject,
                "exp", expirationTime.getEpochSecond(),
                "scope", scope));

        String header = base64UrlEncode(headerJson.getBytes(StandardCharsets.UTF_8));
        String payload = base64UrlEncode(payloadJson.getBytes(StandardCharsets.UTF_8));
        String signingInput = header + "." + payload;

        Signature signature = Signature.getInstance("Ed25519");
        signature.initSign(key.getPrivate());
        signature.update(signingInput.getBytes(StandardCharsets.UTF_8));
        byte[] signatureBytes = signature.sign();

        return signingInput + "." + base64UrlEncode(signatureBytes);
    }

    private KeyPair generateEd25519KeyPair() throws Exception {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("Ed25519");
        return keyPairGenerator.generateKeyPair();
    }

    private String toPem(String type, byte[] encodedKey) {
        String base64 = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.UTF_8))
            .encodeToString(encodedKey);
        return "-----BEGIN " + type + "-----\n" + base64 + "\n-----END " + type + "-----";
    }

    private String base64UrlEncode(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
