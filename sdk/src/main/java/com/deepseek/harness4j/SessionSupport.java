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
}
