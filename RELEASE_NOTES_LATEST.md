## Distribution

**This is an alpha release for CLUSTERED TypeDB 3.x. Do not use this to connect to a stable version of TypeDB.**
**Instead, reference a non-alpha release of the same major and minor versions.**

Download from TypeDB Package Repository: https://cloudsmith.io/~typedb/repos/public-release/packages/?q=name:^typedb-console+version:3.7.0-alpha-0


## New Features

### Introduce TypeDB Console for Clusters

TypeDB Console now supports clustering and replicated requests. Since it's an alpha release, the release notes are limited, but more documentation is expected soon.

Try it out using:
- `--address` for a single address
- `--addresses` if you want to specify multiple addresses
- `--address-translation` if you want to specify address translation for your addresses
- `--replication-disabled` if you don't want Console to automatically connect to other replicas not specified in your address list
- `--help` to get the full list of commands with examples

**Note: unlike in 2.x, you're not forced to specify every address of the cluster in the connection command. If your cluster is stable, specify a single address, and TypeDB Console will fetch its replicas for automatic connection.**

## Bugs Fixed


## Code Refactors


## Other Improvements
  
  
    
