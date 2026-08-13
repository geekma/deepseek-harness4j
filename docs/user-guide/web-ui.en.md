# Use the Web UI

[中文](web-ui.md) | English

> This file is the port of the upstream `docs/user/guide/index.md` (+ `index.zh.md`). The Web UI is a language-neutral browser frontend that drives the same agent core as the SDK; Java SDK callers do not need it, but understanding its workflow helps with the shared configuration and model semantics.

Start the Web UI through the [root README](../../README.md); the command prints its URL. This guide begins after that server is running. The `dsh` process uses its invoking directory as the default filesystem location, but a fresh Web UI has no selected workspace until you add one.

## Configure a model

Open **Settings → Models**, enter a DeepSeek API key, and save it. The model route becomes usable immediately without restarting the server.

The [model configuration guide](./providers.en.md) covers other providers and custom OpenAI-compatible endpoints.

## Choose a workspace

Click **Choose workspace**, add the project directory where you started `dsh`, and select it. The session composer remains unavailable until a workspace is selected.

## Run a task

Start a session and send:

> Summarize this repository and identify its main packages.

The agent can read and edit workspace files, run commands, delegate work, and maintain a plan. The Web UI asks before operations that require approval under the active permission policy.

## Continue

- [Configure models](./providers.en.md)
- [Use the Java SDK](./python-sdk.en.md) (the counterpart of the upstream Python SDK tutorial)
- [Develop a plugin](../../docs/user/develop/basic/) (upstream, language-neutral reference)
