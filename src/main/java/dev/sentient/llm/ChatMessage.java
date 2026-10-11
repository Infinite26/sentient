package dev.sentient.llm;

public class ChatMessage {

    public static final String SYSTEM = "system";
    public static final String USER = "user";
    public static final String ASSISTANT = "assistant";

    public final String role;
    public final String content;

    public ChatMessage(String role, String content) {
        this.role = role;
        this.content = content;
    }

    public static ChatMessage system(String content) {
        return new ChatMessage(SYSTEM, content);
    }

    public static ChatMessage user(String content) {
        return new ChatMessage(USER, content);
    }

    public static ChatMessage assistant(String content) {
        return new ChatMessage(ASSISTANT, content);
    }
}
