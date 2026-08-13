package com.deepseek.harness4j.model;

import java.util.List;
import java.util.Map;

/**
 * Helpers for the JSON value model used across the SDK.
 *
 * <p>Python defines these type aliases in {@code models.py}:
 * <pre>{@code
 * JsonScalar: TypeAlias = str | int | float | bool | None
 * JsonValue:  TypeAlias = JsonScalar | dict[str, JsonValue] | list[JsonValue]
 * JsonObject: TypeAlias = dict[str, JsonValue]
 * }</pre>
 *
 * <p>Java has no structural union types, so a JSON value is represented as
 * {@link Object} restricted to: {@link String}, {@link Integer}/{@link Long},
 * {@link Double}, {@link Boolean}, {@code null}, {@code Map<String, Object>},
 * and {@code List<Object>} — exactly the shapes Jackson produces by default.
 * A JSON object is {@code Map<String, Object>} and a JSON array is
 * {@code List<Object>}. This class provides the small typed accessors used
 * by the client so the rest of the port reads naturally.
 */
public final class JsonValues {

    private JsonValues() {
    }

    /**
     * @return {@code true} when the value is a JSON scalar: a string, an
     *         integral or floating number, a boolean, or {@code null}.
     */
    public static boolean isScalar(Object value) {
        return value == null
                || value instanceof String
                || value instanceof Number
                || value instanceof Boolean;
    }

    /**
     * @return {@code true} when the value is a JSON object (a map of values).
     */
    public static boolean isObject(Object value) {
        return value instanceof Map;
    }

    /**
     * @return {@code true} when the value is a JSON array (a list of values).
     */
    public static boolean isArray(Object value) {
        return value instanceof List;
    }

    /**
     * @return the value cast to a JSON object, or {@code null} when it is not.
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> asObject(Object value) {
        return value instanceof Map ? (Map<String, Object>) value : null;
    }

    /**
     * @return the value cast to a JSON array, or {@code null} when it is not.
     */
    @SuppressWarnings("unchecked")
    public static List<Object> asArray(Object value) {
        return value instanceof List ? (List<Object>) value : null;
    }

    /**
     * @return {@code true} when the value is an integral JSON number
     *         (the port of Python's {@code isinstance(value, int)} check).
     */
    public static boolean isIntegral(Object value) {
        return value instanceof Integer || value instanceof Long;
    }

    /**
     * @return the value as an integral JSON number, or {@code null} when it is not.
     */
    public static Integer asIntOrNull(Object value) {
        if (value instanceof Integer integer) {
            return integer;
        }
        if (value instanceof Long longValue) {
            return longValue.intValue();
        }
        return null;
    }

    /**
     * @return the value as a string, or {@code null} when it is not a string.
     */
    public static String asStringOrNull(Object value) {
        return value instanceof String string ? string : null;
    }

    /**
     * @return the value as a boolean, or {@code false} when it is not a boolean.
     */
    public static boolean asBoolean(Object value) {
        return value instanceof Boolean booleanValue && booleanValue;
    }
}
