# evil-autograder

```bash
./run.sh
```

```powershell
.\run.bat
```

Generate all question testers in one request:

1. Copy `.env.example` to `.env` and set `LLM_API_KEY` to your OpenAI API key.
2. The config points to the local sample at `rawr/Project/`, with answer templates in `RenameToYourUsername`. Change `input.directory` and `resources.folder` if using another exam project.
3. Run from the repository root:

```bash
mvn compile exec:java -Dexec.mainClass=generation.TesterGeneratorApp
```

The default model is `gpt-6.1-terra`, with high reasoning effort for structured generation. All discovered questions and subparts are sent together with the exam PDF. The response is validated and saved as separate `<QuestionId>Tester.java` files in `generation.output.dir`, relative to `input.directory`. The combined template bundle and JSON response are saved as `context/all.md` and `response/all.json` inside that output folder.

`generation.max.retries=0` disables repair requests; the official OpenAI client also disables SDK retries. The request timeout is five minutes to allow for the larger response. PDF upload and cleanup are separate API operations. A nonzero retry setting is rejected by the one-shot generator.

Generation includes declared support text/data files, public dependency signatures from `javap`, and matching class documentation. Unreadable context is reported per affected question. Student ZIPs and ground-truth grades are excluded.

Responses contain `id`, `marks`, `issue`, and `testCode` for each requested question. Missing or ambiguous specifications are flagged rather than assigned invented marks. A generated tester must finish, emit exactly `marks` complete cases, and end with a numeric score matching its passed outcomes. The starter answers may legitimately fail all cases. These checks do not prove that the model interpreted the exam correctly.

Previous testers for the requested IDs are moved to `archive/run-*/` before generation. Only validated new testers appear in the output root; invalid sources are saved under `review/run-*/`. Any incomplete run exits with a nonzero status, even if some testers succeeded. Review the per-question summary before grading.
