// Tests exact-first lookup, case fallback, and ambiguous matches.
// Testing only; not required by the application.

package autograder.validation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import static autograder.validation.ValidationTestSupport.*;

public class AnswerFileLocatorTest {
    public static void main(String[] args) throws Exception {
        exactMatchNeverCallsFallback();
        fallbackPreservesActualFilenameAndMissingAnswersAreOmitted();
        ambiguityPropagatesThroughSingleAndBatchLookup();
        System.out.println("AnswerFileLocatorTest: 3 tests passed");
    }

    private static void exactMatchNeverCallsFallback() throws Exception {
        try (ValidationTestSupport fixture = new ValidationTestSupport()) {
            Path answer = fixture.write("Q1/Answer.java", "class Answer {}\n");
            AnswerFileLocator locator = new AnswerFileLocator() {
                @Override
                public Optional<Path> findIgnoringCase(Path directory, String required) {
                    throw new AssertionError("fallback must not run after exact match");
                }
            };
            Path directory = answer.getParent();
            check(locator.findCandidate(directory, "Answer.java").orElseThrow().equals(answer), "exact lookup failed");
            check(locator.locateAnswerFiles(directory, List.of("Answer.java")).equals(List.of(answer)),
                    "handoff did not reuse exact-first lookup");
            check(locator.locateAnswerFiles(directory, List.of("Answer.java", "Answer.java"))
                    .equals(List.of(answer, answer)), "existing repeated-required-file behavior changed");
        }
    }

    private static void fallbackPreservesActualFilenameAndMissingAnswersAreOmitted() throws Exception {
        try (ValidationTestSupport fixture = new ValidationTestSupport()) {
            Path answer = fixture.write("Q1/answer.java", "class Answer {}\n");
            AnswerFileLocator locator = new AnswerFileLocator();
            Path selected = locator.findCandidate(answer.getParent(), "Answer.java").orElseThrow();
            check(selected.getFileName().toString().equals("answer.java"), "fallback lost actual filename spelling");
            check(locator.locateAnswerFiles(answer.getParent(), List.of("Answer.java", "Missing.java"))
                    .equals(List.of(selected)), "handoff must retain candidate and omit absent files");
            check(locator.findCandidate(fixture.root.resolve("absent"), "Answer.java").isEmpty(),
                    "missing folder should have no candidates");
        }
    }

    private static void ambiguityPropagatesThroughSingleAndBatchLookup() throws Exception {
        try (ValidationTestSupport fixture = new ValidationTestSupport()) {
            Path directory = Files.createDirectory(fixture.root.resolve("Q1"));
            IOException expected = new IOException("ambiguous filename > Answer.java");
            AnswerFileLocator locator = new AnswerFileLocator() {
                @Override
                public Optional<Path> findIgnoringCase(Path folder, String required) throws IOException {
                    throw expected;
                }
            };
            try {
                locator.findCandidate(directory, "Answer.java");
                throw new AssertionError("single lookup swallowed ambiguity");
            } catch (IOException actual) {
                check(actual == expected, "single lookup replaced original error");
            }
            try {
                locator.locateAnswerFiles(directory, List.of("Answer.java"));
                throw new AssertionError("batch lookup swallowed ambiguity");
            } catch (IOException actual) {
                check(actual == expected, "batch lookup replaced original error");
            }
        }
    }
}
