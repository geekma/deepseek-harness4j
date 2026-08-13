# 配置模型

[English](providers.en.md) | 中文

> 本文件是上游 `docs/user/guide/providers.md`（+`providers.zh.md`）的移植。模型配置作用于**运行时**（`llm-pi-ai` / `llm-deepseek` / `settings.yaml`），与客户端语言无关——Java SDK、Python SDK 与 Web UI 共享同一套配置语义。

本指南假设你已通过[根 README](../../README.md) 启动 Web UI。模型变更在下次请求时生效，无需重启服务器。

## 配置 DeepSeek

打开 **Settings → Models**。DeepSeek 卡片暴露一个 API key 字段；输入 key 并保存。

密钥是**只写**的。保存后页面只显示脱敏描述符，绝不显示明文。密钥存于 `$DSH_HOME/.credentials.yaml`，settings 只保留其凭据引用。

## 添加目录提供方

选择 **Add provider**，选择如 Anthropic 或 OpenAI 的提供方，输入其 API key 并保存。已安装目录提供端点、协议与模型列表。

带原生认证的提供方需要各自的原生凭据。Bedrock、Vertex、Azure 与 Codex 分别使用 AWS 凭据+区域、ADC 项目、`api-version` 与 OAuth；只填 API key 字段无法完成配置。

## 添加自定义提供方

为公司网关、自托管服务器或已安装目录之外的提供方选择 **Add a custom provider**。提供小写 Provider ID、base URL、API 协议、凭据与至少一个模型。

Provider ID 是**永久的**，因为请求、已保存会话、模型默认值与凭据引用都使用它。要改名请新建提供方并删除旧的。显示名、base URL、协议、凭据与模型保持可编辑。

在 **Model catalog** 下选择 **Fetch available models** 可查询表单中当前显示的 base URL 与凭据。选中候选会更新草稿；提供方在保存前不会存储。目录提供方使用其已安装目录，无需网络请求。

### 图片输入

手工输入的模型默认按纯文本处理，因为没有任何东西能询问端点接受哪些模态。给此类模型附加图片会在发送前被拒绝，并指名该模型。

因此自定义提供方上的视觉模型需要一行配置。表单没有该字段；请在 `$DSH_HOME/settings.yaml` 中给模型加 `input`：

```yaml
llm-pi-ai:
  providers:
    my-gateway:
      apiKeyEnv: GATEWAY_API_KEY
      api: openai-completions
      baseURL: https://gateway.example/v1
      models:
        - id: legacy-chat
        - id: vision-preview
          input: [text, image]
```

`input` 接受 `text` 与 `image`，只作用于该模型，因此一个路由可同时服务两种。省略它——或写空列表，二者含义相同——保留已安装目录为该模型记录的模态，并在目录未描述的模型上回退到路由的 `defaultInput`。

如果手填的每个模型都接收图片，就在路由上设置一次兜底，而不是每个模型都设：

```yaml
llm-pi-ai:
  providers:
    vision-gateway:
      apiKeyEnv: GATEWAY_API_KEY
      api: openai-completions
      baseURL: https://vision.example/v1
      defaultInput: [text, image]
      models:
        - id: first-model
        - id: second-model
```

`defaultInput` 是兜底而非覆盖，默认 `[text]`：在目录提供方上它只对目录未描述的模型生效，因此绝不会从带图模态的目录模型上移除图片。用该模型自己的 `input` 收窄其中一个。目录提供方没有可放入的 `models` 列表，因此写在 `modelOverrides` 下、按模型 id 为键：

```yaml
llm-pi-ai:
  providers:
    anthropic:
      modelOverrides:
        claude-sonnet-4-5:
          input: [text]
```

除模型自己的列表（空列表与省略同义）外，每个列表必须至少声明一种模态。任何位置写入未知模态都会被拒绝。

两个字段都只是对端点的**声明**而非检查。声明了图片但端点不提供的模型不会被此处拦截；由提供方拒绝该请求。

## 选择模型

已配置提供方出现在模型选择器中。选择模型也使其成为新会话的默认。已发送过请求的会话保留其自身日志中记录的模型。

如果已保存的默认值指向被删除的提供方，输入框会显示 **Select model** 并阻止输入，直到选择另一个模型。

## 排错

- **`MISSING_CREDENTIAL`** —— 通过 Models 页存储提供方 key，或提供被引用的环境变量。
- **`UNKNOWN_MODEL`** —— 选择一个已配置模型，或给自定义提供方补上缺失模型。
- **拉取可用模型返回 401** —— 检查 key。模型发现调用 OpenAI 兼容 `GET /models` 端点；不支持该端点的请手动输入模型。
- **图片在发送前被拒绝** —— 模型未声明图片模态。给自定义提供方的模型加 `input: [text, image]`；DeepSeek 自己的 chat-completions 路由为纯文本，无法另行配置。
- **提供方拒绝携带图片的请求** —— 模型声明了端点实际不支持的图片能力。从授予它的列表中移除 `image`——模型的 `input` 或路由的 `defaultInput`——然后新建会话：附加图片保留在会话日志中，因此同一请求会重复，直到会话离开它。

## 高级配置

生成的[插件配置目录](../../docs/config-catalog.md)列出每个受支持字段与默认值。`dsh-llm-pi-ai` 与 `dsh-llm-deepseek` 参考拥有直接的 `settings.yaml` 配置、目录解析、推理控制、凭据与适配器错误（上游 `packages/llm/`，语言无关）。
