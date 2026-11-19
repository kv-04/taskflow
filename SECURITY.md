# Security

## Reporting a vulnerability

Please do not open a public issue. Use GitHub's **Report a vulnerability** (private advisory) on this
repository. You will get an acknowledgement within a few days.

## Threat model

TaskFlow is a single-user, local CLI. There is no network surface, no server and no authentication.
The relevant adversaries and what the code does about them:

| Threat | Mitigation |
| --- | --- |
| Other local users reading your tasks | Data directory `0700`, files `0600` on POSIX systems |
| Terminal escape-sequence injection (a task title containing `ESC[2J` etc. clearing or rewriting your terminal) | Titles are validated in the domain model: control characters are rejected on every input path, including legacy import and hand-edited data files (re-validated on load) |
| "Trojan Source" bidi-override characters making output misleading | Bidirectional override/isolate characters rejected in titles |
| Markup injection into coloured output | Colour is applied only to fixed strings; user text is never passed through picocli's markup parser |
| `@file` argument expansion turning `taskflow add @/etc/passwd` into a file read | `@argfile` expansion disabled |
| Corrupted or maliciously large data files | 16 MiB cap, strict schema (unknown fields rejected), Jackson nesting/string-length limits, version check; corrupt files are never overwritten |
| Data loss from crashes or concurrent runs | Atomic write-then-rename with fsync; OS file lock + JVM lock around every read-modify-write |
| Log injection / sensitive data in logs | Structured key=value log lines; task titles are never logged |
| Vulnerable dependencies | Dependabot, GitHub dependency review on PRs, CodeQL (see `.github/`) |
| Container privilege | Docker image runs as a non-root user |

Out of scope: an attacker who already has your user's file-system access (they can read `tasks.json`
regardless) and encryption at rest.
