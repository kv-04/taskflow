# TaskFlow, Explained From Zero

Written so you can explain this project to anyone: a friend, a family member, a recruiter, an
interviewer. Each section starts simple and then goes deeper.

---

## 1. What is this project? (the 30-second version)

**TaskFlow is a to-do list that you run by typing short commands.** You tell it what you need to do
and when it is due. It remembers everything, even after you close the program, and it warns you
about anything late or due soon.

```
taskflow add "Submit report" --due tomorrow --priority high
taskflow list
taskflow remind
taskflow done 1
```

### Why does it exist?

Two reasons:

1. **A useful tool.** It is fast, works offline, keeps your data on your own computer, and can be
   used inside scripts (for example, show your overdue tasks every time you open a terminal).
2. **A demonstration of engineering quality.** The first version (your original) worked on a good day.
   This version is built to keep working on a bad day: power cuts, two programs writing at once,
   corrupted files, and unusual or hostile input. The project is a way of *showing how real software
   is built*, using a simple idea so the engineering is easy to see.

### The analogy for non-technical people

> The first version was a sticky note on the fridge. This version is a **filing cabinet with a lock,
> labelled drawers, an assistant who double-checks everything you hand in, and a rule that nobody
> ever tears a page out halfway through writing it.**

---

## 2. What can it do right now?

| Ability | Example |
| --- | --- |
| Add tasks with a due date, priority and tags | `add "Pay rent" --due +3d -p high -t home` |
| Understand friendly dates | `today`, `tomorrow`, `+3d`, `+2w`, `2026-12-01` |
| Show tasks, most urgent first | `list`, `list --overdue`, `list --tag home`, `list --status all` |
| Mark done, reopen, delete | `done 1`, `reopen 1`, `rm 1` |
| Remind you | `remind`, `remind --within-days 7` |
| Give machine-readable output | `list --json` (for other programs to use) |
| Import your old data | `import-legacy tasks.txt` |
| Explain itself | `--help`, `--version` |
| Tell scripts what happened | Exit codes: 0 ok, 2 bad command, 3 not found, 4 invalid input, 74 file problem |

**Safety features (the part people don't see):**
- Saves can't be half-finished (a crash leaves the old file or the new file, never a broken one).
- Two copies of the program at once can't overwrite each other.
- A damaged data file is reported and **never overwritten**.
- Task titles containing hidden terminal control codes are rejected.
- Your data folder is readable only by you.

---

## 3. What could it do more? (roadmap you can talk about)

Easy next steps:
- **Edit** a task's title/date/priority (`taskflow edit 3 --due friday`).
- **Search** (`taskflow search "report"`) and sorting options.
- **Interactive mode** (`taskflow shell`): the old menu, rebuilt on the new foundation.
- **Colour themes**, shell auto-completion (picocli can generate it).

Medium steps:
- **Recurring tasks** ("every Monday"): completing one creates the next.
- **Subtasks, notes, projects.**
- **Export/import** to CSV or calendar (`.ics`) files.
- **A real database (SQLite)** instead of a JSON file, for thousands of tasks. Because storage sits
  behind an interface, only one class has to be written.
- **Native desktop notifications** from `remind`.

Bigger steps (these show architecture skill):
- **A web API (Spring Boot or Javalin)** reusing the same `TaskService` and domain, with the CLI
  untouched. This is the payoff of the layered design.
- **User accounts and syncing across devices** (add authentication, a server database, conflict
  rules).
- **A web or mobile front end** talking to that API.
- **Metrics and tracing** (Prometheus/OpenTelemetry) for the server version.
- **Native binary with GraalVM** for instant start-up.

---

## 4. The technologies: what, when, and when not

For each: a plain explanation, an analogy, when to use it, when *not* to, and where it is here.

### Java 21
- **What:** a programming language. Code is compiled to run on the Java Virtual Machine (JVM), so the
  same program runs on Windows, Mac and Linux.
- **Analogy:** writing in a language that a universal translator (the JVM) can read aloud anywhere.
- **Use it for:** large, long-lived, team-maintained systems: banks, back ends, Android, enterprise.
  Strong typing catches mistakes before the program runs.
- **Not ideal for:** tiny one-off scripts (Python is quicker), or when start-up time must be
  near-zero (a CLI like this starts in a fraction of a second, which is fine, but Go/Rust are faster).
- **Modern features used here:** `record` (compact immutable data), `sealed` classes (a closed list of
  subtypes), pattern-matching `switch`, text-friendly `String` methods, `java.time`.

### Maven
- **What:** the build tool. It downloads libraries, compiles, runs tests, packages a program.
- **Analogy:** the factory manager who follows a checklist: fetch parts, assemble, inspect, box up.
- **Use it for:** almost any Java project. Alternative: Gradle (more flexible, steeper to learn).
- **Key idea:** `pom.xml` is the single description of the project; `mvn verify` does everything.

### JUnit 5
- **What:** the standard framework for writing automated tests in Java.
- **Analogy:** a checklist inspector who runs through every promise your code makes, every time.
- **Use it:** for every project beyond a toy. Features used: `@Test`, `@ParameterizedTest` (one test,
  many inputs), `@TempDir` (a throw-away folder).

### AssertJ
- **What:** a library that makes test checks read like sentences:
  `assertThat(list).containsExactly(1, 2, 3)`.
- **Use it:** whenever plain `assertEquals` becomes hard to read. Gives clearer failure messages.

### picocli
- **What:** a library for building command-line programs: parses `--due tomorrow`, prints `--help`,
  reports mistakes.
- **Analogy:** a receptionist who understands what you asked for, rejects nonsense politely, and
  passes clean requests to the right department.
- **Use it for:** Java CLIs with options and subcommands (like `git add`, `git commit`).
- **Not for:** programs with no command-line arguments (then it's overkill), or web servers.

### Jackson
- **What:** converts Java objects to JSON text and back.
- **Analogy:** a translator between the program's internal objects and a text format that can be
  saved or sent.
- **Use it:** whenever you store or exchange JSON (web APIs, config files). Alternatives: Gson, or
  `JSON-B`.
- **Care needed:** configure it strictly. Here, unknown fields are rejected and sizes are limited.

### JSON (the file format)
- **What:** a plain-text way to write structured data: `{ "title": "Buy milk", "status": "OPEN" }`.
- **Use it:** for small to medium data that humans should be able to read, diff and back up.
- **Move to a database when:** you have lots of data, need searching/filtering at speed, or many
  users writing at once. See ADR-0002.

### SLF4J + Logback (logging)
- **What:** SLF4J is the *standard way to write log messages*; Logback is the *thing that decides
  where they go and how they look*.
- **Analogy:** SLF4J is a standard form; Logback is the clerk who files it.
- **Use it:** always, instead of `System.out.println`, in anything you maintain. Log events and ids,
  **never passwords or user content**.
- **Concepts used:** levels (WARN/INFO/DEBUG), a correlation id per run (so one command's lines can be
  found together), and key=value format (easy to search).

### JaCoCo (coverage)
- **What:** measures which lines of your code the tests actually ran.
- **Use it:** as a *smoke detector*, not a goal. 100% coverage doesn't prove correctness; low
  coverage proves something is untested. Here the build fails below 80%.

### Maven Shade plugin
- **What:** packs your code and all its libraries into one file: `taskflow.jar`.
- **Use it:** when you want to give someone one file to run: `java -jar taskflow.jar`.

### Git and GitHub
- **What:** Git records the history of your files. GitHub hosts the repository and runs automation.
- **Use it:** always. Commit small, write clear messages.

### GitHub Actions (CI: Continuous Integration)
- **What:** a robot that, on every push, builds and tests your project on a clean machine.
- **Why:** "works on my machine" is not proof. The robot is the neutral judge.
- **Here:** builds on Linux and macOS, runs all tests, smoke-tests the real jar.

### CodeQL and Dependabot
- **CodeQL:** scans your code for known security mistakes.
- **Dependabot:** opens pull requests when a library you use has a new version or a security fix.
- **Use them:** on any public repository; they are free and low effort.

### Docker
- **What:** packages a program with everything it needs into an *image* that runs the same anywhere.
- **Analogy:** a lunchbox that contains the meal, the cutlery and the table.
- **Use it:** for servers and deployment. For a personal CLI it is optional; it's here to show you can.
- **Good habits shown:** two-stage build (small final image) and a non-root user.

### Markdown, README, ADRs
- **Markdown:** simple text that renders as formatted pages.
- **README:** the front door. **ADR:** a one-page record of *why* a decision was made. **SECURITY.md:**
  how to report problems and what the threat model is.

### Design ideas (not libraries, but just as important)

| Idea | One-line meaning | Use it when |
| --- | --- | --- |
| Layered architecture | Keep rules, workflow, storage and UI in separate boxes | Always, once a project has more than ~3 classes |
| Port and adapter | Business code talks to an interface; real storage plugs in behind it | Anything touching a database, files, network |
| Dependency injection | Hand a class its helpers instead of letting it build them | Always; it is what makes testing possible |
| Immutability | Objects never change after creation | Default choice; reduces bugs |
| Validate at the boundary | Check input once, where it enters, in the type itself | Always, for every input |
| Inject the clock | Pass in "what time is it" | Anything with dates/times |
| Sealed exceptions | A closed list of expected errors | When you want the compiler to force you to handle every case |
| Atomic writes + locks | Save safely; take turns | Any file or data that must not be corrupted |
| stdout vs stderr | Results on one channel, noise on the other | Every command-line tool |

---

## 5. How to test your project properly

Testing has three goals: **prove it works**, **catch it breaking later**, and **document what it is
supposed to do**.

### 5.1 Five kinds of testing, in the order to do them

1. **Does it build?** `mvn verify` on a clean checkout. If this fails, fix it before anything else.
2. **Do the automated tests pass?** `mvn test`. Read any failure: expected vs actual.
3. **Do the tests actually test anything?** Open `target/site/jacoco/index.html`. Red lines are never
   run. Then do the *break-it* exercise in LEARNING_GUIDE.md (change code on purpose; a test must
   fail).
4. **Does the real packaged program behave?** Run the manual script (LEARNING_GUIDE.md, section 4.2)
   against `target/taskflow.jar` with a throw-away folder.
5. **Does it survive abuse?** Corrupt file, 20 parallel writers, hostile titles, missing file,
   unreadable folder, huge input.

### 5.2 The questions to ask for every feature

- **Happy path:** does the normal case work?
- **Edges:** empty, one, many, maximum; yesterday, today, tomorrow; the exact boundary.
- **Bad input:** blank, too long, wrong type, special characters, hostile content.
- **Failure of the world:** the file is missing, corrupt, locked, read-only; the disk is full.
- **Repetition:** does doing it twice cause harm? (`done` twice, `import` twice.)
- **Concurrency:** what if two copies run at once?
- **Output contract:** exit code, stdout, stderr: do scripts get what the README promises?

### 5.3 A test plan you can paste into a document

| Area | Test | Pass condition |
| --- | --- | --- |
| Add | Add with all options | Task appears with correct fields |
| Add | Blank title / bad date / bad tag / control character | Exit 4, clear message, nothing saved |
| List | Empty list; many tasks; filters; JSON | Correct order and content; JSON parses |
| Done/Reopen | Normal; twice; unknown id | Correct state; no error on repeat; exit 3 for unknown |
| Delete | Delete then add | New id is not reused |
| Remind | Overdue, today, tomorrow, later, none; widened window | Only the right tasks, most urgent first |
| Import | Good, bad lines, pipes, re-run | Good lines imported, bad reported by line number, no duplicates |
| Storage | Restart; corrupt file; future-version file; huge file | Data persists; clear error; file untouched |
| Concurrency | 20 parallel adds | Exactly 20 tasks, unique ids |
| Security | Escape codes, `@file` titles, markup in titles | Rejected or shown literally; nothing executed |
| Logging | `-v`, `-vv` | Logs on stderr only; no titles in logs |
| Packaging | `java -jar taskflow.jar --version` | Prints a version, exit 0 |

### 5.4 A fair warning

I wrote and ran checks on the storage and rules code in my environment (they passed), but I could not
run the picocli command-line layer or the JUnit suite there. So *your* `mvn verify` is the first real
run of those parts. A failure is not a disaster; it is the test doing its job. Send me the message and
we fix it.

---

## 6. How to explain it to someone who knows nothing

### The two-minute version (spoken)

> "I built a to-do list program you control by typing commands. You can add a task, give it a due
> date and a priority, and ask it what's overdue. That part is simple on purpose.
>
> The real project is *how* it's built. My first version worked, but if the computer crashed while
> saving, you could lose all your tasks, and if you typed something strange, it could break. So I
> rebuilt it the way professional software is built: the rules, the saving, and the screen are kept in
> separate parts so each can be checked on its own. Saving is done in a way that can't leave a
> half-written file. And I wrote automatic tests, around a hundred small checks, that run every time
> the code changes, on a clean computer, so I'd know immediately if I broke something."

*(Check the actual test count with `mvn test` before you quote a number.)*

### Three analogies that work with any audience

- **Restaurant:** the CLI is the waiter, the services are the kitchen, the storage is the pantry,
  and the domain is the recipe book. The waiter never cooks; the kitchen never talks to customers.
- **Bank vault door:** saving to a new file and then swapping it in is like writing the new ledger on a
  fresh page and only then replacing the old one. Nobody ever sees a half-written ledger.
- **Smoke alarm:** automated tests are alarms in every room; they tell you when something starts to
  burn, before the whole house does.

### Questions you will be asked, and good answers

| Question | Answer |
| --- | --- |
| Why not just use a to-do app? | The to-do list is the vehicle. The goal is to show professional engineering: design, testing, security, automation. |
| Why Java? | Strong typing, huge ecosystem, runs anywhere, and it is what most large companies use for back-end systems. |
| Why a file and not a database? | Right-sized for one user, human-readable, easy to back up. The code is built so a database can replace it by changing one class. |
| How do you know it works? | Automated tests at several levels, a coverage gate, a build robot on GitHub, and a manual checklist. |
| What was the hardest part? | Making saving safe: crashes, two programs at once, corrupted files. |
| What would you do next? | Edit command, recurring tasks, then a web API on the same core. |
| Is it secure? | It's a local tool, so the threats are different from a website. I handled the relevant ones: private file permissions, rejecting terminal control characters, strict file validation, size limits, no private data in logs. |
| What are its limits? | One user, reads the whole file each time, no sync between devices. Documented in the README. |

### A five-minute walkthrough (demo script)

1. `taskflow list` -> "No tasks." (a fresh start)
2. `taskflow add "Submit report" --due tomorrow --priority high` -> show friendly dates.
3. `taskflow add "Old thing" --due 2020-01-01`, then `taskflow list` -> point at the overdue marker.
4. `taskflow remind` -> "this is what I'd put in my shell startup."
5. `taskflow list --json` -> "other programs can use this."
6. `taskflow add "   "` then `echo $?` -> "it refuses nonsense, and tells scripts why (exit code 4)."
7. `echo '{ nope' > $TASKFLOW_HOME/tasks.json`, run `taskflow list` -> "damaged data is reported and
   never overwritten."
8. Show `mvn verify` finishing with `BUILD SUCCESS`, and the GitHub Actions green tick.

---

## 7. Glossary (for the beginner)

- **CLI**: command-line interface; you use it by typing.
- **Library**: someone else's code you reuse (picocli, Jackson).
- **Dependency**: a library your project needs.
- **Build**: turning source code into a runnable program.
- **JAR**: a Java program packaged as one file.
- **Test**: code that checks other code.
- **Unit test**: checks one small piece. **End-to-end test**: checks the whole program.
- **CI**: a robot that builds and tests every change.
- **Immutable**: cannot be changed after it is created.
- **Validation**: checking that input is acceptable.
- **Injection (attack)**: tricking a program into treating data as instructions.
- **Atomic**: happens completely or not at all.
- **Exit code**: the number a program returns to say how it went (0 = fine).
- **stdout / stderr**: the two output channels: results and diagnostics.
- **JSON**: a text format for structured data.
- **ADR**: a short document recording a design decision.
