
## New Features


## Bugs Fixed


## Code Refactors
- **Reconfigure CircleCI executors to use GLIBC 2.26**
  
  We compile and release TypeDB Console using an older version of Linux, which requires GLIBC 2.26 instead of GLIBC 2.27. This change switches the build platform to Amazon Linux 2 (via Docker), which is based on CentOS, instead of Ubuntu 18.04, which is based on Debian.
  
  Additionally, we upgrade the Windows Orb to 5.0.0, which also allowed using a larger executor to reduce CI time.
  
  

## Other Improvements
- **Shorten diagnostic ID to 16 hex chars**

