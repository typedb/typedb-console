# TypeDB Loader

A CSV-driven bulk loader for TypeDB. Each CSV row is bound to the variables of a TypeQL query
template and submitted in batches over write transactions, with checkpointing, rejects capture,
and resumable runs.

## Example: loading people from a CSV

### 1. Schema (`schema.tql`)

```typeql
define
attribute name, value string;
attribute age, value integer;
attribute active, value boolean;
entity person owns name @key, owns age, owns active;
```

### 2. Query template (`query.tql`)

```typeql
given $name: string, $age: integer?, $active: boolean?;
insert
  $p isa person, has name == $name;
  try { $p has age == $age; };
  try { $p has active == $active; };
```

The `given` variables become the loader's inputs. The `?` marks them optional, and
`try { ... };` lets a row succeed even when that column is empty.

### 3. Data (`data.csv`)

```csv
name,age,active
alice,34,true
bob,,false
carol,29,
```

CSV header names must match the `given` variable names exactly.

### 4. Run

```bash
typedb-loader \
  --address localhost:1729 --username admin \
  --database people \
  --schema-file schema.tql --create-db \
  --query query.tql \
  --data data.csv --header \
  --null-values '' --null-values 'NULL' \
  --batch-rows 1000 --parallel-batches 4 \
  --max-rejects 100
```

### 5. Resuming

If the run is interrupted (Ctrl+C, network blip, server restart), restart it with:

```bash
typedb-loader --resume data-checkpoint.json --password ...
```

The checkpoint stores the original parameters and a hash of the data + live schema; you'll get a
warning prompt if either has drifted.

## General advice

- **`given` ↔ CSV headers**: the loader binds CSV columns to query variables by name. Mismatches
  surface as parse rejections — check the rejects log first.
- **Make optional columns optional in the query, not the CSV**: declare `$x: T?` in `given` and
  wrap inserts in `try { ... };`. Without `--null-values`, only empty cells become absent inputs.
- **`--null-values` replaces the empty-cell default — it doesn't extend it.** As soon as you pass
  `--null-values NA`, empty cells stop being treated as null. The example above includes
  `--null-values ''` explicitly so that both empty cells and the string `NULL` count as null.
- **`--max-rows 0` clears an inherited cap on resume.** `--max-rows` is persisted in the
  checkpoint, so a resumed run inherits the original cap. To resume past it without specifying a
  new finite limit, pass `--max-rows 0` to mean "no cap at all".
- **Tune `--batch-rows` and `--parallel-batches` together**: larger batches mean fewer commits but
  longer write transactions; more parallelism means more concurrent write conflicts. Start at
  `1000 × 4` and adjust based on commit failures in the rejects log.
- **Don't change `--batch-rows` on resume** — the loader's cursor depends on it and will refuse to
  continue.
- **`--schema-file` and `--create-db` are ignored on resume**, with a warning. Keep schema setup
  as a separate one-shot step if you expect to iterate.
- **Two rejects files are written**: `*-rejects.csv` (the raw failing rows, reloadable) and
  `*-rejects.log` (the per-row error message). Re-running the loader on the rejects CSV after a
  fix is a common workflow.
- **Use `--stop-on-error` for schema bring-up** (so you catch query/CSV mismatches immediately)
  and **`--max-rejects N` for bulk loads** (let bad rows accumulate, abort if the failure rate is
  too high).
- **TLS is on by default.** Only use `--tls-disabled` against a local dev server.
