package mx.mrw.chattodolist.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.auth.token")
public class AppAuthTokenProperties {

    private boolean required = true;
    private String publicKeyPem = "";
    private List<String> acceptedScopes = List.of("feedback_chat");
    private long clockSkewSeconds = 60;

    public boolean isRequired() {
        return required;
    }

    public void setRequired(boolean required) {
        this.required = required;
    }

    public String getPublicKeyPem() {
        return publicKeyPem;
    }

    public void setPublicKeyPem(String publicKeyPem) {
        this.publicKeyPem = publicKeyPem;
    }

    public List<String> getAcceptedScopes() {
        return acceptedScopes;
    }

    public void setAcceptedScopes(List<String> acceptedScopes) {
        this.acceptedScopes = acceptedScopes;
    }

    public long getClockSkewSeconds() {
        return clockSkewSeconds;
    }

    public void setClockSkewSeconds(long clockSkewSeconds) {
        this.clockSkewSeconds = clockSkewSeconds;
    }
}
