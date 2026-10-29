# evil-autograder

```bash
mvn test
```

<!-- migrated-maven-guide -->
## Build and test

Requires JDK 25, Maven on PATH, and the local assessment input files.
Run commands from the evil-autograder repository root.

~~~bash
mvn -DskipTests compile
mvn -Dtest=SubmissionValidationTest test
mvn test
~~~

`mvn test` runs validation tests and QuestionGraderTest, which grades real
submission ZIPs and prints results. `sh maven.sh` (macOS/Linux) and
`maven.bat` (Windows Command Prompt) run the same full test suite.
There is no separate integration profile.

## Local input configuration

The final migration step sets `input.directory=../hehe/rawr/Project` in
config.properties. Change this path if your assessment lives elsewhere.
The directory must contain student-submission/, Tester-Files/,
RenameToYourUsername/, and IS442-ScoreSheet.csv. Grading uses temporary workspaces and cleans them up afterward.
The local assessment data is not included in Git.

Run the application with `mvn exec:java` after compiling.
The legacy compile.sh and run.sh use pre-Maven paths; use the Maven
commands above.

The original source README is preserved in [docs/hehe-README.md](docs/hehe-README.md).
