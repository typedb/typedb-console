# Grakn Console

[![Grabl](https://grabl.io/api/status/graknlabs/console/badge.svg)](https://grabl.io/graknlabs/console)
[![Slack Status](http://grakn-slackin.herokuapp.com/badge.svg)](https://grakn.ai/slack)
[![Discussion Forum](https://img.shields.io/discourse/https/discuss.grakn.ai/topics.svg)](https://discuss.grakn.ai)
[![Stack Overflow](https://img.shields.io/badge/stackoverflow-grakn-796de3.svg)](https://stackoverflow.com/questions/tagged/grakn)
[![Stack Overflow](https://img.shields.io/badge/stackoverflow-graql-3dce8c.svg)](https://stackoverflow.com/questions/tagged/graql)

## Runing Grakn Console in the terminal

Go to the directory whe you have your `grakn-core-all` or `grakn-core-console` distribution unarchived, and run `./grakn console`
```
cd <your_grakn_console_dir>/
./grakn console
```

## Command line arguments

You can provide several command arguments when running console in the terminal.

- `--server=<address>` : Server address to which the console will connect to.
- `-V, --version` : Print version information and exit.
- `-h, --help` : Show help message.

## Console commands

Grakn Console provides two levels of interaction: database-level commands and transaction-level commands. The database-level command is the first level of interaction, i.e. first-level REPL. From one of the database-level commands, you cna open a transaction to the database. This will open a transaction-level interface, i.e. second-level REPL.

### Database-level commands

- `database create <db>` : Create a database with name `<db>` on the server. For example:
  ```
  > database create my-grakn-database
  Database 'my-grakn-database' created
  ```
- `database list` : List the databases on the server. For example:
  ```
  > database list
  my-grakn-database
  ```
- `database delete <db>` : Delete a database with name `<db>` on the server. For example:
  ```
  > database delete my-grakn-database
  Database 'my-grakn-database' deleted
  ```
- `transaction <db> schema|data read|write` : Start a transaction to database `<db>` with session type `schema` or `data`, and transaction type `write` or `read`. For example:
  ```
  > transaction my-grakn-database schema write
  my-grakn-database::schema::write>
  ```
  This will then take you to the transaction-level interface, i.e. the second-level REPL.
- `help` : Print help menu
- `clear` : Clear console screen
- `exit` : Exit console

### Transaction-level commands

- `<query>` : Once you're in the transaction REPL, the terminal immediately accepts a multi-line Graql query, and will execute it when you hit enter twice. For example:
  ```
  my-grakn-database::schema::write> define
                                    name sub attribute, value string;
                                    person sub entity, owns name;

  Concepts have been defined
  ```
- `source <file>` : Run Graql queries in a file, which you can refer to using relative or absolute path. For example:
  ```
  my-grakn-database::schema::write> source ./schema.gql
  Concepts have been defined
  ```
- `commit` : Commit the transaction changes and close transaction. For example:
  ```
  my-grakn-database::schema::write> commit
  Transaction changes committed
  ```
- `rollback` : Will remove any uncommitted changes you've made in the transaction, while leaving transaction open. For example:
  ```
  my-grakn-database::schema::write> rollback
  Rolled back to the beginning of the transaction
  ```
- `close` : Close the transaction without committing changes, and takes you back to the database-level interface, i.e. first-level REPL. For example:
  ```
  my-grakn-database::schema::write> close
  Transaction closed without committing changes
  ```
- `help` : Print this help menu
- `clear` : Clear console screen
- `exit` : Exit console
