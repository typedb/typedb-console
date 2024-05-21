## Distribution

Download from TypeDB Package Repository: https://cloudsmith.io/~typedb/repos/public-release/packages/?q=name:^typedb-console+version:2.28.2-rc1


## New Features


## Bugs Fixed


## Code Refactors


## Other Improvements
- **Turn off statistics reporting in CI**
  We turn off the `--diagnostics.reporting.statistics` in our CI builds not to send non-real diagnostics data.
  
  In version 2.28 and earlier, this flag purely prevents `TypeDB` from sending any diagnostics data.
  In the upcoming version 2.28.1, this flag still allows `TypeDB` to send a single diagnostics snapshot with the information of when the diagnostics data has been turned off, but it happens only after the server runs for 1 hour, so we expect the CI builds not to reach this point and not to send any diagnostics data as well.
