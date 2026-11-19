# ADR-0002: Versioned JSON file with atomic writes (not SQLite, not the pipe-delimited format)

**Status:** accepted

## Context

The prototype persisted `description|date|completed` lines. It broke on any description containing
`|`, had no schema version, wrote non-atomically (a crash mid-write truncates the file), and kept
`tasks.txt` in the *current working directory*, so launching from another folder showed an empty list.

## Options considered

1. **SQLite (JDBC).** Real transactions and queries, but adds a native driver, a heavier jar, and
   is overkill for hundreds of rows.
2. **Keep a line format, fix escaping.** Still unversioned and hard to evolve.
3. **Versioned JSON, atomic replace, lock around read-modify-write.** Human-readable, diff-able,
   trivially backed up, and sufficient for a single-user CLI.

## Decision

Option 3. Each operation: take JVM lock → take exclusive OS file lock → read → mutate → write temp
file in the same directory → `fsync` → atomic rename.

* `schemaVersion` is checked on load; newer files are refused rather than mangled.
* Ids come from a persisted `nextId` counter and are never reused.
* Corrupt files raise `StorageException` and are never overwritten.
* Storage sits behind the `TaskRepository` port, so moving to SQLite later is an adapter change.

## Consequences

* Every operation reads the whole file: O(n) per command. Fine up to tens of thousands of tasks
  (the file is capped at 16 MiB); beyond that, switch adapters.
* Last-writer-wins is impossible by construction: concurrent processes serialise on the lock.
