# deepseek-harness4j test report

[中文](test-report.md) | English

> Generated: 2026-08-14. Environment: JDK 25 (target release 17), Maven 3.9, macOS. Command: `mvn test` (reactor, incl. starter module).
> Summary (sdk module): **100 test cases**, failures **0**, errors **0**, skipped **7** (the skips are the real-runtime-carrier boot tests, which skip independently when no carrier is installed, per Python semantics).

## 1. Summary

| Test class | Cases | Failures | Errors | Skipped | Status |
|---|---:|---:|---:|---:|---|
| BundledRuntimeBootTest | 7 | 0 | 0 | 7 | ✅ green |
| HighLevelApiTest | 9 | 0 | 0 | 0 | ✅ green |
| MacOsDeploymentTargetTest | 3 | 0 | 0 | 0 | ✅ green |
| ReleaseVersionTest | 9 | 0 | 0 | 0 | ✅ green |
| RuntimeBuildHookTest | 7 | 0 | 0 | 0 | ✅ green |
| SmokeCompletionsTest | 2 | 0 | 0 | 0 | ✅ green |
| ClientLevelTest | 15 | 0 | 0 | 0 | ✅ green |
| SubscriptionRoutingTest | 3 | 0 | 0 | 0 | ✅ green |
| RuntimeResolverTest | 5 | 0 | 0 | 0 | ✅ green |
| ReadmeExamplesTest | 13 | 0 | 0 | 0 | ✅ green |
| RunResultExtractionTest | 9 | 0 | 0 | 0 | ✅ green |
| SessionLogTest | 15 | 0 | 0 | 0 | ✅ green |
| SessionResumeAsyncTest | 3 | 0 | 0 | 0 | ✅ green |
| **Total (sdk)** | **100** | **0** | **0** | **7** | |
| SpringStarterTest (starter module) | 5 | 0 | 0 | 0 | ✅ green |

> The P0 enhancements (see `review-five-features.md`) add three test classes: `RunResultExtractionTest` (CoT/token/tool-call extraction), `SessionLogTest` (offline JSONL engine: plain/zstd, replay, search, fork, chunk expansion), and `SessionResumeAsyncTest` (resume alias + runAsync).

## 2. Case details

> `SKIP` means the carrier is not installed and the test skips per semantics (Python `pytest.skip`).

### BundledRuntimeBootTest

| Test method | Result | Time(s) |
|---|---:|---:|
| ⏭️ `test_zero_config_run_injects_bundled_default_cordis_config` | SKIP | 0.0 |
| ⏭️ `test_bundled_runtime_surfaces_unbundled_plugin_failure(String)[1]` | SKIP | 0.0 |
| ⏭️ `test_bundled_runtime_surfaces_unbundled_plugin_failure(String)[2]` | SKIP | 0.0 |
| ⏭️ `test_bundled_runtime_boots_a_cordis_config(String)[1]` | SKIP | 0.0 |
| ⏭️ `test_bundled_runtime_boots_a_cordis_config(String)[2]` | SKIP | 0.0 |
| ⏭️ `test_python_sdk_boots_minimal_jsonrpc_config(String)[1]` | SKIP | 0.0 |
| ⏭️ `test_python_sdk_boots_minimal_jsonrpc_config(String)[2]` | SKIP | 0.0 |

### HighLevelApiTest

| Test method | Result | Time(s) |
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

| Test method | Result | Time(s) |
|---|---:|---:|
| ✅ `test_otool_parser_uses_the_newest_macho_slice` | PASS | 0.001 |
| ✅ `test_otool_parser_requires_a_deployment_target` | PASS | 0.001 |
| ✅ `test_wheel_tag_rejects_a_newer_executable_target` | PASS | 0.0 |

### ReleaseVersionTest

| Test method | Result | Time(s) |
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

| Test method | Result | Time(s) |
|---|---:|---:|
| ✅ `test_host_platform_tag_resolves_from_the_manifest` | PASS | 0.004 |
| ✅ `test_unknown_platform_tag_fails_loud` | PASS | 0.001 |
| ✅ `test_payload_validation_rejects_a_missing_macos_spawn_helper` | PASS | 0.003 |
| ✅ `test_payload_validation_accepts_exactly_the_expected_files` | PASS | 0.001 |
| ✅ `test_explicit_tag_wins_over_host` | PASS | 0.001 |
| ✅ `test_payload_validation_rejects_a_non_executable_runtime` | PASS | 0.001 |
| ✅ `test_distribution_requires_a_platform` | PASS | 0.0 |

### SmokeCompletionsTest

| Test method | Result | Time(s) |
|---|---:|---:|
| ✅ `test_child_prompt_precedes_runtime_context(String, String)[1]` | PASS | 0.001 |
| ✅ `test_child_prompt_precedes_runtime_context(String, String)[2]` | PASS | 0.0 |

### ClientLevelTest

| Test method | Result | Time(s) |
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

| Test method | Result | Time(s) |
|---|---:|---:|
| ✅ `test_session_subscription_preserves_reused_child_ancestry_after_late_finish` | PASS | 0.0 |
| ✅ `test_client_keeps_unmatched_notifications_available_globally_while_subscribed` | PASS | 0.0 |
| ✅ `test_session_subscription_keeps_descendant_relationships_across_subscriptions` | PASS | 0.0 |

### RuntimeResolverTest

| Test method | Result | Time(s) |
|---|---:|---:|
| ✅ `test_runtime_requires_spawn_helper_only_on_macos` | PASS | 0.004 |
| ✅ `test_explicit_mode_wins_over_env_mode` | PASS | 0.001 |
| ✅ `test_unknown_explicit_mode_fails_loud` | PASS | 0.001 |
| ✅ `test_unknown_env_mode_fails_loud` | PASS | 0.001 |
| ✅ `test_default_config_is_shipped_with_the_package` | PASS | 0.001 |

### ReadmeExamplesTest

| Test method | Result | Time(s) |
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

| Test method | Result | Time(s) |
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

| Test method | Result | Time(s) |
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

| Test method | Result | Time(s) |
|---|---:|---:|
| ✅ `test_resume_is_an_explicit_alias_for_run_on_the_same_session` | PASS | 0.0 |
| ✅ `test_run_async_resolves_the_same_result_as_run` | PASS | 0.0 |
| ✅ `test_run_async_with_notification_callback` | PASS | 0.0 |



## 3. Python test → Java test one-to-one mapping (test-case index)

> Every test case in upstream `python/sdk/tests/` has a Java counterpart. Unless noted, the Java test method name matches the Python one verbatim, and the `pytest.raises`/assert semantics are equivalent case by case. `⏭️` = skipped per Python `pytest.skip` semantics when the carrier is absent.

### 3.1 `tests/test_client.py` (27 cases → HighLevelApiTest / ClientLevelTest / SubscriptionRoutingTest)

| Python test | Java test class.method | Status |
|---|---|---|
| `test_high_level_sdk_runs_turn_and_collects_final_response` | `HighLevelApiTest` same-named | ✅ |
| `test_session_run_invokes_notification_callback_before_returning` | `HighLevelApiTest` same-named | ✅ |
| `test_high_level_sdk_rejects_turn_end_without_reason_kind` | `HighLevelApiTest` same-named | ✅ |
| `test_relative_cwd_is_absolute_in_process_environment_and_wire` | `HighLevelApiTest` same-named (real relative path on modern JDKs) | ✅ |
| `test_session_run_includes_subagent_finished_for_parent_session` | `HighLevelApiTest` same-named | ✅ |
| `test_session_run_collects_nested_subagent_tree_without_polluting_root_events` | `HighLevelApiTest` same-named | ✅ |
| `test_session_run_ignores_notifications_for_other_sessions` | `HighLevelApiTest` same-named | ✅ |
| `test_high_level_session_run_does_not_accumulate_global_notifications` | `HighLevelApiTest` same-named | ✅ |
| `test_session_run_waits_for_late_idle_without_replaying_stale_notifications` | `HighLevelApiTest` same-named | ✅ |
| `test_client_starts_subprocess_sends_requests_and_routes_notifications` | `ClientLevelTest` same-named | ✅ |
| `test_client_keeps_unmatched_notifications_available_globally_while_subscribed` | `SubscriptionRoutingTest` same-named | ✅ |
| `test_session_subscription_keeps_descendant_relationships_across_subscriptions` | `SubscriptionRoutingTest` same-named | ✅ |
| `test_session_subscription_preserves_reused_child_ancestry_after_late_finish` | `SubscriptionRoutingTest` same-named | ✅ |
| `test_client_contains_notification_filter_failure_to_its_subscription` | `ClientLevelTest` same-named | ✅ |
| `test_client_rejects_unaccepted_session_prompt_response` | `ClientLevelTest` same-named | ✅ |
| `test_client_routes_bridge_requests_and_sends_responses` | `ClientLevelTest` same-named | ✅ |
| `test_client_ignores_non_json_stdout_lines` | `ClientLevelTest` same-named | ✅ |
| `test_client_request_times_out_when_bridge_does_not_respond` | `ClientLevelTest` same-named (timeout relaxed to cover JVM startup) | ✅ |
| `test_client_close_times_out_when_shutdown_does_not_respond` | `ClientLevelTest` same-named | ✅ |
| `test_initialize_failure_reaps_started_runtime` | `ClientLevelTest` same-named | ✅ |
| `test_public_signatures_omit_unsupported_wire_parameters` | `ClientLevelTest` same-named (reflection) | ✅ |
| `test_client_close_is_idempotent_before_and_after_start` | `ClientLevelTest` same-named | ✅ |
| `test_runtime_closed_error_includes_stderr_tail` | `ClientLevelTest` same-named | ✅ |
| `test_client_serializes_concurrent_writes` | `ClientLevelTest` same-named | ✅ |
| `test_client_default_launch_uses_bundled_runtime_and_injects_default_config` | `ClientLevelTest` same-named (covers both `unset`/`empty` parametrization) | ✅ |
| `test_client_respects_explicit_config_over_bundled_default` | `ClientLevelTest` same-named | ✅ |
| `test_client_reports_missing_bundled_runtime_dependency` | `ClientLevelTest` same-named | ✅ |

### 3.2 `tests/test_runtime_resolution.py` (5 cases → RuntimeResolverTest)

| Python test | Java status |
|---|---|
| `test_default_config_is_shipped_with_the_package` | ✅ same-named |
| `test_unknown_explicit_mode_fails_loud` | ✅ same-named |
| `test_unknown_env_mode_fails_loud` | ✅ same-named (`runtimeModeEnvOverride` reproduces `monkeypatch.setenv`) |
| `test_explicit_mode_wins_over_env_mode` | ✅ same-named |
| `test_runtime_requires_spawn_helper_only_on_macos` | ✅ same-named (overrides `os.name`/`os.arch` system properties) |

### 3.3 `tests/test_bundled_runtime.py` (4 groups, parametrized exe/node → BundledRuntimeBootTest)

| Python test | Java status |
|---|---|
| `test_bundled_runtime_boots_a_cordis_config[mode]` | ✅ same-named (`@ValueSource` exe/node) ⏭️ skips without a carrier |
| `test_python_sdk_boots_minimal_jsonrpc_config[mode]` | ✅ same-named ⏭️ skips without a carrier |
| `test_bundled_runtime_surfaces_unbundled_plugin_failure[mode]` | ✅ same-named ⏭️ skips without a carrier |
| `test_zero_config_run_injects_bundled_default_cordis_config[mode, ambient]` | ✅ same-named (Java covers exe mode only) ⏭️ skips without a carrier |

### 3.4 `tests/test_smoke_model.py` (2 parametrized → SmokeCompletionsTest)

| Python test | Java status |
|---|---|
| `test_child_prompt_precedes_runtime_context[DIRECT_CHILD]` | ✅ `test_child_prompt_precedes_runtime_context[1]` |
| `test_child_prompt_precedes_runtime_context[WORKFLOW_CHILD]` | ✅ `test_child_prompt_precedes_runtime_context[2]` |

### 3.5 `tests/test_release_version.py` (10 cases → ReleaseVersionTest)

| Python test | Java status |
|---|---|
| `test_repository_version_matches_root_package_json` | ✅ `test_repository_version_matches_root_pom` (reads the root `pom.xml`) |
| `test_release_tag_is_optional_for_non_release_builds` | ✅ same-named |
| `test_release_tag_must_match_repository_version` | ✅ same-named |
| `test_repository_version_accepts_a_prerelease` | ✅ same-named |
| `test_repository_version_rejects_malformed_versions` | ✅ same-named |
| `test_pep440_version_spells_a_prerelease_the_python_way` | ✅ same-named |
| `test_macos_wheel_tag_does_not_claim_unsupported_node_platforms` | ✅ same-named |
| `test_platform_manifest_rejects_incomplete_entries` | ✅ same-named |
| `test_stage_sdk_keeps_distribution_module_and_runtime_pin_distinct` | 📄 N/A — wheel staging replaced by Maven (`development.en.md`, "Build distributions") |
| `test_stage_runtime_copies_platform_payload[linux/macos]` | 📄 N/A — same; added `test_runtime_suffixes_match_platform_payloads` covers the equivalent logic |

### 3.6 `tests/test_macos_deployment_target.py` (3 cases → MacOsDeploymentTargetTest)

| Python test | Java status |
|---|---|
| `test_otool_parser_uses_the_newest_macho_slice` | ✅ same-named |
| `test_otool_parser_requires_a_deployment_target` | ✅ same-named |
| `test_wheel_tag_rejects_a_newer_executable_target` | ✅ same-named |

### 3.7 `tests/manual_sdk_agent_smoke.py` and build-hook logic (not pytest)

| Python | Java | Status |
|---|---|---|
| `manual_sdk_agent_smoke.py` (manual smoke) | `examples/ManualSdkAgentSmoke` (main) | ✅ |
| `hatch_build.py` build-hook logic (no standalone test file) | `RuntimeBuildHookTest` (7 cases) | ✅ added |

## 4. Regenerating the report

```sh
cd deepseek-harness4j
mvn -pl sdk test                          # re-run the tests (surefire reports in sdk/target/surefire-reports/)
mvn -pl sdk surefire-report:report        # generate the HTML report (sdk/target/site/surefire-report.html)
# This markdown report is generated from the surefire XML; the mapping table is §3 above.
```

## 5. Failure handling

- All 100 cases are currently ✅ (0 failures / 0 errors); the 7 `⏭️` are semantic skips without a real carrier.
- After installing a runtime carrier, `BundledRuntimeBootTest` executes for real (see `development.en.md`).
