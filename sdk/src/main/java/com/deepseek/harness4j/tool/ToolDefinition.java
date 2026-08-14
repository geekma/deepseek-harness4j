package com.deepseek.harness4j.tool;

import java.util.List;
import java.util.Map;

/**
 * Descriptor of a registered Java tool.
 */
public record ToolDefinition(
        String name,
        String description,
        Map<String, Object> parametersSchema,
        List<ParameterDefinition> parameters) {

    public record ParameterDefinition(
            String name,
            Class<?> type,
            String description,
            boolean required) {}
}
