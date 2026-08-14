package com.deepseek.harness4j.cordis;

import java.util.ArrayList;
import java.util.List;

/**
 * Strong-typed security and isolation policy configuration for Cordis sandbox plugins.
 */
public record SandboxPolicy(
        String mode,
        String workspaceRoot,
        boolean allowNetwork,
        boolean readOnlyRoot,
        List<String> allowedCommands,
        List<String> blockedCommands,
        List<String> readOnlyPaths,
        Integer timeoutSeconds) {

    public static final String MODE_DANGER_FULL_ACCESS = "danger-full-access";
    public static final String MODE_RESTRICTED = "restricted";
    public static final String MODE_READ_ONLY = "read-only";

    public static Builder builder() {
        return new Builder();
    }

    public static SandboxPolicy defaultPolicy() {
        return builder().build();
    }

    public static final class Builder {
        private String mode = MODE_DANGER_FULL_ACCESS;
        private String workspaceRoot;
        private boolean allowNetwork = true;
        private boolean readOnlyRoot = false;
        private final List<String> allowedCommands = new ArrayList<>();
        private final List<String> blockedCommands = new ArrayList<>();
        private final List<String> readOnlyPaths = new ArrayList<>();
        private Integer timeoutSeconds;

        public Builder mode(String mode) {
            this.mode = mode;
            return this;
        }

        public Builder workspaceRoot(String workspaceRoot) {
            this.workspaceRoot = workspaceRoot;
            return this;
        }

        public Builder allowNetwork(boolean allowNetwork) {
            this.allowNetwork = allowNetwork;
            return this;
        }

        public Builder readOnlyRoot(boolean readOnlyRoot) {
            this.readOnlyRoot = readOnlyRoot;
            return this;
        }

        public Builder allowCommand(String command) {
            this.allowedCommands.add(command);
            return this;
        }

        public Builder blockCommand(String command) {
            this.blockedCommands.add(command);
            return this;
        }

        public Builder readOnlyPath(String path) {
            this.readOnlyPaths.add(path);
            return this;
        }

        public Builder timeoutSeconds(Integer timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
            return this;
        }

        public SandboxPolicy build() {
            return new SandboxPolicy(
                    mode,
                    workspaceRoot,
                    allowNetwork,
                    readOnlyRoot,
                    List.copyOf(allowedCommands),
                    List.copyOf(blockedCommands),
                    List.copyOf(readOnlyPaths),
                    timeoutSeconds);
        }
    }
}
