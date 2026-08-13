# jsonrpc-agent (deepseek-harness4j Java example)

[中文](README.md) | English

The unattended coding-agent composition for the Java SDK's bundled JSON-RPC runtime. It intentionally loads no terminal UI, console logger, approval UI, or user-questions tool because stdout belongs to the SDK protocol and turns are driven by the SDK.

> This file is the Java port of the upstream `examples/jsonrpc-agent/README.md`: `minimal.py` is replaced by `MinimalAgent` (`com.deepseek.harness4j.examples.MinimalAgent`); everything else (the `cordis.yml` composition, environment variables, runtime semantics) is identical to upstream.

The model-facing tools are:

- `bash`, foreground only
- `read`, `write`, and `edit`
- `subagent`, using one foreground in-process spawn provider
- `todo_write`

The surrounding runtime also loads JSONL session persistence and automatic context compaction. `maxTokensAsSuccess` keeps a token-limited model turn as an accepted evaluation result while preserving its `max-tokens` reason.

## Runtime environment

| Variable | Purpose |
|---|---|
| `DEEPSEEK_API_KEY` | Credential passed to the OpenAI-compatible host endpoint |
| `DEEPSEEK_BASE_URL` | Host endpoint used by `dsh-llm-deepseek` |
| `DSH_CWD` | Agent workspace for bash and filesystem tools |
| `DSH_CONTEXT_WINDOW` | Context capacity recorded for the `DSH_MODEL` catalog entry in the minimal variant |
| `DSH_MAX_TOKENS_AS_SUCCESS` | `true` (default) accepts token-limited results; `false` reports them as errors |
| `DSH_MODEL` | Default model used by `MinimalAgent`; `--model` takes precedence |
| `DSH_SESSION_ROOT` | JSONL session directory |
| `DSH_SYSTEM_PROMPT` | Deployment-provided coding persona |

Pass the config path through the SDK's `cordis` option or `DSH_CORDIS_CONFIG`. The bundled executable already carries every plugin named by this file; the target machine does not need Node.js.

## Minimal variant

`minimal.cordis.yml` is the complete standalone counterpart of the Web `minimal` preset. `DSH_SYSTEM_PROMPT` selects its system prompt, with `You are a helpful software engineer assistant.` as the fallback. It suppresses every system-prompt runtime-context contribution for fresh sessions and mounts no context-compaction plugin. Its model-facing tools are exactly:

- owner-scoped persistent `bash`
- `str_replace_editor` with `view`, `create`, `str_replace`, and `insert`

It composes the local PTY, bare `fs-local` backend, danger-full-access policy for persistent Bash, and uncompressed JSONL persistence needed by the bundled runtime. Bash and absolute editor paths can modify any path available to the runtime process, so run this variant only against a disposable checkout or container. The persistent PTY requires a POSIX terminal environment and is not a Windows agent interface.

`MinimalAgent` (Java) runs the composition through the SDK and uses `DSH_MODEL` as its default model. To run (a runtime carrier must be installed first; see `development.en.md`):

```sh
cd deepseek-harness4j
export DEEPSEEK_API_KEY=sk-xxx
mvn -q -pl sdk exec:java \
  -Dexec.mainClass=com.deepseek.harness4j.examples.MinimalAgent \
  -Dexec.args="--workspace /absolute/path/workspace --session-root /absolute/path/sessions --session-id example-001 'Read package.json and print the list of scripts.'"
```

or directly on the built classpath:

```sh
mvn -q -pl sdk dependency:build-classpath -Dmdep.outputFile=/tmp/dsh4j-cp.txt
java -cp "sdk/target/classes:$(cat /tmp/dsh4j-cp.txt)" \
  com.deepseek.harness4j.examples.MinimalAgent \
  --workspace /absolute/path/workspace "your task"
```

SDK runtime lifecycle and result semantics live in `sdk/README.en.md`; Java migration notes in `docs/java-migration-notes.en.md`.
