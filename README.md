# evil-autograder
CS102 Project

## Submission normalization

Pair 1 runs `identify -> extract -> normalize -> validate`. The normalizer changes only extracted working copies; submitted ZIPs are read-only.

- Selects the question root from a username folder, `RenameToYourUsername`, direct question folders, or a unique wrapper containing recognizable question folders.
- Restores the exact assessment spelling of question folders, required answer filenames and explicitly declared resource filenames. Java filenames are never indiscriminately lowercased.
- Matches other question-folder names only through a unique set of required answer filenames or explicit aliases. It does not compare with other students' submissions.
- Fills missing name/email comments from a matched roster entry. Conflicting identification stays unchanged and produces a warning; source logic stays unchanged.
- Records corrections as warnings and ambiguous mappings, collisions or unsafe layouts as errors. Conflicting files/folders are never merged or overwritten. Unrelated resources and metadata are retained.

`SubmissionNormalizer.normalize(submission, assessment)` updates the submission root and attaches issues before `SubmissionValidator` creates question attempts. An optional third argument accepts folder aliases, for example `Map.of("QuestionOne", "Q1")`. Expected names come from `AssessmentDefinition`; the sample entry point still constructs that definition in code. Pair 3 supplies real project discovery/configuration.

The static loader/extractor APIs from the submission refactor are retained. `ZipExtractor.ExtractZipFiles` returns actual top-level directories, including directories that were implicit in the ZIP. Invalid archives, path traversal and colliding ZIP paths are rejected; existing extraction destinations are preserved. Extraction failures are reported to standard error and the batch continues.

