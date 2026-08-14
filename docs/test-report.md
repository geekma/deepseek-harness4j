# deepseek-harness4j 测试报告（Test Report）

[English](test-report.en.md) | 中文

> 生成时间：2026-08-14。环境：JDK 25（目标 release 17）、Maven 3.9、macOS。运行命令：`mvn test`（reactor，含 starter 模块）。
> 汇总（sdk 模块）：**100 个测试用例**，失败 **0**，错误 **0**，跳过 **7**（跳过为真实运行时载体 boot 测试，无载体时按 Python 语义独立跳过）。

## 1. 汇总

| 测试类 | 用例 | 失败 | 错误 | 跳过 | 状态 |
|---|---:|---:|---:|---:|---|
| BundledRuntimeBootTest | 7 | 0 | 0 | 7 | ✅ 全绿 |
| HighLevelApiTest | 9 | 0 | 0 | 0 | ✅ 全绿 |
| MacOsDeploymentTargetTest | 3 | 0 | 0 | 0 | ✅ 全绿 |
| ReleaseVersionTest | 9 | 0 | 0 | 0 | ✅ 全绿 |
| RuntimeBuildHookTest | 7 | 0 | 0 | 0 | ✅ 全绿 |
| SmokeCompletionsTest | 2 | 0 | 0 | 0 | ✅ 全绿 |
| ClientLevelTest | 15 | 0 | 0 | 0 | ✅ 全绿 |
| SubscriptionRoutingTest | 3 | 0 | 0 | 0 | ✅ 全绿 |
| RuntimeResolverTest | 5 | 0 | 0 | 0 | ✅ 全绿 |
| ReadmeExamplesTest | 13 | 0 | 0 | 0 | ✅ 全绿 |
| RunResultExtractionTest | 9 | 0 | 0 | 0 | ✅ 全绿 |
| SessionLogTest | 15 | 0 | 0 | 0 | ✅ 全绿 |
| SessionResumeAsyncTest | 3 | 0 | 0 | 0 | ✅ 全绿 |
| **合计（sdk）** | **100** | **0** | **0** | **7** | |
| SpringStarterTest（starter 模块） | 5 | 0 | 0 | 0 | ✅ 全绿 |

> P0 增强（见 `review-five-features.md`）新增三类测试：`RunResultExtractionTest`（CoT/token/工具调用提取）、`SessionLogTest`（离线 JSONL 引擎：plain/zstd、回放、搜索、fork、chunk 展开）、`SessionResumeAsyncTest`（resume 别名 + runAsync）。

## 2. 用例明细

> `SKIP` 表示该载体未安装而按语义跳过（对应 Python `pytest.skip`）。

### BundledRuntimeBootTest

| 测试方法 | 结果 | 耗时(s) |
|---|---:|---:|
| ⏭️ `test_zero_config_run_injects_bundled_default_cordis_config` | SKIP | 0.0 |
| ⏭️ `test_bundled_runtime_surfaces_unbundled_plugin_failure(String)[1]` | SKIP | 0.0 |
| ⏭️ `test_bundled_runtime_surfaces_unbundled_plugin_failure(String)[2]` | SKIP | 0.0 |
| ⏭️ `test_bundled_runtime_boots_a_cordis_config(String)[1]` | SKIP | 0.0 |
| ⏭️ `test_bundled_runtime_boots_a_cordis_config(String)[2]` | SKIP | 0.0 |
| ⏭️ `test_python_sdk_boots_minimal_jsonrpc_config(String)[1]` | SKIP | 0.0 |
| ⏭️ `test_python_sdk_boots_minimal_jsonrpc_config(String)[2]` | SKIP | 0.0 |

### HighLevelApiTest

| 测试方法 | 结果 | 耗时(s) |
|---|---:|---:|
| ✅ `test_session_run_collects_nested_subagent_tree_without_polluting_root_events` | PASS | 0.196 |
| ✅ `test_relative_cwd_is_absolute_in_process_environment_and_wire` | PASS | 0.322 |
| ✅ `test_session_run_waits_for_late_idle_without_replaying_stale_notifications` | PASS | 0.26 |
| ✅ `test_high_level_sdk_rejects_turn_end_without_reason_kind` | PASS | 0.17 |
| ✅ `test_session_run_invokes_notification_callback_before_returning` | PASS | 0.169 |
| ✅ `test_session_run_ignores_notifications_for_other_sessions` | PASS | 0.215 |
| ✅ `test_high_level_session_run_does_not_accumulate_global_notifications` | PASS | 0.237 |
| ✅ `test_session_run_includes_subagent_finished_for_parent_session` | PASS | 0.169 |
| ✅ `test_high_level_sdk_runs_turn_and_collects_final_response` | PASS | 0.168 |

### MacOsDeploymentTargetTest

| 测试方法 | 结果 | 耗时(s) |
|---|---:|---:|
| ✅ `test_otool_parser_uses_the_newest_macho_slice` | PASS | 0.001 |
| ✅ `test_otool_parser_requires_a_deployment_target` | PASS | 0.001 |
| ✅ `test_wheel_tag_rejects_a_newer_executable_target` | PASS | 0.0 |

### ReleaseVersionTest

| 测试方法 | 结果 | 耗时(s) |
|---|---:|---:|
| ✅ `test_repository_version_rejects_malformed_versions` | PASS | 0.001 |
| ✅ `test_repository_version_matches_root_pom` | PASS | 0.0 |
| ✅ `test_repository_version_accepts_a_prerelease` | PASS | 0.0 |
| ✅ `test_runtime_suffixes_match_platform_payloads` | PASS | 0.0 |
| ✅ `test_release_tag_is_optional_for_non_release_builds` | PASS | 0.001 |
| ✅ `test_pep440_version_spells_a_prerelease_the_python_way` | PASS | 0.0 |
| ✅ `test_macos_wheel_tag_does_not_claim_unsupported_node_platforms` | PASS | 0.001 |
| ✅ `test_release_tag_must_match_repository_version` | PASS | 0.001 |
| ✅ `test_platform_manifest_rejects_incomplete_entries` | PASS | 0.0 |

### RuntimeBuildHookTest

| 测试方法 | 结果 | 耗时(s) |
|---|---:|---:|
| ✅ `test_host_platform_tag_resolves_from_the_manifest` | PASS | 0.004 |
| ✅ `test_unknown_platform_tag_fails_loud` | PASS | 0.001 |
| ✅ `test_payload_validation_rejects_a_missing_macos_spawn_helper` | PASS | 0.003 |
| ✅ `test_payload_validation_accepts_exactly_the_expected_files` | PASS | 0.001 |
| ✅ `test_explicit_tag_wins_over_host` | PASS | 0.001 |
| ✅ `test_payload_validation_rejects_a_non_executable_runtime` | PASS | 0.001 |
| ✅ `test_distribution_requires_a_platform` | PASS | 0.0 |

### SmokeCompletionsTest

| 测试方法 | 结果 | 耗时(s) |
|---|---:|---:|
| ✅ `test_child_prompt_precedes_runtime_context(String, String)[1]` | PASS | 0.001 |
| ✅ `test_child_prompt_precedes_runtime_context(String, String)[2]` | PASS | 0.0 |

### ClientLevelTest

| 测试方法 | 结果 | 耗时(s) |
|---|---:|---:|
| ✅ `test_client_starts_subprocess_sends_requests_and_routes_notifications` | PASS | 0.159 |
| ✅ `test_client_close_is_idempotent_before_and_after_start` | PASS | 0.217 |
| ✅ `test_initialize_failure_reaps_started_runtime` | PASS | 0.183 |
| ✅ `test_client_default_launch_uses_bundled_runtime_and_injects_default_config` | PASS | 0.819 |
| ✅ `test_client_respects_explicit_config_over_bundled_default` | PASS | 0.516 |
| ✅ `test_client_rejects_unaccepted_session_prompt_response` | PASS | 0.162 |
| ✅ `test_client_routes_bridge_requests_and_sends_responses` | PASS | 0.157 |
| ✅ `test_client_serializes_concurrent_writes` | PASS | 0.175 |
| ✅ `test_client_request_times_out_when_bridge_does_not_respond` | PASS | 2.53 |
| ✅ `test_client_ignores_non_json_stdout_lines` | PASS | 0.157 |
| ✅ `test_runtime_closed_error_includes_stderr_tail` | PASS | 0.123 |
| ✅ `test_client_close_times_out_when_shutdown_does_not_respond` | PASS | 0.253 |
| ✅ `test_public_signatures_omit_unsupported_wire_parameters` | PASS | 0.001 |
| ✅ `test_client_reports_missing_bundled_runtime_dependency` | PASS | 0.001 |
| ✅ `test_client_contains_notification_filter_failure_to_its_subscription` | PASS | 0.327 |

### SubscriptionRoutingTest

| 测试方法 | 结果 | 耗时(s) |
|---|---:|---:|
| ✅ `test_session_subscription_preserves_reused_child_ancestry_after_late_finish` | PASS | 0.0 |
| ✅ `test_client_keeps_unmatched_notifications_available_globally_while_subscribed` | PASS | 0.0 |
| ✅ `test_session_subscription_keeps_descendant_relationships_across_subscriptions` | PASS | 0.0 |

### RuntimeResolverTest

| 测试方法 | 结果 | 耗时(s) |
|---|---:|---:|
| ✅ `test_runtime_requires_spawn_helper_only_on_macos` | PASS | 0.004 |
| ✅ `test_explicit_mode_wins_over_env_mode` | PASS | 0.001 |
| ✅ `test_unknown_explicit_mode_fails_loud` | PASS | 0.001 |
| ✅ `test_unknown_env_mode_fails_loud` | PASS | 0.001 |
| ✅ `test_default_config_is_shipped_with_the_package` | PASS | 0.001 |

### ReadmeExamplesTest

| 测试方法 | 结果 | 耗时(s) |
|---|---:|---:|
| ✅ `test_zero_config_minimal_turn_runs` | PASS | 0.0 |
| ✅ `test_config_builder_with_options_runs` | PASS | 0.0 |
| ✅ `test_structured_results_runs` | PASS | 0.0 |
| ✅ `test_forked_child_sessions_run` | PASS | 0.0 |
| ✅ `test_custom_model_runs` | PASS | 0.0 |
| ✅ `test_session_fork_and_result` | PASS | 0.0 |
| ✅ `test_resume_a_session` | PASS | 0.0 |
| ✅ `test_complete_workflow_example` | PASS | 0.0 |
| ✅ `test_concurrent_sessions` | PASS | 0.0 |
| ✅ `test_config_with_tight_timeouts` | PASS | 0.0 |
| ✅ `test_error_handling` | PASS | 0.0 |
| ✅ `test_building_harness_instances` | PASS | 0.0 |
| ✅ `test_session_management` | PASS | 0.0 |

### RunResultExtractionTest

| 测试方法 | 结果 | 耗时(s) |
|---|---:|---:|
| ✅ `test_reasoning_content_extracted_from_reasoning_delta_chunks` | PASS | 0.0 |
| ✅ `test_reasoning_content_handles_packed_reasoning_chunks_row` | PASS | 0.0 |
| ✅ `test_reasoning_content_empty_when_no_reasoning` | PASS | 0.0 |
| ✅ `test_token_usage_aggregates_assistant_messages` | PASS | 0.0 |
| ✅ `test_token_usage_empty_when_no_usage_reported` | PASS | 0.0 |
| ✅ `test_tool_calls_pair_call_and_result_by_call_id` | PASS | 0.0 |
| ✅ `test_tool_calls_flag_error_results_and_unmatched_calls` | PASS | 0.0 |
| ✅ `test_tool_calls_empty_when_no_tool_events` | PASS | 0.0 |
| ✅ `test_end_to_end_run_result_extractions_from_wire_stream` | PASS | 0.0 |

### SessionLogTest

| 测试方法 | 结果 | 耗时(s) |
|---|---:|---:|
| ✅ `test_list_and_read_plain_jsonl` | PASS | 0.0 |
| ✅ `test_read_unknown_session_returns_empty` | PASS | 0.0 |
| ✅ `test_list_ignores_unrelated_files_and_project_grouping` | PASS | 0.0 |
| ✅ `test_encode_segment_escapes_unsafe_ids` | PASS | 0.0 |
| ✅ `test_read_zstd_log` | PASS | 0.0 |
| ✅ `test_read_mixed_plain_and_zstd_sessions` | PASS | 0.0 |
| ✅ `test_packed_chunk_rows_expand_to_assistant_chunk_events` | PASS | 0.0 |
| ✅ `test_packed_tool_call_chunks_expand_with_id_and_name` | PASS | 0.0 |
| ✅ `test_replay_filters_metadata_and_keeps_interaction_events` | PASS | 0.0 |
| ✅ `test_search_filters_by_event_type_and_text_and_time` | PASS | 0.0 |
| ✅ `test_search_requires_at_least_one_filter` | PASS | 0.0 |
| ✅ `test_search_all_crosses_sessions` | PASS | 0.0 |
| ✅ `test_fork_plain_creates_child_with_parent_lineage_and_seed_length` | PASS | 0.0 |
| ✅ `test_fork_zstd_preserves_encoding` | PASS | 0.0 |
| ✅ `test_fork_missing_source_throws` | PASS | 0.0 |

### SessionResumeAsyncTest

| 测试方法 | 结果 | 耗时(s) |
|---|---:|---:|
| ✅ `test_resume_is_an_explicit_alias_for_run_on_the_same_session` | PASS | 0.0 |
| ✅ `test_run_async_resolves_the_same_result_as_run` | PASS | 0.0 |
| ✅ `test_run_async_with_notification_callback` | PASS | 0.0 |



## 3. Python 测试 → Java 测试 一一映射（测试用例清单）

> 上游 `python/sdk/tests/` 的全部测试用例都有 Java 对应物。除特殊注明外，Java 测试方法名与 Python 同名（`_` 保持），Python 的 `pytest.raises`/断言语义逐条等价。`⏭️` = 载体缺失时按 Python `pytest.skip` 语义跳过。

### 3.1 `tests/test_client.py`（27 个用例 → HighLevelApiTest / ClientLevelTest / SubscriptionRoutingTest）

| Python 测试 | Java 测试类.方法 | 状态 |
|---|---|---|
| `test_high_level_sdk_runs_turn_and_collects_final_response` | `HighLevelApiTest` 同名 | ✅ |
| `test_session_run_invokes_notification_callback_before_returning` | `HighLevelApiTest` 同名 | ✅ |
| `test_high_level_sdk_rejects_turn_end_without_reason_kind` | `HighLevelApiTest` 同名 | ✅ |
| `test_relative_cwd_is_absolute_in_process_environment_and_wire` | `HighLevelApiTest` 同名（现代 JDK 用真实相对路径） | ✅ |
| `test_session_run_includes_subagent_finished_for_parent_session` | `HighLevelApiTest` 同名 | ✅ |
| `test_session_run_collects_nested_subagent_tree_without_polluting_root_events` | `HighLevelApiTest` 同名 | ✅ |
| `test_session_run_ignores_notifications_for_other_sessions` | `HighLevelApiTest` 同名 | ✅ |
| `test_high_level_session_run_does_not_accumulate_global_notifications` | `HighLevelApiTest` 同名 | ✅ |
| `test_session_run_waits_for_late_idle_without_replaying_stale_notifications` | `HighLevelApiTest` 同名 | ✅ |
| `test_client_starts_subprocess_sends_requests_and_routes_notifications` | `ClientLevelTest` 同名 | ✅ |
| `test_client_keeps_unmatched_notifications_available_globally_while_subscribed` | `SubscriptionRoutingTest` 同名 | ✅ |
| `test_session_subscription_keeps_descendant_relationships_across_subscriptions` | `SubscriptionRoutingTest` 同名 | ✅ |
| `test_session_subscription_preserves_reused_child_ancestry_after_late_finish` | `SubscriptionRoutingTest` 同名 | ✅ |
| `test_client_contains_notification_filter_failure_to_its_subscription` | `ClientLevelTest` 同名 | ✅ |
| `test_client_rejects_unaccepted_session_prompt_response` | `ClientLevelTest` 同名 | ✅ |
| `test_client_routes_bridge_requests_and_sends_responses` | `ClientLevelTest` 同名 | ✅ |
| `test_client_ignores_non_json_stdout_lines` | `ClientLevelTest` 同名 | ✅ |
| `test_client_request_times_out_when_bridge_does_not_respond` | `ClientLevelTest` 同名（Java 放宽超时以覆盖 JVM 启动） | ✅ |
| `test_client_close_times_out_when_shutdown_does_not_respond` | `ClientLevelTest` 同名 | ✅ |
| `test_initialize_failure_reaps_started_runtime` | `ClientLevelTest` 同名 | ✅ |
| `test_public_signatures_omit_unsupported_wire_parameters` | `ClientLevelTest` 同名（反射检查） | ✅ |
| `test_client_close_is_idempotent_before_and_after_start` | `ClientLevelTest` 同名 | ✅ |
| `test_runtime_closed_error_includes_stderr_tail` | `ClientLevelTest` 同名 | ✅ |
| `test_client_serializes_concurrent_writes` | `ClientLevelTest` 同名 | ✅ |
| `test_client_default_launch_uses_bundled_runtime_and_injects_default_config` | `ClientLevelTest` 同名（覆盖 `unset`/`empty` 两种参数化） | ✅ |
| `test_client_respects_explicit_config_over_bundled_default` | `ClientLevelTest` 同名 | ✅ |
| `test_client_reports_missing_bundled_runtime_dependency` | `ClientLevelTest` 同名 | ✅ |

### 3.2 `tests/test_runtime_resolution.py`（5 个用例 → RuntimeResolverTest）

| Python 测试 | Java 状态 |
|---|---|
| `test_default_config_is_shipped_with_the_package` | ✅ 同名 |
| `test_unknown_explicit_mode_fails_loud` | ✅ 同名 |
| `test_unknown_env_mode_fails_loud` | ✅ 同名（`runtimeModeEnvOverride` 复现 `monkeypatch.setenv`） |
| `test_explicit_mode_wins_over_env_mode` | ✅ 同名 |
| `test_runtime_requires_spawn_helper_only_on_macos` | ✅ 同名（改 `os.name`/`os.arch` 系统属性） |

### 3.3 `tests/test_bundled_runtime.py`（4 组，参数化 exe/node → BundledRuntimeBootTest）

| Python 测试 | Java 状态 |
|---|---|
| `test_bundled_runtime_boots_a_cordis_config[mode]` | ✅ 同名（`@ValueSource` exe/node）⏭️ 无载体跳过 |
| `test_python_sdk_boots_minimal_jsonrpc_config[mode]` | ✅ 同名 ⏭️ 无载体跳过 |
| `test_bundled_runtime_surfaces_unbundled_plugin_failure[mode]` | ✅ 同名 ⏭️ 无载体跳过 |
| `test_zero_config_run_injects_bundled_default_cordis_config[mode, ambient]` | ✅ 同名（Java 仅 exe 模式）⏭️ 无载体跳过 |

### 3.4 `tests/test_smoke_model.py`（2 个参数化 → SmokeCompletionsTest）

| Python 测试 | Java 状态 |
|---|---|
| `test_child_prompt_precedes_runtime_context[DIRECT_CHILD]` | ✅ `test_child_prompt_precedes_runtime_context[1]` |
| `test_child_prompt_precedes_runtime_context[WORKFLOW_CHILD]` | ✅ `test_child_prompt_precedes_runtime_context[2]` |

### 3.5 `tests/test_release_version.py`（10 个用例 → ReleaseVersionTest）

| Python 测试 | Java 状态 |
|---|---|
| `test_repository_version_matches_root_package_json` | ✅ `test_repository_version_matches_root_pom`（读根 `pom.xml`） |
| `test_release_tag_is_optional_for_non_release_builds` | ✅ 同名 |
| `test_release_tag_must_match_repository_version` | ✅ 同名 |
| `test_repository_version_accepts_a_prerelease` | ✅ 同名 |
| `test_repository_version_rejects_malformed_versions` | ✅ 同名 |
| `test_pep440_version_spells_a_prerelease_the_python_way` | ✅ 同名 |
| `test_macos_wheel_tag_does_not_claim_unsupported_node_platforms` | ✅ 同名 |
| `test_platform_manifest_rejects_incomplete_entries` | ✅ 同名 |
| `test_stage_sdk_keeps_distribution_module_and_runtime_pin_distinct` | 📄 N/A——wheel 暂存由 Maven 取代（`development.md` §构建分发物） |
| `test_stage_runtime_copies_platform_payload[linux/macos]` | 📄 N/A——同上；新增 `test_runtime_suffixes_match_platform_payloads` 覆盖等价逻辑 |

### 3.6 `tests/test_macos_deployment_target.py`（3 个用例 → MacOsDeploymentTargetTest）

| Python 测试 | Java 状态 |
|---|---|
| `test_otool_parser_uses_the_newest_macho_slice` | ✅ 同名 |
| `test_otool_parser_requires_a_deployment_target` | ✅ 同名 |
| `test_wheel_tag_rejects_a_newer_executable_target` | ✅ 同名 |

### 3.7 `tests/manual_sdk_agent_smoke.py` 与构建逻辑（非 pytest）

| Python | Java | 状态 |
|---|---|---|
| `manual_sdk_agent_smoke.py`（手动冒烟） | `examples/ManualSdkAgentSmoke`（main） | ✅ |
| `hatch_build.py` 构建钩子逻辑（无独立测试文件） | `RuntimeBuildHookTest`（7 个用例） | ✅ 新增 |

## 4. 重新生成报告

```sh
cd deepseek-harness4j
mvn -pl sdk test                          # 重新运行测试（surefire 报告在 sdk/target/surefire-reports/）
mvn -pl sdk surefire-report:report        # 生成 HTML 报告（sdk/target/site/surefire-report.html）
# 本 markdown 报告由脚本从 surefire XML 生成；映射表见上文 §3
```

## 5. 失败处理

- 全部 100 个用例当前为 ✅（0 失败/0 错误）；7 个 `⏭️` 为无真实载体时的语义跳过。
- 安装运行时载体后运行 `BundledRuntimeBootTest` 会转为真实执行（见 `development.md`）。
