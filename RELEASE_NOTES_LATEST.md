## Distribution

Download from TypeDB Package Repository: https://cloudsmith.io/~typedb/repos/public-release/packages/?q=name:^typedb-console+version:2.28.0-rc0


## New Features
- **Cloud address translation**

  We allow connection to the cloud servers using an address translation mapping (cf. https://github.com/vaticle/typedb-driver/pull/624). This is useful when the route from the user to the servers differs from the route the servers are configured with (e.g. connection to public-facing servers from an internal network).

  Example usage:
  ```bash
  console \
      --cloud=typedb1.domain.com:1729=typedb.local:11729,typedb2.domain.com:1729=typedb.local:21729 \
      --username=<user> --password=<password>
  ```
  or:
  ```bash
  console \
      --cloud=typedb1.domain.com:1729=typedb.local:11729 \
      --cloud=typedb2.domain.com:1729=typedb.local:21729 \
      --username=<user> --password=<password>
  ```

  Note: we currently require that the user provides translation for the addresses of _all_ nodes in the Cloud deployment.

## Bugs Fixed


## Code Refactors


## Other Improvements
- **Merge master into development after 2.27.0 release**

  We merge changes made during the release of 2.27.0 back into development.

