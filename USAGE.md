# pair 2 usage — workspace preparation & process running

this document shows how to use the pair 2 code (`WorkspacePreparer` and
`ProcessRunner`) to prepare and run **one** question attempt.

> note: `app.java` (the `autogradingapplication` orchestrator) does **not**
> exist yet. this is guidance for whoever writes it. grading (`questiongrader`)
> is also intentionally **not** implemented yet, so this guide stops at
> producing a `ProcessResult` — it does **not** produce a grade. mapping a
> `ProcessResult` to a `gradestatus`/`questiongrade` is the job of the future
> `questiongrader`.

## what pair 2 provides today

| class                                      | package                        | role                                                     |
|--------------------------------------------|--------------------------------|----------------------------------------------------------|
| `WorkspacePreparer`                        | `autograder.workspace`         | create an isolated temp folder, copy answer+resources+tester, clean up |
| `ProcessRunner`                            | `autograder.runner`            | run `javac` then `java` with a timeout, capture output   |
| `ProcessResult`                            | `autograder.model`             | immutable holder: exit code, stdout, stderr, timed-out   |
| `WorkspacePreparationException`            | `autograder.autograderexception` | workspace failure (codes `wsp-001`..`wsp-005`)         |
| `ProcessExecutionException`                | `autograder.autograderexception` | process failure (codes `prc-001`..`prc-004`)           |

the inputs `QuestionDefinition`, `QuestionAttempt` and `TesterDefinition`
(package `autograder.model`) are owned by other pairs (pair 1 / pair 3). in the
examples below they are shown as placeholders — construct them however the real
teammate types require.

## one attempt, end to end

```java
import autograder.model.QuestionDefinition;
import autograder.model.QuestionAttempt;
import autograder.model.ProcessResult;
import autograder.workspace.WorkspacePreparer;
import autograder.runner.ProcessRunner;

import java.nio.file.Path;

// 1. obtain the question + attempt.
//    these come from pair 1 (QuestionAttempt) and pair 3 (QuestionDefinition).
//    shown here as placeholders — use the real constructors/sources.
QuestionDefinition question = /* provided by pair 3 */ null;
QuestionAttempt   attempt  = /* provided by pair 1 */ null;

// 2. prepare an isolated workspace. try-with-resources guarantees cleanup,
//    even if compile/run throws. close() deletes the temp workspace.
try (WorkspacePreparer prep = new WorkspacePreparer()) {

    Path ws = prep.prepare(question, attempt);
    // ws now contains: the student's answer file(s), the question's resource
    // folder (recursively), and the tester source — all copied, originals
    // are never mutated. if the attempt had no id, a process-wide unique
    // "auto-<n>" id was used so concurrent attempts never collide.

    // 3. compile everything in the workspace with the question's timeout.
    ProcessRunner runner = new ProcessRunner(/* question.getTimeout() */ java.time.Duration.ofSeconds(10));
    ProcessResult compileResult = runner.compile(ws);

    // 4. only run the tester if compilation succeeded.
    if (compileResult.isSuccessful()) {
        // tester main class comes from the question's TesterDefinition
        String mainClass = /* question.getTester().getMainClass() */ "Tester";
        ProcessResult runResult = runner.runTester(ws, mainClass);

        // inspect the raw result (grading happens later, in questiongrader):
        if (runResult.isTimedOut()) {
            // -> later maps to gradestatus.timeout
        } else if (!runResult.isSuccessful()) {
            // non-zero exit -> later maps to gradestatus.runtime_error
        } else {
            String stdout = runResult.getStandardOutput(); // tester output to parse later
            // -> later maps to gradestatus.completed and test-case parsing
        }
    } else {
        // compile failed -> later maps to gradestatus.compile_error
        String why = compileResult.getErrorOutput();
    }

} // 5. workspace is automatically deleted here by close()
```

## concurrency note

`WorkspacePreparer` is safe to use from multiple threads: each attempt gets its
own `WorkspacePreparer` instance and its own temp folder. when an attempt has no
id, a **process-wide, thread-safe, monotonically increasing** `auto-<n>` id is
used for the folder prefix, so two concurrent id-less attempts can never produce
the same workspace and overwrite each other. a batch grader can therefore run
one `prepare` → `compile` → `runTester` pipeline per student in parallel.

## `ProcessResult` fields

| accessor                | meaning                                                              |
|-------------------------|----------------------------------------------------------------------|
| `getExitCode()`         | the process exit code (meaningful only when not timed out)           |
| `getStandardOutput()`   | everything the process wrote to stdout (the tester output to parse)  |
| `getErrorOutput()`      | everything the process wrote to stderr (compiler/runtime errors)     |
| `isTimedOut()`          | `true` if the process was killed for exceeding the timeout           |
| `isSuccessful()`        | convenience: `exitCode == 0 && !timedOut`                            |

## workspace isolation (javac / java commands)

`ProcessRunner` runs both tools with the attempt's workspace as the working
directory, and every path it uses is workspace-relative:

- compile: `javac -d classes -cp . <sources>` — compiled `.class` files are
  written into a `classes/` sub-folder of the workspace; the compile classpath
  is the workspace root (`.`), which also picks up any precompiled
  `.class`/`.jar` resources the question shipped.
- run: `java -cp classes<sep>. <mainClass>` (`<sep>` is `;` on windows, `:`
  elsewhere) — the tester loads compiled classes from `classes/` and reads any
  runtime resource files from the workspace root.

because nothing on the classpath or output path points outside the temp
workspace, one attempt can never read code/data from, or write into, another
attempt's workspace.

## error codes

### workspace (`WorkspacePreparationException.getCode()`)

| code      | condition                             | typical caller handling                     |
|-----------|---------------------------------------|---------------------------------------------|
| `wsp-001` | temporary workspace cannot be created | fail the attempt; log; continue the batch   |
| `wsp-002` | answer file copy failed               | fail the attempt; log; continue the batch   |
| `wsp-003` | question resource folder missing      | fail the attempt; log; continue the batch   |
| `wsp-004` | tester source copy failed             | fail the attempt; log; continue the batch   |
| `wsp-005` | workspace cleanup failed              | log a warning; does not affect the grade    |

on any failure during `prepare`, the partially-created workspace is
self-cleaned before the exception is thrown — no orphaned temp directories are
left behind.

### process (`ProcessExecutionException.getCode()`)

| code      | condition                               | typical caller handling                   |
|-----------|-----------------------------------------|-------------------------------------------|
| `prc-001` | `javac` executable could not be started | environment problem; fail the attempt     |
| `prc-002` | `java` executable could not be started  | environment problem; fail the attempt     |
| `prc-003` | stream capture failed                   | fail the attempt; log                     |
| `prc-004` | worker interrupted while waiting        | propagate interruption; stop the worker   |

note: a **compile error** or a **runtime error in the student/tester program**
is *not* an exception — it comes back as a normal `ProcessResult` with a
non-zero exit code (and, for timeouts, `isTimedOut() == true`). exceptions are
reserved for failures of the grading machinery itself (cannot start a tool,
cannot read its output, interrupted).

## what happens next (not in pair 2 yet)

the future `questiongrader` will take the `ProcessResult`s above and:

- map compile failure → `compile_error`
- map non-zero tester exit → `runtime_error`
- map `isTimedOut()` → `timeout`
- parse `getStandardOutput()` into test-case results and sum earned marks → `completed`

that logic is deliberately **out of scope** for the current pair 2 deliverable.
