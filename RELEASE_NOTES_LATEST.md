## Distribution

Download from TypeDB Package Repository: https://cloudsmith.io/~typedb/repos/public-release/packages/?q=name:^typedb-console+version:3.1.0


## New Features

### Rust console

We re-implement TypeDB console in Rust, using a slightly more sophisticated architecture. Most of the UX of the console remains the same, with the exception of three usages:

- **TLS enabled by default**

TypeDB console enables TLS connections by default. This further will error if you try to connect to an HTTP endpoint, instead of an HTTPS one. This change was required because username and password are now required to connect to the any TypeDB 3.0 server.

To disable TLS (for example, for local development), use the `--tls-disabled` flag when booting TypeDB console. Note that this will send passwords in plaintext over the network.

- **Replacement of `--core=<address>` and `--cloud=<address>` with `--address=<address>`**

To connect to a TypeDB server (either a Core/CE 3.0, or a Cloud 3.0 deployment) use the new `--address=<address>` argument to the console.

- **The command to open a transaction has changed**
  ```
  >> transaction <db> <read|write|schema>
  ```
Has become:
  ```
  >> transaction <read|write|schema> <db>
  ```
Note how the position of the database name has been swapped.

### Build

This repository is now supports dual Bazel/Cargo build systems.

To run TypeDB console directly from this repository, please see the updated README!

### Artifacts

As before, Console will be published as platform-specific archives. However, they now contain a single bash/bat file and the compiled console binary. No more Java libs or requirement to have Java installed!

We also no longer deploy a `console-runner` artifact, since it was only used to run the local `assembly` test. The assembly test is now written in rust, using an unpublished, repository-local runner library that drives both a Server and a Console in the test.

### Note

Some details of the TypeDB Console UX has changed. Please try it out and don't hesitate to reach out to share your thoughts!

## Bugs Fixed


## Code Refactors
- **Update TypeDB driver dependency and constructor**
  Update TypeDB driver dependency and use the unified driver constructor to support the latest API.



## Other Improvements
- **Fix query command parsing**

- **Fix next-command index bug**

- **Improve UX of Ctrl+C and fix bugs in command submission**

  We an important bug:
    1.
  ```
  >> match
  $x isa person; |
  ```
    2. navigate cursor and enter newlines
  ```
  >> match
  
  | $x isa person;
  ```

  Various things would break, including submitting the "script" prematurely. Now, the block is only submitted when the sequence of commands entered are a valid script.

  Additionally, the UX if Ctr+C is improved: when hitting Ctrl+C on a non-empty line, the line is retained, rather than cleared.


- **Multiline script paste and unified scripting format**

  We refactor the Console to allow pasting a "script" into the current REPL. This will be correctly interpreted and executed as a block of separate commands. The inputs must look as they would when using the console interactively:
  ```
  database create tmp
  transaction schema tmp
  define entity person;
  
  commit
  ```

  With the empty newline, as in regular interactive console, being used to mark the end of the query.

  This is particularly useful for pasting fully self-contained examples from TypeDB documentation into the console!

  The format of `--file` scripts (now called `--script`) must follow this same format, instead of using `\` to write multiline queries. We will refer to this as the `TypeDB Console Script`, or `Scripted TypeQL` in some contexts. We use `.tqls` for console scripts, instead of the usual `.tql` which contains pure TypeQL.


- **Strip newline before adding to history**

- **Implement search based on currently entered term**

- **Fix test-assembly**

- **Clear line if ctrl+c is hit with content on the current line**

- **Fix UX bugs and documentation**

- **Incorporate UX improvements, fix windows distribution**

- **Implement uncommitted changes indicator**

- **Update database list error messages to print that none were found**
