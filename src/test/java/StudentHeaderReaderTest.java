import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import utils.StudentHeaderReader;

class StudentHeaderReaderTest {
    @TempDir
    Path temp;

    @Test
    void blankTemplateFieldsStillIdentifyAnIdentitySection() {
        var header = parse("/*\n * Name:    \n * Email ID:\n */\npublic class Answer {}\n");

        assertTrue(header.hasCommentSection());
        assertEquals(List.of(""), header.names());
        assertEquals(List.of(""), header.emails());
    }

    @Test
    void readsMixedLeadingCommentStylesAndCaseInsensitiveFieldAliases() {
        var header = parse("""
                /** Assessment instructions.
                 * nAmE:   Alice Example
                 */
                // eMaIl Id:   alice@example.edu
                /* EMAIL: alternate@example.edu */
                public class Answer {}
                """);

        assertTrue(header.hasCommentSection());
        assertEquals(List.of("Alice Example"), header.names());
        assertEquals(List.of("alice@example.edu", "alternate@example.edu"), header.emails());
    }

    @Test
    void ignoresCodeBodyCommentsStringLiteralsAndIncompleteComments() {
        for (String source : List.of(
                "public class Answer {\n// Name: Body Comment\n/* Email: body@example.edu */\n}",
                "class Answer { String text = \"// Name: String Literal\"; }",
                "package answer;\n// Name: After Package\n// Email: after@example.edu\nclass Answer {}",
                "/*\n * Name: Unfinished\n * Email: unfinished@example.edu")) {
            var header = parse(source);

            assertFalse(header.hasCommentSection(), source);
            assertTrue(header.names().isEmpty(), source);
            assertTrue(header.emails().isEmpty(), source);
        }
    }

    @Test
    void readsFieldsBeyondFifteenLinesButStopsWhenCodeBegins() {
        String source = "/*\n" + " * Assessment instructions\n".repeat(25)
                + " * Name: Alice\n * Email: alice@example.edu\n */\n"
                + "public class Answer {}\n// Name: Not A Header\n";

        var header = parse(source);

        assertEquals(List.of("Alice"), header.names());
        assertEquals(List.of("alice@example.edu"), header.emails());
    }

    @Test
    void preservesDuplicateAndConflictingValuesInImmutableLists() {
        var header = parse("""
                // Name: Alice
                // Name: Alice
                // Name: Bob
                // Email: alice@example.edu
                // Email ID: bob@example.edu
                public class Answer {}
                """);

        assertEquals(List.of("Alice", "Alice", "Bob"), header.names());
        assertEquals(List.of("alice@example.edu", "bob@example.edu"), header.emails());
        assertThrows(UnsupportedOperationException.class, () -> header.names().add("Eve"));
        assertThrows(UnsupportedOperationException.class, () -> header.emails().clear());
    }

    @Test
    void recognisesUtf8BomAndWindowsLineEndings() {
        var header = parse("\uFEFF/*\r\n * Name: Alice\r\n * Email ID: alice@example.edu\r\n */\r\nclass Answer {}\r\n");

        assertEquals(List.of("Alice"), header.names());
        assertEquals(List.of("alice@example.edu"), header.emails());
    }

    @Test
    void carriageReturnTerminatesLineComments() {
        var header = parse("// Name: Alice\r// Email: alice@example.edu\rclass Answer {}\r// Name: Body");

        assertEquals(List.of("Alice"), header.names());
        assertEquals(List.of("alice@example.edu"), header.emails());
    }

    @Test
    void readsMalformedUtf8WithReplacementWithoutChangingTheFile() throws IOException {
        Path source = temp.resolve("Answer.java");
        byte[] original = ("\uFEFF// Name: Ali\u0001ce\n// Email ID: alice@example.edu\nclass Answer {}")
                .getBytes(StandardCharsets.UTF_8);
        for (int i = 0; i < original.length; i++) {
            if (original[i] == 1) {
                original[i] = (byte) 0xFF;
            }
        }
        Files.write(source, original);

        var header = StudentHeaderReader.read(source);

        assertEquals(List.of("Ali\uFFFDce"), header.names());
        assertEquals(List.of("alice@example.edu"), header.emails());
        assertArrayEquals(original, Files.readAllBytes(source));
    }

    @Test
    void missingFileRemainsAnIoFailureForTheCallingStage() {
        assertThrows(IOException.class, () -> StudentHeaderReader.read(temp.resolve("missing.java")));
    }

    private static StudentHeaderReader.Header parse(String source) {
        return StudentHeaderReader.parse(source.getBytes(StandardCharsets.UTF_8));
    }
}
