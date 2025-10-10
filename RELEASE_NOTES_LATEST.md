## Distribution

Download from TypeDB Package Repository: https://cloudsmith.io/~typedb/repos/public-release/packages/?q=name:^typedb-console+version:3.5.4


## New Features


## Bugs Fixed
- **Fix optional argument parsing and add tests**
  
  We fix a reported error where optional arguments (such as the sha for a `source` command) would cause errors when not present in a script. In particular, the next script line would be consumed as the sha.
  
  For example, pasting the following script into a open transaction repl would error:
  ```
  source attributes.tql
  source entities.tql
  
  ++ source /Users/joshua/Documents/vaticle/gh_vaticle/typedb-console/attributes.tql
  source
  error: **Error executing command**
  Expected 'attributes.tql' to have sha256 'source', but calculated 'e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855',
  ```
  
  We fix this by annotating which commands are multiline and which are single line. Single-line commands always stop parsing at a newline. Only queries are allowed to be multiline at this point in time.
  
  We also add a new assembly test, which runs an extensive script testing various parts of Console:
  
  - user management
  - database management
  - import/export
  - transaction management
  - single line and multi-line queries
  
  

## Code Refactors


## Other Improvements

    
