// Tests direct file streaming, stream closure, and I/O failures.
// Testing only; not required by the application.

package autograder.validation;

import autograder.utils.FileUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import static autograder.validation.ValidationTestSupport.*;

public class FileUtilsTest {
    public static void main(String[] args) throws Exception {
        onlyDirectRegularFilesAreReturned();
        returnedDirectoryStreamCanBeClosedWithoutConsumption();
        missingOrNonDirectoryInputRemainsAnIoFailure();
        System.out.println("FileUtilsTest: 3 tests passed");
    }

    private static void onlyDirectRegularFilesAreReturned() throws Exception {
        try (ValidationTestSupport fixture = new ValidationTestSupport()) {
            Path answer = fixture.write("Q1/Answer.java", "answer");
            Path resource = fixture.write("Q1/resource.txt", "resource");
            fixture.write("Q1/nested/Other.java", "nested answer");
            try (Stream<Path> files = FileUtils.streamRegularFiles(fixture.root.resolve("Q1"))) {
                List<Path> actual = files.sorted().toList();
                check(actual.equals(Stream.of(answer, resource).sorted().toList()),
                        "utility returned subdirectories or recursively listed their contents");
            }
            check(Files.readString(answer).equals("answer"), "listing changed source contents");
        }
    }

    private static void returnedDirectoryStreamCanBeClosedWithoutConsumption() throws Exception {
        try (ValidationTestSupport fixture = new ValidationTestSupport()) {
            Path file = fixture.write("Q1/Answer.java", "answer");
            Path directory = file.getParent();
            Stream<Path> files = FileUtils.streamRegularFiles(directory);
            files.close();
            try {
                files.findFirst();
                throw new AssertionError("closed stream should reject consumption");
            } catch (IllegalStateException expected) {
                // The caller retains ownership of the closeable directory stream.
            }
            Files.delete(file);
            Files.delete(directory);
            check(!Files.exists(directory), "closed listing retained a directory handle");
        }
    }

    private static void missingOrNonDirectoryInputRemainsAnIoFailure() throws Exception {
        try (ValidationTestSupport fixture = new ValidationTestSupport()) {
            Path regular = fixture.write("not-a-directory.txt", "contents");
            for (Path path : List.of(fixture.root.resolve("missing"), regular)) {
                try (Stream<Path> ignored = FileUtils.streamRegularFiles(path)) {
                    throw new AssertionError("invalid directory was silently treated as empty: " + path);
                } catch (IOException expected) {
                    // Callers must still decide whether missing question/input folders are acceptable.
                }
            }
        }
    }
}
