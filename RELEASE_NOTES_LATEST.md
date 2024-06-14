## Distribution

Download from TypeDB Package Repository: https://cloudsmith.io/~typedb/repos/public-release/packages/?q=name:^typedb-console+version:2.28.4


## New Features


## Bugs Fixed


## Code Refactors


## Other Improvements
- **Bump dependencies for rules-python & pin CircleCI windows executor**
  Bump dependencies for rules-python update. This fixes an error on windows builds in CircleCI.
  We also pin the image used for Windows builds  on CircleCI to prevent updates from breaking the pipeline.
  
- **Turn off statistics reporting in CI**
  We turn off the statistics reporting in our CI builds not to send non-real diagnostics data.
