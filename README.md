# Grakn Console

[![Grabl](https://grabl.io/api/status/graknlabs/console/badge.svg)](https://grabl.io/graknlabs/console)
[![Slack Status](http://grakn-slackin.herokuapp.com/badge.svg)](https://grakn.ai/slack)
[![Discussion Forum](https://img.shields.io/discourse/https/discuss.grakn.ai/topics.svg)](https://discuss.grakn.ai)
[![Stack Overflow](https://img.shields.io/badge/stackoverflow-grakn-796de3.svg)](https://stackoverflow.com/questions/tagged/grakn)
[![Stack Overflow](https://img.shields.io/badge/stackoverflow-graql-3dce8c.svg)](https://stackoverflow.com/questions/tagged/graql)

## Command line arguments

- `--server=<address>`
    Server address to which the console will connect to.

- `-V, --version`
    Print version information and exit.

- `-h, --help`
    Show help message.

## Console commands

Console implements database management commands and transation querying commands. These commands are separated into two level. Initially database management commands are available, and after opening a transaction, you can start using transaction querying commands.

### Database management commands

- `database list`
    List the databases on the server
- `database create <db>`
    Create a database with name `<db>` on the server
- `database delete <db>`
    Delete a database with name `<db>` on the server
- `transaction <db> schema|data read|write`
    Start a transaction to database `<db>` with schema or data session, with read or write transaction
- `help`
    Print help menu
- `clear`
    Clear console screen
- `exit`
    Exit console

### Transaction querying commands

- `<query>`
    Run Graql query
- `source <file>`
    Run Graql queries in file
- `commit`
    Commit the transaction changes and close transaction
- `rollback`
    Rollback the transaction to the beginning state
- `close`
    Close the transaction without committing changes
- `help`
    Print this help menu
- `clear`
    Clear console screen
- `exit`
    Exit console
