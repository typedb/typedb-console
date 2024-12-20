## Distribution

Download from TypeDB Package Repository: https://cloudsmith.io/~typedb/repos/public-release/packages/?q=name:^typedb-console+version:3.0.0


## New Features
- **Introduce TypeDB Console 3.0**

  We introduce the updated TypeDB Console compatible with TypeDB 3.0.
  This bring significant opportunities and UX improvements, including:
    - **Streamlined transactions**: Sessions and transactions are now consolidated into standalone transactions.
    - **Unified authentication**: The updated authentication mechanism is shared across all types of the TypeDB server.
    - **New query result formats**: Introducing `Concept Row`s for table-like outputs and `Concept Document`s for structured outputs used in `fetch` queries.
      ```
      hi::read> match $x isa! person;
                
      Finished validation and compilation...
      Streaming answers...
      
         --------
          $x | iid 0x1e00000000000000000000 isa person
         --------
      
      Finished. Total answers: 1
      hi::read> match $x isa! person; fetch {$x.*};
                
      Finished validation and compilation...
      Streaming documents...
      
      {
          "age": [ 25 ],
          "balance": [ "1234567890.000123456789" ],
          "birth-date": [ "2024-09-20" ],
          "birth-time": [ "1999-02-26T12:15:05.000000000" ],
          "current-time": [ "2024-09-20T16:40:05.000000000 Europe/London" ],
          "current-time-off": [ "2024-09-20T16:40:05.028129323+05:45" ],
          "expiration": [ "P1Y10M7DT15H44M5.003948920S" ],
          "is-new": [ true ],
          "name": [ "John" ],
          "success": [ 66.6 ]
      }
      
      Finished. Total answers: 1
      ```

  Some features are currently disabled due to limitations on the TypeDB Server side:
    - Options.
    - Replicas information.

  Explore all the exciting features of TypeDB 3.0 [here](https://github.com/typedb/typedb/releases).


## Bugs Fixed
- **Speed up transaction opening and fix parallel**
  - We fix transaction opening for all the supported drivers, speeding up the operation by 2x.
  - We eliminate database-related errors happening during concurrent database management (create and delete operations) by multiple drivers due to cache mismatches.
  - We make transaction opening requests immediately return errors instead of waiting for additional transaction operations to be performed (e.g. commit or query).

## Code Refactors


## Other Improvements
- **Update factory owners to typedb**
  Update factory owners to typedb

- **Update factory owners to typedb**

- **Update final reference of git org to @typedb**

- **Replaced Vaticle with TypeDB in strings and copyrights**
  
    
