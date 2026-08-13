package com.deepseek.harness4j.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Result of the {@code initialize} handshake with the runtime.
 *
 * <p>Python (pydantic {@code BaseModel}):
 * <pre>{@code
 * class InitializeResponse(BaseModel):
 *     serverInfo: ServerInfo | None = None
 * }</pre>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class InitializeResponse {

    @JsonProperty("serverInfo")
    private ServerInfo serverInfo;

    public InitializeResponse() {
    }

    public InitializeResponse(ServerInfo serverInfo) {
        this.serverInfo = serverInfo;
    }

    /** @return the server identity, or {@code null} when the wire omitted it. */
    public ServerInfo serverInfo() {
        return serverInfo;
    }

    public void setServerInfo(ServerInfo serverInfo) {
        this.serverInfo = serverInfo;
    }

    @Override
    public String toString() {
        return "InitializeResponse{serverInfo=" + serverInfo + '}';
    }
}
