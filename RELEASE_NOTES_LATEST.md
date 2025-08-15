## Distribution

Download from TypeDB Package Repository: https://cloudsmith.io/~typedb/repos/public-release/packages/?q=name:^typedb-console+version:3.4.4


## New Features


## Bugs Fixed


## Code Refactors


## Other Improvements
- **Create specific exit error codes**
  
  We generate specific error codes to check when executing commands and scripts programmatically.
  We now have the following exit codes:
  ```
    Success = 0,
    GeneralError = 1,
    CommandError = 2,
    ConnectionError = 3,
    UserInputError = 4,
    QueryError = 5,
  ```
  
  
  
    
