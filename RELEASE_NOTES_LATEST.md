## Breaking changes
This release breaks backwards compatibility.
This version is only compatible with TypeDB server versions >= 3.11.0.
Connections to older servers will be rejected.

## Distribution

Download from TypeDB Package Repository: https://cloudsmith.io/~typedb/repos/public-release/packages/?q=name:^typedb-console+version:3.11.0-rc1


## New Features
- **Introduce TypeDB cluster support**
  
  Add cluster support to TypeDB Console.
  
  - Support connecting to multi-server clusters via `--address host1:port1,host2:port2,...` (also accessible as `--addresses`).
  - Support address translation for clusters behind NAT/firewalls via `--address-translation pub1=priv1,pub2=priv2,...`.
  - Add `server` command group in the REPL:
    - `server version [address]` — retrieve server distribution and version [from a specific machine].
    - `server list [address]` — list all servers with id, address, role, term, and availability status [from a specific machine].
    - `server primary [address]` — get the current primary server address [from a specific machine].
  - Database and user lists are now sorted alphabetically.
  - Add a 5-minute request timeout for all non-transactional operations.
  
  **Breaking**: `--diagnostics-disable` is renamed to `--diagnostics-disabled` to maintain the same naming convention for all arguments.
  
  

## Bugs Fixed


## Code Refactors


## Other Improvements
- **Update cargo files**
  Update cargo files with the latest dependencies to resolve previously merged conflict markers
  
  
- **Update Rust dependencies**
  
  
  
    
