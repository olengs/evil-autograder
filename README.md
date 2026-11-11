# Evil Autograder

Batch grading for Java lab tests, exams, and assignments. Compiles and tests student submissions, handles common folder mistakes, and exports scores to CSV.

## Table of contents

- [Requirements](#requirements)
- [Setup](#setup)
  - [Configuration](#configuration)
- [How to run](#how-to-run)
- [UI features](#ui-features)
  - [Generate testers](#1-generate-testers)
  - [Run grading](#2-run-grading)
- [Assumptions](#assumptions)
- [Misc Notes](#misc-notes)

## Requirements

- JDK 25 on your PATH.
- Maven: install it and add it to PATH.
- Assessment resources, testers, and student ZIPs.
- An OpenAI (ChatGPT) API key for AI tester generation.

## Setup

Provide the assessment PDF, starter files, dependencies, testers, and student ZIPs.

- Eg. folder layout:

  ```text
  Project/
  ├── assessment.pdf
  ├── student-submission/       # Student ZIPs; subfolders allowed
  ├── Tester-Files/             # Java testers, marks.txt, numTestCases.txt
  └── RenameToYourUsername/
      ├── Q1/
      ├── Q2/
      └── Q3/
  ```

1. **Set assessment paths**

   - Update the paths in `config.properties`.
   - Include required helper classes and data files.

2. **Organise starter answers**

   - Put answers in question folders.

     - Eg. `Q1/Q1a.java` or `Task/Task.java`.

   - Folder names can vary.

     - Eg. `Q1`, `Q2`, or `Task`.

   - Answer filenames must match the folder name, optionally followed by letters. Matching is case-sensitive.

3. **Prepare existing testers**

   - Name each tester `<AnswerName>Tester.java`.

     - Eg. `Q1Tester.java`.

   - Add question marks to `marks.txt`.

     - Eg. `Q1:5` means 5 marks.

   - Add case counts to `numTestCases.txt`.

     - Eg. `Q1:10` means 10 cases.

   - Missing counts are inferred from literal `System.out.println("Passed");` statements when possible.
   - Number of test cases should be a multiple of marks eg. 5 mark 5 test case, 5 mark 10 test case, 2.5 marks 5 test case
   - Number of test case should be a whole number
   - AI generation creates the testers and metadata files automatically.

4. **Set up AI generation**

   - Copy `.env.example` to `.env`.
   - Set `openapikey=your-api-key`.
   - Check `exam.pdf.file` and the `llm.*` settings in `config.properties`.

### Configuration

Key settings in `config.properties`:

| Settings | Purpose |
| --- | --- |
| `input.directory`, `output.directory` | Input and report locations |
| `submissions.folder`, `testers.folder`, `resources.folder` | Assessment folders under the input directory |
| `scoresheet.file` | Export filename |
| `exam.pdf.file` | Assessment PDF for tester generation |
| `grading.workers` | Parallel workers; default: 12 |
| `compilation.timeout.seconds`, `execution.timeout.seconds` | Time limits; default: 5 seconds each (increase this as you increase number of grading workers, more info under misc notes) |
| `submission.header.search.depth`, `submission.answer.search.depth` | Search depths; default: 6 and 5 |
| `log.levels` | Logs `ERROR` by default; add `WARNING` for validation warnings or `LOG` for all logs |

## How to run

Run from the repository root. Both scripts compile and start the app.

| Platform | Command |
| --- | --- |
| macOS/Linux | `./run.sh` |
| Windows PowerShell | `.\run.bat` |

The terminal menu has two features: **1 — generate testers** and **2 — run grading**.

## UI features

### 1. Generate testers

Creates new Java testers from your assessment PDF and starter files using OpenAI.

- Select **1 — generate testers** and review the summary.
- The app checks that testers compile, run, and produce valid output. Instructors should review the test cases.
- A complete set becomes active for grading in the current session. Failed generation keeps the previous testers.
- To reuse a set after restarting, point `testers.folder` to its saved run folder under the input directory.

  - Eg. `trial/run-*`.

### 2. Run grading

Grades all student ZIPs using the active tester set. You can use existing testers without generating new ones.

- Select **2 — run grading** to start the batch.
- The app handles common folder mistakes and reports missing files and header issues.
- Blocked questions are skipped so the batch can continue.
- Marks use `passed cases × maximum marks ÷ declared case count`.
- Execution timeouts retain completed-case marks when the expected count is known.

Results are saved to **`output/IS442-ScoreSheet.csv`**, replacing the previous report. The CSV includes totals, question marks, and identification flags. Change `output.directory` and `scoresheet.file` to use another location or filename.

## Assumptions

- All ZIPs in a batch belong to one assessment and use the same testers.
- Answers follow the supplied Java templates and use the default package. Required dependencies are provided.
- Usernames follow the expected format.

  - Eg. `jiagu.wen.2025`.

- Header checks expect `Name:` and `Email:` or `Email ID:` in leading comments within the first 15 lines.
- Duplicate ZIPs remain separate report rows; the instructor chooses which attempt counts.
- Test cases have equal weight within each question. Warnings do not automatically deduct marks.
- Instructors review tester correctness and unclear identities. Missing headers are flagged, not rewritten.
- Submissions run locally with the application's permissions. Time limits do not provide a security sandbox.


## Misc Notes

- Only have 1 compiler is a bottleneck in a monolithic architecture. We cant compile all the student’s submission concurrently.
- Because of only having 1 compiler, the compiler timeout need to increased based on the number of worker threads, the compiler timeout include waiting for the java compiler to be freed up
- AI generation: When the pdf given is not very specific about the test it has to do for each question, the generated tester would be based on the AI’s knowledge