# TypeDB Console

[![Factory](https://factory.vaticle.com/api/status/typedb/typedb-console/badge.svg)](https://factory.vaticle.com/typedb/typedb-console)
[![GitHub release](https://img.shields.io/github/release/typedb/typedb-console.svg)](https://github.com/typedb/typedb-console/releases/latest)
[![Discord](https://img.shields.io/discord/665254494820368395?color=7389D8&label=chat&logo=discord&logoColor=ffffff)](https://typedb.com/discord)
[![Discussion Forum](https://img.shields.io/discourse/https/forum.typedb.com/topics.svg)](https://forum.typedb.com)
[![Stack Overflow](https://img.shields.io/badge/stackoverflow-typedb-796de3.svg)](https://stackoverflow.com/questions/tagged/typedb)
[![Stack Overflow](https://img.shields.io/badge/stackoverflow-typeql-3dce8c.svg)](https://stackoverflow.com/questions/tagged/typeql)

## Running TypeDB Console 

Go to the directory where you have your `typedb-all` or `typedb-console` distribution unarchived, and run `./typedb console`
```bash
cd <your_typedb_console_dir>/
./typedb console
```

To build and run from Cargo, use:
```bash
cargo run -- --username=<username> --address=<address>
```

Or to use bazel, use:
```bash
bazel run //:console-binary-native -- --username=<username> --address=<address>
```

TypeDB console binaries are platform-specific, so cannot be moved across platforms - please use the correct
platform-specific distribution.

## Command line arguments

You can provide several command arguments when running console in the terminal.

- `--username=<username>` : TypeDB server username to log in with (mandatory).
- `--address=<address>` : TypeDB server address to which the console will connect to.
- `--file=<file>` : Run commands in the script file in non-interactive mode.
- `--command=<command1> --command=<command2> ...` : Run commands in non-interactive mode.
- `-V, --version` : Print version information and exit.
- `-h, --help` : Show help message.

TypeDB Console will by default prompt you for your password in a safe way. If you must,
you are still able to pass in the login password with `--password=<password>`.

**By default, TLS encryption is enabled to ensure passwords are not sent over the network in plaintext**

For development or local work, you can disable this with:

`--tls-disabled`

For TypeDB Cloud deployments, there is **no reason to use this setting** as they can only operate with network TLS encryption.

Alternatively, you may securely connect by managing your own certificates for both the client-side and server-side,
and provide your certificate to the console with:
`--tls-root-ca=<path>`

See documentation at https://typedb.com/docs/manual/configure/encryption for further details.

## Console commands

TypeDB Console provides two levels of interaction: server-level commands and transaction-level commands. 
To enter the transaction command mode, open a transaction, using a `transaction` command.

Console offers command completion, accessible with a `tab` keypress. Some non-keyword data, such as database and usernames,
will also be autocompleted, while others, such as queries, will not.

### Database-level commands

- `database create <db>` : Create a database with name `<db>` on the server.
  ```
  > database create my-typedb-database
  Successfully created database.
  ```
- `database list` : List the databases on the server. 
  ```
  > database list
  my-typedb-database
  ```
- `database delete <db>` : Delete a database with name `<db>` on the server.
  ```
  > database delete my-typedb-database
  Successfully deleted database.
  ```
- `transaction read|write|schema <db>` : Start a `read`, `write`, or `schema` transaction to database `<db>`.
  ```
  > transaction schema my-typedb-database
  my-typedb-database::schema>
  ```
  This will then take you to the transaction-level interface, i.e. the second-level REPL.
- `help` : Print help menu
- `exit` : Exit console

### Transaction-level commands

- `<query>` : Once you're in the transaction REPL, the terminal immediately accepts a multi-line TypeQL query, and will execute it when you hit enter twice.
  ```
  my-typedb-database::schema>> define
                              attribute name, value string;
                              entity person, owns name;
  
  Finished schema query.
  >>
  ```
- `source <file>` : Run TypeQL queries in a file, which you can refer to using relative or absolute path. Multiline TypeQL queries in these files must be indicated by using the backslash (\) character
  ```
  my-typedb-database::schema> source ./schema.tql
  Successfully executed 1 queries.
  ```
- `commit` : Commit the transaction changes and close transaction. For example:
  ```
  my-typedb-database::schema> commit
  Successfully committed transaction.
  ```
- `rollback` : Will remove any uncommitted changes you've made in the transaction, while leaving transaction open. For example:
  ```
  my-typedb-database::schema> rollback
  Transaction changes rolled back.
  ```
- `close` : Close the transaction without committing changes, and takes you back to the database-level interface, i.e. first-level REPL. For example:
  ```
  my-typedb-database::schema> close
  Transaction closed without committing changes
  ```
- `help` : Print this help menu
- `exit` : Exit console

### Non-interactive mode

To invoke console in a non-interactive manner, we can define a script file that contains the list of commands to run, then invoke console with `./typedb console --file=<file>`. We can also specify ordered commands to run directly from the command line using `./typedb console --command=<command1> --command=<command2> ...`.

For example given the following command script file:

```
database create test
transaction schema test
    define entity person;
    commit
transaction write test
    insert $x isa person;
    commit
transaction read test
    match $x isa person;
    close
database delete test
```

You will see the following output:
```
./typedb console --username="user" --password="password" --file=commands.tql     
>> database create test
Successfully created database.
>> transaction schema test
test::schema>>     define entity person;
Finished schema query.
test::schema>>     commit
Successfully committed transaction.
>> transaction write test
test::write>>     insert $x isa person;
Finished write query validation and compilation...
Finished writes...
Streaming answers...
   --------
    $x | isa person, iid 0x1e00000000000000000000
   --------
test::write>>     commit
Successfully committed transaction.
>> transaction read test
test::read>>     match $x isa person;
Finished read query validation and compilation...
Streaming answers...
   --------
    $x | isa person, iid 0x1e00000000000000000000
   --------
test::read>>     close
Transaction closed
>> database delete test
Successfully deleted database.

```

The indentation in the script file are only for visual guide and will be ignored by the console. 
Each line in the script is executed as a single command, unless split over multiple lines using a backslash (\) character.
