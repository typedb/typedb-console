## Distribution

Download from Cloudsmith: https://cloudsmith.io/~typedb/repos/public-release/packages/?q=name:^typedb-console+version:2.26.6-rc1


## New Features
- **Allow unicode TypeQL variables**
  
  We update to the latest TypeQL, which supports unicode variables. This means TypeDB Console can now use unicode variables, as well as labels and string attribute values. 
  
  For example, we can use Mandarin character sets:
  
  ```
  test::schema::write> define 人 sub entity;
                    
  Concepts have been defined
  
  test::schema::write*> commit
  Transaction changes committed
  > transaction test data write
  test::data::write> insert $人 isa 人; 
                  
  { $人 iid 0x826e80017fffffffffffffff isa 人; }
  
  answers: 1, total duration: 102 ms
  
  test::data::write*> commit
  Transaction changes committed
  > transaction test data read
  test::data::read> match $人 isa 人; get;
                 
  { $人 iid 0x826e80018000000000000000 isa 人; }
  
  answers: 1, total duration: 54 ms
  ```
  
  
  

## Bugs Fixed


## Code Refactors
- **Use typedb-common from typeql/common, only deploy to CloudSmith**
  
  We update Bazel dependencies and target paths following the merging of typedb-common into [vaticle/typeql](https://github.com/vaticle/typeql/) (see https://github.com/vaticle/typeql/pull/313).
  
  We also no longer upload build artifacts to the github releases page. Instead, the artifacts are available from our public cloudsmith repository, linked in the release notes.
  
  
- **Bring in launch binary and console runner library from common**
  
  We move the `binary` package and `console-runner` into this repository from typedb-common. `typedb-console-runner` is deployed to maven such that we can safely depend on it from other repos without creating Bazel dependency cycles.
  

## Other Improvements
- **Explicitly install python tool dependencies**
  
  Since the upgrade to rules-python v0.24 (https://github.com/vaticle/dependencies/pull/460), we are required to explicitly install python dependencies in the WORKSPACE file. The python tools happened to be unused, so these errors were not visible until the sync dependencies tool was restored.
  
- **Sync dependencies in CI**
  
  We add a sync-dependencies job to be run in CI after successful snapshot and release deployments. The job sends a request to vaticle-bot to update all downstream dependencies.
  
  Note: this PR does _not_ update the `dependencies` repo dependency. It will be updated automatically by the bot during its first pass.
  
- **Only submit uncaught exceptions to diagnostics**

- **Set up CI filters for master-development workflow**

- **Make console runner use the same java installation as the calling process**
  Makes TypeDB console runner use the  same java installation as the calling process, so the system remain hermetic. 
  
- **Fix CI file and disable Core diagnostics in test**

- **Migrate artifact hosting to cloudsmith**
  Updates artifact credentials, and deployment & consumption rules to use cloudsmith (repo.typedb.com) instead of the self-hosted sonatype repository (repo.vaticle.com).
  
- **Remove typedb-console-runner's dependency on typedb-common**
  
  We remove `typedb-console-runner`'s dependency on common in order to reduce deployment complexity and make the maven library self-contained.
  
- **Force hermitic JDK for builds**

- **Fix install-bazel-apt typo**

    
