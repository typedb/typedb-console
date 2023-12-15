
## New Features


## Bugs Fixed


## Code Refactors
- **Replace all instances of 'enterprise' with 'cloud'**
  
  We replace the term 'enterprise' with 'cloud', to reflect the new consistent terminology used throughout Vaticle. 
  In particular, this means that to connect to a Cloud instance (previously Enterprise), `typedb console --cloud <address>` replaces `typedb console --enterprise <address>`.
  
- **Update command line interface**
  
  We update the CLI options to use the more distinct `--core` flag for connecting to the core server, mirroring `--enterprise` (note: subsequently renamed to `--cloud`).
  
  Connecting to TypeDB Core:
  ```
  typedb console --core=<address>
  ```
  Connecting to TypeDB Cloud:
  ```
  typedb console --cloud=<address> --username=<username> --password --tls-enabled
  ```
  
  See https://github.com/vaticle/typedb/issues/6942 for full details.
  
  We also improve the UX of the windows version of the entry point. Console no longer opens in a new window, but rather begins the REPL in the current command line window.
  

## Other Improvements
- **Add aliases for encryption enable to match Cloud options more closely**

    
