## Distribution

Download from TypeDB Package Repository: https://cloudsmith.io/~typedb/repos/public-release/packages/?q=name:^typedb-console+version:3.4.0


## New Features
- **Introduce database export and import**
  Add database export and database import operations.

  Database export saves the database information (its schema and data) on the client machine as two files at provided locations:
  ```
  # database export <name> <schema file location> <data file location>
  database export my-database export/my-database.typeql export/my-database.typedb
  ```

  Database import uses the exported files to create a new database with equivalent schema and data:
  ```
  # database export <name> <schema file location> <data file location>
  database import their-database export/my-database.typeql export/my-database.typedb
  ```

- **Support relative script and source commands**

  We support using relative paths for the `--script` command (relative to the current directory), as well as relative paths for the REPL `source` command.

  When `source` is invoked _from_ a script, the sourced file is relativised to the script, rather than the current working directory.



## Bugs Fixed


## Code Refactors


## Other Improvements
- **Update zlib dependency**
  Support build on Apple Clang 17+ by updating dependencies (details: https://github.com/typedb/typedb-dependencies/pull/577).

