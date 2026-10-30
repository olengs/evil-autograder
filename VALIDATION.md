# Submission and validation issue logging

This integrates issue logging with Jerxl's `submission-normalizer` branch. It does not
change grading or SubmissionLoader, and does not add another normalization/detection algorithm.

## Collection and handoff

`StudentSubmission.getSubmissionIssues()` is the complete in-memory list for reporting.
Each `SubmissionIssue` has code, severity, message, questionId, affectedPath, stage and resolved.
The five-argument constructor remains compatible and defaults to validation / unresolved.

- `preparation`: ZIP extraction failures and unmatched students from SubmissionPreparer.
- `normalization`: corrections and failures from SubmissionNormalizer.
- `editable detection`: an unreadable sampled file, when header inspection throws IOException.
- `validation`: remaining structure, filename, required-file and identity-header findings.

Successfully applied normalization corrections are WARNING / resolved=true. Unresolved
failures are ERROR / resolved=false. A resolved ERROR is rejected because existing grading
blocks every ERROR regardless of resolution metadata. Warnings never become blocking just
because they remain unresolved.

Normalization preserves earlier issues when it returns a replacement StudentSubmission.
Affected paths beneath the answer root are rebased when that root moves; ZIP paths outside
that root are retained. Existing correction messages retain their before/after descriptions.
Historical paths for files that cannot be moved/read need not identify an existing file.

Validation snapshots upstream and submission-rule issues before constructing attempts.
A QuestionAttempt receives submission-wide issues (null questionId), issues for its own ID,
and its new validation findings. Only new findings are appended to the submission collection;
inherited issues are not appended again for every attempt. Folder findings are scoped to all
questions sharing that folder, rather than unrelated questions.

CSV/reporting code should iterate the submission list once, not flatten all attempt lists,
which intentionally share relevant records. These records are in memory and Main currently
prints them; this change does not introduce persistent JSONL/CSV output. The existing
scoresheet-reader JSONL logger remains separate.

## Validation file-access failures

Expected filesystem failures (including lazy UncheckedIOException and access-denied
SecurityException) are recorded as unresolved validation errors. Question-rule and file-lookup
failures affect that question; folder enumeration failures affect questions sharing that folder.
A submission-rule failure without question information affects only its student submission.
Validation continues with the remaining rules/questions and later students. Programming errors
such as IllegalStateException are not swallowed by this handling.

## Compatibility with editable-file detection

The branch's existing detector runs after normalization and before validation. Header
validation checks only requiredFiles, matching the files whose headers normalization repairs.
Detected editable companions such as ShapeComparator.java do not receive identity-header
warnings, but remain in each attempt's submittedFiles for the grading handoff. Editable does
not imply required: missing-file errors also use requiredFiles. Header checks inspect
case-insensitive filename candidates and tolerate a UTF-8 BOM while retaining the branch's
non-UTF-8 comment handling.

## Shared identity-comment reader

StudentHeaderReader is the single read-only parser used by normalization, editable-file
detection and validation. It reads complete leading line/block comments up to the first
Java token, including headers longer than 15 lines. It recognizes Name, Email and Email ID
without case sensitivity, strips a UTF-8 BOM for inspection, and tolerates malformed UTF-8.
Header.names()/emails() preserve blank and repeated values in immutable lists. Blank identity
fields identify template sections; populated conflicting fields are never discarded.

The reader does not modify source files, compare roster details or create SubmissionIssues.
StudentHeaderNormalizer fills missing required-answer details while retaining original source
bytes and BOM; inserted comments follow the existing CRLF/LF style. EditableFileDetector checks hasCommentSection() to keep
its existing union behavior. StudentHeaderRule checks required answers against the roster;
blank placeholders alongside corrected values are ignored, while any conflicting populated
value produces a warning. Identity text inside Java code is excluded from header parsing.

## Explicit and nested assessment layouts

Place assessment.json in the trusted resource root to describe arbitrary required filenames:

```json
{
  "questions": [
    {
      "id": "Q1a",
      "folder": "Q1a",
      "requiredFiles": ["src/Solution.java", "src/Node.java"]
    }
  ]
}
```

Paths are relative to the question folder and retain their directory structure. Configuration
is validated against trusted template files before processing submissions. Declared paths must
match template capitalization exactly; conflicting shared-prefix capitalization and overlapping
question directories are rejected. Multiple questions may share the same directory. Support resources
are discovered recursively; required answers shared by questions in the same folder are
excluded from the supplied-support list. Without assessment.json the existing Q1/Q1a.java
filename convention still works, with nested support resources included.

Normalization recognizes declared relative answer paths, corrects nested capitalization and
creates missing destination parents when relocating unambiguous answers. It never borrows
another question's correctly placed same-named answer. Required and editable remain separate:
header detection may select a supplied companion, while only required answers receive the
required-file and identity checks. Validation hands off full question-relative paths.

This supports submission preparation, detection and validation for nested layouts. The
existing grading compiler enumerates top-level Java files only. Recursive/package-aware
compilation and execution are still the grading team's responsibility; grading source is
unchanged here.

## Exact pipeline handoff

SubmissionPreparer.prepare returns List<StudentSubmission>. Each contains the matched
StudentDetails (or null), originalZip, normalized rootDirectory and preparation/normalization
issues; questionAttempts is initially empty. EditableFileDetector.detect returns an
AssessmentDefinition containing QuestionDefinitions with detected editableFiles, requiredFiles,
allowedResourceFiles and the trusted resource directory. It does not replace submissions.

SubmissionValidator.validate receives one prepared StudentSubmission plus that detected
AssessmentDefinition. It returns the same submission, adding one QuestionAttempt per question.
Each attempt contains the detected QuestionDefinition, the absolute normalized questionDirectory,
absolute existing submittedFiles selected from editableFiles, and relevant upstream/validation
issues. Missing optional editable files do not become missing-required errors. Relative paths
can be recovered with attempt.getQuestionDirectory().relativize(file).

AssessmentGrader.grade receives List<StudentSubmission>, the same detected AssessmentDefinition,
and Map<String, TesterDefinition>. It checks the full submission issue list and attempt.isValid(),
then passes QuestionDefinition + QuestionAttempt + TesterDefinition to QuestionGrader. An ERROR
with null questionId skips the submission's questions; a question-specific ERROR skips only that
question. Warnings, including resolved normalizations, allow grading. Its returned map keeps
StudentSubmission keys, so issue lists remain available beside QuestionGrade results.

Existing GradingWorkspace consumes questionDirectory and QuestionDefinition's editable/locked
resource lists. It currently copies the student folder and overlays locked originals; it does
not yet copy exclusively from QuestionAttempt.submittedFiles. Both interfaces are supplied for
the grading team, with no source changes to grading in this integration. Main cleans temporary
submission folders after grading, so returned affectedPath values are diagnostic locations,
not a promise that extracted files remain available after Main.run returns.

## File protection retracted

The earlier validation ProtectedFileRule, automatic restoration, restoration helpers and
restoration regression runner were removed. Unexpected files remain warnings. The check walks nested question directories without following
symbolic links and compares complete relative paths, so allowing src/Solution.java does not
authorize other/Solution.java. Declared nested answer/resource paths are recognized. Validation
preserves supplied helper edits and extra files for the grading team to handle. Existing
GradingWorkspace behavior on Jerxl's branch is unchanged by this integration.

Other retained earlier work delegates ZipExtractor to FileUtils, checks contained paths,
supports nested answer-file lookup and fixes FileUtils calls to a nonexistent ValidatePath.

## Verification

Run from the repository root with Maven and JDK 25 or newer:

```powershell
mvn '-Dtest=*Test,!QuestionGraderTest' test
```

The tests use temporary synthetic submissions, including real ZIP preparation, normalization,
detection and validation. Existing branch tests also check temporary grading workspaces and
compare synthetic clean/messy submission grades. No grading source is changed. The separate
QuestionGraderTest requires a complete external assessment dataset and is not part of this run.

AI assistance: Codex assisted with issue handoff metadata, path rebasing, validation review,
ZIP utility delegation, protection retraction, shared header parsing and regression tests.
