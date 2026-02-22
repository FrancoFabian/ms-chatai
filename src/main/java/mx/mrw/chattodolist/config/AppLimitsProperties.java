package mx.mrw.chattodolist.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.limits")
public class AppLimitsProperties {

    private int maxUserMessageChars = 2000;
    private int maxContextChars = 4000;

    public int getMaxUserMessageChars() {
        return maxUserMessageChars;
    }

    public void setMaxUserMessageChars(int maxUserMessageChars) {
        this.maxUserMessageChars = maxUserMessageChars;
    }

    public int getMaxContextChars() {
        return maxContextChars;
    }

    public void setMaxContextChars(int maxContextChars) {
        this.maxContextChars = maxContextChars;
    }
}
