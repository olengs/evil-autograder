# Library Lab test set

40 student submissions covering normal grading and common edge cases. The main `config.properties` is already set up for this assessment.

## Run

1. From the repository root, run `./run.sh` (Windows: `.\run.bat`).
2. Select **2 — run grading**.
3. Compare `results/Library-Lab-Scores.csv` with `expected-scores.csv` in this folder.

The app creates `results/` when needed. No API key or tester generation is required.

## Contents

| Folder/file | Purpose |
| --- | --- |
| `Inbox/` | 40 student ZIPs |
| `StarterKit/` | Java answer templates |
| `Checks/` | Testers, marks, and test-case counts |
| `expected-scores.csv` | Expected scores, statuses, and scenario for each submission |
| `results/` | Grading output and logs, created when you run the app |

## Questions

| Answer | Task | Marks |
| --- | --- | --- |
| `Inventory.java` | Sum positive quantities; ignore zero and negatives | 4 |
| `Texta.java` | Count English vowels, ignoring case | 2 |
| `Textb.java` | Reverse text, preserving spaces and punctuation | 4 |

All inputs are non-null. Each question has four equally weighted test cases. Total: **10 marks**.

## Scenarios

- Flat, nested, unrenamed, and lowercase folders/files.
- Missing, blank, mismatched, and block-comment headers; unknown identity.
- Missing or ambiguous answers, hidden files, stale classes, and search-depth limits.
- Uppercase ZIP extension, year prefix, empty ZIP, corrupt ZIP, and unsafe archive paths.
- Compilation errors, runtime errors, output limits, and timeout with partial marks.
- Correct answers, logic mistakes, zero scores, and fractional marks.

Some ZIPs are deliberately invalid. Their errors are expected; the batch should still finish with **40 report rows**. See `expected-scores.csv` for each submission's expected result.
