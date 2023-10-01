
## New Features
- **Deploy one artifact per platform**
  
  We split the typedb-console distribution into 5: one per operating system+architecture. We now publish:
  
  1) `linux-x86_64`
  2) `linux-arm64`
  3) `mac-x86_64`
  4) `mac-arm64`
  5) `windows-x86_64`
  
  We orchestrate all releases through CircleCI, for both artifacts and apt. Build targets in this repository are now platform-native, and deployment rules for specific platforms are protected by Bazel's platform target compatibility flags.
  
  

## Bugs Fixed


## Code Refactors
- **Replace usages of 'client' and 'cluster' with 'driver' and 'enterprise' throughout**
  
  We replace the term 'cluster' with 'enterprise', to reflect the new consistent terminology used through Vaticle. We also replace 'client' with 'driver', where appropriate.
  
  
- **Upgrade underlying typedb-driver**
  
  We upgrade the underlying Java driver to the latest version.
  
  

## Other Improvements

    
