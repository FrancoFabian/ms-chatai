package mx.mrw.chattodolist.service;

public enum AiFlow {
    FEEDBACK_CHAT("feedback-chat", "/api/feedback-chat", "feedback-v2"),
    IMPROVE_MESSAGE("improve-message", "/api/feedback-chat/improve-message", "improve-v2");

    private final String flowName;
    private final String endpoint;
    private final String promptVersion;

    AiFlow(String flowName, String endpoint, String promptVersion) {
        this.flowName = flowName;
        this.endpoint = endpoint;
        this.promptVersion = promptVersion;
    }

    public String flowName() {
        return flowName;
    }

    public String endpoint() {
        return endpoint;
    }

    public String promptVersion() {
        return promptVersion;
    }
}
