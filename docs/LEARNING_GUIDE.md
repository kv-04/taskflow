# TaskFlow Learning Guide

How this project works, why each choice was made, how to reuse each idea in other projects, and how
to test everything yourself. Read it with the code open beside it.

---

## Part 0 - Your two build errors, and what they teach

**1. `expression not allowed as annotation value` (TaskTest line 81).**
Annotations such as `@ValueSource(strings = {...})` are read by the compiler, so their values must be
*compile-time constants*: literals, or `static final` primitives and Strings initialised with
literals. `"abc".repeat(5)` is a method call, evaluated only at run time, so it is rejected. Fix: write
the long string out as a literal. Lesson: when something is "configuration for the compiler or
framework" (annotations), you cannot compute it.

**2. Spotless `NoSuchMethodError ... javac.util.Log$DeferredDiagnosticHandler`.**
Spotless's formatter reaches into the JDK's *internal* compiler classes. Those internals change
between JDK versions, and your JDK is newer than the formatter version I pinned. Fix: I removed the
plugin; formatting is optional. Lesson: tools that touch JDK internals break on JDK upgrades. Pin
versions, and keep non-essential tooling out of the build that must always pass.

Both are fixed in the updated zip. Notice that *main* code compiled on your Mac. Only the tests and
the optional formatter failed. A failing build is information, not a verdict.

---

## Part 1 - How to read this project

Read in this order. Each step only depends on the ones before it.

1. `domain/Task.java` - the heart. No frameworks.
2. `domain/*Exception.java`, `Urgency`, `Priority`, `Status`.
3. `application/TaskRepository.java` (an interface), then `TaskService`, `ReminderService`.
4. `infrastructure/JsonFileTaskRepository.java` - the storage.
5. `cli/TaskFlowCli.java` (wiring), then any `*Command.java`.
6. `src/test/...` - for every class you read, read its test next.
7. `pom.xml`, `.github/workflows/ci.yml`, `Dockerfile`, `docs/adr/*`.

Rule of thumb for any codebase: **start from the data, then the rules, then the plumbing, then the
UI.**

---

## Part 2 - The concepts

Each section: **What** - **Why** - **Where** - **Use it elsewhere**.

### 2.1 Maven and the build lifecycle

**What.** `pom.xml` describes the project: its libraries (dependencies) and the tools that build it
(plugins). Maven runs *phases* in order: `compile` -> `test` -> `package` -> `verify`. Running a later
phase runs all earlier ones. So `mvn verify` = compile + run tests + build the jar + run quality gates.

**Why.** One command gives the same result on your laptop and in CI.

**Where.** `pom.xml`:
- `<dependencyManagement>` with the Jackson and JUnit *BOMs* ("bills of materials"): one version
  decision keeps all related libraries compatible.
- `maven-compiler-plugin`: `release 21`, `-Xlint:all` (all warnings on). CI adds
  `-Dcompiler.failOnWarning=true`, so new warnings fail the build.
- `jacoco`: measures which lines tests executed, and fails the build under 80%.
- `maven-shade-plugin`: bundles everything into one runnable `target/taskflow.jar`.
- `maven-enforcer-plugin`: refuses to build on an old JDK or Maven.
- `project.build.outputTimestamp`: makes the jar byte-for-byte reproducible.

**Use elsewhere.** Every Java project: pin Java version, use a BOM, run tests in `verify`, add a
coverage gate once you have tests (start low, raise over time).

### 2.2 Layered architecture ("dependencies point inward")

**What.** Code split into layers, where inner layers know nothing about outer ones:

```
cli  ->  application  ->  domain
              ^
infrastructure ┘
```

- **domain**: the business concepts and rules (`Task`). Imports only the JDK.
- **application**: use cases (`TaskService`: "complete a task"). Talks to storage only through an
  *interface* (`TaskRepository`) called a **port**.
- **infrastructure**: concrete **adapters** (JSON file storage) that implement the ports.
- **cli**: the user interface. Parses text, calls services, prints results.

**Why.** Your original `Main` created tasks, mutated lists, printed and read files all in one place,
so nothing could be tested alone. With layers, you can test the rules without files, swap storage
without touching rules, and swap the CLI for a web API without touching either.

**Where.** `docs/adr/0001-layered-architecture.md`. Check it yourself: `grep -r "import dev.taskflow"
src/main/java/dev/taskflow/domain` should print nothing.

**Use elsewhere.** Ask of every class: "what is the one thing it knows about?" If a rule class imports
`java.sql` or a web framework, a layer is leaking. For Spring Boot: controllers = cli layer,
`@Service` = application, JPA repositories = infrastructure, entities/value objects = domain.

### 2.3 Immutable records and "make illegal states impossible"

**What.** `Task` is a Java `record`: fields set once, never changed. Changing a task creates a new
one (`task.complete(now)` returns a new `Task`). The *constructor* checks every rule (title not
blank, no control characters, tags valid, `DONE` must have a completion time).

**Why.** If an invalid `Task` can never exist, no other code needs defensive checks. Immutable
objects are safe to share between threads and easy to reason about. It is the principle "parse,
don't validate": convert untrusted input into a trusted type once, at the edge.

**Where.** `domain/Task.java` (the compact constructor), `Task.create`, `complete`, `reopen`.
Note `complete` on an already-done task returns the *same* object (idempotent).

**Use elsewhere.** Wrap meaningful values in types with validation (`Email`, `Money`, `UserId`)
instead of passing raw `String`/`double` around. Prefer `final` fields and "with"-style methods.

### 2.4 Dependency injection by hand

**What.** A class never creates what it needs; it receives it in its constructor:
`new TaskService(repository, clock)`. The one place that builds the whole object graph is the
**composition root**: `TaskFlowCli.create(...)` and `context()`.

**Why.** Because dependencies are passed in, tests can pass fakes. No framework is needed; Spring and
Guice only automate this same pattern.

**Where.** Constructors of `TaskService`, `ReminderService`, `JsonFileTaskRepository`;
`TaskFlowCli.context()`.

**Use elsewhere.** Avoid `new` for collaborators and avoid static singletons inside logic. Create
things in `main` (or the framework's configuration) and hand them down.

### 2.5 Injecting time (`java.time.Clock`)

**What.** Nothing calls `LocalDate.now()`. Services hold a `Clock`; the domain receives `today`/`now`
as parameters. Production uses the system clock; tests use `Clock.fixed(...)`.

**Why.** A test that depends on today's date passes today and fails tomorrow. With a fixed clock,
"overdue by 3 days" is the same forever. Your original `ReminderSystem` could only be tested by
changing the real date.

**Where.** `TaskService`, `ReminderService`, tests' `CLOCK` constants, ADR-0003.

**Use elsewhere.** Same trick for randomness (inject a `Random`/`UUID` supplier), environment
variables (`DataDirResolver` takes a `Map`, not `System.getenv()`), and anything non-deterministic.

### 2.6 Errors as a sealed hierarchy mapped to exit codes

**What.** Expected failures are `ValidationException`, `TaskNotFoundException`, `StorageException`,
all under `sealed abstract class TaskFlowException`. `ExitCodes.of(...)` maps each to a number with a
`switch` that has no `default`.

**Why.** `sealed` tells the compiler the complete list of subtypes. Add a fourth failure type and the
`switch` stops compiling until you handle it. Anything that is *not* a `TaskFlowException` is, by
definition, a bug and gets exit code 70 and a short message, with the full stack trace in the logs.

**Where.** `domain/*Exception.java`, `cli/ExitCodes.java`, `TaskFlowCli.handleFailure`.

**Use elsewhere.** Decide, per project, which errors the user can cause (report nicely) and which are
bugs (log loudly, show a generic message). In web apps, the same mapping becomes HTTP 400 / 404 / 500.

### 2.7 Reliable file storage

This is the most "senior" part of the project. Four problems, four solutions:

| Problem | Solution | Where |
| --- | --- | --- |
| Crash mid-write leaves a half-written file | Write to a temp file in the same folder, `fsync` it, then atomically rename over the real file | `JsonFileTaskRepository.write` |
| Two processes write at once and one update is lost | Exclusive OS file lock around *read -> change -> write* | `transaction(...)` |
| Two threads in one JVM both take the OS lock (it throws) | A JVM-wide `ReentrantLock` per file path, taken first | `JVM_LOCKS` |
| Future file format changes | `schemaVersion` in the file; refuse newer versions | `read()` |

Also: ids come from a stored `nextId` counter and are never reused; a corrupt file raises an error and
is **never overwritten** (so you can still recover it); the file has a 16 MiB cap; unknown JSON fields
are rejected.

**Use elsewhere.** Any time you persist user data: never overwrite in place, always version the
format, and decide up front what happens on corruption. When data grows or needs queries, replace the
adapter with a database (the port makes that a local change; see ADR-0002).

### 2.8 Separating results from diagnostics

**What.** `stdout` carries results only. `stderr` carries errors and logs. Services return data; only
the `cli` layer prints. `--json` produces a stable machine-readable shape (`JsonOutput`).

**Why.** `taskflow list --json | jq ...` only works if nothing else pollutes `stdout`. Scripts also
rely on exit codes, so they are documented in the README.

**Use elsewhere.** Every CLI: results -> stdout, everything else -> stderr, meaningful exit codes.
Every API: return data, let a thin layer format it.

### 2.9 Logging and observability

**What.** SLF4J (the logging *facade*) with logback (the implementation), configured in
`resources/logback.xml`. Log lines are `key=value` ("logfmt"). A per-command **correlation id** and
command name are attached with **MDC** (a thread-local map logback prints on every line). Each
command ends with `command_finished exit_code=... duration_ms=...`. Levels: default WARN, `-v` INFO,
`-vv` DEBUG.

**Why.** When something breaks on someone else's machine, a log with ids and durations tells you what
happened. Key=value lines can be grepped by humans and parsed by log tools. **Never log user content**
(titles) or secrets; log ids and counts.

**Use elsewhere.** Always log through SLF4J, not `System.out`. Log events as `snake_case_event
key=value`. Add a correlation id per request/command. In web services you would also add metrics
(counters, latency histograms) and tracing; the same ideas apply at larger scale.

### 2.10 Security thinking

Security is a list of "what could an attacker or a mistake do here?" questions. See `SECURITY.md`.
The ones worth memorising:

- **Validate at the boundary, in the domain.** Control characters in titles would let a task clear or
  rewrite your terminal (ANSI escape injection). Because the rule lives in `Task`'s constructor, it
  applies to every input path, including a hand-edited data file.
- **Never feed user text to a template or markup interpreter.** picocli colours use `@|red text|@`
  markup; a title containing that would be interpreted. So colour is applied only to fixed strings.
  This class of bug (injection) is the same as SQL injection or XSS.
- **Disable surprising features.** picocli's `@file` expansion would turn `add @/etc/passwd` into a
  file read.
- **Bound everything.** File size, JSON depth, string length, title length, tag count.
- **Least privilege.** File mode 0600/0700; CI `permissions: contents: read`; Docker runs as non-root.
- **Supply chain.** Dependabot, dependency review, CodeQL.

**Use elsewhere.** For any input ask: "what is the worst string/size/number someone could send?" For
any output ask: "which interpreter will read this (terminal, browser, SQL, shell)?"

### 2.11 Picocli (the command-line library)

Annotations declare the CLI: `@Command` (name, subcommands), `@Option` (`--due`), `@Parameters`
(positional `TITLE`). Picocli parses arguments, converts types (`Priority`, `List<Long>`), prints help
and usage errors (exit 2), and calls `call()`. Two features used here: `scope = INHERIT` (so `-v` and
`--data-dir` work after any subcommand) and a custom *execution strategy* (wraps every run with
logging context and timing). `RelativeDate` adds `tomorrow`, `+3d`, `+2w`.

### 2.12 Jackson (JSON)

`ObjectMapper` turns objects into JSON and back. Note the separate `StorageDocument` record: the file
format is its own type so the domain stays free of JSON annotations and the format can evolve
independently. The mapper is configured strictly (`FAIL_ON_UNKNOWN_PROPERTIES`, depth/length limits).

### 2.13 CI/CD with GitHub Actions

`.github/workflows/ci.yml` runs on every push and pull request: set up JDK 21, `mvn verify` with
warnings-as-errors, then smoke-test the real jar (`add`, `list --json | grep`, `--version`). Extra
workflows: CodeQL (security scanning) and Dependabot (automatic dependency PRs).

**Use elsewhere.** Minimum viable CI for any repo: check out, install toolchain, build, test. Add a
smoke test of the *packaged* artifact; unit tests alone cannot catch packaging mistakes.

### 2.14 Docker

Two stages: a big image with Maven builds the jar; a small JRE image runs it as a non-root user with a
volume for data. Only the jar is copied into the final image.

### 2.15 Documentation as engineering

- **README**: what it is, how to run, how to use, how it is built.
- **ADRs** (Architecture Decision Records): *Context -> Decision -> Consequences*, one page each.
  Reviewers love these because they show you weigh trade-offs.
- **SECURITY.md**: a threat table. **CONTRIBUTING.md**: the rules for changes.

---

## Part 3 - Testing: the ideas

### 3.1 The test pyramid used here

| Layer | Example | Speed | What it proves |
| --- | --- | --- | --- |
| Unit (domain) | `TaskTest` | milliseconds | Rules and boundaries of one class |
| Unit (application) | `TaskServiceTest`, `ReminderServiceTest` | milliseconds | Use-case logic, using a fake repository and a fixed clock |
| Contract | `TaskRepositoryContractTest` | fast | Every repository implementation behaves identically |
| Integration | `JsonFileTaskRepositoryTest` | ~1s | Real files: corruption, permissions, concurrency |
| End-to-end | `CliEndToEndTest` | ~1-2s | The whole program: arguments in, exit code and output out |

Many fast unit tests at the bottom, few slower end-to-end tests at the top.

### 3.2 Techniques to copy

- **Fake instead of mock.** `InMemoryTaskRepository` is a simple working implementation. Fakes keep
  tests readable and are verified by the same contract test as the real thing.
- **Contract tests.** An abstract test class (`TaskRepositoryContractTest`); each implementation
  extends it. Adding SQLite later = one small subclass.
- **Table-driven tests.** `@ParameterizedTest` + `@CsvSource` lists many inputs/outputs in a table
  (the urgency boundaries). Boundaries (0, 1, off-by-one) are where bugs live.
- **Fixed clock + temp directories.** `Clock.fixed`, `@TempDir` -> deterministic, isolated, no
  clean-up.
- **Test the unhappy paths as much as the happy ones.** Corrupt file, unknown id, bad date, hostile
  title.
- **Test security explicitly.** There are tests for escape characters, markup injection, `@file`.
- **A concurrency test.** 8 threads x 25 adds across two repository instances; assert no lost writes
  and no duplicate ids.
- **Assert on behaviour, not implementation.** The e2e tests only look at exit codes, stdout, stderr
  and files.
- **Arrange-Act-Assert.** Each test: set up, do one thing, check.

### 3.3 What a good test name looks like

`corruptFileFailsLoudlyAndIsNeverOverwritten`: it states the rule. When it fails, the name alone tells
you what broke.

---

## Part 4 - How to test it yourself

### 4.1 Automated

```bash
mvn verify                        # everything: compile, all tests, coverage gate, jar
mvn test                          # tests only
mvn -Dtest=TaskTest test          # one test class
mvn -Dtest='CliEndToEndTest#addThenListShowsTheTask' test   # one test method
mvn -Dcompiler.failOnWarning=true verify                    # what CI does
```

Read the result:
- `Tests run: N, Failures: 0, Errors: 0` and `BUILD SUCCESS` = good.
- A failure prints the test name and expected-vs-actual. Detailed reports:
  `target/surefire-reports/`.
- Coverage report: `open target/site/jacoco/index.html` (macOS). Red lines = never executed by a test.
  If the build fails with "Rule violated for bundle ... lines covered ratio is X, but expected minimum
  is 0.80", either add tests or lower `coverage.minimum` in `pom.xml`, then raise it later.

If a test fails on your machine, that is useful: copy the message, and work out whether the *test* or
the *code* is wrong. (I could not run these tests myself, so a first-run mismatch is possible.)

### 4.2 Manual test script (about 5 minutes)

Use a throw-away data folder so you never touch real data:

```bash
export TASKFLOW_HOME="$(mktemp -d)"
tf() { java -jar target/taskflow.jar "$@"; }     # shortcut for this terminal session
```

| # | Command | Expect |
| --- | --- | --- |
| 1 | `tf list` | `No tasks.` |
| 2 | `tf add "Buy milk" --due tomorrow -p high -t home` | `Added #1: Buy milk` |
| 3 | `tf add "Old thing" --due 2020-01-01` | `Added #2: Old thing` |
| 4 | `tf list` | Table; "Old thing" first, `overdue ...d`; "Buy milk" shows `tomorrow` |
| 5 | `tf remind` | `OVERDUE` line for #2 and `SOON` line for #1 |
| 6 | `tf done 2` then `tf list` | Completed message; #2 disappears from the list |
| 7 | `tf list --status all` | Shows `[x]` for #2 |
| 8 | `tf reopen 2`, `tf rm 2` | Reopened, then Deleted |
| 9 | `tf add "Next"` | `Added #3` (id 2 is **not** reused) |
| 10 | `tf list --json` | JSON array; `echo $?` prints 0 |

Error cases, checking exit codes with `echo $?`:

| Command | Expect |
| --- | --- |
| `tf done 999` | `error: No task with id 999`, exit **3** |
| `tf add "   "` | `error: ... blank`, exit **4** |
| `tf add x --due someday` | `error: Invalid date. ...`, exit **4** |
| `tf add` | usage message, exit **2** |
| `tf add $'bad\e[31mtext'` | rejected (control character), exit **4** |
| `tf add "@/etc/passwd"` | simply adds a task titled `@/etc/passwd` |

Robustness:

```bash
# 1. Data survives restarts: run `tf list`, close the terminal, reopen, set TASKFLOW_HOME again, list.

# 2. Concurrency: 20 simultaneous adds should give exactly 20 distinct tasks
export TASKFLOW_HOME="$(mktemp -d)"
for i in $(seq 1 20); do tf add "t$i" >/dev/null & done; wait
tf list --json | grep -c '"id"'          # expect 20

# 3. Corruption is detected, and the file is left alone
echo '{ nope' > "$TASKFLOW_HOME/tasks.json"
tf list; echo "exit=$?"                  # error mentioning "corrupt", exit 74
cat "$TASKFLOW_HOME/tasks.json"          # still: { nope

# 4. Logging goes to stderr, results to stdout
tf -vv add "logged" 2>log.txt; cat log.txt   # key=value lines with command_id; no task titles

# 5. Legacy import (use your own tasks.txt)
tf import-legacy path/to/tasks.txt --dry-run
tf import-legacy path/to/tasks.txt
tf import-legacy path/to/tasks.txt       # second run: skipped duplicates
```

### 4.3 Break it on purpose (the best way to learn what tests are for)

Change one thing, run `mvn test`, and see *which* test catches it. Then undo the change.
(These are the failures I expect; if you see something different, you have learnt something.)

| Change | Expected failing test(s) |
| --- | --- |
| `Task.urgency`: change `days <= soonDays` to `days < soonDays` | `TaskTest` urgency table, `ReminderServiceTest` |
| `JsonFileTaskRepository.insert`: reuse ids (e.g. `state.tasks.size() + 1`) | contract test `idsAreNeverReusedAfterDeletion` (both repos) |
| Delete the `jvmLock.lock()` line in `transaction` | `concurrentWritersAcrossInstances...` |
| Remove `commandLine.setExpandAtFiles(false)` | `titleBeginningWithAtSignIsNotTreatedAsAnArgumentFile` |
| Remove the control-character check in `Task.validateTitle` | `TaskTest` injection tests, `CliEndToEndTest` invalid-input test |
| Make `TaskService.complete` always call `repository.save` | idempotence tests may still pass: a hint that a test is missing. Write one |

This technique is called **mutation testing**; tools like PIT automate it. If no test fails after a
change, that area is under-tested.

### 4.4 Common problems

- **`release version 21 not supported`**: your JDK is older than 21. On macOS:
  `brew install openjdk@21`, then `export JAVA_HOME=$(/usr/libexec/java_home -v 21)`.
- **`mvn: command not found`**: `brew install maven`.
- **Tests pass locally, fail in CI** (or reverse): usually time zone, file paths, or ordering. This is
  why the clock and temp dirs are injected.

---

## Part 5 - A reusable recipe for your next project

1. **Name the concepts** (nouns) and the **rules**. Write them as immutable types that validate
   themselves. No I/O.
2. **Write the use cases** as small services. Inject dependencies and a `Clock`. Define interfaces
   (ports) for anything external: storage, email, HTTP.
3. **Write a fake** for each port, and a **contract test** for the port.
4. **Add the real adapter** and make it pass the contract.
5. **Add the thin UI** (CLI, REST controller). Parse -> call service -> format. Map exceptions to
   exit codes or HTTP statuses.
6. **Add observability**: SLF4J, correlation id, no sensitive data, errors vs expected failures.
7. **Run a security pass**: for each input and output, what is the worst case? Add limits.
8. **Automate**: `mvn verify` locally and in CI, coverage gate, Dependabot, CodeQL.
9. **Document decisions**: README for users, ADRs for the "why", SECURITY.md for threats.
10. **Ship**: runnable jar, optional Docker image; smoke-test the artifact in CI.

Pre-merge checklist: tests for unhappy paths - no static `now()` - no `System.out` outside the UI
layer - no user data in logs - every limit explicit - one-sentence README description.

---

## Part 6 - Exercises (in order of difficulty)

1. Add `taskflow edit ID --title ... --due ... --priority ...`. (New `Task` "with" methods ->
   `TaskService.edit` -> `EditCommand` -> tests at each layer.)
2. Add a `search TEXT` command.
3. Add a `--sort` option to `list`.
4. Write `SqliteTaskRepository` and make it pass `TaskRepositoryContractTest`. This proves why the
   port exists.
5. Add a `taskflow shell` interactive mode that reuses the services (the old menu, rebuilt properly).
6. Add recurring tasks (`--repeat weekly`): completing one creates the next. Start with the domain
   test for the date arithmetic.
7. Add PIT mutation testing to the build and raise coverage until the survivors are explained.

---

## Part 7 - Explaining this in an interview

Lead with the problem, not the tech. For example:

> "I took a menu-driven prototype and rebuilt it as a production-style CLI. The interesting part was
> reliability: writes are atomic and fsync'd, access is locked across processes, and corrupt files are
> never overwritten. I tested that with a concurrency test that fires 200 writes from multiple threads
> and repository instances. I also threat-modelled it: for example, a task title containing terminal
> escape codes can rewrite your screen, so the domain model rejects control characters on every input
> path."

Be ready to answer: *Why a JSON file and not SQLite?* (ADR-0002) - *Why inject a Clock?* (ADR-0003) -
*How would you scale this to a multi-user service?* (swap the adapter for a database, put a REST layer
in front of the same services, add authentication; the domain and application layers stay as they are).
Be honest about limits: single user, whole-file reads, no sync.
