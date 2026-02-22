package mx.mrw.chattodolist.security;

import java.time.Instant;
import java.util.Set;

public record AuthContext(
        String subject,
        Set<String> scopes,
        Instant expiresAt) {
}
