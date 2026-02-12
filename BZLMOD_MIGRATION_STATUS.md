# Bzlmod Migration Status: typedb-console

## Current Status: Complete

**Bazel version**: 8.0.0
**Branch**: `bazel-8-upgrade`

## Build Status

`bazel build //...` — **51 targets, all pass**

| Target | Status | Notes |
|--------|--------|-------|
| `//:console-native` | Pass | Core Rust binary |
| `//binary/...` | Pass | Distribution packaging |
| `//tool/runner/...` | Pass | Test runner library |
| `//tests/assembly/...` | Pass | Integration tests (need TypeDB server to run) |
| `//:checkstyle` | Pass | License header checks |
| `//:tools` | Pass | `@rust_analyzer_toolchain_tools` commented out |

## Known Exclusions

| Target | Reason |
|--------|--------|
| `@rust_analyzer_toolchain_tools` in `:tools` | Not available in Bzlmod (same as typedb-driver) |

## Dependencies

All upstream dependencies are migrated on their `bazel-8-upgrade` branches:

- `typedb_dependencies` - Complete
- `typedb_bazel_distribution` - Complete
- `typeql` - Complete
- `typedb_driver` - In Progress (Rust driver complete)
- `typedb_protocol` - Complete

## Environment Requirements

- Bazelisk (Bazel version manager)
- OpenJDK 21 (`apt-get install openjdk-21-jdk-headless`)
- GCC (`apt-get install gcc`)
- make (`apt-get install make`)

## Migration Notes

- Console is a pure Rust CLI app, so migration is straightforward
- `@typedb_artifact_*` repos for integration tests are defined as `http_file` in MODULE.bazel (same pattern as typedb-driver)
- `workspace_refs` uses the Bzlmod `_workspace_refs` repo rule with empty dicts
- `dependencies/typedb/repositories.bzl` is kept for reference but superseded by MODULE.bazel
