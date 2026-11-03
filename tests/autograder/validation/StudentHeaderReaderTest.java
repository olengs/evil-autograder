// Tests shared comment parsing and preserved encoding behavior.
// Testing only; not required by the application.

package autograder.validation;

import autograder.utils.StudentHeaderReader;
import autograder.validation.rules.StudentHeaderRule;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import static autograder.validation.ValidationTestSupport.*;

public class StudentHeaderReaderTest {
    public static void main(String[] args) throws Exception {
        readerSharesLeadingCommentsWithoutIncludingBodyComments();
        directReaderAndCompatibilityWrapperKeepTheExistingScanBoundary();
        bomAndStrictUtf8BehaviorRemainUnchanged();
        System.out.println("StudentHeaderReaderTest: 3 tests passed");
    }

    private static void readerSharesLeadingCommentsWithoutIncludingBodyComments() throws Exception {
        try (ValidationTestSupport fixture = new ValidationTestSupport()) {
            String source = "/*\n * Name: Alice\n */\n// Email ID: alice@school.edu\n"
                    + "class Answer {}\n// Name: Body Only\n";
            Path file = fixture.write("Answer.java", source);
            byte[] original = Files.readAllBytes(file);
            String comments = StudentHeaderReader.readLeadingComments(file);
            check(comments.contains("Name: Alice") && comments.contains("Email ID: alice@school.edu"),
                    "reader lost leading block or line comments");
            check(!comments.contains("class Answer") && !comments.contains("Body Only"),
                    "reader included Java code or comments after code");
            check(StudentHeaderReader.hasHeaderSection(file), "reader did not recognize identity labels");
            check(StudentHeaderRule.hasHeaderSection(file), "compatibility wrapper changed detection");
            check(Arrays.equals(original, Files.readAllBytes(file)), "shared reader changed source bytes");
        }
    }

    private static void directReaderAndCompatibilityWrapperKeepTheExistingScanBoundary() throws Exception {
        try (ValidationTestSupport fixture = new ValidationTestSupport()) {
            Path inside = fixture.write("Inside.java", "// introduction\n".repeat(14) + "// Name:\nclass Inside {}\n");
            Path outside = fixture.write("Outside.java", "// introduction\n".repeat(15) + "// Name:\nclass Outside {}\n");
            check(StudentHeaderReader.hasHeaderSection(inside), "shared reader missed line fifteen");
            check(!StudentHeaderReader.hasHeaderSection(outside), "shared reader extended the scan policy");
            check(StudentHeaderRule.hasHeaderSection(inside) == StudentHeaderReader.hasHeaderSection(inside)
                    && StudentHeaderRule.hasHeaderSection(outside) == StudentHeaderReader.hasHeaderSection(outside),
                    "validation compatibility wrapper differs from shared template inspection");
            check(!StudentHeaderReader.readLeadingComments(outside).contains("Name:"),
                    "raw shared comments unexpectedly include a field after line fifteen");
        }
    }

    private static void bomAndStrictUtf8BehaviorRemainUnchanged() throws Exception {
        try (ValidationTestSupport fixture = new ValidationTestSupport()) {
            Path bom = fixture.write("Bom.java", "\ufeff// Name: Alice\n// Email: alice@school.edu\nclass Bom {}\n");
            byte[] bomBytes = Files.readAllBytes(bom);
            check(StudentHeaderReader.readLeadingComments(bom).isEmpty(), "refactor changed existing BOM parsing");
            check(!StudentHeaderReader.hasHeaderSection(bom) && !StudentHeaderRule.hasHeaderSection(bom),
                    "refactor made the compatibility wrapper and reader disagree on BOM handling");
            check(Arrays.equals(bomBytes, Files.readAllBytes(bom)), "inspection stripped the source BOM");
            Path malformed = fixture.root.resolve("Malformed.java");
            Files.write(malformed, new byte[] {(byte) 0xc3, 0x28});
            for (Path file : new Path[] {malformed, fixture.root.resolve("Missing.java")}) {
                try {
                    StudentHeaderReader.readLeadingComments(file);
                    throw new AssertionError("shared reader swallowed a checked read failure: " + file);
                } catch (IOException expected) {
                    // Shared inspection must retain strict UTF-8 and checked read failures.
                }
            }
        }
    }
}
