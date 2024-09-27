# TypeDB Console

[![Factory](https://factory.vaticle.com/api/status/vaticle/typedb-console/badge.svg)](https://factory.vaticle.com/vaticle/typedb-console)
[![GitHub release](https://img.shields.io/github/release/vaticle/typedb-console.svg)](https://github.com/vaticle/typedb-console/releases/latest)
[![Discord](https://img.shields.io/discord/665254494820368395?color=7389D8&label=chat&logo=discord&logoColor=ffffff)](https://typedb.com/discord)
[![Discussion Forum](https://img.shields.io/discourse/https/forum.typedb.com/topics.svg)](https://forum.typedb.com)
[![Stack Overflow](https://img.shields.io/badge/stackoverflow-typedb-796de3.svg)](https://stackoverflow.com/questions/tagged/typedb)
[![Stack Overflow](https://img.shields.io/badge/stackoverflow-typeql-3dce8c.svg)](https://stackoverflow.com/questions/tagged/typeql)

## Running TypeDB Console in the terminal

Go to the directory whe you have your `typedb-all` or `typedb-console` distribution unarchived, and run `./typedb console`
```
cd <your_typedb_console_dir>/
./typedb console
```

## Command line arguments

You can provide several command arguments when running console in the terminal.

- `--core=<address>` : TypeDB server address to which the console will connect to.
- `--script=<script>` : Run commands in the script file in non-interactive mode.
- `--command=<command1> --command=<command2> ...` : Run commands in non-interactive mode.
- `-V, --version` : Print version information and exit.
- `-h, --help` : Show help message.

## Console commands

TypeDB Console provides two levels of interaction: database-level commands and transaction-level commands. The database-level command is the first level of interaction, i.e. first-level REPL. From one of the database-level commands, you can open a transaction to the database. This will open a transaction-level interface, i.e. second-level REPL.

Console also offers command completion, accessible with a `tab` keypress.

### Database-level commands

- `database create <db>` : Create a database with name `<db>` on the server. For example:
  ```
  > database create my-typedb-database
  Database 'my-typedb-database' created
  ```
- `database list` : List the databases on the server. For example:
  ```
  > database list
  my-typedb-database
  ```
- `database delete <db>` : Delete a database with name `<db>` on the server. For example:
  ```
  > database delete my-typedb-database
  Database 'my-typedb-database' deleted
  ```
- `transaction <db> read|write|schema` : Start a `read`, `write`, or `schema` transaction to database `<db>`. For example:
  ```
  > transaction my-typedb-database schema
  my-typedb-database::schema>
  ```
  This will then take you to the transaction-level interface, i.e. the second-level REPL.
- `help` : Print help menu
- `clear` : Clear console screen
- `exit` : Exit console

### Transaction-level commands

- `<query>` : Once you're in the transaction REPL, the terminal immediately accepts a multi-line TypeQL query, and will execute it when you hit enter twice. For example:
  ```
  my-typedb-database::schema> define
                              attribute name, value string;
                              entity person, owns name;

  Success
  ```
- `source <file>` : Run TypeQL queries in a file, which you can refer to using relative or absolute path. For example:
  ```
  my-typedb-database::schema> source ./schema.tql
  Finished writes
  ```
- `commit` : Commit the transaction changes and close transaction. For example:
  ```
  my-typedb-database::schema> commit
  Transaction changes committed
  ```
- `rollback` : Will remove any uncommitted changes you've made in the transaction, while leaving transaction open. For example:
  ```
  my-typedb-database::schema> rollback
  Transaction changes have rolled back, i.e. erased, and not committed
  ```
- `close` : Close the transaction without committing changes, and takes you back to the database-level interface, i.e. first-level REPL. For example:
  ```
  my-typedb-database::schema> close
  Transaction closed without committing changes
  ```
- `help` : Print this help menu
- `clear` : Clear console screen
- `exit` : Exit console

### Non-interactive mode

To invoke console in a non-interactive manner, we can define a script file that contains the list of commands to run, then invoke console with `./typedb console --script=<script>`. We can also specify the commands to run directly from the command line using `./typedb console --command=<command1> --command=<command2> ...`.

For example given the following command script file:

```
database create test
transaction test schema 
    define entity person;
    commit
transaction test write
    insert $x isa person;
    commit
transaction test read
    match $x isa person;
    close
database delete test
```

You will see the following output:
```
typedb console --script=script      
+ database create test
Database 'test' created
+ transaction test schema
++ define entity person;
Success
++ commit
Transaction changes committed
+ transaction test write
++ insert $x isa person;
Finished validation and compilation...
Finished writes. Streaming answers...

   --------
    $x | iid 0x1e00000000000000000000 isa person
   --------

Finished. Total answers: 1
++ commit
Transaction changes committed
+ transaction test read
++ match $x isa person;
Finished validation and compilation...
Streaming answers...

   --------
    $x | iid 0x1e00000000000000000000 isa person
   --------

Finished. Total answers: 1
++ close
Transaction closed
+ database delete test
Database 'test' deleted
```

The indentation in the script file are only for visual guide and will be ignored by the console. Each line in the script is interpreted as one command, so multiline query is not available in this mode.
