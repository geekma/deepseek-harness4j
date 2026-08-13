package com.deepseek.harness4j.build;

import com.deepseek.harness4j.test.SmokeCompletions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Port of {@code python/sdk/tests/test_smoke_model.py}: the deterministic model mock must answer
 * the snapshot child prompt before any runtime context injected later.
 */
class SmokeCompletionsTest {

    @ParameterizedTest
    @CsvSource({
            "SNAPSHOT_DIRECT_CHILD_PROMPT, DIRECT_CHILD_OK",
            "SNAPSHOT_WORKFLOW_CHILD_PROMPT, WORKFLOW_CHILD_OK",
    })
    void test_child_prompt_precedes_runtime_context(String promptName, String expected) {
        String prompt = promptFor(promptName);
        List<Map<String, Object>> chunks = SmokeCompletions.completionChunks(Map.of(
                "messages", List.of(
                        Map.of("role", "user", "content", prompt),
                        Map.of("role", "user", "content", "Current runtime context"))));

        boolean matched = false;
        for (Map<String, Object> chunk : chunks) {
            Object choicesValue = chunk.get("choices");
            if (!(choicesValue instanceof List)) {
                continue;
            }
            for (Object choice : (List<?>) choicesValue) {
                if (!(choice instanceof Map)) {
                    continue;
                }
                Object delta = ((Map<?, ?>) choice).get("delta");
                if (delta instanceof Map && expected.equals(((Map<?, ?>) delta).get("content"))) {
                    matched = true;
                }
            }
        }
        assertTrue(matched, "chunks must contain delta content " + expected);
    }

    private static String promptFor(String name) {
        if ("SNAPSHOT_DIRECT_CHILD_PROMPT".equals(name)) {
            return SmokeCompletions.SNAPSHOT_DIRECT_CHILD_PROMPT;
        }
        return SmokeCompletions.SNAPSHOT_WORKFLOW_CHILD_PROMPT;
    }
}
