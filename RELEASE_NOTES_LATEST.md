## Distribution

Download from TypeDB Package Repository: https://cloudsmith.io/~typedb/repos/public-release/packages/?q=name:^typedb-console+version:3.2.0


## New Features
- **Add database 'schema' command to retrieve the database schema**


- **Increase transaction timeout**
  Set transaction timeout for opened transactions to 1 hour.
  
  This change significantly lowers the impact of https://github.com/typedb/typedb-console/issues/287.
  
  
- **Improve multiline query support**
  
  We improve multi-line query support to allow copy-pasting queries and scripts containing empty newlines. In particular this makes pasting entire schema definitions from files. 
  
  For example, pasting a console script opening a transaction, defining a schema containing newlines, and committing, is now possible:
  ```
  >> transaction schema test
      define 
        entity person;  # newlines are allowed in pasted scripts:
       
        attribute name, value string;
      
        person owns name; 
   
      commit
  ```
  
  Empty newlines when written _interactively_ still cause queries to be submitted. However, an explicit query `end;` clause is a valid alternative now:
  
  ```
  >> transaction schema test
      define 
        entity person;  # newlines are allowed in pasted scripts:
       
        attribute name, value string;
      
        person owns name; 
      end; # <--- will submit immediately
  ```
  
  Pasted query pipelines may now be ambiguous, such as the following example which by defaults executs a single "match-insert" query, even though there are newlines:
  ```
  > transaction schema test
    match $x isa person;
  
    insert $y isa person;
  
    commit
  ```
  
  To make this a "match" query and a separate "insert" query, we must use the `end;` markers:
  ```
  > transaction schema test
    match $x isa person;
    end;
  
    insert $y isa person;
    end;
  
    commit
  ```
  
  **Note that now `end` is a reserved keyword and cannot be used as a type!**
  
  

## Bugs Fixed


## Code Refactors


## Other Improvements

- **Update typedb-driver dependency for token-based authentication**
  
  
    
