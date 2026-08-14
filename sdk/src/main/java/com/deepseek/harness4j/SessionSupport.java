package com.deepseek.harness4j;

import com.deepseek.harness4j.error.SdkProtocolException;
import com.deepseek.harness4j.model.JsonValues;
import com.deepseek.harness4j.model.Notification;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Port of the module-level helper functions in the Python {@code api.py}:
 * {@code _is_inbox_receipt}, {@code normalize_input}, {@code final_response}, and
 * {@code finish_reason}.
 */
final class SessionSupport {

    private SessionSupport() {
    }

    /**
     * @return {@code true} when the notification is the durable inbox receipt for the given
     *         session and message id (Python {@code _is_inbox_receipt}).
     */
    static boolean isInboxReceipt(Notification notification, String sessionId, String messageId) {
        if (!"session.event".equals(notification.method())
                || !sessionId.equals(notification.payload().get("sessionId"))) {
            return false;
        }
        Object event = notification.payload().get("event");
        Map<String, Object> eventMap = JsonValues.asObject(event);
        if (eventMap == null || !"agent/inbox/spliced".equals(eventMap.get("type"))) {
            return false;
        }
        Object data = eventMap.get("data");
        Map<String, Object> dataMap = JsonValues.asObject(data);
        Object inserted = dataMap == null ? null : dataMap.get("inserted");
        List<Object> insertedList = JsonValues.asArray(inserted);
        if (insertedList == null) {
            return false;
        }
        for (Object message : insertedList) {
            Map<String, Object> messageMap = JsonValues.asObject(message);
            if (messageMap != null && messageId.equals(messageMap.get("id"))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Normalize a prompt input into content blocks (Python {@code normalize_input}): a plain
     * string becomes a single {@code {"type":"text","text": input}} block; a list is passed
     * through unchanged.
     */
    static List<Map<String, Object>> normalizeInput(Object input) {
        if (input instanceof String text) {
            Map<String, Object> block = new java.util.LinkedHashMap<>();
            block.put("type", "text");
            block.put("text", text);
            return List.of(block);
        }
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blocks = (List<Map<String, Object>>) input;
        return blocks;
    }

    /**
     * Return the last committed root-session assistant text in the interval (Python
     * {@code final_response}).
     */
    static String finalResponse(List<Map<String, Object>> events) {
        for (int i = events.size() - 1; i >= 0; i--) {
            Map<String, Object> event = events.get(i);
            if (!"assistant/message".equals(event.get("type"))) {
                continue;
            }
            Map<String, Object> data = JsonValues.asObject(event.get("data"));
            if (data == null) {
                continue;
            }
            Object message = data.get("message");
            Map<String, Object> contentOwner = JsonValues.asObject(message);
            if (contentOwner == null) {
                contentOwner = data;
            }
            Object content = contentOwner.get("content");
            List<Object> contentList = JsonValues.asArray(content);
            if (contentList == null) {
                continue;
            }
            StringBuilder parts = new StringBuilder();
            for (Object block : contentList) {
                Map<String, Object> blockMap = JsonValues.asObject(block);
                if (blockMap != null && "text".equals(blockMap.get("type"))) {
                    Object text = blockMap.get("text");
                    parts.append(text == null ? "" : String.valueOf(text));
                }
            }
            return parts.toString();
        }
        return "";
    }

    /**
     * Return the last turn-ending kind.
     *
     * <p>The input must contain root-session events from one owned run interval.
     *
     * @throws SdkProtocolException when the last {@code turn/end} has no string reason kind
     */
    static String finishReason(List<Map<String, Object>> events) {
        for (int i = events.size() - 1; i >= 0; i--) {
            Map<String, Object> event = events.get(i);
            if (!"turn/end".equals(event.get("type"))) {
                continue;
            }
            Map<String, Object> data = JsonValues.asObject(event.get("data"));
            Object reason = data == null ? null : data.get("reason");
            Map<String, Object> reasonMap = JsonValues.asObject(reason);
            Object kind = reasonMap == null ? null : reasonMap.get("kind");
            if (!(kind instanceof String)) {
                throw new SdkProtocolException(
                        "turn/end event requires a string data.reason.kind");
            }
            return (String) kind;
        }
        return null;
    }

    /**
     * Concatenate the CoT reasoning chain: every {@code reasoning-delta} chunk (both the
     * live {@code assistant/chunk} shape and the packed {@code reasoning-chunks} storage
     * row), in wire order.
     */
    static String extractReasoningContent(List<Map<String, Object>> events) {
        StringBuilder out = new StringBuilder();
        for (Map<String, Object> event : events) {
            String type = String.valueOf(event.get("type"));
            Map<String, Object> data = JsonValues.asObject(event.get("data"));
            if ("assistant/chunk".equals(type)) {
                Map<String, Object> chunk = JsonValues.asObject(data.get("chunk"));
                if (chunk != null && "reasoning-delta".equals(chunk.get("type"))) {
                    Object text = chunk.get("text");
                    out.append(text == null ? "" : String.valueOf(text));
                }
            } else if ("reasoning-chunks".equals(type)) {
                List<Object> texts = JsonValues.asArray(data.get("texts"));
                if (texts != null) {
                    for (Object text : texts) {
                        out.append(text == null ? "" : String.valueOf(text));
                    }
                }
            }
        }
        return out.toString();
    }

    /**
     * Aggregate token accounting over every {@code assistant/message} that carried a
     * {@code usage} object (upstream {@code TokenUsage}: inputTokens / outputTokens /
     * cacheReadTokens / cacheWriteTokens / reasoningTokens).
     */
    static RunResult.TokenUsage extractTokenUsage(List<Map<String, Object>> events) {
        long prompt = 0;
        long completion = 0;
        long reasoning = 0;
        long cacheRead = 0;
        long cacheWrite = 0;
        boolean found = false;
        for (Map<String, Object> event : events) {
            if (!"assistant/message".equals(event.get("type"))) {
                continue;
            }
            Map<String, Object> data = JsonValues.asObject(event.get("data"));
            Map<String, Object> usage = JsonValues.asObject(data.get("usage"));
            if (usage == null) {
                continue;
            }
            found = true;
            prompt += asLong(usage.get("inputTokens"));
            completion += asLong(usage.get("outputTokens"));
            reasoning += asLong(usage.get("reasoningTokens"));
            cacheRead += asLong(usage.get("cacheReadTokens"));
            cacheWrite += asLong(usage.get("cacheWriteTokens"));
        }
        if (!found) {
            return RunResult.TokenUsage.EMPTY;
        }
        long total = prompt + completion + cacheRead + cacheWrite;
        return new RunResult.TokenUsage(
                (int) prompt, (int) completion, (int) reasoning,
                (int) cacheRead, (int) cacheWrite, (int) total);
    }

    /**
     * Build the structured tool-call tree: each {@code tool/call} paired with its matching
     * {@code tool/result} by {@code callId}. An unmatched call yields a record with a
     * {@code null} result. {@code durationMs} is {@code result.time - call.time}, or 0 when
     * either event carries no timestamp (the live wire event stream omits {@code time}).
     */
    static List<RunResult.ToolCallRecord> extractToolCalls(List<Map<String, Object>> events) {
        Map<String, Map<String, Object>> calls = new java.util.LinkedHashMap<>();
        Map<String, Long> callTimes = new java.util.LinkedHashMap<>();
        List<Map<String, Object>> results = new java.util.ArrayList<>();
        for (Map<String, Object> event : events) {
            String type = String.valueOf(event.get("type"));
            Map<String, Object> data = JsonValues.asObject(event.get("data"));
            if ("tool/call".equals(type)) {
                String callId = String.valueOf(data.get("callId"));
                calls.put(callId, event);
                callTimes.put(callId, longValue(event.get("time")));
            } else if ("tool/result".equals(type)) {
                results.add(event);
            }
        }
        List<RunResult.ToolCallRecord> records = new java.util.ArrayList<>();
        Map<String, Boolean> consumed = new java.util.LinkedHashMap<>();
        for (Map.Entry<String, Map<String, Object>> entry : calls.entrySet()) {
            String callId = entry.getKey();
            Map<String, Object> call = entry.getValue();
            Map<String, Object> callData = JsonValues.asObject(call.get("data"));
            Map<String, Object> result = null;
            for (Map<String, Object> candidate : results) {
                Map<String, Object> candidateData = JsonValues.asObject(candidate.get("data"));
                if (callId.equals(candidateData.get("callId"))
                        && !Boolean.TRUE.equals(consumed.get(callId))) {
                    result = candidate;
                    consumed.put(callId, Boolean.TRUE);
                    break;
                }
            }
            long duration = 0;
            boolean isError = false;
            Object resultValue = null;
            if (result != null) {
                Map<String, Object> resultData = JsonValues.asObject(result.get("data"));
                resultValue = resultData.get("message");
                isError = resultData.get("error") != null;
                Long callTime = callTimes.get(callId);
                Long resultTime = longValue(result.get("time"));
                if (callTime != null && resultTime != null) {
                    duration = Math.max(0, resultTime - callTime);
                }
            }
            records.add(new RunResult.ToolCallRecord(
                    callId,
                    String.valueOf(callData.get("name")),
                    callData.get("arguments") instanceof String s ? s : String.valueOf(callData.get("arguments")),
                    resultValue,
                    isError,
                    duration));
        }
        return records;
    }

    /**
     * Map an incoming runtime notification into logical streaming chunks.
     */
    static List<com.deepseek.harness4j.model.StreamChunk> extractStreamChunks(
            Notification notification, String sessionId) {
        if (notification == null) {
            return List.of();
        }
        List<com.deepseek.harness4j.model.StreamChunk> chunks = new ArrayList<>();
        if ("session.event".equals(notification.method())) {
            Object eventObj = notification.payload().get("event");
            Map<String, Object> event = JsonValues.asObject(eventObj);
            if (event != null) {
                String type = String.valueOf(event.get("type"));
                Map<String, Object> data = JsonValues.asObject(event.get("data"));
                if ("assistant/chunk".equals(type) && data != null) {
                    Map<String, Object> chunk = JsonValues.asObject(data.get("chunk"));
                    if (chunk != null) {
                        String chunkType = String.valueOf(chunk.get("type"));
                        String text = chunk.get("text") != null ? String.valueOf(chunk.get("text")) : "";
                        if ("reasoning-delta".equals(chunkType)) {
                            chunks.add(new com.deepseek.harness4j.model.StreamChunk(
                                    com.deepseek.harness4j.model.StreamChunk.TYPE_REASONING, text, event, notification));
                        } else if ("text-delta".equals(chunkType)) {
                            chunks.add(new com.deepseek.harness4j.model.StreamChunk(
                                    com.deepseek.harness4j.model.StreamChunk.TYPE_CONTENT, text, event, notification));
                        } else if ("tool-call-delta".equals(chunkType)) {
                            chunks.add(new com.deepseek.harness4j.model.StreamChunk(
                                    com.deepseek.harness4j.model.StreamChunk.TYPE_TOOL_CALL, text, event, notification));
                        }
                    }
                } else if ("reasoning-chunks".equals(type) && data != null) {
                    List<Object> texts = JsonValues.asArray(data.get("texts"));
                    if (texts != null) {
                        for (Object t : texts) {
                            chunks.add(new com.deepseek.harness4j.model.StreamChunk(
                                    com.deepseek.harness4j.model.StreamChunk.TYPE_REASONING, String.valueOf(t), event, notification));
                        }
                    }
                } else if ("text-chunks".equals(type) && data != null) {
                    List<Object> texts = JsonValues.asArray(data.get("texts"));
                    if (texts != null) {
                        for (Object t : texts) {
                            chunks.add(new com.deepseek.harness4j.model.StreamChunk(
                                    com.deepseek.harness4j.model.StreamChunk.TYPE_CONTENT, String.valueOf(t), event, notification));
                        }
                    }
                } else if ("tool/call".equals(type) && data != null) {
                    String toolName = String.valueOf(data.get("name"));
                    String args = data.get("arguments") != null ? String.valueOf(data.get("arguments")) : "";
                    chunks.add(new com.deepseek.harness4j.model.StreamChunk(
                            com.deepseek.harness4j.model.StreamChunk.TYPE_TOOL_CALL, toolName + ": " + args, event, notification));
                } else if ("tool/result".equals(type) && data != null) {
                    String msg = data.get("message") != null ? String.valueOf(data.get("message")) : "";
                    chunks.add(new com.deepseek.harness4j.model.StreamChunk(
                            com.deepseek.harness4j.model.StreamChunk.TYPE_TOOL_RESULT, msg, event, notification));
                } else if ("turn/end".equals(type) && data != null) {
                    Map<String, Object> reason = JsonValues.asObject(data.get("reason"));
                    String kind = reason != null ? String.valueOf(reason.get("kind")) : "";
                    chunks.add(new com.deepseek.harness4j.model.StreamChunk(
                            com.deepseek.harness4j.model.StreamChunk.TYPE_TURN_END, kind, event, notification));
                } else {
                    chunks.add(new com.deepseek.harness4j.model.StreamChunk(
                            com.deepseek.harness4j.model.StreamChunk.TYPE_RAW, "", event, notification));
                }
            }
        } else if ("session.status".equals(notification.method())) {
            String status = String.valueOf(notification.payload().get("status"));
            chunks.add(new com.deepseek.harness4j.model.StreamChunk(
                    com.deepseek.harness4j.model.StreamChunk.TYPE_STATUS, status, null, notification));
        }
        return chunks;
    }

    private static long asLong(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private static Long longValue(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }
}
