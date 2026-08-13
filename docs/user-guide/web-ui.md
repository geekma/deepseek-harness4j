# 使用 Web UI

[English](web-ui.en.md) | 中文

> 本文件是上游 `docs/user/guide/index.md`（+`index.zh.md`）的移植。Web UI 是语言无关的浏览器前端，驱动与 SDK 相同的 agent 内核；Java SDK 调用方无需使用它，但了解其工作流有助于理解同一套配置与模型语义。

通过[根 README](../../README.md) 启动 Web UI；命令会打印其 URL。本指南在该服务器运行之后开始。`dsh` 进程把其调用目录作为默认文件系统位置，但新建的 Web UI 在你添加工作区之前没有选中任何工作区。

## 配置模型

打开 **Settings → Models**，输入 DeepSeek API key 并保存。模型路由立即可用，无需重启服务器。

[模型配置指南](./providers.md)覆盖其他提供方与自定义 OpenAI 兼容端点。

## 选择工作区

点击 **Choose workspace**，添加你启动 `dsh` 时的项目目录并选中它。在选中工作区之前，会话输入框不可用。

## 运行任务

新建会话并发送：

> Summarize this repository and identify its main packages.

agent 可以读写工作区文件、运行命令、委派工作并维护 plan。Web UI 会在当前权限策略要求审批的操作之前询问。

## 继续

- [配置模型](./providers.md)
- [使用 Java SDK](./python-sdk.md)（对应上游的 Python SDK 教程）
- [开发一个插件](../../docs/user/develop/basic/)（上游，语言无关参考）
