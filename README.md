[![TypeDB](https://github.com/typedb/.github/raw/master/profile/banner.png)](https://typedb.com/)

# TypeDB Tools

[![Build & test](https://github.com/typedb/typedb-console/actions/workflows/build.yml/badge.svg)](https://github.com/typedb/typedb-console/actions/workflows/build.yml)
[![GitHub release](https://img.shields.io/github/release/typedb/typedb-console.svg)](https://github.com/typedb/typedb-console/releases/latest)
[![Discord](https://img.shields.io/discord/665254494820368395?color=7389D8&label=chat&logo=discord&logoColor=ffffff)](https://typedb.com/discord)

This repository contains the command-line tools that ship alongside TypeDB. Each tool is
its own Rust binary in a Cargo workspace, sharing connection / TLS plumbing via a small
common crate.

## Tools

- **[`console/`](console/README.md)** — TypeDB Console: an interactive REPL for managing
  databases, running TypeQL queries, and executing scripts of console commands. See the
  [console README](console/README.md) for the full command reference and scripting model.
- **[`loader/`](loader/README.md)** — TypeDB Loader: a bulk loader that drives a
  user-supplied TypeQL `given`-stage insert pipeline over a CSV file, with batching,
  parallel writes, reject capture, and resumable checkpoints. See the
  [loader README](loader/README.md) for details.
- **[`typeql-check/`](typeql-check/)** — TypeQL Check: a utility that verifies the
  syntactic validity of a given TypeQL query.

## Repo layout

- `console/` — TypeDB Console sources, tests, and packaging.
- `loader/` — TypeDB Loader sources, tests, and packaging.
- `typeql-check/` — TypeQL Check sources.
- `common/` — Shared helpers (address parsing, TLS config) used by both tools.
- `binary/` — The `typedb` wrapper script that dispatches to either `console` or `loader`
  inside an assembled distribution (`typedb console …`, `typedb loader …`).
- `tool/` — Build / release tooling.
