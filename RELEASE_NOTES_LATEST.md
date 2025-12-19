## Distribution

<<<<<<< HEAD
Download from TypeDB Package Repository: https://cloudsmith.io/~typedb/repos/public-release/packages/?q=name:^typedb-console+version:3.10.1
=======
**This is an alpha release for CLUSTERED TypeDB 3.x. Do not use this to connect to a stable version of TypeDB.**
**Instead, reference a non-alpha release of the same major and minor versions.**

Download from TypeDB Package Repository: https://cloudsmith.io/~typedb/repos/public-release/packages/?q=name:^typedb-console+version:3.7.0-alpha-0
>>>>>>> f842420 (Update release notes and temporarily disable TLS checks)


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
<<<<<<< HEAD
- **Restore dependencies folder because it's hardcoded**
  Restore dependencies folder because it's hardcoded
  
  
- **Fix windows deployment jobs**
  Usage and product changes Fix windows deployment jobs by adding --enable-runfiles. Also updates the patch.
  
- **Bazel 8 upgrade**
  
  Update Bazel version from 6.2 to 8.0 and migrate from WORKSPACE to Bzlmod.
  
  
- **Fix Mac CI builds by override bazel's python version locally**
  
  
  
- **Add a symlink for python 3.9 in CI to override the default Mac's python version**
  Updated Mac image depends on python 3.13, which conflicts with the outdated dependencies and ignores the pre-installed python@3.9. We create a symlink to python 3.9 for the Mac build to succeed.
  
  
- **Update dependencies to the latest commit**
  
- **Update circleci mac executor to address the old resource deprecation**
  Update CircleCI's mac executor to the version matching `typedb-driver` https://github.com/typedb/typedb-driver/commit/bfd66729a29ab4df7e23cf118b040c9f5af78b2e
=======
>>>>>>> f842420 (Update release notes and temporarily disable TLS checks)
  
  
    
