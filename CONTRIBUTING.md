# Contributing

## Setup

Requires JDK 21+ and Maven 3.9+.

```bash
mvn verify           # compile (warnings are errors in CI), test, coverage gate, package
java -jar target/taskflow.jar --help
```

## Ground rules

* **Dependencies point inward** (`cli → application → domain`; `infrastructure → application`). The
  domain package imports nothing but the JDK. See [ADR-0001](docs/adr/0001-layered-architecture.md).
* **No static clocks.** Take a `Clock` (services) or a `LocalDate`/`Instant` (domain) as a parameter.
* **Services never print.** Return data; let `cli` render it.
* **New failure mode?** Add it to the sealed `TaskFlowException` hierarchy; the compiler will point
  you at `ExitCodes.of` until it is mapped.
* **New repository implementation?** Extend `TaskRepositoryContractTest`.
* **Never log task titles** (user data) or build terminal output by passing user text through
  picocli markup.
* Keep `stdout` for results and `stderr` for diagnostics so `--json` output stays pipe-safe.

## Commit style

[Conventional Commits](https://www.conventionalcommits.org/) (`feat:`, `fix:`, `test:`, `docs:`...).
