
## New Features


## Bugs Fixed


## Code Refactors


## Other Improvements

- **Downgrade release jobs to Ubuntu 18.04 for GLIBC version 2.27.0**

- **Implement error diagnostics**
  
  We implement completely anonymous error reporting using Sentry. We submit error messages when the user receives a fatal error in Console.
  
  To disable diagnostics, use:
  ```
  typedb console --diagnostics-disable=true
  ```

- **Update driver with fetch null fix**

- **Disable CircleCI mac remote cache to avoid too_many_open_files**

    
