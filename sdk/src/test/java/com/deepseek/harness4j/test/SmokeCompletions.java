package com.deepseek.harness4j.test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Test-only port of the {@code completion_chunks} / {@code text_chunks} helpers in
 * {@code scripts/smoke-python-runtime.py}, restricted to the snapshot child-prompt behavior
 * exercised by {@code test_smoke_model.py}: choose the next deterministic model response from
 * request history.
 */
public final class SmokeCompletions {

    public static final String SNAPSHOT_DIRECT_CHILD_PROMPT =
            "Reply with exactly DIRECT_CHILD_OK and nothing else.";
    public static final String SNAPSHOT_WORKFLOW_CHILD_PROMPT =
            "Reply with exactly WORKFLOW_CHILD_OK and nothing else.";

    private SmokeCompletions() {
    }

    /**
     * @param body the model request body
     * @return the deterministic streaming response chunks for the snapshot child prompts
     */
    public static List<Map<String, Object>> completionChunks(Map<String, Object> body) {
        Object messagesValue = body.get("messages");
        if (!(messagesValue instanceof List) || ((List<?>) messagesValue).isEmpty()) {
            throw new AssertionError("model request has no messages: " + body);
        }
        List<?> messages = (List<?>) messagesValue;
        Object latest = messages.get(messages.size() - 1);
        if (!(latest instanceof Map)) {
            throw new AssertionError("model request has an invalid latest message: " + body);
        }

        List<String> userPrompts = new ArrayList<>();
        for (int i = messages.size() - 1; i >= 0; i--) {
            Object message = messages.get(i);
            if (message instanceof Map messageMap
                    && "user".equals(messageMap.get("role"))) {
                userPrompts.add(messageText(messageMap.get("content")));
            }
        }
        for (String prompt : userPrompts) {
            if (SNAPSHOT_DIRECT_CHILD_PROMPT.equals(prompt)) {
                return textChunks("DIRECT_CHILD_OK");
            }
            if (SNAPSHOT_WORKFLOW_CHILD_PROMPT.equals(prompt)) {
                return textChunks("WORKFLOW_CHILD_OK");
            }
        }
        return textChunks(messageText(((Map<?, ?>) latest).get("content")));
    }

    /**
     * Build a complete streaming text response (port of {@code text_chunks}).
     */
    public static List<Map<String, Object>> textChunks(String text) {
        List<Map<String, Object>> chunks = new ArrayList<>();
        Map<String, Object> firstDelta = new LinkedHashMap<>();
        firstDelta.put("role", "assistant");
        firstDelta.put("content", null); // Python's content: None
        firstDelta.put("reasoning_content", "");
        chunks.add(Map.of("choices", List.of(Map.of("delta", firstDelta))));
        chunks.add(Map.of("choices", List.of(Map.of("delta", Map.of("content", text)))));
        chunks.add(Map.of(
                "choices", List.of(Map.of("delta", Map.of("content", ""), "finish_reason", "stop")),
                "usage", Map.of("prompt_tokens", 3, "completion_tokens", 3)));
        return chunks;
    }

    /**
     * Read OpenAI text content in either string or block-list form (port of {@code message_text}).
     */
    public static String messageText(Object content) {
        if (content instanceof String string) {
            return string;
        }
        if (content instanceof List<?> list) {
            StringBuilder builder = new StringBuilder();
            for (Object block : list) {
                if (block instanceof Map<?, ?> blockMap
                        && blockMap.get("text") instanceof String text) {
                    builder.append(text);
                }
            }
            return builder.toString();
        }
        return "";
    }
}
