# Submission and validation

## Workflow

1. `Main` loads the roster, assessment definitions, and testers.
2. `SubmissionLoader` extracts each ZIP into a unique temporary directory.
3. `StudentIdentifier` matches archive usernames; preparation failures remain issue records.
4. `SubmissionValidator` runs submission checks, then checks each question.
5. One cached `SubmissionFileFinder` is shared by the question rules for that submission.
6. Each `QuestionAttempt` carries its definition, question directory, selected paths, and issues.
7. The populated `StudentSubmission` continues through the existing grading pipeline.

## Responsibilities

| File | Purpose |
| --- | --- |
| `StudentCsvReader` | Read roster rows and log skipped or corrected CSV records. |
| `SubmissionLoader` | Discover ZIPs, retain failures, and unwrap a single outer folder. |
| `ZipExtractor` | Extract archive entries while checking destination containment. |
| `StudentIdentifier` | Match archive usernames, including academic-year prefixes. |
| `SubmissionFileNames` | Share ZIP-extension and archive-base-name conventions. |
| `SubmissionValidator` | Run rules, select answers, and construct attempts. |
| `QuestionValidationRule` | Define a check for one question using its shared finder. |
| `SubmissionValidationRule` | Define a check requiring the whole assessment. |
| `AnswerFileRule` | Check folders, required answers, and filename or layout differences. |
| `StudentHeaderRule` | Report missing or mismatched required-answer identity fields. |
| `UnexpectedFileRule` | Warn about undeclared direct files; combine shared-folder requirements. |
| `StudentHeaderUtils` | Parse leading identity comments for validation and file selection. |
| `SubmissionFileFinder` | Locate answers and reuse results within one submission. |
| `FileSearch` | Resolve path spelling and index relocated answers when needed. |
| `FileUtils` | Share file listing, copying, deletion, and path checks. |

## Selection and handoff

- Required answers are always candidates for selection.
- A declared `.java` companion is selected when its trusted template has both Name and Email labels.
- Blank template values count; student-added headers do not determine editability.
- Header scanning keeps PR #19's first-15-lines, leading-comments-only behavior.
- Missing optional student files are omitted; template read failures produce question errors.
- Existing folder recovery, nested paths, ambiguity checks, and absolute result paths are preserved.
- `QuestionAttempt.getSubmittedFiles()` supplies selected student paths to existing consumers.
- `QuestionAttempt.getQuestionDirectory()` retains the expected question-directory path.
- `StudentSubmission.getSubmissionIssues()` collects preparation and validation findings.
- `QuestionAttempt.getIssues()` contains question findings; ERROR makes that attempt invalid.
- Submission-wide findings remain in the submission list rather than being copied into attempts.
- Roster CSV corrections are logged separately by `StudentCsvReader` through `Logger`.

## Changes carried onto PR #19

- Three structural rules became `AnswerFileRule`; duplicate lookup errors are reduced.
- Rule names distinguish question checks from submission checks.
- ZIP naming and direct-file listing are shared instead of repeated.
- Filename-spelling warnings compare capitalization explicitly on Windows too.
- Existing header/search utilities and newer model APIs are reused without parallel replacements.
- Grading source and answer-lookup behavior are unchanged; no normalizer was introduced.

## Tests

`src/test/java` contains optional development tests, not application dependencies.
Run `mvn test` with JDK 25+; `test-validation.ps1` also checks the 200-line Java-file limit.
