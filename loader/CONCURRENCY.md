# Loader concurrency model

## Shape

```
[CSV reader] → [main coroutine] ⇄ [N tokio worker tasks] → [TypeDB driver]
                       │
                       └→ [RejectsWriter (file)]
```

- **Main coroutine** drives the CSV reader, dispatches batches to workers, awaits
  their results, updates stats, writes rejected rows to the rejects CSV/log,
  prints progress, and decides when to stop.
- **Workers** (`tokio::spawn`'d tasks) each take ownership of one
  `data::BatchOutcome`, open a write transaction, call `query_with_inputs`,
  commit, and return a `BatchResult` containing the batch's rejection data.
  Up to `--parallel-batches` workers run at once, gated by `FuturesUnordered`
  in main.
- **`RejectsWriter`** is owned solely by the main coroutine. Workers never see
  it; they hand back `Vec<RowRejection>` plus the parallel `Vec<StringRecord>`
  in the `BatchResult` and main is the only thing that calls
  `record_row`/`record_batch_failure`/`flush`.

## Why a single writer in main

We considered three designs:

1. **Workers write directly with a `Mutex<RejectsWriter>`.** Cheapest to write,
   adds locking on the hot path, and contention scales with worker count.
2. **Dedicated writer task with an `mpsc` channel.** Workers send rejections to
   a writer task. Workers don't block on file I/O, but adds moving parts:
   channel, separate join handle, careful shutdown ordering (close sender →
   await writer drain).
3. **Single writer in main (chosen).** Workers return rejection data in
   `BatchResult`. Main is the only thing that touches the file. No locks, no
   second task to manage, and the "no concurrent writes" invariant is true
   *by construction* — workers don't hold a writer handle.

The deciding factor was that file writes are not the bottleneck. A typical load
is dominated by network round-trips to TypeDB; rejections (with flush) take ~ms
each. Locking or a dedicated writer task would add complexity to fix a
bottleneck that doesn't exist for normal workloads.

## Trade-offs we accepted

- **Throughput dip under heavy rejection rates.** When main is writing a
  rejection (and flushing), it isn't dispatching new batches or collecting
  results. For sporadic rejections this is invisible; for loads where most
  rows reject, the writer becomes a serial bottleneck. If that becomes a real
  workload, switch to design #2 (dedicated writer task).
- **Stop-condition over-shoot.** When `--stop-on-error` or `--max-rejects`
  fires, up to `parallel_batches × batch_rows` rows may already be in flight.
  We let them complete (see Shutdown below), so they're fully recorded — but
  the final committed/rejected counts will exceed the threshold. This is
  inherent to parallel batching.

## Durability

`process::exit` (via `fatal()`) skips `Drop`, so any data buffered inside
`csv::Writer` or `BufWriter` would be lost. To make this safe,
`RejectsWriter::record_row` and `record_batch_failure` flush both writers
before returning. Cost is one extra syscall per write, negligible vs. the
network commit it accompanies.

## Shutdown

Normal completion: CSV reader returns `None` → main stops dispatching → drain
remaining workers → flush rejects → print summary.

Triggered stop (`--stop-on-error` or `--max-rejects`): set `stop_now` → main
stops dispatching new batches but **lets in-flight workers finish gracefully**.
Their results are processed normally (stats updated, rejections written), so
the final picture reflects everything that actually happened. Once the
in-flight set drains, flush rejects → print summary → exit non-zero with the
captured stop reason.

Worst-case extra latency on triggered stop is the longest running in-flight
batch's commit time. SIGINT/Ctrl-C remains the escape hatch if a transaction
hangs (tokio cancels the runtime).

## Sequential default

`--parallel-batches 1` (default) preserves the previous strictly-sequential
behaviour: one batch is in flight at a time and main awaits each result before
dispatching the next. Same code path as `N > 1`; the cap simply forces serial
execution.
