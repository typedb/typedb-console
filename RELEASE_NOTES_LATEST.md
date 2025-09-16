## Distribution

Download from TypeDB Package Repository: https://cloudsmith.io/~typedb/repos/public-release/packages/?q=name:^typedb-console+version:3.5.0


## New Features
- **Make version argument not require other fields and unify error messaging**
  Fix an incorrect requirement of `username` and `address` when executing `console --version`. Additionally, unify the format of error messages and add colors (e.g., the "error" header is printed in bold red and argument references are printed in yellow, similar to some of the existing parsing errors).
  
  

## Bugs Fixed


## Code Refactors


## Other Improvements
- **Fix incorrect error println**

- **Fix build and cargo lock**
  Fix build and cargo lock

- **Introduce 'create-init' command to load a new database from schema/data**
  
  We introduce a new command `database create-init`, which 
  
  1) create the new database
  2) loads a provided schema file (from URL, or from local file)
  3) loads a provided data file (from URL or from local file)
  
  Command format:
  ```
  database create-init <db> <schema file> <data file> <[optional] schema file sha256 (hex or sha256:hex)> <[optional] data file sha256 (hex or sha256:hex)>
  ```
  
  Usage example to load bookstore example from `typedb-examples`:
  ```
  >> database create-init bookstore https://github.com/typedb/typedb-examples/releases/download/3.5.0/bookstore-schema.tql https://github.com/typedb/typedb-examples/releases/download/3.5.0/bookstore-data.tql
  ```
  
  You can also optionally provide sha256 checksums to verify your files are correct (these come from the Github releases page):
  ```
  >> database create-init bookstore https://github.com/typedb/typedb-examples/releases/download/3.5.0/bookstore-schema.tql https://github.com/typedb/typedb-examples/releases/download/3.5.0/bookstore-data.tql sha256:b2de488d9f64ccdfdba016029c7932be69ec5d35d18977c85bb12ad4cc97e95f sha256:828806afe1ce939d0ee87d6ae89598f5d7f967155c7757263b151673480bcad1
  ```
  
  We've also upgraded the `source` command in a transaction to allow reading from a remote URL, not just local files:
  ```
  bookstore::write >> source https://github.com/typedb/typedb-examples/releases/download/3.5.0/bookstore-data.tql 
  ```
  
  This can also optionally take a sha256 (the `sha256:` prefix optional):
  ```
  bookstore::write >> source https://github.com/typedb/typedb-examples/releases/download/3.5.0/bookstore-data.tql 828806afe1ce939d0ee87d6ae89598f5d7f967155c7757263b151673480bcad1
  ```
  
  These commands can also receive local in relative or absolute path formats.
  
  
    
