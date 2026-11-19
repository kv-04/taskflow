# ADR-0001: Layered architecture with dependencies pointing inward

**Status:** accepted

## Context

The original prototype had `Main` reaching into `TaskManager`'s internal list
(`taskManager.getTasks().add(...)`), business logic that printed directly to `System.out`,
reminder state held in an in-memory set, and date logic reading the system clock statically.
None of that could be unit-tested without capturing stdout and mocking time.

## Decision

Four packages, with dependencies pointing only inward:

```
cli  ──►  application  ──►  domain
              ▲
infrastructure ┘   (implements application's TaskRepository port)
```

* **domain** – immutable `Task` record, enums, sealed exception hierarchy. No I/O, no framework,
  no clock. Invalid states are unrepresentable.
* **application** – `TaskService`, `ReminderService`, and the `TaskRepository` *port*. Pure
  orchestration; returns data, never prints.
* **infrastructure** – the JSON-file adapter, legacy-format parser, data-directory resolution.
* **cli** – picocli commands, rendering, exit codes, and the composition root (`TaskFlowCli.create`).

## Consequences

* Business logic is tested against an in-memory repository; the real adapter is held to the same
  behaviour by a shared contract test (`TaskRepositoryContractTest`).
* Swapping storage (SQLite, a remote API) touches only `infrastructure`.
* More files than a one-class prototype. That is the intended trade: each file has one reason to change.
