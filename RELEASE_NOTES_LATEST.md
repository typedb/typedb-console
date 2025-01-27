## Distribution

Download from TypeDB Package Repository: https://cloudsmith.io/~typedb/repos/public-release/packages/?q=name:^typedb-console+version:3.0.4


## New Features
- **Update console to support TypeDB 3.0.4 and its features**

## Bugs Fixed
- **Fix printing of variables with empty values and rows with no columns**
  Enhance printing logic to handle two special cases of received answers:
  
  * When a variable with no values is returned, an empty result will be shown instead of a crash (it used to be considered an impossible situation).
  * When concept rows are returned, but they do not have any columns inside (possible for `delete` stages), a special message is written, and the number of answers (rows) is still presented.
