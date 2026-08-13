package com.deepseek.harness4j.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

/**
 * Server identity reported by the runtime during {@code initialize}.
 *
 * <p>Python (pydantic {@code BaseModel}):
 * <pre>{@code
 * class ServerInfo(BaseModel):
 *     name: str | None = None
 *     version: str | None = None
 * }</pre>
 *
 * <p>Unlike Python, Java cannot express "optional with {@code null} default"
 * as a plain field contract, so getters return {@code null} when absent.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class ServerInfo {

    @JsonProperty("name")
    private String name;

    @JsonProperty("version")
    private String version;

    public ServerInfo() {
    }

    public ServerInfo(String name, String version) {
        this.name = name;
        this.version = version;
    }

    /** @return the runtime name, or {@code null} when the wire omitted it. */
    public String name() {
        return name;
    }

    /** @return the runtime version, or {@code null} when the wire omitted it. */
    public String version() {
        return version;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    @Override
    public String toString() {
        return "ServerInfo{" + "name='" + name + '\'' + ", version='" + version + '\'' + '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ServerInfo that)) {
            return false;
        }
        return Objects.equals(name, that.name) && Objects.equals(version, that.version);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, version);
    }
}
