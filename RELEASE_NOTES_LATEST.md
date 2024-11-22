## Distribution

Download from TypeDB Package Repository: https://cloudsmith.io/~typedb/repos/public-release/packages/?q=name:^typedb-console+version:3.0.0-alpha-9


## New Features

- **Update concept APIs to quickly access optional instances and values properties**
 
**Note that some of the primary APIs have changed (e.g., asBoolean -> getBoolean for Attribute and Value, to separate value retrieval and concept casting)**, but their functioning has not.

We generalize the approach to getting concepts properties in TypeDB Drivers, introducing a set of new APIs for fetching optional values related to instances of Concept classes in Java and Python.
Now, all subclasses of Concept have a set of new interfaces starting with try to access IIDs, labels, value types, and values without a need to cast to a specific Instance or Value. These methods can be useful if:
    - you have an established workflow with constant queries and always expect these properties to exist;
    - you want to implement a custom handling of cases where the expected values are missing shorter (without exceptions).

Additionally, value type checks like is_boolean/isBoolean are also declared on the top-level Concept.
Note that casting is still possible, and its benefits are, as usual:
    - static type checking for your programs using TypeDB Driver;
    - access to the non-optional get interfaces for specific subclasses of Concept like get_iid/getIID for Entity and Relation.


## Bugs Fixed

- **Speed up transaction opening and fix parallel**
  - We fix transaction opening for all the supported drivers, speeding up the operation by 2x.
  - We eliminate database-related errors happening during concurrent database management (create and delete operations) by multiple drivers due to cache mismatches.
  - We make transaction opening requests immediately return errors instead of waiting for additional transaction operations to be performed (e.g. commit or query).

- **Remove promises resolves in destructors to eliminate redundant exceptions. Cleanup Python exceptions formatting**

We remove the feature of `Promises` to call `resolve` in destructors in languages like Java and Python. Now, if a promise is destroyed, it's not resolved, and, thus, not awaited. This helps the driver to remove the excessive duplicated errors printing in destructors of promises, although the error is already returned from a commit operation.

Now, if you just run queries and close the transaction, nothing will be awaited and persisted. However, if you commit your transaction, all the ongoing operations on the server side will finish before the actual commit. This lets you speed up the query execution (in case these queries don't collide with each other):
```python
for query in queries:
    tx.query(query)
tx.commit()
```
If one of the `queries` contains an error and it's not resolved, it will be returned from the `commit` call, and no excessive errors will be printed on resource release.

Detailed examples for each language supported are presented in READMEs.

Additionally, Python Driver's `TypeDBDriverError` exceptions no longer show the excessive traceback of its implementation, and only the short informative version for your executed code is presented.

## Code Refactors


## Other Improvements

- **Bump version to 3.0.0-alpha-9**

- **Update factory owners to typedb**
  Update factory owners to typedb
  
- **Update factory owners to typedb**

- **Update final reference of git org to @typedb**
  
- **Replaced Vaticle with TypeDB in strings and copyrights**
  
    
