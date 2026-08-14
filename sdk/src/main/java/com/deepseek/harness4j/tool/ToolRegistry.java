package com.deepseek.harness4j.tool;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for discovering, inspecting, and executing Java methods exposed as Agent tools.
 */
public final class ToolRegistry {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final Map<String, ToolEntry> tools = new ConcurrentHashMap<>();

    private record ToolEntry(
            ToolDefinition definition,
            Object target,
            Method method,
            List<ParameterEntry> parameters) {}

    private record ParameterEntry(
            String name,
            Class<?> type,
            boolean required) {}

    /**
     * Scan the given object for {@link HarnessTool} annotated methods and register them.
     */
    public ToolRegistry register(Object target) {
        if (target == null) {
            return this;
        }
        for (Method method : target.getClass().getMethods()) {
            HarnessTool annotation = method.getAnnotation(HarnessTool.class);
            if (annotation == null) {
                continue;
            }
            String toolName = annotation.name().isEmpty() ? method.getName() : annotation.name();
            String description = annotation.description();

            List<ToolDefinition.ParameterDefinition> paramDefs = new ArrayList<>();
            List<ParameterEntry> paramEntries = new ArrayList<>();
            Map<String, Object> propertiesSchema = new LinkedHashMap<>();
            List<String> requiredList = new ArrayList<>();

            for (Parameter param : method.getParameters()) {
                Param paramAnn = param.getAnnotation(Param.class);
                String paramName;
                String paramDesc = "";
                boolean required = true;

                if (paramAnn != null) {
                    paramName = !paramAnn.value().isEmpty()
                            ? paramAnn.value()
                            : (!paramAnn.name().isEmpty() ? paramAnn.name() : param.getName());
                    paramDesc = paramAnn.description();
                    required = paramAnn.required();
                } else {
                    paramName = param.getName();
                }

                paramDefs.add(new ToolDefinition.ParameterDefinition(paramName, param.getType(), paramDesc, required));
                paramEntries.add(new ParameterEntry(paramName, param.getType(), required));

                Map<String, Object> prop = new LinkedHashMap<>();
                prop.put("type", toJsonSchemaType(param.getType()));
                if (!paramDesc.isEmpty()) {
                    prop.put("description", paramDesc);
                }
                propertiesSchema.put(paramName, prop);
                if (required) {
                    requiredList.add(paramName);
                }
            }

            Map<String, Object> schema = new LinkedHashMap<>();
            schema.put("type", "object");
            schema.put("properties", propertiesSchema);
            if (!requiredList.isEmpty()) {
                schema.put("required", requiredList);
            }

            ToolDefinition def = new ToolDefinition(toolName, description, schema, paramDefs);
            tools.put(toolName, new ToolEntry(def, target, method, paramEntries));
        }
        return this;
    }

    /**
     * Execute a registered tool by name with the given argument map.
     */
    public Object execute(String toolName, Map<String, Object> arguments) throws Exception {
        ToolEntry entry = tools.get(toolName);
        if (entry == null) {
            throw new IllegalArgumentException("Unknown tool: " + toolName);
        }
        Map<String, Object> args = arguments == null ? Collections.emptyMap() : arguments;
        Object[] invokeArgs = new Object[entry.parameters.size()];

        for (int i = 0; i < entry.parameters.size(); i++) {
            ParameterEntry p = entry.parameters.get(i);
            Object raw = args.get(p.name);
            if (raw == null && p.required) {
                // If named lookup failed, fallback to case-insensitive or single arg check
                for (Map.Entry<String, Object> e : args.entrySet()) {
                    if (e.getKey().equalsIgnoreCase(p.name)) {
                        raw = e.getValue();
                        break;
                    }
                }
            }
            if (raw == null) {
                invokeArgs[i] = defaultValue(p.type);
            } else if (p.type.isAssignableFrom(raw.getClass())) {
                invokeArgs[i] = raw;
            } else {
                invokeArgs[i] = MAPPER.convertValue(raw, p.type);
            }
        }

        entry.method.setAccessible(true);
        return entry.method.invoke(entry.target, invokeArgs);
    }

    /**
     * @return all registered tool definitions.
     */
    public List<ToolDefinition> listDefinitions() {
        List<ToolDefinition> list = new ArrayList<>();
        for (ToolEntry entry : tools.values()) {
            list.add(entry.definition);
        }
        return list;
    }

    public boolean hasTool(String toolName) {
        return tools.containsKey(toolName);
    }

    private static String toJsonSchemaType(Class<?> type) {
        if (type == String.class || type == char.class || type == Character.class) {
            return "string";
        }
        if (type == int.class || type == Integer.class || type == long.class || type == Long.class
                || type == short.class || type == Short.class || type == byte.class || type == Byte.class) {
            return "integer";
        }
        if (type == double.class || type == Double.class || type == float.class || type == Float.class
                || Number.class.isAssignableFrom(type)) {
            return "number";
        }
        if (type == boolean.class || type == Boolean.class) {
            return "boolean";
        }
        if (type.isArray() || List.class.isAssignableFrom(type)) {
            return "array";
        }
        return "object";
    }

    private static Object defaultValue(Class<?> type) {
        if (type == boolean.class) return false;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == double.class) return 0.0;
        if (type == float.class) return 0.0f;
        return null;
    }
}
