package com.deepseek.harness4j.tool;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolRegistryTest {

    public static class SampleTools {
        @HarnessTool(name = "add_numbers", description = "Add two integers together")
        public int add(@Param(name = "a", description = "First number") int a,
                       @Param(name = "b", description = "Second number") int b) {
            return a + b;
        }

        @HarnessTool(description = "Say hello to a user")
        public String greet(@Param(name = "username") String username,
                            @Param(name = "prefix", required = false) String prefix) {
            return (prefix != null ? prefix : "Hello, ") + username;
        }
    }

    @Test
    void test_tool_registry_registration_and_inspection() {
        ToolRegistry registry = new ToolRegistry();
        registry.register(new SampleTools());

        assertTrue(registry.hasTool("add_numbers"));
        assertTrue(registry.hasTool("greet"));
        assertFalse(registry.hasTool("unknown_tool"));

        List<ToolDefinition> defs = registry.listDefinitions();
        assertEquals(2, defs.size());

        ToolDefinition addDef = defs.stream().filter(d -> d.name().equals("add_numbers")).findFirst().orElseThrow();
        assertEquals("Add two integers together", addDef.description());
        assertEquals(2, addDef.parameters().size());

        Map<String, Object> schema = addDef.parametersSchema();
        assertNotNull(schema);
        assertEquals("object", schema.get("type"));
    }

    @Test
    void test_tool_execution() throws Exception {
        ToolRegistry registry = new ToolRegistry();
        registry.register(new SampleTools());

        Object result = registry.execute("add_numbers", Map.of("a", 10, "b", 25));
        assertEquals(35, result);

        Object greet = registry.execute("greet", Map.of("username", "DeepSeek", "prefix", "Hi, "));
        assertEquals("Hi, DeepSeek", greet);

        Object greetDefault = registry.execute("greet", Map.of("username", "World"));
        assertEquals("Hello, World", greetDefault);
    }
}
