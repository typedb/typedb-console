## Distribution

Download from TypeDB Package Repository: https://cloudsmith.io/~typedb/repos/public-release/packages/?q=name:^typedb-console+version:3.0.0-alpha-6


## New Features
- **Add fetch queries result printing.** We add `ConceptDocument` printer option to show the results of `fetch`
  queries.

  That's how we print the `match` and `fetch` queries results now:
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

## Bugs Fixed


## Code Refactors


## Other Improvements
