# Editable-file detection

This branch starts from `pair-1-wo-submissionheadernormlizer` and adds the earlier
header-based editable-file detector to that branch's existing submission and
validation pipeline.

## Selection rules

Each question has two separate lists:

- `requiredFiles`: answers that must exist and receive filename and identity checks.
- `editableFiles`: required answers plus selected optional Java companions.

Required answers are always editable, even when their identity header is absent.
Other candidates come only from the instructor-supplied `allowedResourceFiles`
list for that question. Students cannot add arbitrary helper files to that list.
Only declared `.java` companions are inspected; data and compiled files are not
selected by header detection.

A companion is selected when its trusted template **or any sampled student copy**
has a leading `Name:`, `Email:`, or `Email ID:` comment field. Blank template
fields count. The union combines the evidence: if one student removes the header
and another keeps it, both still receive the same editable-file selection.
Detection uses all extracted submissions in the batch.

`StudentHeaderReader` inspects complete leading line/block comments until Java
code starts. It handles long introductory comments and a UTF-8 BOM. Header text
after code begins does not count. Detection identifies the presence of the fields;
it does not decide whether their values match the roster.

The reader and detector do not change any source files.

## Pipeline and API

`Pair1.run` first loads all extracted submissions, then detects the editable set,
then validates each student against the detected assessment.

```java
AssessmentDefinition detected =
        EditableFileDetector.detect(assessment, templateRoot, submissions);
validator.validate(submission, detected);
```

`templateRoot` contains the question directories, for example
`RenameToYourUsername/Q3/ShapeComparator.java`. An explicitly supplied template
root must exist and its declared Java candidates must be readable. Template errors
are reported as `IOException`; an unreadable student sample produces a warning in
that student's existing `SubmissionIssue` list while other samples are inspected.

The two-argument `detect(assessment, submissions)` overload works without a template
copy. In that mode the instructor's declared resource list remains the trusted
candidate list, and only student headers supply detection evidence. If every
student removes a companion's header, it cannot be inferred in this mode.

The existing four-argument `Pair1.run` remains available and uses this sample-only
mode. Its new five-argument overload accepts `templateRoot` as the last argument.
The sample project entry point uses `<project>/RenameToYourUsername` when present;
otherwise it uses the declared-resource/sample-only mode.

Detection returns new question definitions; it does not modify the original
assessment. `QuestionDefinition.getEditableFiles()` exposes the selected filenames.
Validation locates existing selected files and places their paths in
`QuestionAttempt.getAnswerFiles()`, the handoff for the later grading workspace.
Missing optional companions do not become missing-required-file errors.

## Scope

The existing normalizer placeholder, ZIP loader, and required-file/header/naming
rules are retained. Header validation still uses its original 15-line limit;
the complete leading-comment scan here is used for detection.

There is no automatic wrapper-folder or filename repair in this branch. Detection
expects the existing `rootDirectory/expectedFolder/filename` layout. There is no
new assessment configuration format, recursive unexpected-file check, file
restoration, or grading-workspace implementation.

## Verification

From the repository root, with JDK 17 or newer:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\test-editable-files.ps1
```

The runner compiles production code and the standalone regression tests into an
ignored directory under `target`. Tests use temporary synthetic templates and
submissions, including actual ZIP loading and the validation handoff.
