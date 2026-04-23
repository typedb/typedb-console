## Distribution

Download from TypeDB Package Repository: https://cloudsmith.io/~typedb/repos/public-release/packages/?q=name:^typedb-console+version:3.10.1


## New Features


## Bugs Fixed


## Code Refactors


## Other Improvements
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
  
  
    
