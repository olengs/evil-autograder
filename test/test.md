# Submission test batches

Run only the new cases from the repository root:

```sh
mvn -q compile exec:java -Dexec.args=test/batch-02-config.properties
```

Two workers. Batch 2 reads only `test/student-submission-test/batch-02` and saves to `output/submission-tests/batch-02`. Batch 1 and its existing scores are preserved; neither batch was rerun during this reorganisation.

## Batch 1 — completed

74 ZIPs · preparation **73/74 passed** · scores **74/74 matched**.

**Only failure — WS05:** missing `ShapeComparator.java` is replaced by its template. It should be reported as missing.

**Score key:** `1` = Q2a earns 1; all other questions earn 0. `0` = all questions earn 0. A zero score can be the correct test result. Matching scores do not prove preparation is correct.

ZIP folder: `test/student-submission-test/batch-01/` · [Score comparison CSV](expected-scores.csv)

### ZIP names and student identity

| Case | Test | ZIP file | Expected score |
|---|---|---|---|
| ID01 | Correct ZIP, folder and headers | [casea.student.2026.zip](student-submission-test/batch-01/casea.student.2026.zip) | 1 |
| ID02 | Year prefix in ZIP name | [2023-2024-caseb.student.2026.zip](student-submission-test/batch-01/2023-2024-caseb.student.2026.zip) | 1 |
| ID03 | Random ZIP name; use folder | [id03-random.zip](student-submission-test/batch-01/id03-random.zip) | 1 |
| ID04 | Student-number ZIP; use folder | [81000004.zip](student-submission-test/batch-01/81000004.zip) | 1 |
| ID05 | Username ZIP; numeric folder | [casee.student.2026.zip](student-submission-test/batch-01/casee.student.2026.zip) | 1 |
| ID06 | Numeric ZIP/folder; use email | [81000006.zip](student-submission-test/batch-01/81000006.zip) | 1 |
| ID07 | Single-name email ID | [81000007.zip](student-submission-test/batch-01/81000007.zip) | 1 |
| ID08 | Uppercase full email address | [81000008.zip](student-submission-test/batch-01/81000008.zip) | 1 |
| ID09 | Numeric identity; no headers | [81000009.zip](student-submission-test/batch-01/81000009.zip) | 1 |
| ID10 | Random ZIP; numeric folder; no headers | [id10-random.zip](student-submission-test/batch-01/id10-random.zip) | 1 |
| ID11a | Unknown 7-digit identity | [7100001.zip](student-submission-test/batch-01/7100001.zip) | 0 |
| ID11b | Unknown 9-digit identity | [910000001.zip](student-submission-test/batch-01/910000001.zip) | 0 |
| ID12 | Spaces in ZIP name | [ casem.student.2026 .zip](student-submission-test/batch-01/%20casem.student.2026%20.zip) | 1 |
| ID13 | Spaces/capitalisation in folder name | [id13-random.zip](student-submission-test/batch-01/id13-random.zip) | 1 |
| ID14 | ZIP identity wins over folder/header | [caseo.student.2026.zip](student-submission-test/batch-01/caseo.student.2026.zip) | 1 |
| ID15 | Folder identity wins over header | [id15-random.zip](student-submission-test/batch-01/id15-random.zip) | 1 |
| ID16a | Duplicate student: original ZIP | [duplicate.student.2026.zip](student-submission-test/batch-01/duplicate.student.2026.zip) | 1 |
| ID16b | Duplicate student: second ZIP | [id16-duplicate-copy.zip](student-submission-test/batch-01/id16-duplicate-copy.zip) | 1 |

### Folders and subfolders

| Case | Test | ZIP file | Expected score |
|---|---|---|---|
| FL01 | Root still has template name | [cases.student.2026.zip](student-submission-test/batch-01/cases.student.2026.zip) | 1 |
| FL02 | Q1/Q2/Q3 directly inside ZIP | [caset.student.2026.zip](student-submission-test/batch-01/caset.student.2026.zip) | 1 |
| FL03 | One extra enclosing folder | [caseu.student.2026.zip](student-submission-test/batch-01/caseu.student.2026.zip) | 1 |
| FL04 | Several nested enclosing folders | [casev.student.2026.zip](student-submission-test/batch-01/casev.student.2026.zip) | 1 |
| FL05 | Two competing answer folders | [casew.student.2026.zip](student-submission-test/batch-01/casew.student.2026.zip) | 0 |
| FL06 | Q2 folder missing | [casex.student.2026.zip](student-submission-test/batch-01/casex.student.2026.zip) | 0 |
| FL07 | Q2 folder empty | [casey.student.2026.zip](student-submission-test/batch-01/casey.student.2026.zip) | 0 |
| FL08 | Lowercase q1 folder | [casez.student.2026.zip](student-submission-test/batch-01/casez.student.2026.zip) | 1 |
| FL09 | Question1 instead of Q1 | [caseaa.student.2026.zip](student-submission-test/batch-01/caseaa.student.2026.zip) | 1 |
| FL10 | Java files directly in student root | [caseab.student.2026.zip](student-submission-test/batch-01/caseab.student.2026.zip) | 1 |
| FL11 | Q1a.java inside Q2 | [caseac.student.2026.zip](student-submission-test/batch-01/caseac.student.2026.zip) | 1 |
| FL12 | File named Q1 instead of folder | [casead.student.2026.zip](student-submission-test/batch-01/casead.student.2026.zip) | 1 |

### Java files and headers

| Case | Test | ZIP file | Expected score |
|---|---|---|---|
| SF01 | q1A.java filename capitalisation | [caseae.student.2026.zip](student-submission-test/batch-01/caseae.student.2026.zip) | 1 |
| SF02 | Q1a.java missing | [caseaf.student.2026.zip](student-submission-test/batch-01/caseaf.student.2026.zip) | 1 |
| SF03 | Q1a.java is a directory | [caseag.student.2026.zip](student-submission-test/batch-01/caseag.student.2026.zip) | 1 |
| SF04 | Q1a.java empty | [caseah.student.2026.zip](student-submission-test/batch-01/caseah.student.2026.zip) | 1 |
| SF05 | Q1a.java.txt extension | [caseai.student.2026.zip](student-submission-test/batch-01/caseai.student.2026.zip) | 1 |
| SF06 | Q1a (1).java filename | [caseaj.student.2026.zip](student-submission-test/batch-01/caseaj.student.2026.zip) | 1 |
| SF07 | Two misplaced copies of Q1a.java | [caseak.student.2026.zip](student-submission-test/batch-01/caseak.student.2026.zip) | 1 |
| SF08 | Block headers; spaces before colon | [caseal.student.2026.zip](student-submission-test/batch-01/caseal.student.2026.zip) | 1 |
| SF09 | Headers using // comments | [caseam.student.2026.zip](student-submission-test/batch-01/caseam.student.2026.zip) | 1 |
| SF10a | No name or email headers | [casean.student.2026.zip](student-submission-test/batch-01/casean.student.2026.zip) | 1 |
| SF10b | Blank name and email values | [caseao.student.2026.zip](student-submission-test/batch-01/caseao.student.2026.zip) | 1 |
| SF10c | Name only; email missing | [caseap.student.2026.zip](student-submission-test/batch-01/caseap.student.2026.zip) | 1 |
| SF10d | Email only; name missing | [caseaq.student.2026.zip](student-submission-test/batch-01/caseaq.student.2026.zip) | 1 |
| SF11 | Same email with different capitalisation | [81000044.zip](student-submission-test/batch-01/81000044.zip) | 1 |
| SF12 | Conflicting emails across files | [81000045.zip](student-submission-test/batch-01/81000045.zip) | 1 |
| SF13 | Unreadable unrelated Java file | [81000046.zip](student-submission-test/batch-01/81000046.zip) | 1 |
| SF14 | Unreadable required answer | [caseau.student.2026.zip](student-submission-test/batch-01/caseau.student.2026.zip) | 1 |
| SF15 | Recover email identity without name | [81000048.zip](student-submission-test/batch-01/81000048.zip) | 1 |

### Broken or unusual ZIPs

| Case | Test | ZIP file | Expected score |
|---|---|---|---|
| EX01a | Corrupt ZIP | [caseaw.student.2026.zip](student-submission-test/batch-01/caseaw.student.2026.zip) | 0 |
| EX01b | Truncated ZIP | [caseax.student.2026.zip](student-submission-test/batch-01/caseax.student.2026.zip) | 0 |
| EX01c | Text file named .zip | [caseay.student.2026.zip](student-submission-test/batch-01/caseay.student.2026.zip) | 0 |
| EX02 | Empty ZIP | [caseaz.student.2026.zip](student-submission-test/batch-01/caseaz.student.2026.zip) | 0 |
| EX03 | Entry attempts ../ path escape | [caseba.student.2026.zip](student-submission-test/batch-01/caseba.student.2026.zip) | 0 |
| EX04 | Entry uses absolute outside path | [casebb.student.2026.zip](student-submission-test/batch-01/casebb.student.2026.zip) | 0 |
| EX05 | Duplicate entries inside ZIP | [casebc.student.2026.zip](student-submission-test/batch-01/casebc.student.2026.zip) | 0 |
| EX06 | ZIP containing another ZIP | [casebd.student.2026.zip](student-submission-test/batch-01/casebd.student.2026.zip) | 0 |
| EX07 | Uppercase .ZIP extension | [casebe.student.2026.ZIP](student-submission-test/batch-01/casebe.student.2026.ZIP) | 1 |
| EX08a | Mixed batch: valid first ZIP | [mixedaa.student.2026.zip](student-submission-test/batch-01/mixedaa.student.2026.zip) | 1 |
| EX08b | Mixed batch: corrupt middle ZIP | [mixedab.student.2026.zip](student-submission-test/batch-01/mixedab.student.2026.zip) | 0 |
| EX08c | Mixed batch: valid last ZIP | [mixedac.student.2026.zip](student-submission-test/batch-01/mixedac.student.2026.zip) | 1 |

### Workspace preparation

| Case | Test | ZIP file | Expected score |
|---|---|---|---|
| WS01 | Normal workspace preparation | [casebi.student.2026.zip](student-submission-test/batch-01/casebi.student.2026.zip) | 1 |
| WS02a | Restore missing supplied data | [casebj.student.2026.zip](student-submission-test/batch-01/casebj.student.2026.zip) | 1 |
| WS02b | Restore missing dependency classes | [casebk.student.2026.zip](student-submission-test/batch-01/casebk.student.2026.zip) | 1 |
| WS03 | Ignore stale answer bytecode | [casebl.student.2026.zip](student-submission-test/batch-01/casebl.student.2026.zip) | 1 |
| WS04 | Use student-edited helper | [casebm.student.2026.zip](student-submission-test/batch-01/casebm.student.2026.zip) | 1 |
| WS05 **FAIL** | Missing required ShapeComparator.java | [casebn.student.2026.zip](student-submission-test/batch-01/casebn.student.2026.zip) | 1 |
| WS06a | Ignore unrelated Java source | [casebo.student.2026.zip](student-submission-test/batch-01/casebo.student.2026.zip) | 1 |
| WS06b | Use trusted tester over old student tester | [casebp.student.2026.zip](student-submission-test/batch-01/casebp.student.2026.zip) | 1 |
| WS07 | Trusted data/tester beats student copy | [casebq.student.2026.zip](student-submission-test/batch-01/casebq.student.2026.zip) | 1 |
| WS08a | Separate workspace: student A | [casebr.student.2026.zip](student-submission-test/batch-01/casebr.student.2026.zip) | 1 |
| WS08b | Separate workspace: student B | [casebs.student.2026.zip](student-submission-test/batch-01/casebs.student.2026.zip) | 1 |
| WS09 | Correct copied answer path/spelling | [casebt.student.2026.zip](student-submission-test/batch-01/casebt.student.2026.zip) | 1 |
| WS10a | Cleanup after successful preparation | [casebu.student.2026.zip](student-submission-test/batch-01/casebu.student.2026.zip) | 1 |
| WS10b | Cleanup after tester-copy failure | [casebv.student.2026.zip](student-submission-test/batch-01/casebv.student.2026.zip) | 1 |

## Batch 2 — not tested

13 new ZIPs using Ping Lee's implementations. All results are **Pending**; only ZIP construction was checked. [Expected identities and scores](batch-02-expected-scores.csv). These scores differ from batch 1 because this reference student has a full-mark implementation.

| Case | Test | ZIP | Expected total | Result |
|---|---|---|---|---|
| B201 | Email identity on line 15 | [82000001.zip](student-submission-test/batch-02/82000001.zip) | 20 | Pending |
| B202 | Email identity on line 16 | [82000002.zip](student-submission-test/batch-02/82000002.zip) | 20 | Pending |
| B203 | Windows CRLF headers | [82000003.zip](student-submission-test/batch-02/82000003.zip) | 20 | Pending |
| B204 | UTF-8 BOM before headers | [82000004.zip](student-submission-test/batch-02/82000004.zip) | 0 | Pending |
| B205 | Conflicting emails in one file | [82000005.zip](student-submission-test/batch-02/82000005.zip) | 20 | Pending |
| B206 | Case-only duplicate answer filenames | [duplicatecase.student.2026.zip](student-submission-test/batch-02/duplicatecase.student.2026.zip) | 0 | Pending |
| B207 | Correct answer plus relocated backup | [backupcopy.student.2026.zip](student-submission-test/batch-02/backupcopy.student.2026.zip) | 20 | Pending |
| B208 | Relocated answer inside search limit | [inside.student.2026.zip](student-submission-test/batch-02/inside.student.2026.zip) | 20 | Pending |
| B209 | Relocated answer outside search limit | [outside.student.2026.zip](student-submission-test/batch-02/outside.student.2026.zip) | 17 | Pending |
| B210 | README beside username folder | [readmeroot.student.2026.zip](student-submission-test/batch-02/readmeroot.student.2026.zip) | 20 | Pending |
| B211 | ZIP entry order: forward | [b211-entry-order.zip](student-submission-test/batch-02/b211-entry-order.zip) | 20 | Pending |
| B212 | ZIP entry order: reverse | [b212-entry-order.zip](student-submission-test/batch-02/b212-entry-order.zip) | 20 | Pending |
| B213 | Full-mark control | [fullcontrol.student.2026.zip](student-submission-test/batch-02/fullcontrol.student.2026.zip) | 20 | Pending |

- **B201/B202:** Email ID appears exactly on line 15/16 in all six Java files. Line 15 should identify the student; line 16 is outside the reader limit, so username stays blank. Numeric filenames still allow grading.
- **B204:** Intended BOM-aware header recovery is a pending compatibility check. Existing code does not strip BOM; every Java source has one, so compiler rejection and zero marks are expected without preprocessing. Record unsupported identity handling honestly.
- **B205:** Two different emails occur in one file; other files have no identity headers. Expected identity is unresolved. The current reader may incorrectly pick the first email; do not mark this passed without checking the username.
- **B206:** Case-only duplicate filenames must not be silently selected. Expected total is 0 for the detected case-insensitive temporary filesystem. On a case-insensitive filesystem extraction can fail (0); on a case-sensitive filesystem only Q1a can be blocked (17).
- **B207:** Expected-folder answer should win over the relocated backup.
- **B208/B209:** Relocated file depth is 5/6 relative to a fixed answer root. The inside case should score 20; the outside case should skip Q1a and score 17.
- **B210:** README and username folder are siblings. Answers should still be recovered.
- **B211/B212:** Same entry names and contents, opposite entry order; both should prepare and score identically. Shared identity is intentional for this pair.
- **B213:** Full-mark control: `3,3,5,5,4` = **20**.

To rerun batch 1 separately, use `test/test-config.properties`. Its output remains `output/submission-tests`.
