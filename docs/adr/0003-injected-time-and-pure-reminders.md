# ADR-0003: Inject the clock; make reminders a pure query

**Status:** accepted

## Context

`ReminderSystem` called `LocalDate.now()` inside its loop and printed through `MessageSystem`. It also
kept a `Set<String>` of "already reminded" keys in memory, so behaviour depended on how long the
process had been alive, and the only way to test it was to run it on the right calendar day.

## Decision

* `java.time.Clock` is a constructor dependency of every service. Domain methods take `today`/`now`
  as arguments. Nothing calls `now()` statically.
* `ReminderService.upcoming(soonDays)` is a stateless query returning `Reminder` records. The CLI
  decides how to render them (text or JSON).
* The "remind once per session" set is dropped: `taskflow remind` is a one-shot command, so the
  caller (shell profile, cron) controls frequency.

## Consequences

* Boundary conditions (overdue / today / tomorrow / look-ahead window edges) are tested with a
  fixed clock and are deterministic.
* Output is scriptable (`taskflow remind --json`).
