# Evil Autograder

CS102 G2-T5 project. Grades Java submission ZIPs in batches, handles common folder mistakes, and exports scores to CSV.

## Requirements

- JDK 25 and Maven on your PATH.
- Assessment resources, testers, and student ZIPs.
- Internet for the first dependency download. An API key is needed only for AI generation.

## Setup

Keep the supplied files in this layout:

```text
Project/
├── IS442-ExamSample.pdf
├── student-submission/       # Student ZIPs; subfolders allowed
├── Tester-Files/             # Java testers, marks.txt, numTestCases.txt
└── RenameToYourUsername/
    ├── Q1/
    ├── Q2/
    └── Q3/
```

Keep all supplied helper files, including Q3's `Shape.class`, `Circle.class`, and `Rectangle.class`. After cloning, copy any missing assessment files from the original package; some are ignored by Git.

## Run

Run from the repository root:

| Action | Command |
| --- | --- |
| Compile | `mvn compile` |
| Start on macOS/Linux | `./run.sh` |
| Start on Windows PowerShell | `.\run.bat` |

In the menu, select **2 — run grading**. Results are saved to **`output/IS442-ScoreSheet.csv`**, replacing the previous report.

Use **↑/↓ + Enter** or a menu number to select an action.

- **q:** cancel an operation or quit from the menu.
- **Enter:** return to the menu after completion.
- **Ctrl+C:** exit.

If setup reports an invalid path, enter a replacement for the current run.

## Configuration

Edit `config.properties` to change:

| Settings | Purpose |
| --- | --- |
| `input.directory`, `output.directory` | Input and report locations |
| `submissions.folder`, `testers.folder`, `resources.folder` | Assessment folders under the input directory |
| `scoresheet.file` | Export filename |
| `grading.workers` | Parallel workers; default: 30 |
| `compilation.timeout.seconds`, `execution.timeout.seconds` | Time limits; default: 5 seconds each |
| `submission.header.search.depth`, `submission.answer.search.depth` | Search depths; default: 6 and 5 |
| `log.levels` | Logs `ERROR` by default; add `WARNING` for validation warnings |

Relative paths start from the repository root when launched there. Student details come from ZIP names and source headers; no input scoresheet is needed.

## Grading

- Recovers common folder and filename mistakes; reports missing files and header issues.
- Skips blocked questions and continues grading the batch.
- Awards `passed cases × maximum marks ÷ declared case count`.
- Retains completed-case marks after an execution timeout when the expected count is known.
- Exports totals, question marks, and identification flags. Errors appear in the interface and configured logs.

The supplied assessment totals **20 marks**: Q1a: 3, Q1b: 3, Q2a: 5, Q2b: 5, Q3: 4.

## Assumptions

- All ZIPs in a batch belong to one assessment and use the same testers.
- Answers follow the supplied Java templates and use the default package. Required dependencies are provided.
- Usernames resemble `jiagu.wen.2025`. Header checks expect `Name:` and `Email:` or `Email ID:` in leading comments within the first 15 lines.
- Duplicate ZIPs remain separate report rows; the instructor chooses which attempt counts.
- Test cases have equal weight within each question. Warnings do not automatically deduct marks.
- Instructors review tester correctness and unclear identities. Missing headers are flagged, not rewritten.
- Submissions run locally with the application's permissions. Time limits do not provide a security sandbox.

## Optional AI testers

1. Copy `.env.example` to `.env` and set `openapikey=your-api-key`.
2. Check `exam.pdf.file` and the `llm.*` settings in `config.properties`.
3. Choose **1 — generate testers** and review the summary.

The exam PDF and starter files are sent to OpenAI. The app checks that generated testers compile, run, and produce valid output. Instructors should review the test cases.

A complete set is used for the current session. Failed generation keeps the previous testers. To reuse a saved set after restarting, set `testers.folder` to its `trial/run-*` folder under `Project/`.

## Project structure

Java source is in `src/main/java/`, with separate packages for validation, grading, reports, the interface, and tester generation.

Libraries are listed in `pom.xml`: TamboUI/JLine3, Commons IO, Gson, OpenCSV, dotenv-java, OpenAI Java SDK, SLF4J NOP, and JUnit.

## Troubleshooting

- **Maven missing:** install Maven and add it to PATH.
- **Java release error:** check that `mvn -version` uses JDK 25.
- **Missing inputs or Q3 classes:** restore the supplied assessment files and check paths.
- **Frequent timeouts:** lower the worker count or increase time limits.
- **Small terminal:** enlarge it to at least 32 columns × 16 rows.

To check the app, grade the supplied submissions and inspect the CSV and log. This checkout has no automated test suite.
