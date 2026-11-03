# Submission and validation

## Workflow

1. `Pair1` prepares assessment inputs and discovers ZIPs once through `SubmissionLoader`.
2. `loadExtractedSubmissions` returns each original ZIP with its extracted root.
3. `StudentIdentifier` matches the ZIP's username to the roster.
4. `SubmissionValidator` checks the submission, then each question.
5. Each `QuestionAttempt` receives selected answer paths and question issues.
6. The populated `StudentSubmission` is returned for later pipeline stages.

## Responsibilities

| File | Purpose |
| --- | --- |
| `SubmissionLoader` | Discover and extract ZIPs; retain ZIP/root associations. |
| `ZipExtractor` | Extract entries while checking destination containment. |
| `ExtractedSubmission` | Store an original ZIP and its extracted directory. |
| `SubmissionFileNames` | Share ZIP-extension, base-name, and directory conventions. |
| `StudentIdentifier` | Match archive usernames, including academic-year prefixes. |
| `SubmissionNormalizer` | Reserved placeholder; normalization is not implemented. |
| `SubmissionValidator` | Run rules, select answers, and build attempts. |
| `QuestionValidationRule` | Define a check for one question. |
| `SubmissionValidationRule` | Define a check requiring the whole assessment. |
| `AnswerFileRule` | Check folder existence, required files, and capitalization. |
| `StudentHeaderRule` | Compare required-answer headers with roster identities. |
| `UnexpectedFileRule` | Warn about undeclared direct files; combine shared folders. |
| `AnswerFileLocator` | Find exact answers, then unambiguous case-insensitive matches. |
| `StudentHeaderReader` | Share the existing first-15-lines leading-comment reader. |
| `FileUtils` | Stream direct regular files; callers close the stream. |

## Selection and issues

- Required answers are always selected; optional companions remain optional.
- A declared `.java` companion is selected when its trusted template has Name or Email fields.
- Blank template fields count; student-added headers do not determine editability.
- Configure trusted templates through the five-argument `QuestionDefinition` constructor.
- Legacy constructors without a template root select required answers only.
- The sample entry point uses `<project>/RenameToYourUsername` when present.
- Missing optional student files are omitted; template read failures produce question errors.
- `StudentSubmission.getSubmissionIssues()` collects submission and question findings.
- `QuestionAttempt.getIssues()` contains question findings; ERROR makes that attempt invalid.
- Submission-wide issues are not automatically copied into attempts; consumers check both lists.
- Header validation remains exact-filename-only, limited to 15 lines, with strict UTF-8.

## What became simpler

- Three structural rules became `AnswerFileRule`: validation has seven production files.
- Header parsing and filesystem listing have shared implementations.
- ZIP/root pairs remove reverse matching, duplicate discovery, and uppercase ZIP association failures.
- Filename conventions use locale-independent extraction names; the old loader API remains available.
- Answer lookup, grading, and normalization behavior were not expanded by the utility cleanup.

## Tests

Tests under `tests/` and the runner are optional development files, not application dependencies.
```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\test-validation.ps1
```
The runner requires JDK 17+, checks the 200-line Java-file limit, and runs temporary-file regressions.
