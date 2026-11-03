// Tests identity-section detection, scan limits, and read errors.
// Testing only; not required by the application.

package autograder.validation;

import autograder.validation.rules.StudentHeaderRule;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import static autograder.validation.ValidationTestSupport.*;

public class HeaderSectionDetectionTest {
    public static void main(String[] args) throws Exception {
        blankNameOrEmailFieldsAreDetectionEvidence();
        fieldsAfterCodeAreNotDetectionEvidence();
        detectionPreservesFifteenLineBoundary();
        templateReadFailuresRemainCheckedErrors();
        System.out.println("HeaderSectionDetectionTest: 4 tests passed");
    }

    private static void blankNameOrEmailFieldsAreDetectionEvidence() throws Exception {
        try (ValidationTestSupport fixture = new ValidationTestSupport()) {
            for (String source : new String[] {"// Name:\nclass Answer {}\n",
                    "/*\n * Email:\n */\nclass Answer {}\n",
                    "// eMaIl ID: \nclass Answer {}\n"}) {
                Path file = fixture.write("Template.java", source);
                check(StudentHeaderRule.hasHeaderSection(file), "blank identity field was ignored: " + source);
                check(Files.readString(file).equals(source), "detection changed template contents");
            }
        }
    }

    private static void fieldsAfterCodeAreNotDetectionEvidence() throws Exception {
        try (ValidationTestSupport fixture = new ValidationTestSupport()) {
            for (String source : new String[] {"class Answer {}\n// Name: Alice\n",
                    "// introductory comment\nclass Answer {}\n// Email: alice@school.edu\n",
                    "// Username: Alice\n// EmailAddress: alice@school.edu\nclass Answer {}\n"}) {
                check(!StudentHeaderRule.hasHeaderSection(fixture.write("Template.java", source)),
                        "body comment or unrelated label was treated as a template header");
            }
        }
    }

    private static void detectionPreservesFifteenLineBoundary() throws Exception {
        try (ValidationTestSupport fixture = new ValidationTestSupport()) {
            check(StudentHeaderRule.hasHeaderSection(fixture.write("Inside.java",
                    "// introduction\n".repeat(14) + "// Name:\nclass Inside {}\n")),
                    "line fifteen should still be inspected");
            check(!StudentHeaderRule.hasHeaderSection(fixture.write("Outside.java",
                    "// introduction\n".repeat(15) + "// Name:\nclass Outside {}\n")),
                    "detection unexpectedly changed the existing fifteen-line policy");
        }
    }

    private static void templateReadFailuresRemainCheckedErrors() throws Exception {
        try (ValidationTestSupport fixture = new ValidationTestSupport()) {
            Path malformed = fixture.root.resolve("Malformed.java");
            Files.write(malformed, new byte[] {(byte) 0xc3, 0x28});
            for (Path file : new Path[] {fixture.root.resolve("Missing.java"), malformed,
                    Files.createDirectory(fixture.root.resolve("Directory.java"))}) {
                try {
                    StudentHeaderRule.hasHeaderSection(file);
                    throw new AssertionError("unreadable template should produce IOException: " + file);
                } catch (IOException expected) {
                    // Existing strict UTF-8 and checked file-access behavior must be retained.
                }
            }
        }
    }
}
